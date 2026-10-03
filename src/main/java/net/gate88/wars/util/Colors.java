package net.gate88.wars.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/** チームカラー (羊毛16色) */
public final class Colors {
    private Colors() {}

    public static final List<DyeColor> ALL = List.of(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME, DyeColor.YELLOW,
            DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.CYAN, DyeColor.PINK, DyeColor.MAGENTA, DyeColor.LIGHT_BLUE,
            DyeColor.GREEN, DyeColor.BROWN, DyeColor.WHITE, DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BLACK);

    public static Material wool(DyeColor c) {
        return Material.valueOf(c.name() + "_WOOL");
    }

    public static String jp(DyeColor c) {
        return switch (c) {
            case RED -> "赤";
            case BLUE -> "青";
            case LIME -> "黄緑";
            case YELLOW -> "黄";
            case ORANGE -> "橙";
            case PURPLE -> "紫";
            case CYAN -> "水";
            case PINK -> "桃";
            case MAGENTA -> "赤紫";
            case LIGHT_BLUE -> "空";
            case GREEN -> "緑";
            case BROWN -> "茶";
            case WHITE -> "白";
            case LIGHT_GRAY -> "薄灰";
            case GRAY -> "灰";
            case BLACK -> "黒";
        };
    }

    /** &カラーコード */
    public static String code(DyeColor c) {
        return switch (c) {
            case RED -> "&c";
            case BLUE -> "&9";
            case LIME -> "&a";
            case YELLOW -> "&e";
            case ORANGE -> "&6";
            case PURPLE -> "&5";
            case CYAN -> "&3";
            case PINK -> "&d";
            case MAGENTA -> "&d";
            case LIGHT_BLUE -> "&b";
            case GREEN -> "&2";
            case BROWN -> "&6";
            case WHITE -> "&f";
            case LIGHT_GRAY -> "&7";
            case GRAY -> "&8";
            case BLACK -> "&0";
        };
    }

    /** Paper 1.21 Adventure 用の NamedTextColor */
    public static NamedTextColor textColor(DyeColor c) {
        return switch (c) {
            case RED -> NamedTextColor.RED;
            case BLUE -> NamedTextColor.BLUE;
            case LIME -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW;
            case ORANGE -> NamedTextColor.GOLD;
            case PURPLE -> NamedTextColor.DARK_PURPLE;
            case CYAN -> NamedTextColor.DARK_AQUA;
            case PINK, MAGENTA -> NamedTextColor.LIGHT_PURPLE;
            case LIGHT_BLUE -> NamedTextColor.AQUA;
            case GREEN -> NamedTextColor.DARK_GREEN;
            case BROWN -> NamedTextColor.GOLD;
            case WHITE -> NamedTextColor.WHITE;
            case LIGHT_GRAY -> NamedTextColor.GRAY;
            case GRAY -> NamedTextColor.DARK_GRAY;
            case BLACK -> NamedTextColor.BLACK;
        };
    }

    public static org.bukkit.Color color(DyeColor c) {
        return switch (c) {
            case RED -> org.bukkit.Color.fromRGB(255, 85, 85);
            case BLUE -> org.bukkit.Color.fromRGB(85, 85, 255);
            case LIME -> org.bukkit.Color.fromRGB(85, 255, 85);
            case YELLOW -> org.bukkit.Color.fromRGB(255, 255, 85);
            case ORANGE -> org.bukkit.Color.fromRGB(255, 170, 0);
            case PURPLE -> org.bukkit.Color.fromRGB(170, 0, 170);
            case CYAN -> org.bukkit.Color.fromRGB(0, 170, 170);
            case PINK -> org.bukkit.Color.fromRGB(255, 85, 255);
            case MAGENTA -> org.bukkit.Color.fromRGB(255, 85, 255);
            case LIGHT_BLUE -> org.bukkit.Color.fromRGB(85, 255, 255);
            case GREEN -> org.bukkit.Color.fromRGB(0, 170, 0);
            case BROWN -> org.bukkit.Color.fromRGB(170, 85, 0);
            case WHITE -> org.bukkit.Color.fromRGB(255, 255, 255);
            case LIGHT_GRAY -> org.bukkit.Color.fromRGB(170, 170, 170);
            case GRAY -> org.bukkit.Color.fromRGB(85, 85, 85);
            case BLACK -> org.bukkit.Color.fromRGB(0, 0, 0);
        };
    }

    /**
     * タブリスト表示と頭上ネームタグに色を適用する共通メソッド
     */
    public static void updateTabList(Player player, String prefixStr, NamedTextColor color) {
        // 1. タブリストの表示名を設定
        Component tabName = Component.text()
                .append(prefixStr != null && !prefixStr.isEmpty() ? Msg.c(prefixStr) : Component.empty())
                .append(Component.text(player.getName(), color))
                .build();
        player.playerListName(tabName);

        // 2. Scoreboard Team を利用してカラーを固定（頭上のネームタグも同期）
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "wars_" + color.toString().substring(0, Math.min(10, color.toString().length()));
        Team team = sb.getTeam(teamName);
        if (team == null) {
            team = sb.registerNewTeam(teamName);
        }
        team.color(color);
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
    }
}