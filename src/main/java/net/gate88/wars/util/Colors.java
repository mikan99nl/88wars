package net.gate88.wars.util;

import java.util.List;
import org.bukkit.DyeColor;
import org.bukkit.Material;

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
}
