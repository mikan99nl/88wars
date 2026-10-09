package net.gate88.wars.match;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** プレイヤー設置ブロックおよび水流の管理 (ヒビ割れ崩壊 / 試合終了時に原状復帰) */
public final class BlockTracker {
    private record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) {
            return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private final WarsPlugin plugin;
    private final Set<Key> active = new HashSet<>();
    private final Map<Key, BlockState> originals = new HashMap<>();
    private final Map<Key, BukkitTask> tasks = new HashMap<>();
    // 水流・溶岩流として広がったブロックの記録
    private final Set<Key> fluidBlocks = new HashSet<>();

    public BlockTracker(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive(Block b) {
        return active.contains(Key.of(b));
    }

    public void track(Block b, BlockState replaced, boolean decay, int seconds) {
        Key k = Key.of(b);
        originals.putIfAbsent(k, replaced);
        active.add(k);

        BukkitTask old = tasks.remove(k);
        if (old != null) old.cancel();
        clearCrack(k);

        if (decay && seconds > 0) {
            final int totalTicks = seconds * 20;
            final int intervalTicks = 5;
            final int[] elapsed = {0};
            final int sourceId = Math.abs(k.hashCode());

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                elapsed[0] += intervalTicks;

                if (elapsed[0] >= totalTicks) {
                    decay(k);
                    return;
                }

                float progress = Math.min(0.99f, (float) elapsed[0] / totalTicks);
                sendCrack(k, progress, sourceId);

                int remainingTicks = totalTicks - elapsed[0];
                if (remainingTicks <= 30 && elapsed[0] % 10 == 0) {
                    World w = Bukkit.getWorld(k.world());
                    if (w != null) {
                        Location center = new Location(w, k.x() + 0.5, k.y() + 0.5, k.z() + 0.5);
                        Block block = w.getBlockAt(k.x(), k.y(), k.z());
                        w.spawnParticle(Particle.BLOCK, center, 4, 0.2, 0.2, 0.2, block.getBlockData());
                        w.playSound(center, block.getBlockData().getSoundGroup().getHitSound(), 0.5f, 1.4f);
                    }
                }
            }, intervalTicks, intervalTicks);

            tasks.put(k, task);
        }
    }

    /** 流れ出た水・溶岩ブロックを記録 */
    public void trackFluid(Block b, BlockState original) {
        Key k = Key.of(b);
        originals.putIfAbsent(k, original);
        fluidBlocks.add(k);
    }

    /** 固定ブロック(遺品チェスト・中央の白色コンクリートなど)。原状復帰だけ行う */
    public void trackFixed(Block b, BlockState replaced) {
        originals.putIfAbsent(Key.of(b), replaced);
    }

    public void onBroken(Block b) {
        Key k = Key.of(b);
        active.remove(k);
        clearCrack(k);
        BukkitTask t = tasks.remove(k);
        if (t != null) t.cancel();
    }

    private void decay(Key k) {
        BukkitTask t = tasks.remove(k);
        if (t != null) t.cancel();
        clearCrack(k);

        if (!active.remove(k)) return;
        World w = Bukkit.getWorld(k.world());
        if (w == null) return;
        Block b = w.getBlockAt(k.x(), k.y(), k.z());

        if (!b.getType().isAir()) {
            Location center = b.getLocation().add(0.5, 0.5, 0.5);
            w.spawnParticle(Particle.BLOCK, center, 18, 0.3, 0.3, 0.3, b.getBlockData());
            w.playSound(b.getLocation(), b.getBlockData().getSoundGroup().getBreakSound(), 0.8f, 1.0f);
        }

        // 水源・溶岩が消える場合、周囲に広がる水流も消滅を促す
        boolean isLiquid = b.isLiquid();

        BlockState orig = originals.get(k);
        if (orig != null) orig.update(true, true);
        else b.setType(Material.AIR, true);

        if (isLiquid) {
            clearAdjacentFlowingWater(b);
        }
    }

    /** 周囲の水流をきれいに消去 */
    private void clearAdjacentFlowingWater(Block center) {
        int[] dx = {-1, 1, 0, 0, 0, 0};
        int[] dy = {0, 0, -1, 1, 0, 0};
        int[] dz = {0, 0, 0, 0, -1, 1};

        for (int i = 0; i < 6; i++) {
            Block adj = center.getRelative(dx[i], dy[i], dz[i]);
            if (adj.isLiquid()) {
                Key ak = Key.of(adj);
                if (fluidBlocks.contains(ak) || active.contains(ak)) {
                    BlockState s = originals.get(ak);
                    if (s != null) s.update(true, true);
                    else adj.setType(Material.AIR, true);
                }
            }
        }
    }
    /** 破壊された既存ブロックを復元用として記録 */
    public void trackBreak(Block b) {
        originals.putIfAbsent(Key.of(b), b.getState());
    }

    /** 試合終了時: 広がった水流も含めて完全リセット */
    public void restoreAll() {
        for (Map.Entry<Key, BukkitTask> entry : tasks.entrySet()) {
            entry.getValue().cancel();
            clearCrack(entry.getKey());
        }
        tasks.clear();

        // 液体ブロックを先に空気へ戻して水流の残りを防ぐ
        for (Key k : fluidBlocks) {
            World w = Bukkit.getWorld(k.world());
            if (w != null) {
                Block b = w.getBlockAt(k.x(), k.y(), k.z());
                if (b.isLiquid()) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        fluidBlocks.clear();

        for (BlockState s : originals.values()) {
            s.update(true, false);
        }
        originals.clear();
        active.clear();
    }

    private void sendCrack(Key k, float progress, int sourceId) {
        World w = Bukkit.getWorld(k.world());
        if (w == null) return;
        Location loc = new Location(w, k.x(), k.y(), k.z());
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 48 * 48) {
                p.sendBlockDamage(loc, progress, sourceId);
            }
        }
    }

    private void clearCrack(Key k) {
        World w = Bukkit.getWorld(k.world());
        if (w == null) return;
        Location loc = new Location(w, k.x(), k.y(), k.z());
        int sourceId = Math.abs(k.hashCode());
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(loc) <= 48 * 48) {
                p.sendBlockDamage(loc, 0.0f, sourceId);
            }
        }
    }
}