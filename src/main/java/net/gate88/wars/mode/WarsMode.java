package net.gate88.wars.mode;

import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.match.MatchTeam;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

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

    protected ConfigurationSection cfg() {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("modes." + id);
        return s != null ? s : plugin.getConfig().createSection("modes." + id);
    }

    public boolean implemented() { return true; }
    public boolean enabled() { return implemented() && cfg().getBoolean("enabled", true); }
    public int durationSeconds() { return cfg().getInt("duration-seconds", 180); }
    public int graceSeconds() { return cfg().getInt("grace-seconds", 5); }

    // ★ 大元設定: 開始から特定ブロック破壊までの秒数 (デフォルト 0秒 = 即座に破壊)
    public int breakDelaySeconds() { return cfg().getInt("break-delay-seconds", 0); }

    public int teamSize() { return Math.max(1, cfg().getInt("team-size", 1)); }
    public int teamCount() { return Math.max(0, cfg().getInt("team-count", 0)); }
    public boolean teamMode() { return teamSize() > 1 || teamCount() > 0; }
    public int minPlayers() { return Math.max(1, cfg().getInt("min-players", 1)); }
    public String arenaType() { return id; }
    public int blockDecaySeconds() { return cfg().getInt("block-decay-seconds", 12); }

    public BorderSpec border() {
        ConfigurationSection b = cfg().getConfigurationSection("border");
        if (b == null || !b.getBoolean("enabled", false)) return BorderSpec.NONE;
        return new BorderSpec(true, b.getDouble("start-radius", 28), b.getDouble("end-radius", 9),
                b.getInt("shrink-start-seconds", 30), b.getInt("shrink-end-seconds", 150),
                b.getDouble("damage-per-second", 1.0));
    }

    public void onStart(Match m) {}
    public void onGraceEnd(Match m) {}
    public void onSecond(Match m) {}
    public boolean canPlace(Match m, MatchPlayer p, Block b) { return true; }
    public boolean decayExempt(Match m, Block b) { return false; }
    public void onBlockPlaced(Match m, MatchPlayer p, Block b) {}
    public void onBlockBroken(Match m, MatchPlayer p, Block b) {}
    public void onDeathLoot(Match m, MatchPlayer victim, org.bukkit.Location loc, List<org.bukkit.inventory.ItemStack> loot) {}
    public void onEliminated(Match m, MatchPlayer victim) {}
    public abstract MatchTeam pickTimeUpWinner(Match m);
    public int standing(Match m, MatchTeam t) { return 0; }
    public List<String> extraSidebar(Match m, MatchPlayer viewer) { return List.of(); }
    public void onEnd(Match m) {}
}