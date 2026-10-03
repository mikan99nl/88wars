package net.gate88.wars.match;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.scheduler.BukkitTask;

/** プレイヤー設置ブロックの管理 (一定時間後に消滅 / 試合終了時に原状復帰) */
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
        if (decay && seconds > 0) {
            tasks.put(k, Bukkit.getScheduler().runTaskLater(plugin, () -> decay(k), seconds * 20L));
        }
    }

    /** 壊せない・消えないブロック(遺品チェスト等)。試合終了時に原状復帰だけする */
    public void trackFixed(Block b, BlockState replaced) {
        originals.putIfAbsent(Key.of(b), replaced);
    }

    public void onBroken(Block b) {
        Key k = Key.of(b);
        active.remove(k);
        BukkitTask t = tasks.remove(k);
        if (t != null) t.cancel();
    }

    private void decay(Key k) {
        tasks.remove(k);
        if (!active.remove(k)) return;
        World w = Bukkit.getWorld(k.world());
        if (w == null) return;
        Block b = w.getBlockAt(k.x(), k.y(), k.z());
        if (!b.getType().isAir()) {
            w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 14, 0.25, 0.25, 0.25, b.getBlockData());
            w.playSound(b.getLocation(), Sound.BLOCK_WOOL_BREAK, 0.6f, 1.2f);
        }
        BlockState orig = originals.get(k);
        if (orig != null) orig.update(true, false);
        else b.setType(org.bukkit.Material.AIR, false);
    }

    public void restoreAll() {
        for (BukkitTask t : tasks.values()) t.cancel();
        tasks.clear();
        for (BlockState s : originals.values()) {
            s.update(true, false);
        }
        originals.clear();
        active.clear();
    }
}
