package net.gate88.wars.util;

import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** プレイヤーごとのサイドバー (チームprefix方式でちらつきなし) */
public final class Sidebar {
    private static final int MAX = 15;
    private final Scoreboard board;
    private final Objective obj;
    private final List<String> last = new ArrayList<>();
    private String lastTitle = "";

    public Sidebar() {
        board = Bukkit.getScoreboardManager().getNewScoreboard();
        obj = board.registerNewObjective("wars", Criteria.DUMMY, Msg.c("&e&l88WARS"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        try {
            obj.numberFormat(NumberFormat.blank());
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < MAX; i++) {
            Team t = board.registerNewTeam("l" + i);
            t.addEntry(entry(i));
        }
    }

    private static String entry(int i) {
        return "§" + Integer.toHexString(i) + "§r";
    }

    public void render(Player p, String title, List<String> lines) {
        if (!title.equals(lastTitle)) {
            obj.displayName(Msg.c(title));
            lastTitle = title;
        }
        int n = Math.min(lines.size(), MAX);
        for (int i = 0; i < n; i++) {
            String line = lines.get(i);
            if (i >= last.size() || !last.get(i).equals(line)) {
                board.getTeam("l" + i).prefix(Msg.c(line));
            }
            obj.getScore(entry(i)).setScore(n - i);
        }
        for (int i = n; i < last.size(); i++) {
            board.resetScores(entry(i));
        }
        last.clear();
        last.addAll(lines.subList(0, n));
        if (p.getScoreboard() != board) {
            p.setScoreboard(board);
        }
    }

    public void hide(Player p) {
        if (p.getScoreboard() == board) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }
}
