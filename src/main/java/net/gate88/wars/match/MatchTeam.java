package net.gate88.wars.match;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.DyeColor;

public final class MatchTeam {
    public final int index;
    public final DyeColor color;
    public final List<MatchPlayer> members = new ArrayList<>();
    public boolean eliminated;

    public MatchTeam(int index, DyeColor color) {
        this.index = index;
        this.color = color;
    }

    public int aliveCount() {
        int n = 0;
        for (MatchPlayer m : members) if (m.alive) n++;
        return n;
    }

    public int points() {
        int n = 0;
        for (MatchPlayer m : members) n += m.points;
        return n;
    }

    public int kills() {
        int n = 0;
        for (MatchPlayer m : members) n += m.kills;
        return n;
    }

    public String label() {
        return members.size() == 1 ? members.get(0).name : "チーム" + (index + 1);
    }
}
