package net.gate88.wars.mode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
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
 * Randomizer:
 *  - 3分以内に敵を全員倒す、または中央 5x5 を自分の色の羊毛で埋め尽くせば勝利
 *  - 中央には初期状態で白色コンクリートが敷き詰められ、ツルハシで破壊して自分の羊毛に置き換える
 */
public final class RandomizerMode extends WarsMode {
    public static final int HALF = 2; // 5x5
    public static final int CELLS = (HALF * 2 + 1) * (HALF * 2 + 1);

    private final Random random = new Random();
    private String selectedKitId;
    private String selectedKitName;
    private final Map<Integer, Integer> warned = new HashMap<>();

    public RandomizerMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> extraDesc) {
        super(plugin, id, displayName, icon, buildDesc(extraDesc));
    }

    private static List<String> buildDesc(List<String> extra) {
        return new ArrayList<>(extra);
    }

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
        selectedKitId = null;
        selectedKitName = null;
        warned.clear();

        if (fill()) {
            Arena a = m.arena();
            World w = m.world();
            for (int dx = -HALF; dx <= HALF; dx++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    Block b = w.getBlockAt(a.cx + dx, a.cy, a.cz + dz);
                    m.blocks().trackFixed(b, b.getState());
                    b.setType(Material.WHITE_CONCRETE, false);
                }
            }
            m.broadcastToMatch("&e勝利条件: &f敵を全員倒す &7or &f中央5x5の白コンクリートを壊して自色羊毛で埋める");
        } else {
            m.broadcastToMatch("&e勝利条件: &f敵チームを全滅させる" + (deathChest() ? " &7(倒した相手の遺品はチェストに入る)" : ""));
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

        boolean isForced = selectedKitId.equalsIgnoreCase(plugin.kits().getForcedKit());
        int stacks = Math.max(1, cfg().getInt("wool-stacks", 3));

        for (MatchPlayer mp : m.allPlayers()) {
            Player p = mp.player();
            if (p == null || mp.left || !mp.alive) continue;

            plugin.kits().applyKit(p, selectedKitId);
            applyWarsExtras(p, mp.team, stacks);

            Sfx.gearGive(p);
            Msg.title(p, "&6&lFIGHT!", "&e装備: &f" + selectedKitName, 0, 50, 10);

            if (isForced) {
                p.sendMessage(Msg.c(Msg.PREFIX + "&6[運営指定] &a全員に &e" + selectedKitName + " &aが配布されました"));
            } else {
                p.sendMessage(Msg.c(Msg.PREFIX + "&a全員に同じ装備が配布されました: &e" + selectedKitName));
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

        // ★ 鉄のツルハシ (効率強化3・攻撃力1・耐久無限) を配布
        if (fill()) {
            ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
            ItemMeta pmeta = pickaxe.getItemMeta();
            if (pmeta != null) {
                pmeta.displayName(Msg.c("&b&l中央コンクリート破壊用ツルハシ"));
                pmeta.lore(List.of(Msg.c("&7中央の白色コンクリートを破壊できます (攻撃力: 1)")));
                pmeta.addEnchant(Enchantment.EFFICIENCY, 3, true);
                pmeta.setUnbreakable(true);

                // ★ 攻撃力を1に固定 (基礎攻撃力1.0 + 0.0)
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
                Msg.actionBar(pl, Colors.code(p.team.color) + "&l" + Colors.en(p.team.color) + " &f" + p.team.label() + " &cが制圧目前! (" + n + "/" + CELLS + ")");
                pl.playSound(pl.getLocation(), Sound.BLOCK_BELL_USE, 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void onBlockBroken(Match m, MatchPlayer p, Block b) {
        if (fill() && isCell(m.arena(), b)) {
            if (b.getType() == Material.WHITE_CONCRETE) {
                m.world().playSound(b.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0f, 1.0f);
            } else {
                m.world().playSound(b.getLocation(), Sound.BLOCK_WOOL_BREAK, 1.0f, 0.8f);
            }
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
        alive.sort(Comparator.comparingInt((MatchTeam t) -> fill() ? cellsOwned(m, t) : t.aliveCount()).reversed()
                .thenComparing(Comparator.comparingInt(MatchTeam::kills).reversed())
                .thenComparing(Comparator.comparingInt(MatchTeam::points).reversed()));
        return alive.getFirst();
    }

    @Override
    public List<String> extraSidebar(Match m, MatchPlayer viewer) {
        if (!fill()) {
            return List.of("&fチーム生存: &a" + viewer.team.aliveCount() + "&7/" + viewer.team.members.size(),
                    "&fキル: &a" + viewer.kills,
                    "&fあなたの色: " + Colors.code(viewer.team.color) + "&l" + Colors.en(viewer.team.color));
        }
        return List.of("&f制圧マス: &a" + cellsOwned(m, viewer.team) + "&7/" + CELLS,
                "&fあなたの色: " + Colors.code(viewer.team.color) + "&l" + Colors.en(viewer.team.color));
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
        if (spot == null) spot = scanColumn(m, a.cx + 4, a.cz + 4, a.cy + 3);
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
}