package net.gate88.wars.mode;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.match.MatchTeam;
import net.gate88.wars.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/** ゲームモードの基底クラス。新モードはこれを継承して ModeRegistry に登録する。 */
public abstract class WarsMode {
    protected final WarsPlugin plugin;
    public final String id;
    public final String displayName;
    public final Material icon;
    public final List<String> description;

    protected WarsMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> description) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.icon = icon;
        this.description = description;
    }

    /** config.yml の modes.<id> セクション */
    protected ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modes." + id);
        return s != null ? s : plugin.getConfig().createSection("modes." + id);
    }

    public boolean implemented() {
        return true;
    }

    public boolean enabled() {
        return implemented() && cfg().getBoolean("enabled", true);
    }

    public int durationSeconds() {
        return cfg().getInt("duration-seconds", 180);
    }

    public int graceSeconds() {
        return cfg().getInt("grace-seconds", 5);
    }

    public int teamSize() {
        return Math.max(1, cfg().getInt("team-size", 1));
    }

    /** 0 = team-size で組分け / 1以上 = 指定チーム数へ均等に振り分け */
    public int teamCount() {
        return Math.max(0, cfg().getInt("team-count", 0));
    }

    public boolean teamMode() {
        return teamSize() > 1 || teamCount() > 0;
    }

    /** このモードで必要な最低人数 */
    public int minPlayers() {
        return Math.max(1, cfg().getInt("min-players", 1));
    }

    /** 使用するアリーナの種類 (同じ種類のアリーナを複数モードで共有できる) */
    public String arenaType() {
        return id;
    }

    public int blockDecaySeconds() {
        return cfg().getInt("block-decay-seconds", 12);
    }

    public BorderSpec border() {
        ConfigurationSection b = cfg().getConfigurationSection("border");
        if (b == null || !b.getBoolean("enabled", false)) return BorderSpec.NONE;
        return new BorderSpec(true, b.getDouble("start-radius", 28), b.getDouble("end-radius", 9),
                b.getInt("shrink-start-seconds", 30), b.getInt("shrink-end-seconds", 150),
                b.getDouble("damage-per-second", 1.0));
    }

    // ---- フック ----
    public void onStart(Match m) {}

    /** 猶予(grace)終了時: 保存されているキットからランダムに1つ選択して配布 */
    public void onGraceEnd(Match m) {
        // 保存されている全キットの名前リストを取得
        List<String> kitNames = plugin.kits().getKitNames();

        if (kitNames.isEmpty()) {
            m.broadcastToMatch("&c[警告] 保存されているキットがありません！");
            return;
        }

        // 保存されたキットの中からランダムに1つ選出
        int randomIndex = ThreadLocalRandom.current().nextInt(kitNames.size());
        String selectedKit = kitNames.get(randomIndex);

        // 試合参加者全員に選択されたキットを通知
        m.broadcastToMatch("&e今回のランダムキット: &6&l" + selectedKit);

        // 全プレイヤーへ配布 & 画面にタイトル表示
        for (MatchPlayer mp : m.allPlayers()) {
            Player p = mp.player();
            if (p != null && mp.alive) {
                plugin.kits().applyKit(p, selectedKit);

                // 配布時の演出（タイトル表示 & 効果音）
                Msg.title(p, "&6&l" + selectedKit, "&7キットが配布されました！", 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }
        }
    }

    public void onSecond(Match m) {}

    public boolean canPlace(Match m, MatchPlayer p, Block b) {
        return true;
    }

    public boolean decayExempt(Match m, Block b) {
        return false;
    }

    public void onBlockPlaced(Match m, MatchPlayer p, Block b) {}

    public void onBlockBroken(Match m, MatchPlayer p, Block b) {}

    /** 脱落直前(インベントリを消す前)に呼ばれる。loot = 所持品のコピー */
    public void onDeathLoot(Match m, MatchPlayer victim, org.bukkit.Location loc, List<org.bukkit.inventory.ItemStack> loot) {}

    public void onEliminated(Match m, MatchPlayer victim) {}

    /** 時間切れの勝者 */
    public abstract MatchTeam pickTimeUpWinner(Match m);

    /** 同順位の処理などに使う、生存チームの「優勢度」 (大きいほど上位) */
    public int standing(Match m, MatchTeam t) {
        return 0;
    }

    /** サイドバーのモード固有行 */
    public List<String> extraSidebar(Match m, MatchPlayer viewer) {
        return List.of();
    }

    public void onEnd(Match m) {}
}