package net.gate88.wars.arena;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Pos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

/**
 * プレイヤーが作ったマップの保存庫 (plugins/88Wars/maps/)。
 * <name>.schem = 建物本体 (WorldEdit形式) / <name>.yml = 中心からの相対スポーン。
 */
public final class MapStore {
    private final WarsPlugin plugin;

    public MapStore(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    public static boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
    }

    public static boolean validName(String name) {
        return name.matches("[A-Za-z0-9_-]{1,32}");
    }

    private File dir() {
        return new File(plugin.getDataFolder(), "maps");
    }

    private File schem(String name) {
        return new File(dir(), name.toLowerCase(Locale.ROOT) + ".schem");
    }

    private File meta(String name) {
        return new File(dir(), name.toLowerCase(Locale.ROOT) + ".yml");
    }

    public boolean exists(String name) {
        return schem(name).isFile();
    }

    public List<String> list() {
        List<String> l = new ArrayList<>();
        File[] fs = dir().listFiles((d, n) -> n.endsWith(".schem"));
        if (fs == null) return l;
        for (File f : fs) l.add(f.getName().substring(0, f.getName().length() - ".schem".length()));
        l.sort(null);
        return l;
    }

    /** WorldEdit の範囲選択を保存。立っている位置 = アリーナ中心。既存のスポーン情報はリセット */
    public long save(Player p, String name) throws Exception {
        dir().mkdirs();
        long n = WorldEditBridge.saveSelection(p, p.getLocation(), schem(name));
        File m = meta(name);
        if (m.exists() && !m.delete()) plugin.getLogger().warning("古いマップ情報を削除できません: " + m.getName());
        return n;
    }

    public void paste(Player p, String name, Location at) throws Exception {
        WorldEditBridge.paste(p, schem(name), at);
    }

    public boolean delete(String name) {
        boolean ok = schem(name).delete();
        meta(name).delete();
        return ok;
    }

    /** マップに記録された中心からの相対スポーンを、アリーナの中心基準の絶対座標にして返す */
    public List<Pos> spawnsFor(String name, Arena a) {
        List<Pos> out = new ArrayList<>();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(meta(name));
        for (String s : y.getStringList("spawns")) {
            try {
                Pos r = Pos.parse(s);
                out.add(new Pos(a.cx + r.x(), a.cy + r.y(), a.cz + r.z(), r.yaw(), r.pitch()));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** アリーナのスポーンを相対座標にしてマップ側に記録 (次回の貼り付けで自動的に使われる) */
    public void storeSpawns(Arena a) {
        if (a.map == null || !exists(a.map)) return;
        YamlConfiguration y = new YamlConfiguration();
        List<String> l = new ArrayList<>();
        for (Pos s : a.spawns) l.add(new Pos(s.x() - a.cx, s.y() - a.cy, s.z() - a.cz, s.yaw(), s.pitch()).serialize());
        y.set("spawns", l);
        try {
            y.save(meta(a.map));
        } catch (IOException e) {
            plugin.getLogger().warning("マップ情報の保存に失敗: " + e.getMessage());
        }
    }
}
