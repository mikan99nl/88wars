package net.gate88.wars.match;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/** プレイヤー設置ブロックの管理 (ヒビ割れを伴う段階的崩壊 / 試合終了時に原状復帰) */
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
        clearCrack(k);

        if (decay && seconds > 0) {
            final int totalTicks = seconds * 20;
            final int intervalTicks = 5; // 0.25秒ごとに滑らかにヒビ割れを更新
            final int[] elapsed = {0};
            final int sourceId = Math.abs(k.hashCode());

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                elapsed[0] += intervalTicks;

                if (elapsed[0] >= totalTicks) {
                    decay(k);
                    return;
                }

                // ヒビ割れ進行度: 0.0f (無傷) ～ 0.99f (最大ヒビ割れ)
                float progress = Math.min(0.99f, (float) elapsed[0] / totalTicks);
                sendCrack(k, progress, sourceId);

                // 崩壊直前（残り1.5秒以内）の予兆演出（ヒビ音 & 破片パーティクル）
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

    /** 壊せない・消えないブロック(遺品チェスト等)。試合終了時に原状復帰だけする */
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
            // 素材に合わせた破片パーティクルと破壊音を再生
            w.spawnParticle(Particle.BLOCK, center, 18, 0.3, 0.3, 0.3, b.getBlockData());
            w.playSound(b.getLocation(), b.getBlockData().getSoundGroup().getBreakSound(), 0.8f, 1.0f);
        }
        BlockState orig = originals.get(k);
        if (orig != null) orig.update(true, false);
        else b.setType(org.bukkit.Material.AIR, false);
    }

    public void restoreAll() {
        for (Map.Entry<Key, BukkitTask> entry : tasks.entrySet()) {
            entry.getValue().cancel();
            clearCrack(entry.getKey());
        }
        tasks.clear();
        for (BlockState s : originals.values()) {
            s.update(true, false);
        }
        originals.clear();
        active.clear();
    }

    /** 周囲のプレイヤーにヒビ割れ進行度パケットを送信 */
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

    /** ヒビ割れ表示をリセット */
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