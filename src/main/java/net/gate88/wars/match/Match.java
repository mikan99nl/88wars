package net.gate88.wars.match;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.lobby.PodiumManager;
import net.gate88.wars.mode.BorderSpec;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Pos;
import net.gate88.wars.util.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

/** 1試合の進行を管理する。 */
public final class Match {
    public enum State { PREPARING, RUNNING, ENDING, CLOSED }

    private final WarsPlugin plugin;
    private final WarsMode mode;
    private final Arena arena;
    private final World world;
    private final Map<UUID, MatchPlayer> players = new LinkedHashMap<>();
    private final List<MatchTeam> teams = new ArrayList<>();
    private final List<MatchTeam> eliminationOrder = new ArrayList<>();
    private final BlockTracker blocks;
    private final BorderSpec border;

    private State state = State.PREPARING;
    private int elapsed = 0;
    private int eliminationCounter = 0;
    private BukkitTask ticker;
    private BukkitTask particleTask;
    private BukkitTask returnTask;
    private boolean aborted;
    private final List<org.bukkit.entity.Entity> tracked = new ArrayList<>();

    public Match(WarsPlugin plugin, WarsMode mode, Arena arena, List<Player> participants) {
        this.plugin = plugin;
        this.mode = mode;
        this.arena = arena;
        this.world = arena.world();
        this.blocks = new BlockTracker(plugin);
        this.border = mode.border();

        List<Player> list = new ArrayList<>(participants);
        Collections.shuffle(list);
        int count = mode.teamCount();
        if (count > 0) {
            int n = Math.max(1, Math.min(count, list.size()));
            for (int i = 0; i < n; i++) teams.add(new MatchTeam(i, Colors.ALL.get(i % Colors.ALL.size())));
            for (int i = 0; i < list.size(); i++) {
                MatchTeam t = teams.get(i % n);
                MatchPlayer mp = new MatchPlayer(list.get(i));
                mp.team = t;
                t.members.add(mp);
                players.put(mp.uuid, mp);
            }
        } else {
            List<DyeColor> colors = new ArrayList<>(Colors.ALL);
            Collections.shuffle(colors);
            int size = mode.teamSize();
            MatchTeam cur = null;
            for (int i = 0; i < list.size(); i++) {
                if (i % size == 0) {
                    cur = new MatchTeam(teams.size(), colors.get(teams.size() % colors.size()));
                    teams.add(cur);
                }
                MatchPlayer mp = new MatchPlayer(list.get(i));
                mp.team = cur;
                cur.members.add(mp);
                players.put(mp.uuid, mp);
            }
        }
    }

    // ------------------------------------------------------------ getters
    public WarsMode mode() { return mode; }
    public Arena arena() { return arena; }
    public World world() { return world; }
    public State state() { return state; }
    public int elapsed() { return elapsed; }
    public BlockTracker blocks() { return blocks; }
    public List<MatchTeam> teams() { return teams; }
    public Collection<MatchPlayer> allPlayers() { return players.values(); }
    public boolean pvpEnabled() { return state == State.RUNNING; }
    public boolean isOver() { return state == State.ENDING || state == State.CLOSED; }

    public MatchPlayer participant(Player p) {
        MatchPlayer mp = players.get(p.getUniqueId());
        return mp != null && !mp.left ? mp : null;
    }

    public MatchPlayer participant(UUID u) {
        MatchPlayer mp = players.get(u);
        return mp != null && !mp.left ? mp : null;
    }

    public int aliveCount() {
        int n = 0;
        for (MatchPlayer mp : players.values()) if (mp.alive) n++;
        return n;
    }

    public int timeLeft() {
        return Math.max(0, mode.durationSeconds() - elapsed);
    }

    public double borderRadius() {
        return border.enabled() ? border.radiusAt(elapsed) : Double.MAX_VALUE;
    }

    public Location center() {
        return arena.center();
    }

    // ------------------------------------------------------------ start
    public void start() {
        plugin.podium().clear();
        plugin.clearTrackedTridents();
        cleanWorldEntities();
        List<Pos> spawns = new ArrayList<>(arena.spawns);
        Collections.shuffle(spawns);
        for (MatchTeam t : teams) {
            Pos sp = spawns.get(t.index % spawns.size());
            int idx = 0;
            for (MatchPlayer mp : t.members) {
                Player p = mp.player();
                if (p == null) continue;
                prepare(p);
                p.teleport(sp.toLocation(world).add((idx % 3) - 1.0, 0, (idx / 3) * 1.0));
                idx++;
                Msg.title(p, "&e&l" + mode.displayName, "&7" + mode.graceSeconds() + "秒後に装備が配布されます", 5, 50, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.2f);
                Msg.send(p, "あなたの色: " + Colors.code(t.color) + Colors.en(t.color));
            }
        }
        mode.onStart(this);
        updateSidebars();

        // ★ テレポート＆サイドバー初期化直後に、頭上ネームタグとタブリスト表示を全プレイヤーに確実に適用
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (MatchTeam t : teams) {
                for (MatchPlayer mp : t.members) {
                    Player p = mp.player();
                    if (p != null && p.isOnline()) {
                        Colors.applyPlayerDisplay(p, t.color);
                    }
                }
            }
        });

        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        if (border.enabled()) {
            long iv = Math.max(2, plugin.getConfig().getInt("border.interval-ticks", 4));
            particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drawBorder, 10L, iv);
        }
    }

    public static void prepare(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        p.setHealth(attr != null ? attr.getValue() : 20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
        p.setExp(0f);
        p.setLevel(0);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setInvulnerable(false);
    }

    // ------------------------------------------------------------ tick
    private void tick() {
        if (isOver()) return;
        elapsed++;

        if (state == State.PREPARING) {
            int remain = mode.graceSeconds() - elapsed;
            if (remain <= 0) {
                state = State.RUNNING;
                mode.onGraceEnd(this);
            } else {
                for (MatchPlayer mp : players.values()) {
                    Player p = mp.player();
                    if (p == null || mp.left) continue;
                    Msg.actionBar(p, "&e装備配布まで &c" + remain + " &e秒 &7(それまでPvP無効)");
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f + (5 - remain) * 0.1f);
                }
            }
        }

        int interval = Math.max(1, plugin.getConfig().getInt("points.survival-interval-seconds", 5));
        int survPts = plugin.getConfig().getInt("points.survival", 1);
        for (MatchPlayer mp : players.values()) {
            if (!mp.alive) continue;
            mp.survivedSeconds++;
            if (survPts > 0 && elapsed % interval == 0) mp.points += survPts;
        }

        borderTick();
        if (isOver()) return;
        mode.onSecond(this);
        if (isOver()) return;

        if (elapsed >= mode.durationSeconds()) {
            finish(mode.pickTimeUpWinner(this), "時間切れ");
            return;
        }
        updateSidebars();
    }

    private void borderTick() {
        if (!border.enabled()) return;
        if (elapsed == border.shrinkStartSeconds()) {
            broadcastToMatch("&c&lボーダーが縮小を開始しました! &7(外側は毎秒ダメージ)");
            playAll(Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);
        }
        double half = border.radiusAt(elapsed);
        double cx = arena.cx + 0.5, cz = arena.cz + 0.5;
        for (MatchPlayer mp : new ArrayList<>(players.values())) {
            if (!mp.alive || mp.left) continue;
            Player p = mp.player();
            if (p == null) continue;
            Location l = p.getLocation();
            if (Math.abs(l.getX() - cx) > half || Math.abs(l.getZ() - cz) > half) {
                Msg.actionBar(p, "&c&lボーダーの外です! 中央へ戻れ!");
                double dmg = border.damagePerSecond();
                double hp = p.getHealth();
                if (hp - dmg <= 0.0) {
                    eliminate(mp, null, "ボーダーに焼かれた");
                    if (isOver()) return;
                } else {
                    p.setHealth(hp - dmg);
                    p.playHurtAnimation(0f);
                }
            }
        }
    }

    private void drawBorder() {
        if (isOver() || !border.enabled()) return;
        double half = border.radiusAt(elapsed);
        double cx = arena.cx + 0.5, cz = arena.cz + 0.5;
        String pname = plugin.getConfig().getString("border.particle", "DUST").toUpperCase();
        Particle particle;
        try {
            particle = Particle.valueOf(pname);
        } catch (IllegalArgumentException ex) {
            particle = Particle.DUST;
        }
        Particle.DustOptions dust = null;
        if (particle == Particle.DUST) {
            String[] rgb = plugin.getConfig().getString("border.dust-color", "255,60,60").split(",");
            int r = 255, g = 60, b = 60;
            try {
                r = Integer.parseInt(rgb[0].trim());
                g = Integer.parseInt(rgb[1].trim());
                b = Integer.parseInt(rgb[2].trim());
            } catch (Exception ignored) {
            }
            dust = new Particle.DustOptions(Color.fromRGB(r, g, b), 1.6f);
        }
        final double step = 2.0;
        for (MatchPlayer mp : players.values()) {
            if (mp.left) continue;
            Player p = mp.player();
            if (p == null || !p.getWorld().equals(world)) continue;
            Location l = p.getLocation();
            double px = l.getX(), pz = l.getZ(), py = l.getY();
            for (int s = -1; s <= 1; s += 2) {
                double ex = cx + s * half;
                if (Math.abs(px - ex) < 18) {
                    double start = Math.floor((pz - 12) / step) * step;
                    for (double z = start; z <= pz + 12; z += step) {
                        if (Math.abs(z - cz) > half) continue;
                        spawnColumn(p, particle, dust, ex, py, z);
                    }
                }
                double ez = cz + s * half;
                if (Math.abs(pz - ez) < 18) {
                    double start = Math.floor((px - 12) / step) * step;
                    for (double x = start; x <= px + 12; x += step) {
                        if (Math.abs(x - cx) > half) continue;
                        spawnColumn(p, particle, dust, x, py, ez);
                    }
                }
            }
        }
    }

    private static void spawnColumn(Player p, Particle particle, Particle.DustOptions dust, double x, double py, double z) {
        for (double y = py - 3; y <= py + 5; y += 2.5) {
            if (dust != null) p.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, dust);
            else p.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------ sidebar
    public void updateSidebars() {
        boolean extra = plugin.getConfig().getBoolean("scoreboard.extra-lines", true);
        int alive = aliveCount();
        int total = players.size();
        for (MatchPlayer mp : players.values()) {
            if (mp.left) continue;
            Player p = mp.player();
            if (p == null) continue;
            List<String> lines = new ArrayList<>();
            lines.add("&7ゲーム: &f" + mode.displayName);
            lines.add("");
            lines.add("&f残りプレイヤー: &a" + alive + "&7/" + total);
            boolean team = mode.teamMode();
            lines.add("&f" + (team ? "チームpoint: " : "あなたのpoint: ") + "&e" + (team ? mp.team.points() : mp.points));
            if (!mp.alive) lines.add("&c敗退 &7(観戦中)");
            if (extra) {
                lines.add("");
                lines.add("&f残り時間: &b" + Msg.time(timeLeft()));
                lines.addAll(mode.extraSidebar(this, mp));
            }
            plugin.sidebar(p).render(p, "&e&l88WARS", lines);
        }
    }

    // ------------------------------------------------------------ elimination
    public void recordAttack(Player victim, Player attacker) {
        MatchPlayer v = participant(victim);
        MatchPlayer a = participant(attacker);
        if (v == null || a == null || v == a) return;
        v.lastAttacker = a.uuid;
        v.lastAttackMillis = System.currentTimeMillis();
    }

    public void eliminate(MatchPlayer victim, MatchPlayer killer, String cause) {
        if (isOver() || !victim.alive) return;
        if (killer == null && victim.lastAttacker != null
                && System.currentTimeMillis() - victim.lastAttackMillis <= 8000) {
            killer = participant(victim.lastAttacker);
        }
        if (killer == victim || (killer != null && !killer.alive && killer.team == victim.team)) killer = null;

        victim.alive = false;
        victim.eliminatedAtSecond = elapsed;
        victim.team.eliminated = victim.team.aliveCount() == 0;
        if (victim.team.eliminated && !eliminationOrder.contains(victim.team)) eliminationOrder.add(victim.team);

        Player vp = victim.player();

        if (killer != null) {
            killer.kills++;
            int killPts = plugin.getConfig().getInt("points.kill", 30);
            killer.points += killPts;
            Player kp = killer.player();
            if (kp != null && !killer.left) {
                kp.playSound(kp.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                Msg.actionBar(kp, "&a+ " + killPts + "pt &7(キル)");

                String victimDisplayName = Colors.code(victim.team.color) + Colors.en(victim.team.color) + " &f" + victim.name;
                Msg.title(kp, victimDisplayName, "&a+" + killPts + "pt &7(KILL)", 2, 25, 5);
            }

            if (vp != null && !victim.left) {
                Location strikeLoc = vp.getLocation().clone();
                if (strikeLoc.getY() < arena.cy) {
                    strikeLoc.setY(arena.cy + 1.0);
                }
                world.strikeLightningEffect(strikeLoc);
            }

            broadcastToMatch(Colors.code(victim.team.color) + Colors.en(victim.team.color) + " &f" + victim.name
                    + " &7は " + Colors.code(killer.team.color) + Colors.en(killer.team.color) + " &f" + killer.name + " &7に倒された");
        } else {
            broadcastToMatch(Colors.code(victim.team.color) + Colors.en(victim.team.color) + " &f" + victim.name + " &7は脱落した &8(" + cause + ")");
        }

        if (vp != null && !victim.left) {
            Location l = vp.getLocation();
            world.spawnParticle(Particle.POOF, l.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            world.playSound(l, Sound.ENTITY_PLAYER_DEATH, 1.0f, 1.0f);
            List<org.bukkit.inventory.ItemStack> loot = new ArrayList<>();
            for (org.bukkit.inventory.ItemStack it : vp.getInventory().getContents()) {
                if (it == null || it.getType().isAir() || it.getType().name().endsWith("_WOOL")) continue;
                loot.add(it.clone());
            }
            mode.onDeathLoot(this, victim, l, loot);
            vp.getInventory().clear();
            var attr = vp.getAttribute(Attribute.MAX_HEALTH);
            vp.setHealth(attr != null ? attr.getValue() : 20.0);
            vp.setFireTicks(0);
            vp.setFallDistance(0f);
            vp.setGameMode(GameMode.SPECTATOR);
            Location c = center();
            if (c != null && l.getY() < arena.cy - 10) {
                vp.teleport(c.clone().add(0, 10, 0));
            }
            Msg.title(vp, "&c&l敗退", "&7観戦モードになりました", 0, 40, 10);
        }
        mode.onEliminated(this, victim);
        checkEnd();
    }

    public void onQuit(Player p) {
        MatchPlayer mp = participant(p);
        if (mp == null) return;
        if (mp.alive && !isOver()) {
            eliminate(mp, null, "退出");
        }
        mp.left = true;
    }

    private void checkEnd() {
        if (isOver()) return;
        List<MatchTeam> aliveTeams = new ArrayList<>();
        for (MatchTeam t : teams) if (t.aliveCount() > 0) aliveTeams.add(t);
        if (aliveTeams.size() <= 1) {
            finish(aliveTeams.isEmpty() ? null : aliveTeams.get(0), aliveTeams.isEmpty() ? "全員脱落" : "最後の生き残り");
        }
    }

    // ------------------------------------------------------------ finish
    private List<MatchTeam> buildRanking(MatchTeam winner) {
        List<MatchTeam> ranking = new ArrayList<>();
        if (winner != null) ranking.add(winner);
        List<MatchTeam> alive = new ArrayList<>();
        for (MatchTeam t : teams) if (t != winner && t.aliveCount() > 0) alive.add(t);
        alive.sort(Comparator.<MatchTeam>comparingInt(t -> mode.standing(this, t)).reversed()
                .thenComparing(Comparator.<MatchTeam>comparingInt(MatchTeam::kills).reversed())
                .thenComparing(Comparator.<MatchTeam>comparingInt(MatchTeam::points).reversed()));
        ranking.addAll(alive);
        List<MatchTeam> dead = new ArrayList<>(eliminationOrder);
        Collections.reverse(dead);
        for (MatchTeam t : dead) if (!ranking.contains(t)) ranking.add(t);
        for (MatchTeam t : teams) if (!ranking.contains(t)) ranking.add(t);
        return ranking;
    }

    public void finish(MatchTeam winner, String reason) {
        if (isOver()) return;
        state = State.ENDING;
        cancelTasks();

        List<Integer> bonus = plugin.getConfig().getIntegerList("points.rank-bonus");
        List<MatchTeam> ranking = buildRanking(winner);
        for (int i = 0; i < ranking.size(); i++) {
            int b = bonus.isEmpty() ? 0 : bonus.get(Math.min(i, bonus.size() - 1));
            for (MatchPlayer mp : ranking.get(i).members) mp.points += b;
        }

        List<MatchPlayer> sorted = new ArrayList<>(players.values());
        sorted.sort(Comparator.<MatchPlayer>comparingInt(mp -> mp.points).reversed()
                .thenComparing(Comparator.<MatchPlayer>comparingInt(mp -> mp.kills).reversed())
                .thenComparing(Comparator.<MatchPlayer>comparingInt(mp -> mp.survivedSeconds).reversed()));

        PointsManager pm = plugin.points();
        for (MatchPlayer mp : sorted) {
            PointsManager.Entry e = pm.entry(mp.uuid, mp.name);
            e.points += mp.points;
            e.kills += mp.kills;
            e.played++;
            if (winner != null && mp.team == winner) e.wins++;
        }
        pm.save();

        MatchPlayer champ = sorted.isEmpty() ? null : sorted.get(0);
        StringBuilder sb = new StringBuilder();
        sb.append("\n&6&l==== 88WARS 結果 ").append("&7(").append(mode.displayName).append(" / ").append(reason).append(")&6&l ====\n");
        if (champ != null) {
            sb.append("&e&l優勝: &f&l").append(champ.name).append(" &e").append(champ.points).append("pt\n");
        }
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            MatchPlayer mp = sorted.get(i);
            String rank = i == 0 ? "&e1位" : i == 1 ? "&72位" : "&63位";
            sb.append(rank).append(" &f").append(mp.name).append(" &8- &e").append(mp.points).append("pt &7(キル ")
                    .append(mp.kills).append(")\n");
        }
        sb.append("&6&l================================");
        for (String line : sb.toString().split("\n")) {
            Bukkit.broadcast(Msg.c(line));
        }

        for (MatchPlayer mp : players.values()) {
            Player p = mp.player();
            if (p == null || mp.left) continue;
            p.setInvulnerable(true);
            if (champ != null) {
                Msg.title(p, "&6&l優勝 &f&l" + champ.name, "&e" + champ.points + "pt &7- " + reason, 5, 100, 20);
            }
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        List<PodiumManager.Entry> podium = new ArrayList<>();
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            MatchPlayer mp = sorted.get(i);
            podium.add(new PodiumManager.Entry(i + 1, mp.uuid, mp.name, mp.points));
        }

        int delay = Math.max(1, plugin.getConfig().getInt("lobby.return-delay-seconds", 5));
        final int[] left = {delay};
        returnTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (left[0] <= 0) {
                if (returnTask != null) returnTask.cancel();
                close(podium);
                return;
            }
            for (MatchPlayer mp : players.values()) {
                Player p = mp.player();
                if (p == null || mp.left) continue;
                Msg.actionBar(p, "&e" + left[0] + "秒後にロビーへ転送されます");
            }
            left[0]--;
        }, 0L, 20L);
    }

    public void abort() {
        if (state == State.CLOSED) return;
        aborted = true;
        state = State.ENDING;
        cancelTasks();
        close(List.of());
    }

    private void cancelTasks() {
        if (ticker != null) ticker.cancel();
        if (particleTask != null) particleTask.cancel();
    }

    private void close(List<PodiumManager.Entry> podium) {
        if (state == State.CLOSED) return;
        state = State.CLOSED;
        cancelTasks();
        if (returnTask != null) returnTask.cancel();
        blocks.restoreAll();
        plugin.clearTrackedTridents();
        cleanWorldEntities();
        for (org.bukkit.entity.Entity e : tracked) if (e.isValid()) e.remove();
        tracked.clear();
        mode.onEnd(this);
        if (!aborted && !podium.isEmpty()) plugin.podium().show(podium);
        for (MatchPlayer mp : players.values()) {
            Player p = mp.player();
            if (p != null) {
                p.setInvulnerable(false);
                plugin.lobby().sendToLobby(p);
                Colors.applyLobbyDisplay(p);
            }
        }
        plugin.matchClosed(this);
    }

    public void cleanWorldEntities() {
        if (world == null) return;
        for (org.bukkit.entity.Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (entity instanceof org.bukkit.entity.ItemFrame || entity instanceof org.bukkit.entity.GlowItemFrame) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand) continue;
            if (entity instanceof org.bukkit.entity.Villager) continue;
            if (entity.getCustomName() != null && !entity.getCustomName().isEmpty()) continue;
            entity.remove();
        }
    }

    // ------------------------------------------------------------ helpers
    public void trackEntity(org.bukkit.entity.Entity e) {
        tracked.add(e);
    }

    public void broadcastToMatch(String msg) {
        for (MatchPlayer mp : players.values()) {
            Player p = mp.player();
            if (p != null && !mp.left) p.sendMessage(Msg.c(Msg.PREFIX + msg));
        }
    }

    public void playAll(Sound s, float vol, float pitch) {
        for (MatchPlayer mp : players.values()) {
            Player p = mp.player();
            if (p != null && !mp.left) p.playSound(p.getLocation(), s, vol, pitch);
        }
    }
}