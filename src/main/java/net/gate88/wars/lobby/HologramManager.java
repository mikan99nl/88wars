package net.gate88.wars.lobby;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

/** ロビーの累計ポイントランキング (ArmorStand式 = 統合版でも表示できる) */
public final class HologramManager {
    public static final String TAG = "88wars_holo";
    private static final double SPACING = 0.27;

    private final WarsPlugin plugin;
    private final List<ArmorStand> lines = new ArrayList<>();

    public HologramManager(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    private List<String> build() {
        List<String> t = new ArrayList<>();
        t.add("&6&l★ 88WARS ポイントランキング ★");
        t.add("&7累計ポイント");
        List<PointsManager.Entry> top = plugin.points().top(Math.max(1, plugin.getConfig().getInt("hologram.lines", 10)));
        if (top.isEmpty()) {
            t.add("&8まだ記録がありません");
        }
        int i = 1;
        for (PointsManager.Entry e : top) {
            String c = i == 1 ? "&6" : i == 2 ? "&f" : i == 3 ? "&c" : "&7";
            t.add(c + "#" + i + " &f" + e.name + " &8- &e" + e.points + "pt");
            i++;
        }
        return t;
    }

    public void refresh() {
        Location base = plugin.hologramLocation();
        if (base == null || base.getWorld() == null) {
            removeAll();
            return;
        }
        List<String> texts = build();
        boolean ok = lines.size() == texts.size();
        if (ok) {
            for (ArmorStand a : lines) {
                if (a == null || !a.isValid()) {
                    ok = false;
                    break;
                }
            }
        }
        if (!ok) {
            removeAll();
            try {
                base.getChunk().addPluginChunkTicket(plugin);
            } catch (Throwable ignored) {
            }
            base.getChunk().load();
            for (int i = 0; i < texts.size(); i++) {
                Location l = base.clone().add(0, -i * SPACING, 0);
                lines.add(base.getWorld().spawn(l, ArmorStand.class, as -> {
                    as.setInvisible(true);
                    as.setMarker(true);
                    as.setGravity(false);
                    as.setInvulnerable(true);
                    as.setPersistent(false);
                    as.setCustomNameVisible(true);
                    as.addScoreboardTag(TAG);
                }));
            }
        }
        for (int i = 0; i < texts.size(); i++) {
            lines.get(i).customName(Msg.c(texts.get(i)));
        }
    }

    public void removeAll() {
        for (ArmorStand a : lines) {
            if (a != null && a.isValid()) a.remove();
        }
        lines.clear();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) e.remove();
            }
        }
    }
}
