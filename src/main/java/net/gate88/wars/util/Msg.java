package net.gate88.wars.util;

import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** メッセージ系ユーティリティ ('&' カラーコード対応) */
public final class Msg {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    public static final String PREFIX = "&8[&e88WARS&8] &f";

    private Msg() {}

    public static Component c(String s) {
        return LEGACY.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    public static void send(CommandSender to, String msg) {
        to.sendMessage(c(PREFIX + msg));
    }

    public static void broadcast(String msg) {
        Bukkit.broadcast(c(PREFIX + msg));
    }

    public static void title(Player p, String title, String sub, int fadeInTicks, int stayTicks, int fadeOutTicks) {
        p.showTitle(Title.title(c(title), c(sub), Title.Times.times(
                Duration.ofMillis(fadeInTicks * 50L),
                Duration.ofMillis(stayTicks * 50L),
                Duration.ofMillis(fadeOutTicks * 50L))));
    }

    public static void actionBar(Player p, String s) {
        p.sendActionBar(c(s));
    }

    public static String time(int seconds) {
        if (seconds < 0) seconds = 0;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }
}
