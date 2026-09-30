package net.gate88.wars.points;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** 累計ポイント (ロビーのランキングホログラム用) */
public final class PointsManager {
    public static final class Entry {
        public final UUID uuid;
        public String name;
        public long points;
        public int wins;
        public int kills;
        public int played;

        Entry(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private final WarsPlugin plugin;
    private final File file;
    private final Map<UUID, Entry> entries = new HashMap<>();

    public PointsManager(WarsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");
    }

    public void load() {
        entries.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                ConfigurationSection s = sec.getConfigurationSection(key);
                Entry e = new Entry(u, s.getString("name", "?"));
                e.points = s.getLong("points");
                e.wins = s.getInt("wins");
                e.kills = s.getInt("kills");
                e.played = s.getInt("played");
                entries.put(u, e);
            } catch (Exception ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Entry e : entries.values()) {
            String p = "players." + e.uuid + ".";
            y.set(p + "name", e.name);
            y.set(p + "points", e.points);
            y.set(p + "wins", e.wins);
            y.set(p + "kills", e.kills);
            y.set(p + "played", e.played);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("points.yml の保存に失敗: " + ex.getMessage());
        }
    }

    public Entry entry(UUID u, String name) {
        Entry e = entries.computeIfAbsent(u, k -> new Entry(u, name));
        if (name != null) e.name = name;
        return e;
    }

    public Entry find(UUID u) {
        return entries.get(u);
    }

    public Entry findByName(String name) {
        for (Entry e : entries.values()) {
            if (e.name.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    public long total(UUID u) {
        Entry e = entries.get(u);
        return e == null ? 0 : e.points;
    }

    public List<Entry> top(int n) {
        List<Entry> l = new ArrayList<>(entries.values());
        l.removeIf(e -> e.points <= 0);
        l.sort(Comparator.<Entry>comparingLong(e -> e.points).reversed().thenComparing(e -> e.name));
        return l.size() > n ? new ArrayList<>(l.subList(0, n)) : l;
    }

    /** 1始まり。ポイント無しは 0 */
    public int rankOf(UUID u) {
        Entry me = entries.get(u);
        if (me == null || me.points <= 0) return 0;
        int r = 1;
        for (Entry e : entries.values()) {
            if (e.points > me.points) r++;
        }
        return r;
    }
}
