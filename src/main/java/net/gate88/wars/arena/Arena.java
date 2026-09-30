package net.gate88.wars.arena;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * アリーナ定義。
 * center = ボーダー中心 / 中央エリア(5x5)の中心。cy = 立つ高さ(=中央マスの高さ)。
 */
public final class Arena {
    public final String id;
    public String modeId;
    public String worldName;
    public int cx, cy, cz;
    public final List<Pos> spawns = new ArrayList<>();

    public Arena(String id, String modeId) {
        this.id = id;
        this.modeId = modeId;
    }

    public World world() {
        return worldName == null ? null : Bukkit.getWorld(worldName);
    }

    public Location center() {
        World w = world();
        return w == null ? null : new Location(w, cx + 0.5, cy, cz + 0.5);
    }

    public boolean isReady() {
        return world() != null && modeId != null && !spawns.isEmpty();
    }
}
