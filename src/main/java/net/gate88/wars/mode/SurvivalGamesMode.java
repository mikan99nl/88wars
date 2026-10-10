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
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

public final class SurvivalGamesMode extends WarsMode {
    public static final int MAP_RADIUS = 150; // 300x300 マップ (半径150)
    private final Random rand = new Random();

    public record LootEntry(ItemStack item, double chance) {}

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
    private long lastCoreDamageTime = 0; // 0.1秒無敵時間用

    public SurvivalGamesMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> description) {
        super(plugin, id, displayName, icon, description);
    }

    @Override
    public String arenaType() {
        return "survivalgames";
    }

    // ------------------------------------------------------------ 設定アクセサ
    public int pvpGraceSeconds() { return cfg().getInt("pvp-grace-seconds", 90); }
    public int chestCount() { return cfg().getInt("chest-count", 200); }
    public int chestRefreshSeconds() { return cfg().getInt("chest-refresh-seconds", 720); }
    public int coreSpawnSeconds() { return cfg().getInt("core-spawn-seconds", 600); }

    public double borderSpeed() {
        return Math.max(0.1, cfg().getDouble("border.speed", 0.5));
    }

    @Override
    public int durationSeconds() { return cfg().getInt("duration-seconds", 900); }
    @Override
    public int customKillPoints() { return 15; }
    @Override
    public int customAssistPoints() { return 5; }
    @Override
    public int placementBonusPoints() { return 10; }

    // ------------------------------------------------------------ ボーダー仕様
    @Override
    public double customBorderRadius(Match m, int elapsed) {
        double startRadius = MAP_RADIUS;
        double midRadius = 7.5;
        double endRadius = 0.0;
        double speed = borderSpeed();

        int startShrink = 120;
        int shrinkDuration = Math.max(10, (int) Math.ceil((startRadius - midRadius) / speed));
        int midReach = startShrink + shrinkDuration;
        int secondShrinkStart = midReach + 180;
        int secondShrinkDuration = Math.max(5, (int) Math.ceil(midRadius / speed));

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
        lastCoreDamageTime = 0;
        cleanupCore();

        Arena a = m.arena();
        Block centerBlock = m.world().getBlockAt(a.cx, a.cy, a.cz);
        if (centerBlock.getType() != Material.ENCHANTING_TABLE) {
            m.blocks().trackFixed(centerBlock, centerBlock.getState());
            centerBlock.setType(Material.ENCHANTING_TABLE, false);
        }

        setupChests(m);

        m.broadcastToMatch("&6&l[Survival Games] &e第 " + currentRound + " ラウンド開始！ &7(マップ: 300×300)");
        m.broadcastToMatch("&7※ PvP解禁まで " + (pvpGraceSeconds() / 60) + "分" + (pvpGraceSeconds() % 60) + "秒");
    }

    @Override
    public void onSecond(Match m) {
        int elapsed = m.elapsed();

        int pvpRemain = pvpGraceSeconds() - elapsed;
        if (pvpRemain == 30 || pvpRemain == 10 || (pvpRemain > 0 && pvpRemain <= 5)) {
            m.broadcastToMatch("&c&lPvP解禁まであと " + pvpRemain + " 秒！");
            m.playAll(Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        } else if (pvpRemain == 0) {
            m.broadcastToMatch("&c&l⚔ PvPが解禁されました！ ⚔");
            m.playAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.0f);
        }

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

        if (!chestsRefreshed && elapsed >= chestRefreshSeconds()) {
            chestsRefreshed = true;
            refreshAllChests(m);
            m.broadcastToMatch("&6&l★ マップ内の全チェストが再補充されました！ ★");
            m.playAll(Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
        }
    }

    // ------------------------------------------------------------ 透過ブロック無視チェスト生成
    private void setupChests(Match m) {
        chestLocations.clear();
        Arena a = m.arena();
        World w = m.world();
        int count = chestCount();

        for (int i = 0; i < count; i++) {
            int rx = a.cx + rand.nextInt(MAP_RADIUS * 2 + 1) - MAP_RADIUS;
            int rz = a.cz + rand.nextInt(MAP_RADIUS * 2 + 1) - MAP_RADIUS;

            int solidGroundY = findSolidGroundY(w, rx, rz);
            if (solidGroundY <= w.getMinHeight() + 2) continue;

            Block chestBlock = w.getBlockAt(rx, solidGroundY + 1, rz);
            Block below = w.getBlockAt(rx, solidGroundY, rz);
            if (!below.getType().isSolid()) continue;

            m.blocks().trackFixed(chestBlock, chestBlock.getState());
            chestBlock.setType(Material.CHEST, false);

            if (chestBlock.getState() instanceof Chest chest) {
                fillLoot(chest.getBlockInventory());
                chestLocations.add(chestBlock.getLocation());
            }
        }
    }

    private int findSolidGroundY(World w, int x, int z) {
        int maxY = Math.min(w.getMaxHeight() - 1, 319);
        for (int y = maxY; y > w.getMinHeight(); y--) {
            Block b = w.getBlockAt(x, y, z);
            Material mat = b.getType();
            if (isPassThroughBlock(mat)) continue;
            return y;
        }
        return w.getMinHeight();
    }

    private boolean isPassThroughBlock(Material m) {
        if (m.isAir()) return true;
        if (m == Material.BARRIER || m == Material.LIGHT || m == Material.STRUCTURE_VOID) return true;
        String name = m.name();
        if (name.contains("GLASS")) return true;
        return !m.isSolid();
    }

    // ------------------------------------------------------------ ルート抽選 (GUI設定反映)
    public List<LootEntry> loadLootEntries() {
        List<LootEntry> entries = new ArrayList<>();
        ConfigurationSection sec = cfg().getConfigurationSection("custom-loot");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ItemStack it = sec.getItemStack(key + ".item");
                double chance = sec.getDouble(key + ".chance", 25.0);
                if (it != null && !it.getType().isAir()) {
                    entries.add(new LootEntry(it, chance));
                }
            }
        }
        if (entries.isEmpty()) {
            return getDefaultLootEntries();
        }
        return entries;
    }

    public void saveLootEntries(List<LootEntry> list) {
        cfg().set("custom-loot", null);
        for (int i = 0; i < list.size(); i++) {
            LootEntry e = list.get(i);
            cfg().set("custom-loot." + i + ".item", e.item);
            cfg().set("custom-loot." + i + ".chance", e.chance);
        }
        plugin.saveConfig();
    }

    public List<LootEntry> getDefaultLootEntries() {
        List<LootEntry> list = new ArrayList<>();
        list.add(new LootEntry(new ItemStack(Material.COOKED_BEEF, 4), 60.0));
        list.add(new LootEntry(new ItemStack(Material.COOKED_PORKCHOP, 4), 60.0));
        list.add(new LootEntry(new ItemStack(Material.APPLE, 1), 8.0));
        list.add(new LootEntry(new ItemStack(Material.CHAINMAIL_CHESTPLATE), 35.0));
        list.add(new LootEntry(new ItemStack(Material.IRON_HELMET), 15.0));
        list.add(new LootEntry(new ItemStack(Material.IRON_SWORD), 25.0));
        list.add(new LootEntry(new ItemStack(Material.WOODEN_SWORD), 70.0));
        list.add(new LootEntry(new ItemStack(Material.STICK, 2), 50.0));
        list.add(new LootEntry(new ItemStack(Material.LAPIS_LAZULI, 3), 30.0));
        list.add(new LootEntry(new ItemStack(Material.EXPERIENCE_BOTTLE, 2), 20.0));
        list.add(new LootEntry(new ItemStack(Material.DIAMOND, 1), 3.0));
        list.add(new LootEntry(new ItemStack(Material.GOLD_INGOT, 3), 15.0));
        return list;
    }

    private void fillLoot(Inventory inv) {
        inv.clear();
        List<LootEntry> table = loadLootEntries();
        for (LootEntry entry : table) {
            double roll = rand.nextDouble() * 100.0;
            if (roll <= entry.chance) {
                int slot = rand.nextInt(27);
                inv.setItem(slot, entry.item.clone());
            }
        }
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

    // ------------------------------------------------------------ コア報酬設定 (GUI対応)
    public List<ItemStack> loadCoreRewardEntries() {
        List<ItemStack> list = new ArrayList<>();
        ConfigurationSection sec = cfg().getConfigurationSection("core-rewards");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ItemStack it = sec.getItemStack(key);
                if (it != null && !it.getType().isAir()) {
                    list.add(it);
                }
            }
        }
        if (list.isEmpty()) {
            return getDefaultCoreRewards();
        }
        return list;
    }

    public void saveCoreRewardEntries(List<ItemStack> list) {
        cfg().set("core-rewards", null);
        for (int i = 0; i < list.size(); i++) {
            cfg().set("core-rewards." + i, list.get(i));
        }
        plugin.saveConfig();
    }

    public List<ItemStack> getDefaultCoreRewards() {
        return new ArrayList<>(List.of(
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.DIAMOND_HELMET),
                new ItemStack(Material.DIAMOND_CHESTPLATE),
                new ItemStack(Material.DIAMOND_LEGGINGS),
                new ItemStack(Material.DIAMOND_BOOTS)
        ));
    }

    // ------------------------------------------------------------ コアイベント実装
    private void chooseCoreLocation(Match m) {
        Arena a = m.arena();
        World w = m.world();
        int rx = a.cx + rand.nextInt(161) - 80;
        int rz = a.cz + rand.nextInt(161) - 80;
        int y = findSolidGroundY(w, rx, rz);
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

                // ★ 着地時にもチャットへ正確な座標をアナウンス
                m.broadcastToMatch("&b&l【コア着地】 &fコアが地上に着地しました！ &7(座標: X: &e" + coreTargetLoc.getBlockX()
                        + " &7, Y: &e" + coreTargetLoc.getBlockY() + " &7, Z: &e" + coreTargetLoc.getBlockZ() + "&7)");
                m.broadcastToMatch("&e攻撃してコアを破壊し、限定物資を手に入れろ！");

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

    /** ★ コアへのダメージ判定 (0.1秒無敵時間・1ダメージ以下無効・実攻撃力/クールダウン正確計算) */
    public void damageCore(Match m, Player attacker, double rawDamage) {
        if (coreInteraction == null || !coreInteraction.isValid()) return;

        long now = System.currentTimeMillis();
        // 0.1秒 (100ms) の無敵時間判定
        if (now - lastCoreDamageTime < 100) {
            return;
        }

        // プレイヤーの攻撃力属性・クールダウンに応じた正確なダメージ計算
        double actualDmg = rawDamage;
        if (attacker != null) {
            var attr = attacker.getAttribute(Attribute.ATTACK_DAMAGE);
            double baseDmg = (attr != null) ? attr.getValue() : 1.0;
            float cooldown = attacker.getAttackCooldown(); // 0.0 ~ 1.0
            actualDmg = baseDmg * (0.2 + 0.8 * cooldown * cooldown);

            // クリティカル判定 (落下中)
            if (attacker.getFallDistance() > 0.0f && !attacker.isOnGround() && !attacker.isClimbing() && !attacker.isInWater()) {
                actualDmg *= 1.5;
            }
        }

        // 1ダメージ以下の攻撃は無効化 (素手連打やゲージ不足の攻撃を遮断)
        if (actualDmg <= 1.0) {
            if (attacker != null) {
                Msg.actionBar(attacker, "&c攻撃が弱すぎます！ (1ダメージ以下は無効)");
            }
            return;
        }

        lastCoreDamageTime = now;
        coreHealth -= actualDmg;

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

        // ★ GUIで編集可能なコア報酬リストからランダム選出
        List<ItemStack> rewards = loadCoreRewardEntries();
        if (!rewards.isEmpty()) {
            ItemStack reward = rewards.get(rand.nextInt(rewards.size())).clone();
            w.dropItemNaturally(dropLoc, reward);
        }

        w.playSound(dropLoc, Sound.ENTITY_WITHER_DEATH, 1.0f, 1.2f);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, dropLoc, 50, 0.5, 0.5, 0.5, 0.2);

        m.broadcastToMatch("&b&l【コア破壊】 &e" + (destroyer != null ? destroyer.getName() : "誰か") + " &aがコアを破壊し、物資を解放しました！");
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
        if (b.getType() == Material.CHEST) return false;
        if (b.getType() == Material.SEA_LANTERN && chestLocations.contains(b.getLocation())) {
            return false;
        }
        return true;
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
            }, 200L);
            return true;
        }

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