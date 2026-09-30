package net.gate88.wars.command;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.arena.ArenaBuilder;
import net.gate88.wars.gui.AdminGui;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class WarsCommand implements CommandExecutor, TabCompleter {
    private final WarsPlugin plugin;

    public WarsCommand(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final List<String> ADMIN_SUBS = List.of("admin", "start", "stop", "setlobby", "sethologram",
            "setpodium", "arena", "setpoints", "addpoints", "reload");
    private static final List<String> PUBLIC_SUBS = List.of("help", "vote", "top", "points");

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            help(s);
            return true;
        }
        String sub = a[0].toLowerCase();
        if (ADMIN_SUBS.contains(sub) && !s.hasPermission("wars.admin")) {
            Msg.send(s, "&c権限がありません");
            return true;
        }
        switch (sub) {
            case "vote" -> {
                if (s instanceof Player p) VoteMenu.open(plugin, p);
            }
            case "top" -> top(s);
            case "points" -> points(s, a);
            case "admin" -> {
                if (s instanceof Player p) AdminGui.open(plugin, p, AdminGui.Page.MAIN);
                else Msg.send(s, "ゲーム内で実行してください");
            }
            case "start" -> {
                WarsMode forced = null;
                if (a.length >= 2) {
                    forced = plugin.modes().get(a[1]);
                    if (forced == null || !plugin.lobby().isPlayable(forced)) {
                        Msg.send(s, "&cそのモードはプレイできません");
                        return true;
                    }
                }
                if (!plugin.lobby().forceStart(forced)) Msg.send(s, "&c開始できません (試合中/対象プレイヤーなし)");
                else Msg.send(s, "&a開始します");
            }
            case "stop" -> {
                if (plugin.match() == null) Msg.send(s, "&7進行中の試合はありません");
                else {
                    plugin.match().abort();
                    Msg.send(s, "&e試合を中止しました");
                }
            }
            case "setlobby" -> withPlayer(s, p -> {
                plugin.setLobbyLocation(p.getLocation());
                Msg.send(p, "&aロビー地点を設定しました");
                Sfx.success(p);
            });
            case "sethologram" -> withPlayer(s, p -> {
                plugin.setHologramLocation(p.getLocation().add(0, 2.5, 0));
                plugin.holograms().refresh();
                Msg.send(p, "&aランキングホログラムを設定しました");
                Sfx.success(p);
            });
            case "setpodium" -> withPlayer(s, p -> {
                int r;
                try {
                    r = Integer.parseInt(a[1]);
                } catch (Exception ex) {
                    r = 0;
                }
                if (r < 1 || r > 3) {
                    Msg.send(p, "&c/wars setpodium <1|2|3>");
                    return;
                }
                plugin.setPodiumLocation(r, p.getLocation());
                Msg.send(p, "&a表彰台 " + r + "位 を設定しました");
                Sfx.success(p);
            });
            case "arena" -> arena(s, a);
            case "setpoints", "addpoints" -> {
                if (a.length < 3) {
                    Msg.send(s, "&c/wars " + sub + " <player> <number>");
                    return true;
                }
                Player t = Bukkit.getPlayerExact(a[1]);
                long n;
                try {
                    n = Long.parseLong(a[2]);
                } catch (NumberFormatException ex) {
                    Msg.send(s, "&c数値を指定してください");
                    return true;
                }
                PointsManager.Entry e = t != null ? plugin.points().entry(t.getUniqueId(), t.getName()) : plugin.points().findByName(a[1]);
                if (e == null) {
                    Msg.send(s, "&cプレイヤーが見つかりません");
                    return true;
                }
                e.points = sub.equals("setpoints") ? n : e.points + n;
                plugin.points().save();
                plugin.holograms().refresh();
                Msg.send(s, "&a" + e.name + " の累計ポイント: &e" + e.points);
            }
            case "reload" -> {
                plugin.reloadAll();
                Msg.send(s, "&a再読込しました");
            }
            default -> help(s);
        }
        return true;
    }

    private void withPlayer(CommandSender s, java.util.function.Consumer<Player> c) {
        if (s instanceof Player p) c.accept(p);
        else Msg.send(s, "ゲーム内で実行してください");
    }

    private void help(CommandSender s) {
        Msg.send(s, "&e88WARS コマンド");
        s.sendMessage(Msg.c("&7/wars vote &f- 投票メニュー"));
        s.sendMessage(Msg.c("&7/wars top &f- 累計ポイントTOP10"));
        s.sendMessage(Msg.c("&7/wars points [name] &f- 累計ポイント"));
        if (s.hasPermission("wars.admin")) {
            s.sendMessage(Msg.c("&6[管理] &7/wars admin &f- 設定GUI"));
            s.sendMessage(Msg.c("&6[管理] &7/wars start [mode] &f- 強制開始 / /wars stop"));
            s.sendMessage(Msg.c("&6[管理] &7/wars setlobby | sethologram | setpodium <1-3>"));
            s.sendMessage(Msg.c("&6[管理] &7/wars arena create|build|addspawn|clearspawns|delete|tp|list"));
            s.sendMessage(Msg.c("&6[管理] &7/wars setpoints|addpoints <player> <n> | reload"));
        }
    }

    private void top(CommandSender s) {
        Msg.send(s, "&6累計ポイント TOP10");
        int i = 1;
        for (PointsManager.Entry e : plugin.points().top(10)) {
            s.sendMessage(Msg.c("&e#" + i++ + " &f" + e.name + " &8- &e" + e.points + "pt &7(優勝" + e.wins + " / キル" + e.kills + ")"));
        }
    }

    private void points(CommandSender s, String[] a) {
        PointsManager.Entry e = null;
        if (a.length >= 2) e = plugin.points().findByName(a[1]);
        else if (s instanceof Player p) e = plugin.points().find(p.getUniqueId());
        if (e == null) {
            Msg.send(s, "&7記録がありません");
            return;
        }
        Msg.send(s, "&f" + e.name + ": &e" + e.points + "pt &7(順位 " + plugin.points().rankOf(e.uuid) + "位 / 優勝" + e.wins
                + " / キル" + e.kills + " / 試合" + e.played + ")");
    }

    private void arena(CommandSender s, String[] a) {
        if (a.length < 2) {
            Msg.send(s, "&c/wars arena create <id> [mode] | build <id> | addspawn <id> | clearspawns <id> | delete <id> | tp <id> | list");
            return;
        }
        String op = a[1].toLowerCase();
        if (op.equals("list")) {
            for (Arena ar : plugin.arenas().all()) {
                s.sendMessage(Msg.c((ar.isReady() ? "&a" : "&c") + ar.id + " &7mode=" + ar.modeId + " world=" + ar.worldName
                        + " center=" + ar.cx + "," + ar.cy + "," + ar.cz + " spawns=" + ar.spawns.size()));
            }
            return;
        }
        if (a.length < 3) {
            Msg.send(s, "&cid を指定してください");
            return;
        }
        String id = a[2];
        Player p = s instanceof Player pl ? pl : null;
        switch (op) {
            case "create" -> {
                if (p == null) return;
                String mode = a.length >= 4 ? a[3] : "randomizer";
                if (!plugin.modes().hasArenaType(mode)) {
                    Msg.send(s, "&c不明なアリーナ種類です (randomizer)");
                    return;
                }
                Arena ar = plugin.arenas().create(id, mode.toLowerCase());
                Location l = p.getLocation();
                ar.worldName = l.getWorld().getName();
                ar.cx = l.getBlockX();
                ar.cy = l.getBlockY();
                ar.cz = l.getBlockZ();
                plugin.arenas().save();
                Msg.send(s, "&aアリーナ作成: 中心=" + ar.cx + "," + ar.cy + "," + ar.cz + " &7(/wars arena build " + id + " で自動生成)");
            }
            case "build" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) {
                    Msg.send(s, "&cアリーナが見つかりません");
                    return;
                }
                ArenaBuilder.build(ar);
                plugin.arenas().save();
                Msg.send(s, "&aアリーナを生成しました (スポーン" + ar.spawns.size() + "個)");
            }
            case "addspawn" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null) return;
                ar.spawns.add(net.gate88.wars.util.Pos.of(p.getLocation()));
                plugin.arenas().save();
                Msg.send(s, "&aスポーン追加 (" + ar.spawns.size() + ")");
            }
            case "clearspawns" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) return;
                ar.spawns.clear();
                plugin.arenas().save();
                Msg.send(s, "&eスポーンを全削除しました");
            }
            case "delete" -> {
                if (plugin.arenas().delete(id)) {
                    plugin.arenas().save();
                    Msg.send(s, "&e削除しました");
                } else Msg.send(s, "&cアリーナが見つかりません");
            }
            case "tp" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null || ar.center() == null) return;
                p.teleport(ar.center().add(0, 1, 0));
            }
            default -> Msg.send(s, "&c不明なサブコマンドです");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(PUBLIC_SUBS);
            if (s.hasPermission("wars.admin")) out.addAll(ADMIN_SUBS);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("arena")) {
            out.addAll(List.of("create", "build", "addspawn", "clearspawns", "delete", "tp", "list"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("start")) {
            for (WarsMode m : plugin.modes().all()) out.add(m.id);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("setpodium")) {
            out.addAll(List.of("1", "2", "3"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("arena")) {
            for (Arena ar : plugin.arenas().all()) out.add(ar.id);
        } else if (a.length == 4 && a[0].equalsIgnoreCase("arena") && a[1].equalsIgnoreCase("create")) {
            out.add("randomizer");
        }
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
