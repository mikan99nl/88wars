package net.gate88.wars;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.gate88.wars.arena.ArenaManager;
import net.gate88.wars.arena.MapStore;
import net.gate88.wars.command.WarsCommand;
import net.gate88.wars.kit.KitManager;
import net.gate88.wars.listener.LobbyListener;
import net.gate88.wars.listener.MatchListener;
import net.gate88.wars.lobby.HologramManager;
import net.gate88.wars.lobby.LobbyManager;
import net.gate88.wars.lobby.PodiumManager;
import net.gate88.wars.match.Match;
import net.gate88.wars.mode.ModeRegistry;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Pos;
import net.gate88.wars.util.Sidebar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.plugin.java.JavaPlugin;

/** 88WARS - ポイント制マルチミニゲーム */
public final class WarsPlugin extends JavaPlugin {
    private File dataFile;
    private YamlConfiguration data;
    private ArenaManager arenas;
    private MapStore maps;
    private PointsManager points;
    private ModeRegistry modes;
    private LobbyManager lobby;
    private HologramManager holograms;
    private PodiumManager podium;
    private KitManager kits;
    private final Map<UUID, Sidebar> sidebars = new HashMap<>();
    private final Set<UUID> trackedTridents = new HashSet<>();
    private Match match;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadData();
        points = new PointsManager(this);
        points.load();
        arenas = new ArenaManager(this);
        arenas.load();
        maps = new MapStore(this);
        modes = new ModeRegistry(this);
        lobby = new LobbyManager(this);
        holograms = new HologramManager(this);
        podium = new PodiumManager(this);
        kits = new KitManager(this);

        Bukkit.getPluginManager().registerEvents(new LobbyListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MatchListener(this), this);

        WarsCommand cmd = new WarsCommand(this);
        getCommand("wars").setExecutor(cmd);
        getCommand("wars").setTabCompleter(cmd);
        if (getCommand("88start") != null) {
            getCommand("88start").setExecutor(cmd);
            getCommand("88start").setTabCompleter(cmd);
        }
        if (getCommand("createkit") != null) {
            getCommand("createkit").setExecutor(cmd);
            getCommand("createkit").setTabCompleter(cmd);
        }
        // ★ /kit コマンドを登録
        if (getCommand("kit") != null) {
            getCommand("kit").setExecutor(cmd);
            getCommand("kit").setTabCompleter(cmd);
        }
        // WarsPlugin.java の onEnable 内に追加
        if (getCommand("kitsuggest") != null) {
            getCommand("kitsuggest").setExecutor(cmd);
            getCommand("kitsuggest").setTabCompleter(cmd);
        }
        if (getCommand("kitreview") != null) {
            getCommand("kitreview").setExecutor(cmd);
            getCommand("kitreview").setTabCompleter(cmd);
        }

        lobby.start();
        Bukkit.getScheduler().runTask(this, () -> {
            podium.removeStale();
            holograms.removeAll();
            holograms.refresh();
            for (Player p : Bukkit.getOnlinePlayers()) lobby.joinSetup(p);
        });
        getLogger().info("88Wars enabled.");
    }

    @Override
    public void onDisable() {
        clearTrackedTridents();
        lobby.stop();
        if (match != null) match.abort();
        points.save();
        saveData();
        podium.clear();
        holograms.removeAll();
        for (Player p : Bukkit.getOnlinePlayers()) {
            Sidebar sb = sidebars.get(p.getUniqueId());
            if (sb != null) sb.hide(p);
        }
        sidebars.clear();
    }

    public void reloadAll() {
        reloadConfig();
        loadData();
        points.load();
        arenas.load();
        kits.load();
        holograms.refresh();
    }

    // ------------------------------------------------------------ trident tracking
    public void trackTrident(UUID uuid) {
        if (uuid != null) trackedTridents.add(uuid);
    }

    public void clearTrackedTridents() {
        for (UUID id : new HashSet<>(trackedTridents)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Trident trident) {
                trident.remove();
            }
        }
        trackedTridents.clear();
    }

    // ------------------------------------------------------------ accessors
    public ArenaManager arenas() { return arenas; }
    public MapStore maps() { return maps; }
    public PointsManager points() { return points; }
    public ModeRegistry modes() { return modes; }
    public LobbyManager lobby() { return lobby; }
    public HologramManager holograms() { return holograms; }
    public PodiumManager podium() { return podium; }
    public KitManager kits() { return kits; }
    public YamlConfiguration data() { return data; }
    public Match match() { return match; }

    public void setMatch(Match m) {
        this.match = m;
    }

    public void matchClosed(Match m) {
        if (this.match == m) this.match = null;
        lobby.onMatchClosed();
        holograms.refresh();
    }

    public Sidebar sidebar(Player p) {
        return sidebars.computeIfAbsent(p.getUniqueId(), k -> new Sidebar());
    }

    public void removeSidebar(Player p) {
        sidebars.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------ data.yml
    private void loadData() {
        getDataFolder().mkdirs();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveData() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("data.yml の保存に失敗: " + e.getMessage());
        }
    }

    private void setLoc(String path, Location l) {
        data.set(path + ".world", l.getWorld().getName());
        data.set(path + ".pos", Pos.of(l).serialize());
        saveData();
    }

    private Location getLoc(String path) {
        String w = data.getString(path + ".world");
        String p = data.getString(path + ".pos");
        if (w == null || p == null) return null;
        World world = Bukkit.getWorld(w);
        if (world == null) return null;
        try {
            return Pos.parse(p).toLocation(world);
        } catch (Exception e) {
            return null;
        }
    }

    public Location lobbyLocationRaw() { return getLoc("lobby"); }
    public void setLobbyLocation(Location l) { setLoc("lobby", l); }
    public Location hologramLocation() { return getLoc("hologram"); }
    public void setHologramLocation(Location l) { setLoc("hologram", l); }
    public Location podiumLocation(int rank) { return getLoc("podium." + rank); }
    public void setPodiumLocation(int rank, Location l) { setLoc("podium." + rank, l); }
}