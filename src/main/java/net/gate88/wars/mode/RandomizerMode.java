package net.gate88.wars.mode;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.match.MatchTeam;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Pos;
import net.gate88.wars.util.Sfx;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Randomizer 変形トーナメント:
 *  - 最大16チームの変形トーナメント形式 (無敗チームが1チームになるまで継続)
 *  - 複数アリーナによる全対戦ペア並行試合
 *  - 厳密なタイブレイカー & 順位決定ロジック
 */
public final class RandomizerMode extends WarsMode {
    public static final int HALF = 2; // 5x5
    public static final int CELLS = (HALF * 2 + 1) * (HALF * 2 + 1);

    // ------------------------------------------------------------ トーナメント用データ構造
    public static final class TournamentTeam {
        public final MatchTeam team;
        public int wins = 0;
        public int losses = 0;
        public boolean wasBye = false;
        // 直接対決履歴: 対戦相手チームindex -> 勝利したか
        public final Map<Integer, Boolean> headToHead = new HashMap<>();
        public int tieBreakerTerritory = 0;
        public double tieBreakerHealth = 0.0;

        public TournamentTeam(MatchTeam team) {
            this.team = team;
        }

        public double winRate() {
            int total = wins + losses;
            return total == 0 ? 0.0 : (double) wins / total;
        }
    }

    public static final class MatchPair {
        public final TournamentTeam teamA;
        public final TournamentTeam teamB;
        public final Arena arena;
        public boolean finished = false;
        public TournamentTeam winner = null;
        public String winReason = "";
        public final Map<Integer, Integer> warned = new HashMap<>();

        public MatchPair(TournamentTeam teamA, TournamentTeam teamB, Arena arena) {
            this.teamA = teamA;
            this.teamB = teamB;
            this.arena = arena;
        }

        public boolean contains(MatchTeam t) {
            return teamA.team == t || teamB.team == t;
        }

        public TournamentTeam getOpponent(MatchTeam t) {
            if (teamA.team == t) return teamB;
            if (teamB.team == t) return teamA;
            return null;
        }
    }

    private final Random random = new Random();
    private String selectedKitId;
    private String selectedKitName;

    // トーナメント進行状態
    private final List<TournamentTeam> tournamentTeams = new ArrayList<>();
    private final List<MatchPair> currentPairs = new ArrayList<>();
    private TournamentTeam currentByeTeam = null;
    private int currentRoundNumber = 0;
    private boolean tournamentOver = false;
    private int intervalCountdown = -1; // ラウンド間インターバル

    public RandomizerMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> extraDesc) {
        super(plugin, id, displayName, icon, new ArrayList<>(extraDesc));
    }

    private boolean fill() {
        return cfg().getBoolean("wool-fill", true);
    }

    private boolean deathChest() {
        return cfg().getBoolean("death-chest", false);
    }

    public int roundIntervalSeconds() {
        return Math.max(5, cfg().getInt("round-interval-seconds", 10));
    }

    @Override
    public String arenaType() {
        return "randomizer";
    }

    // ------------------------------------------------------------ アリーナ & ペア検索ヘルパー
    public MatchPair getPairForTeam(MatchTeam t) {
        if (t == null) return null;
        for (MatchPair pair : currentPairs) {
            if (pair.contains(t)) return pair;
        }
        return null;
    }

    public MatchPair getPairForPlayer(MatchPlayer mp) {
        return mp != null ? getPairForTeam(mp.team) : null;
    }

    public TournamentTeam getTournamentTeam(MatchTeam t) {
        if (t == null) return null;
        for (TournamentTeam tt : tournamentTeams) {
            if (tt.team == t) return tt;
        }
        return null;
    }

    private static boolean inFootprint(Arena a, Block b) {
        return Math.abs(b.getX() - a.cx) <= HALF && Math.abs(b.getZ() - a.cz) <= HALF;
    }

    private static boolean isCell(Arena a, Block b) {
        return inFootprint(a, b) && b.getY() == a.cy;
    }

    public int cellsOwned(Match m, Arena a, MatchTeam t) {
        Material wool = Colors.wool(t.color);
        int n = 0;
        World w = m.world();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                if (w.getBlockAt(a.cx + dx, a.cy, a.cz + dz).getType() == wool) n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ 大会開始 & ライフサイクル
    @Override
    public void onStart(Match m) {
        selectedKitId = null;
        selectedKitName = null;
        tournamentTeams.clear();
        currentPairs.clear();
        currentByeTeam = null;
        currentRoundNumber = 0;
        tournamentOver = false;
        intervalCountdown = -1;

        for (MatchTeam t : m.teams()) {
            tournamentTeams.add(new TournamentTeam(t));
        }

        m.broadcastToMatch("&6&l★ Randomizer トーナメント開始！ ★");
        m.broadcastToMatch("&e参加チーム数: &f" + tournamentTeams.size() + "チーム &7(無敗チームが1つになるまで戦い抜行)");

        startRound(m);
    }

    // ------------------------------------------------------------ ラウンド構築 & 並行マッチング
    private void startRound(Match m) {
        currentRoundNumber++;
        currentPairs.clear();
        currentByeTeam = null;
        intervalCountdown = -1;

        // 1. 総チーム数が奇数の場合の観戦枠 (Bye) 選定
        List<TournamentTeam> activeTeams = new ArrayList<>(tournamentTeams);
        if (activeTeams.size() % 2 != 0) {
            List<TournamentTeam> byeCandidates = new ArrayList<>();
            for (TournamentTeam tt : activeTeams) {
                if (tt.losses > 0 && !tt.wasBye) {
                    byeCandidates.add(tt);
                }
            }
            if (byeCandidates.isEmpty()) {
                for (TournamentTeam tt : activeTeams) if (!tt.wasBye) byeCandidates.add(tt);
            }
            if (byeCandidates.isEmpty()) byeCandidates.addAll(activeTeams);

            currentByeTeam = byeCandidates.get(random.nextInt(byeCandidates.size()));
            currentByeTeam.wasBye = true;
            activeTeams.remove(currentByeTeam);
        }

        // 2. 無敗グループと敗者グループに分類
        List<TournamentTeam> undefeated = new ArrayList<>();
        List<TournamentTeam> hasLosses = new ArrayList<>();
        for (TournamentTeam tt : activeTeams) {
            if (tt.losses == 0) undefeated.add(tt);
            else hasLosses.add(tt);
        }
        Collections.shuffle(undefeated);

        // 3. 無敗チーム数が奇数の場合 (挙動A: 1敗グループ最上位と対戦)
        TournamentTeam crossOverUndefeated = null;
        TournamentTeam crossOverLoser = null;
        if (undefeated.size() % 2 != 0) {
            crossOverUndefeated = undefeated.remove(undefeated.size() - 1);
            hasLosses.sort((a, b) -> {
                if (a.wins != b.wins) return Integer.compare(b.wins, a.wins);
                int cmpRate = Double.compare(b.winRate(), a.winRate());
                if (cmpRate != 0) return cmpRate;
                double aScore = a.tieBreakerTerritory * 100.0 + a.tieBreakerHealth;
                double bScore = b.tieBreakerTerritory * 100.0 + b.tieBreakerHealth;
                return Double.compare(bScore, aScore);
            });
            if (!hasLosses.isEmpty()) {
                crossOverLoser = hasLosses.remove(0);
            }
        }

        // 4. ペアリングリストの作成
        List<TournamentTeam[]> pairings = new ArrayList<>();
        for (int i = 0; i < undefeated.size(); i += 2) {
            pairings.add(new TournamentTeam[]{undefeated.get(i), undefeated.get(i + 1)});
        }
        if (crossOverUndefeated != null && crossOverLoser != null) {
            pairings.add(new TournamentTeam[]{crossOverUndefeated, crossOverLoser});
        }
        // ★ 修正箇所: Boolean.compare を使用して安全に前ラウンドByeチームを最優先ソート
        hasLosses.sort((a, b) -> Boolean.compare(b.wasBye, a.wasBye));
        for (int i = 0; i < hasLosses.size(); i += 2) {
            if (i + 1 < hasLosses.size()) {
                pairings.add(new TournamentTeam[]{hasLosses.get(i), hasLosses.get(i + 1)});
            }
        }

        // 5. 使用可能アリーナの選定 (できるだけ異種アリーナを優先割り当て)
        List<Arena> availableArenas = getDiverseArenas(pairings.size());

        // 6. 各ペアに対戦カードとアリーナを確定・配置
        for (int i = 0; i < pairings.size(); i++) {
            TournamentTeam a = pairings.get(i)[0];
            TournamentTeam b = pairings.get(i)[1];
            a.wasBye = false;
            b.wasBye = false;

            Arena arena = availableArenas.get(i % availableArenas.size());
            MatchPair pair = new MatchPair(a, b, arena);
            currentPairs.add(pair);

            if (fill()) {
                World w = m.world();
                for (int dx = -HALF; dx <= HALF; dx++) {
                    for (int dz = -HALF; dz <= HALF; dz++) {
                        Block bl = w.getBlockAt(arena.cx + dx, arena.cy, arena.cz + dz);
                        m.blocks().trackFixed(bl, bl.getState());
                        bl.setType(Material.WHITE_CONCRETE, false);
                    }
                }
            }

            teleportTeamToSpawn(m, a.team, arena, 0);
            teleportTeamToSpawn(m, b.team, arena, 1);

            for (MatchPlayer mp : a.team.members) {
                Player p = mp.player();
                if (p != null) {
                    Msg.title(p, "&6&lROUND " + currentRoundNumber, "&7VS " + Colors.code(b.team.color) + "&l" + Colors.en(b.team.color), 5, 50, 10);
                }
            }
            for (MatchPlayer mp : b.team.members) {
                Player p = mp.player();
                if (p != null) {
                    Msg.title(p, "&6&lROUND " + currentRoundNumber, "&7VS " + Colors.code(a.team.color) + "&l" + Colors.en(a.team.color), 5, 50, 10);
                }
            }
        }

        // 7. 観戦 (Bye) チームのスペクテイター配置
        if (currentByeTeam != null) {
            Arena specArena = !currentPairs.isEmpty() ? currentPairs.get(0).arena : m.arena();
            Location specLoc = specArena.center().add(0, 15, 0);
            for (MatchPlayer mp : currentByeTeam.team.members) {
                Player p = mp.player();
                if (p != null) {
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(specLoc);
                    Msg.title(p, "&e&l【観戦 (Bye)】", "&7このラウンドはお休みです (他試合を観戦)", 5, 60, 10);
                    Msg.send(p, "&e今ラウンドは対戦がありません。上空から他チームの試合を観戦してください。");
                }
            }
        }

        m.broadcastToMatch("&6&l==== ROUND " + currentRoundNumber + " マッチング ====");
        for (MatchPair pair : currentPairs) {
            m.broadcastToMatch("⚔ " + Colors.code(pair.teamA.team.color) + "&l" + Colors.en(pair.teamA.team.color) + " &f" + pair.teamA.team.label()
                    + " &7vs " + Colors.code(pair.teamB.team.color) + "&l" + Colors.en(pair.teamB.team.color) + " &f" + pair.teamB.team.label()
                    + " &8(" + pair.arena.id + ")");
        }
        if (currentByeTeam != null) {
            m.broadcastToMatch("&e☕ 観戦(Bye): " + Colors.code(currentByeTeam.team.color) + "&l" + Colors.en(currentByeTeam.team.color) + " &f" + currentByeTeam.team.label());
        }
    }

    private List<Arena> getDiverseArenas(int count) {
        List<Arena> ready = plugin.arenas().readyFor(this.id);
        if (ready.isEmpty()) return List.of(plugin.arenas().all().get(0));

        Map<String, List<Arena>> grouped = new LinkedHashMap<>();
        for (Arena a : ready) {
            String familyKey = (a.parentId != null) ? a.parentId : a.id;
            grouped.computeIfAbsent(familyKey, k -> new ArrayList<>()).add(a);
        }

        List<Arena> diverse = new ArrayList<>();
        int cycle = 0;
        while (diverse.size() < count) {
            boolean added = false;
            for (List<Arena> group : grouped.values()) {
                if (cycle < group.size()) {
                    diverse.add(group.get(cycle));
                    added = true;
                    if (diverse.size() >= count) break;
                }
            }
            if (!added) break;
            cycle++;
        }
        while (diverse.size() < count) {
            diverse.add(ready.get(diverse.size() % ready.size()));
        }
        return diverse;
    }

    private void teleportTeamToSpawn(Match m, MatchTeam team, Arena arena, int spawnIndex) {
        Pos sp = (arena.spawns.size() > spawnIndex) ? arena.spawns.get(spawnIndex) : Pos.of(arena.center());
        Location loc = sp.toLocation(m.world());
        int idx = 0;
        for (MatchPlayer mp : team.members) {
            Player p = mp.player();
            if (p != null) {
                Match.prepare(p);
                Location spawnLoc = loc.clone();
                if (idx > 0) spawnLoc.add((idx % 3) - 1.0, 0, (idx / 3) * 1.0);
                p.teleport(spawnLoc);
                idx++;
                Colors.applyPlayerDisplay(p, team.color);
            }
        }
    }

    @Override
    public void onGraceEnd(Match m) {
        selectedKitId = plugin.kits().pickMatchKit();
        if (selectedKitId == null) {
            m.broadcastToMatch("&c[エラー] 使用可能なキットがありません！");
            return;
        }
        selectedKitName = plugin.kits().getKitDisplayName(selectedKitId);
        int stacks = Math.max(1, cfg().getInt("wool-stacks", 3));

        for (MatchPair pair : currentPairs) {
            for (MatchTeam t : List.of(pair.teamA.team, pair.teamB.team)) {
                for (MatchPlayer mp : t.members) {
                    Player p = mp.player();
                    if (p == null || mp.left || !mp.alive) continue;

                    plugin.kits().applyKit(p, selectedKitId);
                    applyWarsExtras(p, t, stacks);

                    Sfx.gearGive(p);
                    Msg.title(p, "&6&lFIGHT!", "&e装備: &f" + selectedKitName, 0, 50, 10);
                    p.sendMessage(Msg.c(Msg.PREFIX + "&a全員に同じ装備が配布されました: &e" + selectedKitName));
                }
            }
        }
    }

    private void applyWarsExtras(Player p, MatchTeam team, int stacks) {
        PlayerInventory inv = p.getInventory();
        org.bukkit.Color teamColor = Colors.color(team.color);
        for (ItemStack piece : new ItemStack[]{inv.getHelmet(), inv.getChestplate(), inv.getLeggings(), inv.getBoots()}) {
            if (piece != null && piece.getItemMeta() instanceof org.bukkit.inventory.meta.LeatherArmorMeta meta) {
                meta.setColor(teamColor);
                piece.setItemMeta(meta);
            }
        }

        inv.addItem(new ItemStack(Material.SHEARS));
        for (int i = 0; i < stacks; i++) {
            inv.addItem(new ItemStack(Colors.wool(team.color), 64));
        }

        if (fill()) {
            ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
            ItemMeta pmeta = pickaxe.getItemMeta();
            if (pmeta != null) {
                pmeta.displayName(Msg.c("&b&l中央コンクリート破壊用ツルハシ"));
                pmeta.lore(List.of(Msg.c("&7中央の白色コンクリートを破壊できます (攻撃力: 1)")));
                pmeta.addEnchant(Enchantment.EFFICIENCY, 3, true);
                pmeta.setUnbreakable(true);

                AttributeModifier attackMod = new AttributeModifier(
                        new NamespacedKey(plugin, "pickaxe_attack"),
                        0.0,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.MAINHAND
                );
                pmeta.addAttributeModifier(Attribute.ATTACK_DAMAGE, attackMod);
                pmeta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
                pickaxe.setItemMeta(pmeta);
            }
            inv.addItem(pickaxe);
        }
    }

    @Override
    public void onSecond(Match m) {
        if (tournamentOver) return;

        if (intervalCountdown >= 0) {
            if (intervalCountdown == 0) {
                intervalCountdown = -1;
                m.resetForNextRound();
                startRound(m);
            } else {
                for (MatchPlayer mp : m.allPlayers()) {
                    Player p = mp.player();
                    if (p != null) {
                        Msg.actionBar(p, "&e次のラウンド開始まで &c" + intervalCountdown + " &e秒...");
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                    }
                }
                intervalCountdown--;
            }
            return;
        }

        for (MatchPair pair : currentPairs) {
            if (pair.finished) continue;

            int aAlive = pair.teamA.team.aliveCount();
            int bAlive = pair.teamB.team.aliveCount();

            if (aAlive == 0 && bAlive > 0) {
                finishPair(m, pair, pair.teamB, "敵チーム全滅");
            } else if (bAlive == 0 && aAlive > 0) {
                finishPair(m, pair, pair.teamA, "敵チーム全滅");
            } else if (aAlive == 0 && bAlive == 0) {
                resolveTieBreaker(m, pair);
            }
        }

        if (m.timeLeft() <= 0) {
            for (MatchPair pair : currentPairs) {
                if (!pair.finished) {
                    resolveTieBreaker(m, pair);
                }
            }
        }

        checkRoundFinished(m);
    }

    private void resolveTieBreaker(Match m, MatchPair pair) {
        int aAlive = pair.teamA.team.aliveCount();
        int bAlive = pair.teamB.team.aliveCount();
        if (aAlive != bAlive) {
            finishPair(m, pair, aAlive > bAlive ? pair.teamA : pair.teamB, "生き残り人数 (" + Math.max(aAlive, bAlive) + "人)");
            return;
        }

        if (fill()) {
            int aCells = cellsOwned(m, pair.arena, pair.teamA.team);
            int bCells = cellsOwned(m, pair.arena, pair.teamB.team);
            if (aCells != bCells) {
                finishPair(m, pair, aCells > bCells ? pair.teamA : pair.teamB, "陣地占領マス (" + Math.max(aCells, bCells) + "マス)");
                return;
            }
        }

        double aHp = getTeamTotalHp(pair.teamA.team);
        double bHp = getTeamTotalHp(pair.teamB.team);
        if (Math.abs(aHp - bHp) > 0.01) {
            finishPair(m, pair, aHp > bHp ? pair.teamA : pair.teamB, String.format("合計残体力 (%.1f HP)", Math.max(aHp, bHp)));
            return;
        }

        TournamentTeam coinWinner = ThreadLocalRandom.current().nextBoolean() ? pair.teamA : pair.teamB;
        finishPair(m, pair, coinWinner, "完全同点 (コイントス抽選)");
    }

    private double getTeamTotalHp(MatchTeam t) {
        double total = 0.0;
        for (MatchPlayer mp : t.members) {
            if (mp.alive) {
                Player p = mp.player();
                if (p != null) total += p.getHealth();
            }
        }
        return total;
    }

    private void finishPair(Match m, MatchPair pair, TournamentTeam winner, String reason) {
        pair.finished = true;
        pair.winner = winner;
        pair.winReason = reason;

        TournamentTeam loser = (winner == pair.teamA) ? pair.teamB : pair.teamA;

        if (fill()) {
            pair.teamA.tieBreakerTerritory += cellsOwned(m, pair.arena, pair.teamA.team);
            pair.teamB.tieBreakerTerritory += cellsOwned(m, pair.arena, pair.teamB.team);
        }
        pair.teamA.tieBreakerHealth += getTeamTotalHp(pair.teamA.team);
        pair.teamB.tieBreakerHealth += getTeamTotalHp(pair.teamB.team);

        Location specLoc = pair.arena.center().add(0, 15, 0);
        for (MatchTeam t : List.of(pair.teamA.team, pair.teamB.team)) {
            for (MatchPlayer mp : t.members) {
                Player p = mp.player();
                if (p != null) {
                    Msg.title(p, Colors.code(winner.team.color) + "&l" + Colors.en(winner.team.color) + " 勝利!", "&7理由: " + reason, 5, 50, 10);
                    p.setGameMode(GameMode.SPECTATOR);
                    p.teleport(specLoc);
                }
            }
        }

        m.broadcastToMatch("⚔ &6【決着】 " + Colors.code(winner.team.color) + "&l" + Colors.en(winner.team.color) + " &f" + winner.team.label()
                + " &aが " + Colors.code(loser.team.color) + "&l" + Colors.en(loser.team.color) + " &f" + loser.team.label()
                + " &aに勝利! &7(" + reason + ")");
    }

    private void checkRoundFinished(Match m) {
        for (MatchPair pair : currentPairs) {
            if (!pair.finished) return;
        }

        for (MatchPair pair : currentPairs) {
            TournamentTeam win = pair.winner;
            TournamentTeam lose = (win == pair.teamA) ? pair.teamB : pair.teamA;
            win.wins++;
            lose.losses++;
            win.headToHead.put(lose.team.index, true);
            lose.headToHead.put(win.team.index, false);
        }

        m.blocks().restoreAll();

        List<TournamentTeam> undefeated = new ArrayList<>();
        for (TournamentTeam tt : tournamentTeams) {
            if (tt.losses == 0) undefeated.add(tt);
        }

        if (undefeated.size() == 1) {
            tournamentOver = true;
            TournamentTeam champion = undefeated.get(0);
            finishTournament(m, champion);
        } else {
            m.broadcastToMatch("&6&l==== ROUND " + currentRoundNumber + " 終了 ====");
            m.broadcastToMatch("&e残り無敗チーム: &a" + undefeated.size() + "チーム");
            m.broadcastToMatch("&7※ " + roundIntervalSeconds() + " 秒後に次のラウンドを開始します...");
            intervalCountdown = roundIntervalSeconds();
        }
    }

    private void finishTournament(Match m, TournamentTeam champion) {
        List<TournamentTeam> ranked = new ArrayList<>(tournamentTeams);

        ranked.sort((a, b) -> {
            if (a.losses == 0 && b.losses > 0) return -1;
            if (b.losses == 0 && a.losses > 0) return 1;

            if (a.wins != b.wins) return Integer.compare(b.wins, a.wins);

            double aRate = a.winRate();
            double bRate = b.winRate();
            if (Double.compare(aRate, bRate) != 0) return Double.compare(bRate, aRate);

            Boolean aWonB = a.headToHead.get(b.team.index);
            if (aWonB != null) return aWonB ? -1 : 1;

            double aScore = a.tieBreakerTerritory * 100.0 + a.tieBreakerHealth;
            double bScore = b.tieBreakerTerritory * 100.0 + b.tieBreakerHealth;
            if (Double.compare(aScore, bScore) != 0) return Double.compare(bScore, aScore);

            return 0;
        });

        StringBuilder sb = new StringBuilder();
        sb.append("\n&6&l==== 88WARS トーナメント 最終結果 ====\n");
        sb.append("&e&l🏆 優勝: ").append(Colors.code(champion.team.color)).append("&l").append(Colors.en(champion.team.color))
                .append(" &f&l").append(champion.team.label()).append(" &a(無敗完全勝利!)\n\n");
        sb.append("&6&l--- 最終順位表 ---\n");
        for (int i = 0; i < ranked.size(); i++) {
            TournamentTeam tt = ranked.get(i);
            String rLabel = (i == 0) ? "&e1位" : (i == 1) ? "&72位" : (i == 2) ? "&63位" : "&8" + (i + 1) + "位";
            sb.append(rLabel).append(": ").append(Colors.code(tt.team.color)).append("&l").append(Colors.en(tt.team.color))
                    .append(" &f").append(tt.team.label())
                    .append(" &8- &a").append(tt.wins).append("勝 &c").append(tt.losses).append("敗")
                    .append(" &7(陣地: &e").append(tt.tieBreakerTerritory).append("&7, 残HP: &e").append((int) tt.tieBreakerHealth).append("&7)\n");
        }
        sb.append("&6&l=====================================");

        for (String line : sb.toString().split("\n")) {
            Bukkit.broadcast(Msg.c(line));
        }

        m.finish(champion.team, "トーナメント優勝");
    }

    @Override
    public boolean canPlace(Match m, MatchPlayer p, Block b) {
        if (!fill()) return false;
        MatchPair pair = getPairForPlayer(p);
        if (pair == null) return false;

        if (!isCell(pair.arena, b)) {
            Player pl = p.player();
            if (pl != null) {
                Msg.actionBar(pl, "&c中央5x5の制圧マス以外には設置できません！");
                Sfx.deny(pl);
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean decayExempt(Match m, Block b) {
        for (MatchPair pair : currentPairs) {
            if (fill() && isCell(pair.arena, b)) return true;
        }
        return false;
    }

    @Override
    public void onBlockPlaced(Match m, MatchPlayer p, Block b) {
        if (!fill()) return;
        MatchPair pair = getPairForPlayer(p);
        if (pair == null || !isCell(pair.arena, b)) return;

        m.world().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.3f);
        int n = cellsOwned(m, pair.arena, p.team);
        if (n >= CELLS) {
            TournamentTeam tt = getTournamentTeam(p.team);
            finishPair(m, pair, tt, "中央5x5制圧");
            return;
        }

        if (n >= CELLS - 3 && pair.warned.getOrDefault(p.team.index, 0) != n) {
            pair.warned.put(p.team.index, n);
            for (MatchTeam t : List.of(pair.teamA.team, pair.teamB.team)) {
                for (MatchPlayer mp : t.members) {
                    Player pl = mp.player();
                    if (pl != null) {
                        Msg.actionBar(pl, Colors.code(p.team.color) + "&l" + Colors.en(p.team.color) + " &f" + p.team.label() + " &cが制圧目前! (" + n + "/" + CELLS + ")");
                        pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
                    }
                }
            }
        }
    }

    @Override
    public void onBlockBroken(Match m, MatchPlayer p, Block b) {
        MatchPair pair = getPairForPlayer(p);
        if (pair != null && fill() && isCell(pair.arena, b)) {
            if (b.getType() == Material.WHITE_CONCRETE) {
                m.world().playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            } else {
                m.world().playSound(b.getLocation(), Sound.BLOCK_WOOL_BREAK, 1.0f, 0.8f);
            }
        }
    }

    @Override
    public MatchTeam pickTimeUpWinner(Match m) {
        return null;
    }

    @Override
    public boolean handleRoundEnd(Match m, MatchTeam winner) {
        return !tournamentOver;
    }

    @Override
    public List<String> extraSidebar(Match m, MatchPlayer viewer) {
        TournamentTeam myTt = getTournamentTeam(viewer.team);
        MatchPair pair = getPairForPlayer(viewer);

        List<String> list = new ArrayList<>();
        list.add("&fラウンド: &6Round " + currentRoundNumber);
        if (myTt != null) {
            list.add("&f自チーム戦績: &a" + myTt.wins + "勝 &c" + myTt.losses + "敗");
        }
        if (pair != null) {
            TournamentTeam opp = pair.getOpponent(viewer.team);
            if (opp != null) {
                list.add("&f対戦相手: " + Colors.code(opp.team.color) + "&l" + Colors.en(opp.team.color));
            }
        } else if (currentByeTeam != null && currentByeTeam.team == viewer.team) {
            list.add("&e★ 観戦中 (Bye)");
        }

        int undefeatedCount = 0;
        for (TournamentTeam tt : tournamentTeams) if (tt.losses == 0) undefeatedCount++;
        list.add("&f残り無敗: &e" + undefeatedCount + "チーム");
        list.add("&fあなたの色: " + Colors.code(viewer.team.color) + "&l" + Colors.en(viewer.team.color));
        return list;
    }

    @Override
    public void onDeathLoot(Match m, MatchPlayer victim, Location loc, List<ItemStack> loot) {
        if (!deathChest() || loot.isEmpty()) return;
        MatchPair pair = getPairForPlayer(victim);
        Arena a = pair != null ? pair.arena : m.arena();
        World w = m.world();

        Block spot = null;
        for (int r = 0; r <= 3 && spot == null; r++) {
            for (int dx = -r; dx <= r && spot == null; dx++) {
                for (int dz = -r; dz <= r && spot == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    spot = scanColumn(m, a, loc.getBlockX() + dx, loc.getBlockZ() + dz, Math.min(loc.getBlockY(), a.cy + 12));
                }
            }
        }
        if (spot == null) spot = scanColumn(m, a, a.cx + 4, a.cz + 4, a.cy + 3);
        if (spot == null) {
            Location drop = (loc.getY() < a.cy - 2 && a.center() != null) ? a.center() : loc;
            for (ItemStack it : loot) w.dropItemNaturally(drop, it);
            return;
        }

        BlockState replaced = spot.getState();
        spot.setType(Material.CHEST, false);
        if (spot.getBlockData() instanceof org.bukkit.block.data.type.Chest cd) {
            cd.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
            cd.setFacing(faces[random.nextInt(faces.length)]);
            spot.setBlockData(cd, false);
        }
        m.blocks().trackFixed(spot, replaced);
        org.bukkit.block.Chest state = (org.bukkit.block.Chest) spot.getState();
        state.customName(Msg.c("&e" + victim.name + " の遺品"));
        state.update(true, false);
        Map<Integer, ItemStack> left = ((org.bukkit.block.Chest) spot.getState()).getBlockInventory()
                .addItem(loot.toArray(new ItemStack[0]));
        for (ItemStack it : left.values()) w.dropItemNaturally(spot.getLocation().add(0.5, 1, 0.5), it);

        Location c = spot.getLocation().add(0.5, 0.5, 0.5);
        w.playSound(c, Sound.BLOCK_ENDER_CHEST_OPEN, 1.5f, 1.2f);
        w.spawnParticle(Particle.END_ROD, c, 30, 0.3, 0.4, 0.3, 0.05);
        ArmorStand label = w.spawn(spot.getLocation().add(0.5, 1.0, 0.5), ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setPersistent(false);
            as.customName(Msg.c("&e" + victim.name + " &7の遺品"));
            as.setCustomNameVisible(true);
        });
        m.trackEntity(label);
        m.broadcastToMatch("&e" + victim.name + " &7の遺品チェストが出現!");
    }

    private Block scanColumn(Match m, Arena a, int x, int z, int startY) {
        for (int y = startY; y >= a.cy; y--) {
            Block b = m.world().getBlockAt(x, y, z);
            Block below = m.world().getBlockAt(x, y - 1, z);
            if (b.getType().isAir() && below.getType().isSolid() && !m.blocks().isActive(below)
                    && below.getType() != Material.CHEST) {
                return b;
            }
        }
        return null;
    }
}