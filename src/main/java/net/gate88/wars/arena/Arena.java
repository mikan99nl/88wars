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
 * アリーナ定義 (親アリーナ・子アリーナ階層構造対応)
 */
public final class Arena {
    public final String id;
    public String modeId;
    public String worldName;
    public int cx, cy, cz;
    public final List<Pos> spawns = new ArrayList<>();
    public boolean enabled = true;
    public String map;

    // ★ 親子アリーナ関係 (parentId が null なら親、存在すれば子)
    public String parentId = null;
    public final List<String> childIds = new ArrayList<>();

    // 個別設定
    public int maxTeams = 0;
    public int teamSize = 0;
    public final Set<Material> breakOnStart = new HashSet<>();
    public boolean customTimingEnabled = false;
    public int breakDelaySeconds = 0;
    public int customGraceSeconds = 5;
    public int customDurationSeconds = 0;
    public int customBlockDecaySeconds = 0;

    public Arena(String id, String modeId) {
        this.id = id;
        this.modeId = modeId;
    }

    public boolean isChild() {
        return parentId != null;
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

    // ★ 設定の引き継ぎ (親アリーナが存在する場合は親の設定を参照・同期)
    public Arena getEffectiveParent(ArenaManager manager) {
        if (isChild() && manager != null) {
            Arena parent = manager.get(parentId);
            if (parent != null) return parent;
        }
        return this;
    }

    public int getEffectiveMaxTeams(ArenaManager manager) {
        return getEffectiveParent(manager).maxTeams;
    }

    public int getEffectiveTeamSize(ArenaManager manager) {
        return getEffectiveParent(manager).teamSize;
    }

    public Set<Material> getEffectiveBreakOnStart(ArenaManager manager) {
        return getEffectiveParent(manager).breakOnStart;
    }

    public int getBreakDelay(int defaultBreakDelay, ArenaManager manager) {
        Arena eff = getEffectiveParent(manager);
        return eff.customTimingEnabled ? Math.max(0, eff.breakDelaySeconds) : defaultBreakDelay;
    }

    public int getGraceSeconds(int defaultGrace, ArenaManager manager) {
        Arena eff = getEffectiveParent(manager);
        return eff.customTimingEnabled ? Math.max(0, eff.customGraceSeconds) : defaultGrace;
    }

    public int getDurationSeconds(int defaultDuration, ArenaManager manager) {
        Arena eff = getEffectiveParent(manager);
        return (eff.customTimingEnabled && eff.customDurationSeconds > 0) ? eff.customDurationSeconds : defaultDuration;
    }

    public int getBlockDecaySeconds(int defaultDecay, ArenaManager manager) {
        Arena eff = getEffectiveParent(manager);
        return (eff.customTimingEnabled && eff.customBlockDecaySeconds > 0) ? eff.customBlockDecaySeconds : defaultDecay;
    }
}