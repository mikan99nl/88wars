package net.gate88.wars.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Pos;
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
            for (String s : a.getStringList("spawns")) {
                try {
                    arena.spawns.add(Pos.parse(s));
                } catch (Exception ignored) {
                }
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

    public boolean delete(String id) {
        return arenas.remove(id.toLowerCase()) != null;
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
