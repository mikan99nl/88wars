package net.gate88.wars.lobby;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.match.Match;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** ロビー: 待機・投票・抽選ルーレット・カウントダウン・試合開始 */
public final class LobbyManager {
    public static final String RANDOM = "RANDOM";
    private static final int[] ROULETTE_DELAYS = {2, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5, 6, 8, 10, 12};

    private final WarsPlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, String> votes = new HashMap<>();
    private final NamespacedKey itemKey;
    private BukkitTask task;
    private int countdown = -1;
    private boolean busy; // ルーレット中など
    private int tickCount;

    public LobbyManager(WarsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "lobby_item");
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
    }

    // ------------------------------------------------------------ query
    public Location lobbyLocation() {
        Location l = plugin.lobbyLocationRaw();
        if (l != null && l.getWorld() != null) return l;
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public List<Player> lobbyPlayers() {
        Match m = plugin.match();
        List<Player> l = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (m != null && m.participant(p) != null) continue;
            l.add(p);
        }
        return l;
    }

    public boolean isPlayable(WarsMode md) {
        return md.enabled() && !plugin.arenas().readyFor(md.arenaType()).isEmpty();
    }

    public List<WarsMode> playableModes() {
        List<WarsMode> l = new ArrayList<>();
        for (WarsMode md : plugin.modes().all()) if (isPlayable(md)) l.add(md);
        return l;
    }

    public String voteOf(Player p) {
        return votes.get(p.getUniqueId());
    }

    public int votesFor(String id) {
        int n = 0;
        Match m = plugin.match();
        for (Map.Entry<UUID, String> e : votes.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            if (m != null && m.participant(p) != null) continue;
            if (e.getValue().equalsIgnoreCase(id)) n++;
        }
        return n;
    }

    public int maxParticipants() {
        return Math.max(1, Math.min(16, plugin.getConfig().getInt("lobby.max-players", 16)));
    }

    // ------------------------------------------------------------ vote
    public void toggleVote(Player p, String id) {
        String cur = votes.get(p.getUniqueId());
        if (id.equalsIgnoreCase(cur)) {
            votes.remove(p.getUniqueId());
            Msg.send(p, "&7投票を取り消しました");
            Sfx.click(p);
        } else {
            votes.put(p.getUniqueId(), id.toUpperCase().equals(RANDOM) ? RANDOM : id.toLowerCase());
            WarsMode md = plugin.modes().get(id);
            Msg.send(p, "&a投票しました: &e" + (RANDOM.equalsIgnoreCase(id) ? "ランダム抽選" : md.displayName));
            Sfx.vote(p);
        }
        VoteMenu.refreshAll(plugin);
    }

    public void clearVote(UUID u) {
        votes.remove(u);
    }

    // ------------------------------------------------------------ lobby items / transfer
    public boolean isLobbyItem(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    public void giveItems(Player p) {
        ItemStack star = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = star.getItemMeta();
        meta.displayName(Msg.c("&e&l投票メニュー &7(右クリック)"));
        meta.lore(List.of(Msg.c("&7ゲームモードに1票入れる / ランダム抽選")));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "vote");
        star.setItemMeta(meta);
        p.getInventory().setItem(4, star);
        p.getInventory().setHeldItemSlot(4);
    }

    /** 参加時・試合後にロビーへ戻す */
    public void sendToLobby(Player p) {
        Match.prepare(p);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(lobbyLocation());
        giveItems(p);
        Sfx.teleport(p);
        renderSidebar(p);
    }

    public void joinSetup(Player p) {
        if (plugin.getConfig().getBoolean("lobby.clear-inventory", true)) {
            sendToLobby(p);
        } else {
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lobbyLocation());
            giveItems(p);
            renderSidebar(p);
        }
    }

    public void onMatchClosed() {
        countdown = -1;
        busy = false;
    }

    // ------------------------------------------------------------ tick
    private void tick() {
        tickCount++;
        Match m = plugin.match();
        List<Player> waiting = lobbyPlayers();

        if (tickCount % 10 == 0) plugin.holograms().refresh(); // 消えていたら復元

        if (m != null || busy) {
            for (Player p : waiting) renderSidebar(p);
            return;
        }

        int min = Math.max(1, plugin.getConfig().getInt("lobby.min-players", 2));
        if (playableModes().isEmpty()) {
            countdown = -1;
            for (Player p : waiting) {
                if (p.hasPermission("wars.admin")) Msg.actionBar(p, "&cプレイ可能なアリーナがありません &7(/wars admin で設定)");
                renderSidebar(p);
            }
            return;
        }
        if (waiting.size() < min) {
            if (countdown >= 0) {
                countdown = -1;
                for (Player p : waiting) {
                    Msg.send(p, "&c人数が足りないためカウントダウンを中止しました");
                    Sfx.countdownCancel(p);
                }
            }
            for (Player p : waiting) {
                Msg.actionBar(p, "&7あと &e" + (min - waiting.size()) + " &7人で開始します");
                renderSidebar(p);
            }
            return;
        }

        int full = Math.max(1, plugin.getConfig().getInt("lobby.full-countdown-seconds", 10));
        if (countdown < 0) {
            countdown = Math.max(1, plugin.getConfig().getInt("lobby.countdown-seconds", 30));
            for (Player p : waiting) {
                Msg.send(p, "&a" + countdown + "秒後に試合を開始します! &7(星で投票できます)");
                Sfx.countdownStart(p);
            }
        }
        if (waiting.size() >= maxParticipants() && countdown > full) countdown = full;

        if (countdown <= 0) {
            countdown = -1;
            beginSelection(waiting, false, null);
            return;
        }
        if (countdown <= 5 || countdown == 10 || countdown == 20 || countdown == 30) {
            for (Player p : waiting) {
                if (countdown <= 5) Msg.title(p, "&e&l" + countdown, "&7試合開始まで", 0, 20, 5);
                Msg.actionBar(p, "&a試合開始まで &e" + countdown + " &a秒");
                Sfx.countdown(p, countdown);
            }
        }
        countdown--;
        for (Player p : waiting) renderSidebar(p);
    }

    // ------------------------------------------------------------ selection
    public boolean forceStart(WarsMode forced) {
        if (plugin.match() != null || busy) return false;
        List<Player> waiting = lobbyPlayers();
        if (waiting.isEmpty()) return false;
        countdown = -1;
        beginSelection(waiting, true, forced);
        return true;
    }

    private void beginSelection(List<Player> waiting, boolean force, WarsMode forced) {
        List<WarsMode> playable = playableModes();
        if (playable.isEmpty()) return;
        if (forced != null) {
            announceChosen(forced, "管理者により開始");
            Bukkit.getScheduler().runTaskLater(plugin, () -> beginMatch(forced, force), 30L);
            busy = true;
            return;
        }
        String sel = plugin.getConfig().getString("lobby.mode-select", "VOTE");
        Map<String, Integer> tally = new HashMap<>();
        int randomVotes = 0;
        for (Player p : waiting) {
            String v = votes.get(p.getUniqueId());
            if (v == null) continue;
            if (RANDOM.equals(v)) randomVotes++;
            else if (plugin.modes().get(v) != null && isPlayable(plugin.modes().get(v))) tally.merge(v, 1, Integer::sum);
        }
        int best = 0;
        for (int n : tally.values()) best = Math.max(best, n);

        List<WarsMode> candidates = new ArrayList<>(playable);
        boolean lottery = true;
        String why = "ランダム抽選";
        if (!"RANDOM".equalsIgnoreCase(sel) && best > 0 && best > randomVotes) {
            List<WarsMode> top = new ArrayList<>();
            for (WarsMode md : playable) if (tally.getOrDefault(md.id, 0) == best) top.add(md);
            if (top.size() == 1) {
                lottery = false;
                WarsMode chosen = top.get(0);
                announceChosen(chosen, "投票 " + best + "票");
                busy = true;
                Bukkit.getScheduler().runTaskLater(plugin, () -> beginMatch(chosen, force), 40L);
                return;
            }
            candidates = top;
            why = "同票のため抽選";
        }
        WarsMode chosen = candidates.get(random.nextInt(candidates.size()));
        busy = true;
        runRoulette(candidates, chosen, why, force);
    }

    private void announceChosen(WarsMode md, String why) {
        Msg.broadcast("&aゲーム決定: &e&l" + md.displayName + " &7(" + why + ")");
        for (Player p : lobbyPlayers()) {
            Msg.title(p, "&a&l" + md.displayName, "&7" + why, 0, 40, 10);
            Sfx.lotteryResult(p);
        }
    }

    /** ディスペンサーがアイテムを出す音を連続で鳴らしながらゲーム名を回す */
    private void runRoulette(List<WarsMode> candidates, WarsMode chosen, String why, boolean force) {
        int steps = ROULETTE_DELAYS.length;
        int n = candidates.size();
        int chosenIdx = candidates.indexOf(chosen);
        Msg.broadcast("&d&lランダム抽選スタート!");
        spin(0, steps, n, chosenIdx, candidates, chosen, why, force);
    }

    private void spin(int step, int steps, int n, int chosenIdx, List<WarsMode> candidates, WarsMode chosen,
                      String why, boolean force) {
        if (step >= steps) {
            announceChosen(chosen, why);
            Bukkit.getScheduler().runTaskLater(plugin, () -> beginMatch(chosen, force), 40L);
            return;
        }
        int idx = Math.floorMod(chosenIdx - (steps - 1 - step), n);
        WarsMode show = candidates.get(idx);
        float progress = step / (float) steps;
        for (Player p : lobbyPlayers()) {
            Msg.title(p, "&d抽選中...", "&f" + show.displayName, 0, 12, 0);
            Sfx.lotteryTick(p, progress);
        }
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> spin(step + 1, steps, n, chosenIdx, candidates, chosen, why, force), ROULETTE_DELAYS[step]);
    }

    private void beginMatch(WarsMode md, boolean force) {
        busy = false;
        if (plugin.match() != null) return;
        List<Player> waiting = lobbyPlayers();
        int min = Math.max(1, plugin.getConfig().getInt("lobby.min-players", 2));
        if (waiting.isEmpty() || (!force && waiting.size() < min)) {
            Msg.broadcast("&c人数が足りなくなったため開始を中止しました");
            return;
        }
        if (!force && waiting.size() < md.minPlayers()) {
            Msg.broadcast("&c" + md.displayName + " は最低 " + md.minPlayers() + " 人必要です (現在 " + waiting.size() + " 人)");
            return;
        }
        List<Arena> arenas = plugin.arenas().readyFor(md.arenaType());
        if (arenas.isEmpty()) {
            Msg.broadcast("&cアリーナが見つかりません: " + md.displayName);
            return;
        }
        Arena arena = arenas.get(random.nextInt(arenas.size()));
        int cap = md.teamCount() > 0 ? maxParticipants() : Math.min(maxParticipants(), arena.spawns.size() * md.teamSize());
        List<Player> part = new ArrayList<>(waiting);
        Collections.shuffle(part);
        if (part.size() > cap) {
            for (Player extra : part.subList(cap, part.size())) {
                Msg.send(extra, "&e満員のため次の試合をお待ちください");
            }
            part = new ArrayList<>(part.subList(0, cap));
        }
        for (Player p : part) votes.remove(p.getUniqueId());
        Match m = new Match(plugin, md, arena, part);
        plugin.setMatch(m);
        m.start();
    }

    // ------------------------------------------------------------ sidebar
    public void renderSidebar(Player p) {
        PointsManager pm = plugin.points();
        Match m = plugin.match();
        List<String> lines = new ArrayList<>();
        int waiting = lobbyPlayers().size();
        lines.add("&7ロビー");
        lines.add("");
        if (m != null) {
            lines.add("&e試合進行中: &f" + m.mode().displayName);
            lines.add("&f残り: &a" + m.aliveCount() + "&7人 / &b" + Msg.time(m.timeLeft()));
        } else if (busy) {
            lines.add("&dゲーム抽選中...");
        } else if (countdown >= 0) {
            lines.add("&a開始まで: &e" + countdown + "秒");
        } else {
            lines.add("&7待機中...");
        }
        lines.add("&f待機人数: &a" + waiting);
        String v = votes.get(p.getUniqueId());
        String vname = v == null ? "&7未投票" : RANDOM.equals(v) ? "&dランダム" : "&e" + plugin.modes().get(v).displayName;
        lines.add("&f投票: " + vname);
        lines.add("");
        lines.add("&f累計point: &e" + pm.total(p.getUniqueId()));
        int r = pm.rankOf(p.getUniqueId());
        lines.add("&f総合順位: &e" + (r == 0 ? "--" : r + "位"));
        plugin.sidebar(p).render(p, "&e&l88WARS", lines);
    }
}
