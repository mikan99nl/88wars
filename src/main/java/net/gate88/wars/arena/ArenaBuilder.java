package net.gate88.wars.arena;

import net.gate88.wars.util.Pos;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** アリーナ自動生成 (Randomizer用 61x61 / Spleef用 5層円形雪ステージ) */
public final class ArenaBuilder {
    public static final int RADIUS = 30;
    public static final int SPAWN_RADIUS = 22;

    private ArenaBuilder() {}

    /** Randomizer用アリーナ生成 */
    public static void build(Arena a) {
        World w = a.world();
        if (w == null) return;
        int y0 = a.cy - 1;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                Material floor = ((dx + dz) & 1) == 0 ? Material.POLISHED_ANDESITE : Material.ANDESITE;
                w.getBlockAt(a.cx + dx, y0, a.cz + dz).setType(floor, false);
                for (int y = a.cy; y <= a.cy + 8; y++) {
                    Block b = w.getBlockAt(a.cx + dx, y, a.cz + dz);
                    if (!b.getType().isAir()) b.setType(Material.AIR, false);
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean pad = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                w.getBlockAt(a.cx + dx, y0, a.cz + dz)
                        .setType(pad ? Material.SMOOTH_QUARTZ : Material.POLISHED_BLACKSTONE_BRICKS, false);
            }
        }
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(k * 45 + 22.5);
            int px = a.cx + (int) Math.round(Math.cos(ang) * 11);
            int pz = a.cz + (int) Math.round(Math.sin(ang) * 11);
            for (int y = a.cy; y < a.cy + 3; y++) {
                w.getBlockAt(px, y, pz).setType(Material.STONE_BRICKS, false);
            }
        }
        a.spawns.clear();
        for (int k = 0; k < 16; k++) {
            double ang = Math.toRadians(k * 22.5);
            double sx = Math.cos(ang) * SPAWN_RADIUS;
            double sz = Math.sin(ang) * SPAWN_RADIUS;
            float yaw = (float) Math.toDegrees(Math.atan2(sx, -sz));
            a.spawns.add(new Pos(a.cx + 0.5 + sx, a.cy, a.cz + 0.5 + sz, yaw, 0f));
        }
    }

    /** ★ Spleef用 5層円形雪ステージ (雪ブロック以外を一切含めず、指定半径でビルド) */
    public static void buildSpleef(Arena a) {
        World w = a.world();
        if (w == null) return;

        int layers = 5;
        int layerGap = 6;
        // 指定された半径 (未指定時は24)
        int r = (a.spleefRadius > 0) ? a.spleefRadius : 24;

        for (int layer = 0; layer < layers; layer++) {
            int y = a.cy - (layer * layerGap);

            // 上部空間のクリア
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    for (int dy = 1; dy < layerGap; dy++) {
                        Block b = w.getBlockAt(a.cx + dx, y + dy, a.cz + dz);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
                }
            }

            // 床面生成: 純粋に雪ブロック (SNOW_BLOCK) のみで生成
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= r * r) {
                        w.getBlockAt(a.cx + dx, y, a.cz + dz).setType(Material.SNOW_BLOCK, false);
                    }
                }
            }
        }

        // 最上層の外周に16箇所のスポーン地点を自動計算
        a.spawns.clear();
        int spawnCount = 16;
        double spawnR = Math.max(3.0, r - 3.0);
        for (int i = 0; i < spawnCount; i++) {
            double ang = 2 * Math.PI * i / spawnCount;
            double sx = Math.cos(ang) * spawnR;
            double sz = Math.sin(ang) * spawnR;
            float yaw = (float) Math.toDegrees(Math.atan2(-sx, sz));
            a.spawns.add(new Pos(a.cx + 0.5 + sx, a.cy + 1.0, a.cz + 0.5 + sz, yaw, 0f));
        }
    }
}