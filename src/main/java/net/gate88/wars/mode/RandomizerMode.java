package net.gate88.wars.mode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.match.MatchTeam;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ArmorStand;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Randomizer:
 *  - 3分以内に敵を全員倒す、または中央 5x5 を自分の色の羊毛で埋め尽くせば勝利
 *  - 開始5秒後に、抽選された同一の装備が全員に平等に配布される
 */
public final class RandomizerMode extends WarsMode {
    public static final int HALF = 2; // 5x5
    public static final int CELLS = (HALF * 2 + 1) * (HALF * 2 + 1);

    private final Random random = new Random();
    private Loadout loadout;
    private final Map<Integer, Integer> warned = new HashMap<>();

    private record Loadout(String name, Material[] armor, boolean shield, Supplier<List<ItemStack>> items) {}

    public RandomizerMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> extraDesc) {
        super(plugin, id, displayName, icon, buildDesc(extraDesc));
    }

    private static List<String> buildDesc(List<String> extra) {
        return new ArrayList<>(extra);
    }

    /** true: 中央5x5を羊毛で埋めると勝利 (TEAM) / false: 殲滅戦のみ (DUO) */
    private boolean fill() {
        return cfg().getBoolean("wool-fill", true);
    }

    private boolean deathChest() {
        return cfg().getBoolean("death-chest", false);
    }

    @Override
    public String arenaType() {
        return "randomizer";
    }

    // ------------------------------------------------------------ loadouts
    private static ItemStack it(Material m, int n) {
        return new ItemStack(m, n);
    }

    private static Material[] set(Material h, Material c, Material l, Material b) {
        return new Material[]{h, c, l, b};
    }

    private List<Loadout> pool() {
        List<Loadout> l = new ArrayList<>();
        l.add(new Loadout("鉄フルアーマー + 石の剣",
                set(Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS), false,
                () -> List.of(it(Material.STONE_SWORD, 1), it(Material.GOLDEN_APPLE, 2))));
        l.add(new Loadout("チェーンメイル + 鉄の剣 + 弓",
                set(Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS), false,
                () -> List.of(it(Material.IRON_SWORD, 1), it(Material.BOW, 1), it(Material.ARROW, 16), it(Material.GOLDEN_APPLE, 1))));
        l.add(new Loadout("ダイヤ胸当て + 木の剣 + 盾",
                set(null, Material.DIAMOND_CHESTPLATE, null, null), true,
                () -> List.of(it(Material.WOODEN_SWORD, 1), it(Material.GOLDEN_APPLE, 2))));
        l.add(new Loadout("革フル + ダイヤの剣",
                set(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS), false,
                () -> List.of(it(Material.DIAMOND_SWORD, 1), it(Material.GOLDEN_APPLE, 1))));
        l.add(new Loadout("金フル + 鉄のオノ + 金リンゴ",
                set(Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS), false,
                () -> List.of(it(Material.IRON_AXE, 1), it(Material.GOLDEN_APPLE, 4))));
        l.add(new Loadout("弓兵セット (革 + パワー弓)",
                set(Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS), false,
                () -> {
                    ItemStack bow = it(Material.BOW, 1);
                    bow.addUnsafeEnchantment(Enchantment.POWER, 1);
                    return List.of(it(Material.STONE_SWORD, 1), bow, it(Material.ARROW, 32), it(Material.GOLDEN_APPLE, 1));
                }));
        l.add(new Loadout("ノーアーマー + 鉄の剣 + パール",
                set(null, null, null, null), false,
                () -> List.of(it(Material.IRON_SWORD, 1), it(Material.ENDER_PEARL, 2), it(Material.GOLDEN_APPLE, 5))));
        l.add(new Loadout("鉄上半身 + クロスボウ",
                set(Material.IRON_HELMET, Material.IRON_CHESTPLATE, null, null), false,
                () -> List.of(it(Material.STONE_AXE, 1), it(Material.CROSSBOW, 1), it(Material.ARROW, 20), it(Material.GOLDEN_APPLE, 2))));
        l.add(new Loadout("チェーン + トライデント + 盾",
                set(null, Material.CHAINMAIL_CHESTPLATE, null, Material.CHAINMAIL_BOOTS), true,
                () -> List.of(it(Material.TRIDENT, 1), it(Material.GOLDEN_APPLE, 1))));
        l.add(new Loadout("ダイヤ兜&ブーツ + 鉄の剣",
                set(Material.DIAMOND_HELMET, null, null, Material.DIAMOND_BOOTS), false,
                () -> List.of(it(Material.IRON_SWORD, 1), it(Material.GOLDEN_APPLE, 2))));
        return l;
    }

    // ------------------------------------------------------------ geometry
    private static boolean inFootprint(Arena a, Block b) {
        return Math.abs(b.getX() - a.cx) <= HALF && Math.abs(b.getZ() - a.cz) <= HALF;
    }

    private static boolean isCell(Arena a, Block b) {
        return inFootprint(a, b) && b.getY() == a.cy;
    }

    public int cellsOwned(Match m, MatchTeam t) {
        Arena a = m.arena();
        Material wool = Colors.wool(t.color);
        int n = 0;
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                if (m.world().getBlockAt(a.cx + dx, a.cy, a.cz + dz).getType() == wool) n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------ hooks
    @Override
    public void onStart(Match m) {
        loadout = null;
        warned.clear();
        if (fill()) m.broadcastToMatch("&e勝利条件: &f敵を全員倒す &7or &f中央5x5を自分の色の羊毛で埋める");
        else m.broadcastToMatch("&e勝利条件: &f敵チームを全滅させる" + (deathChest() ? " &7(倒した相手の遺品はチェストに入る)" : ""));
    }

    @Override
    public void onGraceEnd(Match m) {
        List<Loadout> pool = pool();
        loadout = pool.get(random.nextInt(pool.size()));
        int stacks = Math.max(1, cfg().getInt("wool-stacks", 3));
        for (MatchPlayer mp : m.allPlayers()) {
            Player p = mp.player();
            if (p == null || mp.left || !mp.alive) continue;
            giveKit(p, mp.team, stacks);
            Sfx.gearGive(p);
            Msg.title(p, "&6&lFIGHT!", "&e装備: &f" + loadout.name(), 0, 50, 10);
            p.sendMessage(Msg.c(Msg.PREFIX + "&a全員に同じ装備が配布されました: &e" + loadout.name()));
        }
    }

    private void giveKit(Player p, MatchTeam team, int stacks) {
        PlayerInventory inv = p.getInventory();
        Material[] a = loadout.armor();
        if (a[0] != null) inv.setHelmet(new ItemStack(a[0]));
        if (a[1] != null) inv.setChestplate(new ItemStack(a[1]));
        if (a[2] != null) inv.setLeggings(new ItemStack(a[2]));
        if (a[3] != null) inv.setBoots(new ItemStack(a[3]));
        if (loadout.shield()) inv.setItemInOffHand(new ItemStack(Material.SHIELD));
        for (ItemStack s : loadout.items().get()) inv.addItem(s);
        inv.addItem(new ItemStack(Material.SHEARS));
        for (int i = 0; i < stacks; i++) inv.addItem(new ItemStack(Colors.wool(team.color), 64));
    }

    @Override
    public void onSecond(Match m) {
        int left = m.timeLeft();
        if (left == 60 || left == 30) {
            m.broadcastToMatch("&c残り " + left + " 秒!");
            m.playAll(Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
        if (left > 0 && left <= 10) {
            for (MatchPlayer mp : m.allPlayers()) {
                Player p = mp.player();
                if (p == null || mp.left) continue;
                Msg.actionBar(p, "&c&l残り " + left + " 秒");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f + (10 - left) * 0.08f);
            }
        }
    }

    @Override
    public boolean canPlace(Match m, MatchPlayer p, Block b) {
        if (!fill()) return true;
        if (inFootprint(m.arena(), b) && b.getY() != m.arena().cy) {
            Player pl = p.player();
            if (pl != null) {
                Msg.actionBar(pl, "&c中央エリアの上には設置できません");
                Sfx.deny(pl);
            }
            return false;
        }
        return true;
    }

    @Override
    public boolean decayExempt(Match m, Block b) {
        return fill() && isCell(m.arena(), b);
    }

    @Override
    public void onBlockPlaced(Match m, MatchPlayer p, Block b) {
        if (!fill() || !isCell(m.arena(), b)) return;
        m.world().playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.3f);
        int n = cellsOwned(m, p.team);
        if (n >= CELLS) {
            m.finish(p.team, "中央5x5を制圧");
            return;
        }
        if (n >= CELLS - 3 && warned.getOrDefault(p.team.index, 0) != n) {
            warned.put(p.team.index, n);
            for (MatchPlayer mp : m.allPlayers()) {
                Player pl = mp.player();
                if (pl == null || mp.left) continue;
                Msg.actionBar(pl, Colors.code(p.team.color) + p.team.label() + " &cが制圧目前! (" + n + "/" + CELLS + ")");
                pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void onBlockBroken(Match m, MatchPlayer p, Block b) {
        if (fill() && isCell(m.arena(), b)) {
            m.world().playSound(b.getLocation(), Sound.BLOCK_WOOL_BREAK, 1.0f, 0.8f);
        }
    }

    @Override
    public int standing(Match m, MatchTeam t) {
        return fill() ? cellsOwned(m, t) : t.aliveCount();
    }

    @Override
    public MatchTeam pickTimeUpWinner(Match m) {
        List<MatchTeam> alive = new ArrayList<>();
        for (MatchTeam t : m.teams()) if (t.aliveCount() > 0) alive.add(t);
        if (alive.isEmpty()) return null;
        alive.sort(Comparator.<MatchTeam>comparingInt(t -> fill() ? cellsOwned(m, t) : t.aliveCount()).reversed()
                .thenComparing(Comparator.<MatchTeam>comparingInt(MatchTeam::kills).reversed())
                .thenComparing(Comparator.<MatchTeam>comparingInt(MatchTeam::points).reversed()));
        return alive.get(0);
    }

    @Override
    public List<String> extraSidebar(Match m, MatchPlayer viewer) {
        if (!fill()) {
            return List.of("&fチーム生存: &a" + viewer.team.aliveCount() + "&7/" + viewer.team.members.size(),
                    "&fキル: &a" + viewer.kills,
                    "&fあなたの色: " + Colors.code(viewer.team.color) + Colors.jp(viewer.team.color));
        }
        return List.of("&f制圧マス: &a" + cellsOwned(m, viewer.team) + "&7/" + CELLS,
                "&fあなたの色: " + Colors.code(viewer.team.color) + Colors.jp(viewer.team.color));
    }

    // ------------------------------------------------------------ 遺品チェスト (DUO)
    private Block scanColumn(Match m, int x, int z, int startY) {
        Arena a = m.arena();
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

    @Override
    public void onDeathLoot(Match m, MatchPlayer victim, Location loc, List<ItemStack> loot) {
        if (!deathChest() || loot.isEmpty()) return;
        Arena a = m.arena();
        World w = m.world();
        Block spot = null;
        for (int r = 0; r <= 3 && spot == null; r++) {
            for (int dx = -r; dx <= r && spot == null; dx++) {
                for (int dz = -r; dz <= r && spot == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    spot = scanColumn(m, loc.getBlockX() + dx, loc.getBlockZ() + dz, Math.min(loc.getBlockY(), a.cy + 12));
                }
            }
        }
        if (spot == null) spot = scanColumn(m, a.cx + 4, a.cz + 4, a.cy + 3); // 奈落などの場合は中央付近
        if (spot == null) {
            Location drop = loc.getY() < a.cy - 2 ? a.center() : loc;
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
}
