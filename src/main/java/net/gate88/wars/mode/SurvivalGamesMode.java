package net.gate88.wars.mode;

import java.util.*;
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
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class SurvivalGamesMode extends WarsMode {
    private final Random rand = new Random();

    // ラウンド管理
    private int currentRound = 1;
    private final Map<UUID, Integer> totalRoundScores = new HashMap<>();
    private final Map<UUID, Integer> totalRoundKills = new HashMap<>();

    // チェスト管理
    private final Set<Location> chestLocations = new HashSet<>();
    private boolean chestsRefreshed = false;

    // コアイベント管理
    private boolean coreWarned = false;
    private boolean coreSpawned = false;
    private Location coreTargetLoc = null;
    private Interaction coreInteraction = null;
    private BlockDisplay coreDisplay = null;
    private double coreHealth = 100.0;
    private BukkitTask coreFallTask = null;

    public SurvivalGamesMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> description) {
        super(plugin, id, displayName, icon, description);
    }

    @Override
    public String arenaType() {
        return "survival_games";
    }

    // ------------------------------------------------------------ 設定アクセサ
    public int pvpGraceSeconds() { return cfg().getInt("pvp-grace-seconds", 90); }
    public int chestCount() { return cfg().getInt("chest-count", 200); }
    public int chestRefreshSeconds() { return cfg().getInt("chest-refresh-seconds", 720); }
    public int coreSpawnSeconds() { return cfg().getInt("core-spawn-seconds", 600); }

    @Override
    public int durationSeconds() { return cfg().getInt("duration-seconds", 900); } // 15分
    @Override
    public int customKillPoints() { return 15; }
    @Override
    public int customAssistPoints() { return 5; }
    @Override
    public int placementBonusPoints() { return 10; }

    // ------------------------------------------------------------ ボーダー仕様
    // スポーンから2分(120s)で縮小開始 -> 15x15(半径7.5)まで縮小
    // 15x15になったのち3分(180s)キープ -> 1分(60s)かけて0x0へ縮小
    @Override
    public double customBorderRadius(Match m, int elapsed) {
        double startRadius = 150.0; // 300x300
        double midRadius = 7.5;     // 15x15
        double endRadius = 0.0;

        int startShrink = 120; // 2分後
        int shrinkDuration = 300; // 5分かけて15x15へ
        int midReach = startShrink + shrinkDuration; // 420s
        int secondShrinkStart = midReach + 180; // 3分経過後 = 600s
        int secondShrinkDuration = 60; // 1分かけて0x0へ = 660s

        if (elapsed < startShrink) return startRadius;
        if (elapsed < midReach) {
            double prog = (double) (elapsed - startShrink) / shrinkDuration;
            return startRadius + (midRadius - startRadius) * prog;
        }
        if (elapsed < secondShrinkStart) {
            return midRadius;
        }
        if (elapsed < secondShrinkStart + secondShrinkDuration) {
            double prog = (double) (elapsed - secondShrinkStart) / secondShrinkDuration;
            return midRadius + (endRadius - midRadius) * prog;
        }
        return endRadius;
    }

    // ------------------------------------------------------------ ライフサイクル
    @Override
    public void onStart(Match m) {
        if (currentRound == 1) {
            totalRoundScores.clear();
            totalRoundKills.clear();
        }
        chestsRefreshed = false;
        coreWarned = false;
        coreSpawned = false;
        cleanupCore();

        setupChests(m);

        m.broadcastToMatch("&e&l[Survival Games] &f第 " + currentRound + " ラウンド開始！");
        m.broadcastToMatch("&7※ PvP解禁まで " + (pvpGraceSeconds() / 60) + "分" + (pvpGraceSeconds() % 60) + "秒");
    }

    @Override
    public void onSecond(Match m) {
        int elapsed = m.elapsed();

        // 1. PvP解禁カウントダウン
        int pvpRemain = pvpGraceSeconds() - elapsed;
        if (pvpRemain == 30 || pvpRemain == 10 || (pvpRemain > 0 && pvpRemain <= 5)) {
            m.broadcastToMatch("&c&lPvP解禁まであと " + pvpRemain + " 秒！");
            m.playAll(Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        } else if (pvpRemain == 0) {
            m.broadcastToMatch("&c&l⚔ PvPが解禁されました！ ⚔");
            m.playAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);
        }

        // 2. コアイベント (10分経過時、15秒前に警告)
        int coreTime = coreSpawnSeconds();
        if (!coreWarned && elapsed >= coreTime - 15) {
            coreWarned = true;
            chooseCoreLocation(m);
            for (MatchPlayer mp : m.allPlayers()) {
                Player p = mp.player();
                if (p != null) {
                    Msg.title(p, "&c&lコア出現", "&eX: " + coreTargetLoc.getBlockX() + " Z: " + coreTargetLoc.getBlockZ() + " に降下予定", 5, 60, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.8f);
                }
            }
            m.broadcastToMatch("&c&l【コア出現予告】 &e座標: X: " + coreTargetLoc.getBlockX() + ", Z: " + coreTargetLoc.getBlockZ() + " に15秒後落下します！");
        }

        if (!coreSpawned && elapsed >= coreTime && coreTargetLoc != null) {
            coreSpawned = true;
            spawnFallingCore(m);
        }

        // 3. チェスト再補充 (12分経過時)
        if (!chestsRefreshed && elapsed >= chestRefreshSeconds()) {
            chestsRefreshed = true;
            refreshAllChests(m);
            m.broadcastToMatch("&6&l★ マップ内の全チェストが再補充されました！ ★");
            m.playAll(Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        }
    }

    // ------------------------------------------------------------ チェスト生成＆物資ルーティング
    private void setupChests(Match m) {
        chestLocations.clear();
        Arena a = m.arena();
        World w = m.world();
        int count = chestCount();

        for (int i = 0; i < count; i++) {
            int rx = a.cx + rand.nextInt(301) - 150;
            int rz = a.cz + rand.nextInt(301) - 150;
            int highestY = w.getHighestBlockYAt(rx, rz);
            if (highestY <= w.getMinHeight() + 2) continue;

            Block chestBlock = w.getBlockAt(rx, highestY + 1, rz);
            Block below = w.getBlockAt(rx, highestY, rz);
            if (!below.getType().isSolid()) continue;

            m.blocks().trackFixed(chestBlock, chestBlock.getState());
            chestBlock.setType(Material.CHEST, false);

            if (chestBlock.getState() instanceof Chest chest) {
                fillLoot(chest.getBlockInventory());
                chestLocations.add(chestBlock.getLocation());
            }
        }
    }

    private void fillLoot(Inventory inv) {
        inv.clear();
        // 食料: 焼いた羊肉 / ステーキ / 豚肉 (2~6個) 極稀にリンゴ1個
        Material[] meats = {Material.COOKED_MUTTON, Material.COOKED_BEEF, Material.COOKED_PORKCHOP};
        inv.setItem(rand.nextInt(27), new ItemStack(meats[rand.nextInt(meats.length)], 2 + rand.nextInt(5)));
        if (rand.nextInt(100) < 5) inv.setItem(rand.nextInt(27), new ItemStack(Material.APPLE, 1));

        // 防具: 革・チェーン (低確率で鉄)
        Material[] armors = {
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
                Material.IRON_HELMET, Material.IRON_BOOTS
        };
        inv.setItem(rand.nextInt(27), new ItemStack(armors[rand.nextInt(armors.length)]));

        // 武器: 木の剣 / 金の剣 / 鉄の剣
        Material[] weapons = {Material.WOODEN_SWORD, Material.GOLDEN_SWORD, Material.IRON_SWORD};
        inv.setItem(rand.nextInt(27), new ItemStack(weapons[rand.nextInt(weapons.length)]));

        // 素材・その他: 棒 / ラピス / 経験値瓶(2-3個)
        inv.setItem(rand.nextInt(27), new ItemStack(Material.STICK, 1 + rand.nextInt(3)));
        if (rand.nextInt(100) < 30) inv.setItem(rand.nextInt(27), new ItemStack(Material.LAPIS_LAZULI, 2 + rand.nextInt(4)));
        if (rand.nextInt(100) < 20) inv.setItem(rand.nextInt(27), new ItemStack(Material.EXPERIENCE_BOTTLE, 2 + rand.nextInt(2)));

        // 極稀にダイヤ1個、金インゴット(2-4個)
        if (rand.nextInt(100) < 3) inv.setItem(rand.nextInt(27), new ItemStack(Material.DIAMOND, 1));
        if (rand.nextInt(100) < 15) inv.setItem(rand.nextInt(27), new ItemStack(Material.GOLD_INGOT, 2 + rand.nextInt(3)));
    }

    public void checkChestEmpty(Inventory inv) {
        if (inv.getLocation() == null || !(inv.getHolder() instanceof Chest)) return;
        Location loc = inv.getLocation();
        if (!chestLocations.contains(loc)) return;

        boolean empty = true;
        for (ItemStack it : inv.getContents()) {
            if (it != null && !it.getType().isAir()) {
                empty = false;
                break;
            }
        }

        if (empty) {
            Block b = loc.getBlock();
            b.setType(Material.SEA_LANTERN, false);
            loc.getWorld().playSound(loc, Sound.BLOCK_SHROOMLIGHT_PLACE, 1.0f, 1.2f);
        }
    }

    private void refreshAllChests(Match m) {
        for (Location loc : chestLocations) {
            Block b = loc.getBlock();
            m.blocks().trackFixed(b, b.getState());
            b.setType(Material.CHEST, false);
            if (b.getState() instanceof Chest chest) {
                fillLoot(chest.getBlockInventory());
            }
        }
    }

    // ------------------------------------------------------------ コアイベント実装
    private void chooseCoreLocation(Match m) {
        Arena a = m.arena();
        World w = m.world();
        int rx = a.cx + rand.nextInt(161) - 80;
        int rz = a.cz + rand.nextInt(161) - 80;
        int y = w.getHighestBlockYAt(rx, rz);
        coreTargetLoc = new Location(w, rx + 0.5, y + 1, rz + 0.5);
    }

    private void spawnFallingCore(Match m) {
        World w = m.world();
        Location startLoc = coreTargetLoc.clone().add(0, 30, 0);

        coreHealth = 100.0;
        coreInteraction = w.spawn(startLoc, Interaction.class, in -> {
            in.setInteractionWidth(1.2f);
            in.setInteractionHeight(1.2f);
            in.setResponsive(true);
            in.setGlowing(true);
        });

        coreDisplay = w.spawn(startLoc.clone().subtract(0.5, 0, 0.5), BlockDisplay.class, bd -> {
            bd.setBlock(Material.LODESTONE.createBlockData());
            bd.setGlowColorOverride(Color.fromRGB(0, 255, 255));
            bd.setGlowing(true);
        });

        m.trackEntity(coreInteraction);
        m.trackEntity(coreDisplay);

        coreFallTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (coreInteraction == null || !coreInteraction.isValid()) {
                cleanupCore();
                return;
            }

            Location cur = coreInteraction.getLocation();
            if (cur.getY() <= coreTargetLoc.getY()) {
                coreInteraction.teleport(coreTargetLoc);
                coreDisplay.teleport(coreTargetLoc.clone().subtract(0.5, 0, 0.5));
                w.playSound(coreTargetLoc, Sound.ENTITY_IRON_GOLEM_DEATH, 1.5f, 0.8f);
                w.spawnParticle(Particle.EXPLOSION_EMITTER, coreTargetLoc, 1);
                m.broadcastToMatch("&b&l【コア着地】 &fコアが地上に着地しました！攻撃して破壊してください！");
                if (coreFallTask != null) coreFallTask.cancel();
                return;
            }

            Location next = cur.clone().subtract(0, 0.5, 0);
            coreInteraction.teleport(next);
            coreDisplay.teleport(next.clone().subtract(0.5, 0, 0.5));
            w.spawnParticle(Particle.GLOW, next, 6, 0.3, 0.3, 0.3, 0.02);
        }, 1L, 1L);
    }

    public boolean isCoreEntity(Entity e) {
        return coreInteraction != null && coreInteraction.equals(e);
    }

    public void damageCore(Match m, Player attacker, double damage) {
        if (coreInteraction == null || !coreInteraction.isValid()) return;
        coreHealth -= Math.max(1.0, damage);
        Location loc = coreInteraction.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_IRON_GOLEM_HURT, 1.0f, 1.2f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 0.5, 0), 10, 0.2, 0.2, 0.2, 0.1);

        for (MatchPlayer mp : m.allPlayers()) {
            Player p = mp.player();
            if (p != null) Msg.actionBar(p, "&bコアHP: &e" + (int) Math.max(0, coreHealth) + " &7/ 100");
        }

        if (coreHealth <= 0) {
            destroyCore(m, attacker);
        }
    }

    private void destroyCore(Match m, Player destroyer) {
        if (coreInteraction == null) return;
        Location dropLoc = coreInteraction.getLocation().clone();
        World w = dropLoc.getWorld();

        // 報酬: ダイヤ装備のいずれか1部位、またはダイヤの剣1つ
        Material[] rewards = {
                Material.DIAMOND_SWORD, Material.DIAMOND_HELMET,
                Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS
        };
        ItemStack reward = new ItemStack(rewards[rand.nextInt(rewards.length)]);
        w.dropItemNaturally(dropLoc, reward);

        w.playSound(dropLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.2f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, dropLoc, 50, 0.5, 0.5, 0.5, 0.2);

        m.broadcastToMatch("&b&l【コア破壊】 &e" + destroyer.getName() + " &aがコアを破壊し、物資を解放しました！");
        cleanupCore();
    }

    private void cleanupCore() {
        if (coreFallTask != null) { coreFallTask.cancel(); coreFallTask = null; }
        if (coreInteraction != null && coreInteraction.isValid()) coreInteraction.remove();
        if (coreDisplay != null && coreDisplay.isValid()) coreDisplay.remove();
        coreInteraction = null;
        coreDisplay = null;
    }

    // ------------------------------------------------------------ ブロックルール
    @Override
    public boolean canBreak(Match m, MatchPlayer p, Block b) {
        // チェストは破壊不可、それ以外（シーランタン含む）は全て破壊可能
        return b.getType() != Material.CHEST;
    }

    // ------------------------------------------------------------ 2ラウンド制＆勝敗判定
    @Override
    public boolean handleRoundEnd(Match m, MatchTeam winner) {
        for (MatchPlayer mp : m.allPlayers()) {
            totalRoundScores.put(mp.uuid, totalRoundScores.getOrDefault(mp.uuid, 0) + mp.points);
            totalRoundKills.put(mp.uuid, totalRoundKills.getOrDefault(mp.uuid, 0) + mp.kills);
        }

        if (currentRound == 1) {
            currentRound = 2;
            m.broadcastToMatch("&6&l第1ラウンド終了！ &e10秒後に第2ラウンドを開始します...");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                m.resetForNextRound();
                m.start();
            }, 200L); // 10秒待機
            return true;
        }

        // 第2ラウンド終了時はそのまま通常終了へ進行
        for (MatchPlayer mp : m.allPlayers()) {
            mp.points = totalRoundScores.getOrDefault(mp.uuid, mp.points);
            mp.kills = totalRoundKills.getOrDefault(mp.uuid, mp.kills);
        }
        currentRound = 1;
        return false;
    }

    @Override
    public MatchTeam pickTimeUpWinner(Match m) {
        List<MatchTeam> alive = new ArrayList<>();
        for (MatchTeam t : m.teams()) if (t.aliveCount() > 0) alive.add(t);
        if (alive.isEmpty()) return null;
        alive.sort(Comparator.comparingInt(MatchTeam::aliveCount).reversed()
                .thenComparing(Comparator.comparingInt(MatchTeam::points).reversed())
                .thenComparing(Comparator.comparingInt(MatchTeam::kills).reversed()));
        return alive.getFirst();
    }

    @Override
    public List<String> extraSidebar(Match m, MatchPlayer viewer) {
        int pvpRemain = Math.max(0, pvpGraceSeconds() - m.elapsed());
        return List.of(
                "&fラウンド: &e" + currentRound + "&7/2",
                "&fPvP解禁: " + (pvpRemain > 0 ? "&c" + Msg.time(pvpRemain) : "&a解禁済"),
                "&fキル数: &a" + viewer.kills,
                "&fあなたの色: " + Colors.code(viewer.team.color) + "&l" + Colors.en(viewer.team.color)
        );
    }

    @Override
    public void onEnd(Match m) {
        cleanupCore();
    }
}