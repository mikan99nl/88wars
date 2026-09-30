package net.gate88.wars.util;

import org.bukkit.Location;
import org.bukkit.World;

/** ワールド非依存の座標 (data.yml 保存用) */
public record Pos(double x, double y, double z, float yaw, float pitch) {

    public static Pos of(Location l) {
        return new Pos(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location toLocation(World w) {
        return new Location(w, x, y, z, yaw, pitch);
    }

    public String serialize() {
        return x + "," + y + "," + z + "," + yaw + "," + pitch;
    }

    public static Pos parse(String s) {
        String[] a = s.split(",");
        return new Pos(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]),
                Float.parseFloat(a[3]), Float.parseFloat(a[4]));
    }
}
