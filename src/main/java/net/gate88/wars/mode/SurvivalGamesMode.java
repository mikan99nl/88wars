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

    // ルートアイテム設定データクラス
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

    // ★ ボーダーの1秒あたりに進むブロック数 (最小0.1)
    public double borderSpeed() {
        return Math.max(0.1, cfg().getDouble("border.speed", 0.5));
    }

    @Override
    public int durationSeconds() { return cfg().getInt("duration-seconds", 900); } // 15分
    @Override
    public int customKillPoints() { return 15; }
    @Override
    public int customAssistPoints() { return 5; }
    @Override
    public int placementBonusPoints() { return 10; }

    // ------------------------------------------------------------ ボーダー仕様 (速度ベース縮小)
    @Override
    public double customBorderRadius(Match m, int elapsed) {
        double startRadius = MAP_RADIUS; // 半径 150 (300x300)
        double midRadius = 7.5;          // 半径 7.5 (15x15)
        double endRadius = 0.0;
        double speed = borderSpeed();

        int startShrink = 120; // 2分経過で開始
        // 150 から 7.5 まで (142.5ブロック) を speed で縮小する所要秒数
        int shrinkDuration = Math.max(10, (int) Math.ceil((startRadius - midRadius) / speed));
        int midReach = startShrink + shrinkDuration;
        int secondShrinkStart = midReach + 180; // 15x15で3分間維持
        // 7.5 から 0 まで (7.5ブロック) を speed で縮小する所要秒数
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
        cleanupCore();

        // 中央エンチャントテーブルの設置保証
        Arena a = m.arena();
        Block centerBlock = m.world().getBlockAt(a.cx, a.cy, a.cz);
        if (centerBlock.getType() != Material.ENCHANTING_TABLE) {
            m.blocks().trackFixed(centerBlock, centerBlock.getState());
            centerBlock.setType(Material.ENCHANTING_TABLE, false);
        }

        // 300x300 範囲内にチェストを自動生成
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

            // ★ バリアやガラスなどの透過ブロックを無視して真の地面Yを探す
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

    /** バリアブロック、ガラス、透過・非固体ブロックを無視して上から下に固体を探索 */
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
        if (name.contains("GLASS")) return true; // 全ガラス・板ガラスを無視
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

    // ------------------------------------------------------------ コアイベント
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
        // チェストは破壊不可
        if (b.getType() == Material.CHEST) return false;
        // ★ 空を知らせるシーランタンも破壊不可
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
            }, 200L); // 10秒待機
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