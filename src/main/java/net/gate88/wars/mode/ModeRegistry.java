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
        register(new SurvivalGamesMode(plugin, "survivalgames", "Survival Games", Material.GRASS_BLOCK,
                List.of("&6300x300の広大なマップ &7/ &e全2ラウンド制",
                        "&7チェストから装備を集めて戦え!",
                        "&710分後に水色発光コア降下 / 12分後チェスト再補充",
                        "&c2段階縮小ボーダー (外側ダメージ)")));

        // ★ Spleef モード登録
        register(new SpleefMode(plugin, "spleef", "Spleef", Material.IRON_SHOVEL,
                List.of("&b5層の円形雪ステージ &7/ &e全3ラウンド制",
                        "&7足元の雪を掘り相手を落下させろ!",
                        "&7PvP不可 / 20掘削でダイヤ効率V / 25掘削で雪玉付与",
                        "&cボーダー縮小 (60x60 -> 15x15)")));

        register(new ComingSoonMode(plugin, "walls", "Walls", Material.BRICKS));
        register(new ComingSoonMode(plugin, "woolwars", "Wool Wars", Material.WHITE_WOOL));
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