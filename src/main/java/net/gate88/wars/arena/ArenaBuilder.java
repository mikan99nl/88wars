package net.gate88.wars.arena;

import net.gate88.wars.util.Pos;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Randomizer用の簡易アリーナ自動生成 (61x61 の平面 + 中央 5x5 パッド + 遮蔽物 + 16スポーン) */
public final class ArenaBuilder {
    public static final int RADIUS = 30;
    public static final int SPAWN_RADIUS = 22;

    private ArenaBuilder() {}

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
        // 中央: 7x7 の縁 + 5x5 のパッド
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean pad = Math.abs(dx) <= 2 && Math.abs(dz) <= 2;
                w.getBlockAt(a.cx + dx, y0, a.cz + dz)
                        .setType(pad ? Material.SMOOTH_QUARTZ : Material.POLISHED_BLACKSTONE_BRICKS, false);
            }
        }
        // 遮蔽物の柱
        for (int k = 0; k < 8; k++) {
            double ang = Math.toRadians(k * 45 + 22.5);
            int px = a.cx + (int) Math.round(Math.cos(ang) * 11);
            int pz = a.cz + (int) Math.round(Math.sin(ang) * 11);
            for (int y = a.cy; y < a.cy + 3; y++) {
                w.getBlockAt(px, y, pz).setType(Material.STONE_BRICKS, false);
            }
        }
        // スポーン16個
        a.spawns.clear();
        for (int k = 0; k < 16; k++) {
            double ang = Math.toRadians(k * 22.5);
            double sx = Math.cos(ang) * SPAWN_RADIUS;
            double sz = Math.sin(ang) * SPAWN_RADIUS;
            float yaw = (float) Math.toDegrees(Math.atan2(sx, -sz)); // 中央を向く
            a.spawns.add(new Pos(a.cx + 0.5 + sx, a.cy, a.cz + 0.5 + sz, yaw, 0f));
        }
    }
}
