package net.gate88.wars.arena;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.gate88.wars.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
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
    public boolean enabled = true;
    public String map;

    public int maxTeams = 0;
    public int teamSize = 0;

    public final Set<Material> breakOnStart = new HashSet<>();

    // ★ アリーナ個別の時間・ルール設定
    public boolean customTimingEnabled = false; // 個別設定の有効/無効フラグ
    public int breakDelaySeconds = 0;          // 開始から特定ブロック破壊までの秒数
    public int customGraceSeconds = 5;         // ブロック破壊後からアイテム配布までの秒数
    public int customDurationSeconds = 0;      // 個別試合時間 (0=モード準拠)
    public int customBlockDecaySeconds = 0;    // 個別設置ブロック崩壊秒数 (0=モード準拠)

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

    public void setSpawn(int teamIndex, Pos pos) {
        while (spawns.size() <= teamIndex) {
            spawns.add(pos);
        }
        spawns.set(teamIndex, pos);
    }

    public boolean addBreakBlock(Material m) {
        if (m != null && m.isBlock() && !m.isAir()) {
            return breakOnStart.add(m);
        }
        return false;
    }

    public boolean removeBreakBlock(Material m) {
        return breakOnStart.remove(m);
    }

    public void clearBreakBlocks() {
        breakOnStart.clear();
    }

    // ★ 大元設定との優先判定
    public int getBreakDelay() {
        return customTimingEnabled ? Math.max(0, breakDelaySeconds) : 0;
    }

    public int getGraceSeconds(int defaultGrace) {
        return customTimingEnabled ? Math.max(0, customGraceSeconds) : defaultGrace;
    }

    public int getDurationSeconds(int defaultDuration) {
        return (customTimingEnabled && customDurationSeconds > 0) ? customDurationSeconds : defaultDuration;
    }

    public int getBlockDecaySeconds(int defaultDecay) {
        return (customTimingEnabled && customBlockDecaySeconds > 0) ? customBlockDecaySeconds : defaultDecay;
    }
}