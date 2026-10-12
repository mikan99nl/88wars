package net.gate88.wars.lobby;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.match.MatchTeam;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * ロビー用ホログラム管理 (独立3種: TOP10 / 直前チーム順位 / 個人成績個別表示)
 */
public final class HologramManager {
    public static final String TAG_TOP = "88wars_holo_top";
    public static final String TAG_TEAM = "88wars_holo_team";
    public static final String TAG_STATS = "88wars_holo_stats";
    private static final double SPACING = 0.27;

    public enum Type { TOP, TEAM, STATS }

    private final WarsPlugin plugin;

    private final List<ArmorStand> topLines = new ArrayList<>();
    private final List<ArmorStand> teamLines = new ArrayList<>();
    // 個人成績用 (プレイヤーUUIDごとに専用のArmorStandリストを保持)
    private final Map<UUID, List<ArmorStand>> statsLines = new HashMap<>();

    // 直前試合のチーム順位キャッシュ
    private final List<String> lastTeamRanking = new ArrayList<>();

    public HologramManager(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setLocation(Type type, Location loc) {
        FileConfiguration cfg = plugin.getConfig();
        String path = "holograms." + type.name().toLowerCase();
        cfg.set(path + ".world", loc.getWorld().getName());
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        plugin.saveConfig();
        refresh();
    }

    public Location getLocation(Type type) {
        FileConfiguration cfg = plugin.getConfig();
        String path = "holograms." + type.name().toLowerCase();
        String wName = cfg.getString(path + ".world");
        if (wName == null) {
            // 後方互換
            if (type == Type.TOP) return plugin.hologramLocation();
            return null;
        }
        World w = Bukkit.getWorld(wName);
        if (w == null) return null;
        return new Location(w, cfg.getDouble(path + ".x"), cfg.getDouble(path + ".y"), cfg.getDouble(path + ".z"));
    }

    public void setLastTeamRanking(List<MatchTeam> ranking) {
        lastTeamRanking.clear();
        lastTeamRanking.add("&6&l★ 直前の試合結果 (チーム順位) ★");
        if (ranking.isEmpty()) {
            lastTeamRanking.add("&8まだ試合が行われていません");
            refreshTeam();
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            MatchTeam t = ranking.get(i);
            String rank = (i == 0) ? "&e1位" : (i == 1) ? "&72位" : (i == 2) ? "&63位" : "&8" + (i + 1) + "位";
            lastTeamRanking.add(rank + ": " + Colors.code(t.color) + "&l" + Colors.en(t.color) + " &7(キル: &e" + t.kills() + "&7)");
        }
        refreshTeam();
    }

    public void refresh() {
        refreshTop();
        refreshTeam();
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshStats(p);
        }
    }

    // ------------------------------------------------------------ 1. TOP10 ホログラム
    private void refreshTop() {
        Location base = getLocation(Type.TOP);
        if (base == null || base.getWorld() == null) {
            removeTop();
            return;
        }
        List<String> texts = new ArrayList<>();
        texts.add("&6&l★ 88WARS ポイントランキング ★");
        texts.add("&7累計ポイント TOP10");
        List<PointsManager.Entry> top = plugin.points().top(10);
        if (top.isEmpty()) {
            texts.add("&8まだ記録がありません");
        } else {
            int i = 1;
            for (PointsManager.Entry e : top) {
                String c = i == 1 ? "&6" : i == 2 ? "&f" : i == 3 ? "&c" : "&7";
                texts.add(c + "#" + i + " &f" + e.name + " &8- &e" + e.points + "pt");
                i++;
            }
        }

        renderLines(base, texts, topLines, TAG_TOP);
    }

    // ------------------------------------------------------------ 2. 直前チーム順位 ホログラム
    private void refreshTeam() {
        Location base = getLocation(Type.TEAM);
        if (base == null || base.getWorld() == null) {
            removeTeam();
            return;
        }
        List<String> texts = new ArrayList<>(lastTeamRanking);
        if (texts.isEmpty()) {
            texts.add("&6&l★ 直前の試合結果 (チーム順位) ★");
            texts.add("&8まだ試合が行われていません");
        }

        renderLines(base, texts, teamLines, TAG_TEAM);
    }

    // ------------------------------------------------------------ 3. 個人成績 (個別表示)
    public void refreshStats(Player p) {
        Location base = getLocation(Type.STATS);
        if (base == null || base.getWorld() == null || !p.isOnline()) {
            removeStats(p.getUniqueId());
            return;
        }

        PointsManager.Entry entry = plugin.points().find(p.getUniqueId());
        int rank = plugin.points().rankOf(p.getUniqueId());

        List<String> texts = new ArrayList<>();
        texts.add("&b&l★ あなたの戦績 ★");
        texts.add("&fプレイヤー: &e" + p.getName());
        texts.add("&f累計ポイント: &e" + (entry != null ? entry.points : 0) + "pt &7(" + (rank > 0 ? rank + "位" : "圏外") + ")");
        texts.add("&f勝利数: &a" + (entry != null ? entry.wins : 0) + "勝");
        texts.add("&fキル数: &c" + (entry != null ? entry.kills : 0) + "キル");
        texts.add("&f参加試合数: &7" + (entry != null ? entry.played : 0) + "試合");

        List<ArmorStand> pLines = statsLines.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        boolean ok = pLines.size() == texts.size();
        if (ok) {
            for (ArmorStand as : pLines) {
                if (as == null || !as.isValid()) {
                    ok = false;
                    break;
                }
            }
        }

        if (!ok) {
            for (ArmorStand as : pLines) if (as != null && as.isValid()) as.remove();
            pLines.clear();

            for (int i = 0; i < texts.size(); i++) {
                Location l = base.clone().add(0, -i * SPACING, 0);
                ArmorStand as = base.getWorld().spawn(l, ArmorStand.class, a -> {
                    a.setInvisible(true);
                    a.setMarker(true);
                    a.setGravity(false);
                    a.setInvulnerable(true);
                    a.setPersistent(false);
                    a.setCustomNameVisible(true);
                    a.addScoreboardTag(TAG_STATS);
                    // ★ 本人以外には見せない設定
                    a.setVisibleByDefault(false);
                });
                p.showEntity(plugin, as);
                pLines.add(as);
            }
        }

        for (int i = 0; i < texts.size(); i++) {
            pLines.get(i).customName(Msg.c(texts.get(i)));
        }
    }

    public void removeStats(UUID uuid) {
        List<ArmorStand> list = statsLines.remove(uuid);
        if (list != null) {
            for (ArmorStand as : list) if (as != null && as.isValid()) as.remove();
        }
    }

    private void renderLines(Location base, List<String> texts, List<ArmorStand> holder, String tag) {
        boolean ok = holder.size() == texts.size();
        if (ok) {
            for (ArmorStand a : holder) {
                if (a == null || !a.isValid()) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            for (ArmorStand a : holder) if (a != null && a.isValid()) a.remove();
            holder.clear();
            try {
                base.getChunk().addPluginChunkTicket(plugin);
            } catch (Throwable ignored) {}
            base.getChunk().load();

            for (int i = 0; i < texts.size(); i++) {
                Location l = base.clone().add(0, -i * SPACING, 0);
                holder.add(base.getWorld().spawn(l, ArmorStand.class, as -> {
                    as.setInvisible(true);
                    as.setMarker(true);
                    as.setGravity(false);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.setCustomNameVisible(true);
                    as.addScoreboardTag(tag);
                }));
            }
        }
        for (int i = 0; i < texts.size(); i++) {
            holder.get(i).customName(Msg.c(texts.get(i)));
        }
    }

    private void removeTop() {
        for (ArmorStand a : topLines) if (a != null && a.isValid()) a.remove();
        topLines.clear();
    }

    private void removeTeam() {
        for (ArmorStand a : teamLines) if (a != null && a.isValid()) a.remove();
        teamLines.clear();
    }

    public void removeAll() {
        removeTop();
        removeTeam();
        for (List<ArmorStand> list : statsLines.values()) {
            for (ArmorStand a : list) if (a != null && a.isValid()) a.remove();
        }
        statsLines.clear();

        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG_TOP) || e.getScoreboardTags().contains(TAG_TEAM) || e.getScoreboardTags().contains(TAG_STATS)) {
                    e.remove();
                }
            }
        }
    }
}