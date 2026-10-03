package net.gate88.wars.mode;

import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchTeam;
import org.bukkit.Material;

/** 未実装モードのプレースホルダー (投票メニューに「準備中」で表示) */
public final class ComingSoonMode extends WarsMode {
    public ComingSoonMode(WarsPlugin plugin, String id, String name, Material icon) {
        super(plugin, id, name, icon, List.of("&7準備中です"));
    }

    @Override
    public boolean implemented() {
        return false;
    }

    @Override
    public MatchTeam pickTimeUpWinner(Match m) {
        return null;
    }
}
