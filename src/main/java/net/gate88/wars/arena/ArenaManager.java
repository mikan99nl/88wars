package net.gate88.wars.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Pos;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public final class ArenaManager {
    private final WarsPlugin plugin;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        arenas.clear();
        ConfigurationSection sec = plugin.data().getConfigurationSection("arenas");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection a = sec.getConfigurationSection(id);
            if (a == null) continue;
            Arena arena = new Arena(id, a.getString("mode"));
            arena.worldName = a.getString("world");
            arena.cx = a.getInt("cx");
            arena.cy = a.getInt("cy");
            arena.cz = a.getInt("cz");
            arena.enabled = a.getBoolean("enabled", true);
            arena.map = a.getString("map");
            arena.parentId = a.getString("parentId", null);
            arena.childIds.addAll(a.getStringList("childIds"));

            arena.maxTeams = a.getInt("maxTeams", 0);
            arena.teamSize = a.getInt("teamSize", 0);
            arena.customTimingEnabled = a.getBoolean("customTimingEnabled", false);
            arena.breakDelaySeconds = a.getInt("breakDelaySeconds", 0);
            arena.customGraceSeconds = a.getInt("customGraceSeconds", 5);
            arena.customDurationSeconds = a.getInt("customDurationSeconds", 0);
            arena.customBlockDecaySeconds = a.getInt("customBlockDecaySeconds", 0);

            for (String mName : a.getStringList("break-on-start")) {
                Material m = Material.getMaterial(mName.toUpperCase());
                if (m != null) arena.breakOnStart.add(m);
            }

            for (String s : a.getStringList("spawns")) {
                try {
                    arena.spawns.add(Pos.parse(s));
                } catch (Exception ignored) {}
            }
            arenas.put(id.toLowerCase(), arena);
        }
    }

    public void save() {
        plugin.data().set("arenas", null);
        for (Arena a : arenas.values()) {
            String p = "arenas." + a.id + ".";
            plugin.data().set(p + "mode", a.modeId);
            plugin.data().set(p + "world", a.worldName);
            plugin.data().set(p + "cx", a.cx);
            plugin.data().set(p + "cy", a.cy);
            plugin.data().set(p + "cz", a.cz);
            plugin.data().set(p + "enabled", a.enabled);
            plugin.data().set(p + "map", a.map);
            plugin.data().set(p + "parentId", a.parentId);
            plugin.data().set(p + "childIds", a.childIds);

            plugin.data().set(p + "maxTeams", a.maxTeams);
            plugin.data().set(p + "teamSize", a.teamSize);
            plugin.data().set(p + "customTimingEnabled", a.customTimingEnabled);
            plugin.data().set(p + "breakDelaySeconds", a.breakDelaySeconds);
            plugin.data().set(p + "customGraceSeconds", a.customGraceSeconds);
            plugin.data().set(p + "customDurationSeconds", a.customDurationSeconds);
            plugin.data().set(p + "customBlockDecaySeconds", a.customBlockDecaySeconds);

            List<String> bList = new ArrayList<>();
            for (Material m : a.breakOnStart) bList.add(m.name());
            plugin.data().set(p + "break-on-start", bList);

            List<String> sp = new ArrayList<>();
            for (Pos pos : a.spawns) sp.add(pos.serialize());
            plugin.data().set(p + "spawns", sp);
        }
        plugin.saveData();
    }

    public Arena get(String id) {
        return id == null ? null : arenas.get(id.toLowerCase());
    }

    public Arena create(String id, String modeId) {
        Arena a = new Arena(id, modeId);
        arenas.put(id.toLowerCase(), a);
        return a;
    }

    /** ★ 子アリーナを作成 */
    public Arena createChild(String parentId, String childId) {
        Arena parent = get(parentId);
        if (parent == null) return null;

        Arena child = new Arena(childId, parent.modeId);
        child.parentId = parent.id;
        parent.childIds.add(child.id);

        arenas.put(childId.toLowerCase(), child);
        save();
        return child;
    }

    public boolean delete(String id) {
        Arena a = arenas.remove(id.toLowerCase());
        if (a != null) {
            // 親なら子も削除
            for (String cId : a.childIds) {
                arenas.remove(cId.toLowerCase());
            }
            // 子なら親のリストから除外
            if (a.isChild()) {
                Arena parent = get(a.parentId);
                if (parent != null) parent.childIds.remove(a.id);
            }
            save();
            return true;
        }
        return false;
    }

    public List<Arena> all() {
        return new ArrayList<>(arenas.values());
    }

    public List<Arena> readyFor(String modeId) {
        List<Arena> l = new ArrayList<>();
        for (Arena a : arenas.values()) {
            if (modeId.equalsIgnoreCase(a.modeId) && a.isReady()) l.add(a);
        }
        return l;
    }
}
