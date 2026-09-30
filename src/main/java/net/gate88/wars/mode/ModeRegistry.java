package net.gate88.wars.mode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.gate88.wars.WarsPlugin;
import org.bukkit.Material;

public final class ModeRegistry {
    private final Map<String, WarsMode> modes = new LinkedHashMap<>();

    public ModeRegistry(WarsPlugin plugin) {
        register(new RandomizerMode(plugin, "randomizer_duo", "Randomizer DUO", Material.DISPENSER,
                List.of("&e2人1組 &7/ &c特殊ボーダーあり", "&73分以内に敵チームを全滅させろ!",
                        "&7倒した相手の遺品はチェストに入る", "&7開始5秒後に全員へ同じランダム装備を配布")));
        register(new RandomizerMode(plugin, "randomizer_team", "Randomizer TEAM", Material.CHEST_MINECART,
                List.of("&eチーム戦 &7/ &aボーダーなし", "&73分以内に敵を全滅、または", "&7中央5x5を自分の色の羊毛で埋めれば勝利!",
                        "&7開始5秒後に全員へ同じランダム装備を配布")));
        register(new ComingSoonMode(plugin, "spleef", "Spleef", Material.IRON_SHOVEL));
        register(new ComingSoonMode(plugin, "walls", "Walls", Material.BRICKS));
        register(new ComingSoonMode(plugin, "woolwars", "Wool Wars", Material.WHITE_WOOL));
        register(new ComingSoonMode(plugin, "survivalgames", "Survival Games", Material.CHEST));
        register(new ComingSoonMode(plugin, "skywars", "Skywars", Material.FEATHER));
    }

    public void register(WarsMode m) {
        modes.put(m.id.toLowerCase(), m);
    }

    public WarsMode get(String id) {
        return id == null ? null : modes.get(id.toLowerCase());
    }

    public boolean hasArenaType(String type) {
        for (WarsMode m : modes.values()) if (m.arenaType().equalsIgnoreCase(type)) return true;
        return false;
    }

    public List<WarsMode> all() {
        return new ArrayList<>(modes.values());
    }
}
