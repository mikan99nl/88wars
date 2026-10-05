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
    /** false = 編集中。試合には使われない (生成・貼り付け直後は false) */
    public boolean enabled = true;
    /** 貼り付け元のマップ名 (maps/<name>.schem)。自動生成アリーナは null */
    public String map;

    /** 0 = モード設定準拠 / 1以上 = アリーナ個別の最大チーム数 */
    public int maxTeams = 0;
    /** 0 = モード設定準拠 / 1以上 = 1チームあたりの最大人数 */
    public int teamSize = 0;

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
        return enabled && world() != null && modeId != null && !spawns.isEmpty();
    }

    /** チーム番号 (0〜N) に応じたスポーン地点を設定 */
    public void setSpawn(int teamIndex, Pos pos) {
        while (spawns.size() <= teamIndex) {
            spawns.add(pos);
        }
        spawns.set(teamIndex, pos);
    }
}