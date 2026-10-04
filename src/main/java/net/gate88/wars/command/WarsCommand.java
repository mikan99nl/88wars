package net.gate88.wars.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.arena.ArenaBuilder;
import net.gate88.wars.arena.MapStore;
import net.gate88.wars.gui.AdminGui;
import net.gate88.wars.gui.KitGui;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class WarsCommand implements CommandExecutor, TabCompleter {
    private final WarsPlugin plugin;
    private final Map<UUID, Map<Integer, Location>> tempAreaPos = new HashMap<>();

    public WarsCommand(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final List<String> ADMIN_SUBS = List.of("admin", "kit", "start", "stop", "setlobby", "sethologram",
            "setpodium", "arena", "map", "setpoints", "addpoints", "reload");
    private static final List<String> PUBLIC_SUBS = List.of("help", "vote", "top", "points");

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String cmdName = cmd.getName().toLowerCase();

        // /kit コマンド
        if (cmdName.equals("kit")) {
            if (!s.hasPermission("wars.admin") && !s.isOp()) {
                Msg.send(s, "&c権限がありません");
                return true;
            }
            if (!(s instanceof Player p)) {
                Msg.send(s, "ゲーム内で実行してください");
                return true;
            }
            handleKitCommand(p, a);
            return true;
        }

        if (cmdName.equals("88start")) {
            if (!s.hasPermission("wars.admin")) {
                Msg.send(s, "&c権限がありません");
                return true;
            }
            WarsMode forced = null;
            if (a.length >= 1) {
                forced = plugin.modes().get(a[0]);
                if (forced == null || !plugin.lobby().isPlayable(forced)) {
                    Msg.send(s, "&cそのモードはプレイできません");
                    return true;
                }
            }
            if (!plugin.lobby().forceStart(forced)) Msg.send(s, "&c開始できません (試合中/対象プレイヤーなし)");
            else Msg.send(s, "&a開始します");
            return true;
        }

        // ★ /createkit コマンド（追加権限保持者も実行可能・上書きは管理者のみ）
        if (cmdName.equals("createkit")) {
            if (!(s instanceof Player p)) {
                Msg.send(s, "ゲーム内で実行してください");
                return true;
            }
            if (!plugin.kits().canCreateKit(p)) {
                Msg.send(p, "&cキットを追加する権限がありません");
                return true;
            }
            if (a.length < 1) {
                Msg.send(p, "&c使用方法: /createkit <kitName>");
                return true;
            }
            String kitName = a[0];

            // 管理者ではなく「追加権限のみ」のプレイヤーは、既存キットの上書きを禁止
            boolean isAdmin = p.isOp() || p.hasPermission("wars.admin");
            if (!isAdmin && plugin.kits().exists(kitName)) {
                Msg.send(p, "&cその名前のキットは既に存在します（新規追加のみ可能です）");
                Sfx.deny(p);
                return true;
            }

            plugin.kits().createKit(p, kitName);
            Msg.send(p, "&aキット &e" + kitName + " &aを追加・保存しました (作成者: " + p.getName() + ")");
            Sfx.success(p);
            return true;
        }

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
            case "kit" -> {
                if (!(s instanceof Player p)) {
                    Msg.send(s, "ゲーム内で実行してください");
                    return true;
                }
                String[] kitArgs = a.length > 1 ? Arrays.copyOfRange(a, 1, a.length) : new String[0];
                handleKitCommand(p, kitArgs);
            }
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
            case "setlobby" -> withPlayer(s, player -> {
                plugin.setLobbyLocation(player.getLocation());
                Msg.send(player, "&aロビー地点を設定しました");
                Sfx.success(player);
            });
            case "sethologram" -> withPlayer(s, player -> {
                plugin.setHologramLocation(player.getLocation().add(0, 2.5, 0));
                plugin.holograms().refresh();
                Msg.send(player, "&aランキングホログラムを設定しました");
                Sfx.success(player);
            });
            case "setpodium" -> withPlayer(s, player -> {
                int r;
                try {
                    r = Integer.parseInt(a[1]);
                } catch (Exception ex) {
                    r = 0;
                }
                if (r < 1 || r > 3) {
                    Msg.send(player, "&c/wars setpodium <1|2|3>");
                    return;
                }
                plugin.setPodiumLocation(r, player.getLocation());
                Msg.send(player, "&a表彰台 " + r + "位 を設定しました");
                Sfx.success(player);
            });
            case "arena" -> arena(s, a);
            case "map" -> map(s, a);
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

    /** /kit および /wars kit 共通の処理 */
    private void handleKitCommand(Player p, String[] a) {
        if (a.length == 0) {
            KitGui.openList(plugin, p);
            return;
        }

        String sub = a[0].toLowerCase();

        // ★ 権限管理: /kit perm <add|remove|list> [player] [create|creative]
        if (sub.equals("perm")) {
            if (a.length < 2) {
                Msg.send(p, "&c使用方法: /kit perm <add|remove|list> [player] [create|creative]");
                return;
            }
            String action = a[1].toLowerCase();
            if (action.equals("list")) {
                List<String> creates = plugin.kits().getCreatePermPlayerNames();
                List<String> creatives = plugin.kits().getCreativePermPlayerNames();
                Msg.send(p, "&e[Kit権限一覧]");
                p.sendMessage(Msg.c(" &aKit追加権限 (create): &f" + (creates.isEmpty() ? "なし" : String.join(", ", creates))));
                p.sendMessage(Msg.c(" &bエリア自動Creative権限 (creative): &f" + (creatives.isEmpty() ? "なし" : String.join(", ", creatives))));
                return;
            }
            if (a.length < 4) {
                Msg.send(p, "&c使用方法: /kit perm <add|remove> <player> <create|creative>");
                return;
            }
            String targetName = a[2];
            String type = a[3].toLowerCase();
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);
            UUID uuid = target.getUniqueId();

            boolean allow = action.equals("add") || action.equals("grant");
            if (type.equals("create")) {
                plugin.kits().setCreatePerm(uuid, allow);
                Msg.send(p, "&e" + targetName + " &fの &a[Kit追加のみ権限] &fを " + (allow ? "&a付与" : "&c剥奪") + " &fしました");
                if (onlineTarget != null) {
                    Msg.send(onlineTarget, allow ? "&aKit追加権限 (/createkit) が付与されました！" : "&cKit追加権限が解除されました");
                }
                Sfx.success(p);
            } else if (type.equals("creative")) {
                plugin.kits().setCreativePerm(uuid, allow);
                Msg.send(p, "&e" + targetName + " &fの &b[Kitエリア自動クリエイティブ権限] &fを " + (allow ? "&a付与" : "&c剥奪") + " &fしました");
                if (onlineTarget != null) {
                    Msg.send(onlineTarget, allow ? "&bKit制作エリアでの自動クリエイティブ権限が付与されました！" : "&cKit制作エリアでの自動クリエイティブ権限が解除されました");
                }
                Sfx.success(p);
            } else {
                Msg.send(p, "&c権限タイプは create または creative を指定してください");
            }
            return;
        }

        // エリア設定: /kit area <pos1|pos2|clear|info>
        if (sub.equals("area")) {
            if (a.length < 2) {
                Msg.send(p, "&c/kit area <pos1|pos2|clear|info>");
                return;
            }
            String op = a[1].toLowerCase();
            Map<Integer, Location> map = tempAreaPos.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());

            switch (op) {
                case "pos1", "1" -> {
                    map.put(1, p.getLocation().getBlock().getLocation());
                    Msg.send(p, "&aKit制作エリア 角① を設定しました: &e" + formatLoc(p.getLocation()));
                    checkAndApplyArea(p, map);
                }
                case "pos2", "2" -> {
                    map.put(2, p.getLocation().getBlock().getLocation());
                    Msg.send(p, "&aKit制作エリア 角② を設定しました: &e" + formatLoc(p.getLocation()));
                    checkAndApplyArea(p, map);
                }
                case "clear" -> {
                    plugin.kits().clearArea();
                    map.clear();
                    Msg.send(p, "&eKit制作エリアを解除しました");
                    Sfx.success(p);
                }
                case "info" -> {
                    if (!plugin.kits().hasArea()) {
                        Msg.send(p, "&7Kit制作エリアは現在設定されていません");
                    } else {
                        Msg.send(p, "&aKit制作エリアは有効です");
                    }
                }
                default -> Msg.send(p, "&c/kit area <pos1|pos2|clear|info>");
            }
            return;
        }

        KitGui.openList(plugin, p);
    }

    private void checkAndApplyArea(Player p, Map<Integer, Location> map) {
        if (map.containsKey(1) && map.containsKey(2)) {
            Location p1 = map.get(1);
            Location p2 = map.get(2);
            if (!p1.getWorld().equals(p2.getWorld())) {
                Msg.send(p, "&c2つの座標は同じワールドで設定してください");
                return;
            }
            plugin.kits().setArea(p1, p2);
            Msg.send(p, "&6&l[完了] &aKit制作エリアを設定・保存しました！");
            Msg.send(p, "&7このエリア内では投票アイテムが消え、自由なKit作成が可能になります。");
            Sfx.success(p);
        } else {
            Msg.send(p, "&7もう一方の角に立って &f/kit area " + (map.containsKey(1) ? "pos2" : "pos1") + " &7を実行してください");
        }
    }

    private String formatLoc(Location l) {
        return l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ();
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
            s.sendMessage(Msg.c("&6[管理] &7/kit &f- Kit一覧・管理GUI"));
            s.sendMessage(Msg.c("&6[管理] &7/kit area pos1|pos2|clear &f- Kit制作エリアの設定"));
            s.sendMessage(Msg.c("&6[管理] &7/kit perm add|remove <player> create|creative &f- Kit権限付与"));
            s.sendMessage(Msg.c("&6[管理] &7/kit perm list &f- Kit権限一覧"));
            s.sendMessage(Msg.c("&6[管理] &7/wars admin &f- 設定GUI"));
            s.sendMessage(Msg.c("&6[管理] &7/wars start [mode] &f- 強制開始 / /wars stop"));
            s.sendMessage(Msg.c("&6[管理] &7/wars setlobby | sethologram | setpodium <1-3>"));
            s.sendMessage(Msg.c("&6[管理] &7/wars arena create|build|paste|addspawn|clearspawns|enable|disable|delete|tp|list"));
            s.sendMessage(Msg.c("&6[管理] &7/wars map save <name> | list | delete <name>"));
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
            Msg.send(s, "&c/wars arena create <id> [mode] | build <id> | paste <id> <map> [mode] | addspawn <id> | clearspawns <id>"
                    + " | enable <id> | disable <id> | delete <id> | tp <id> | list");
            return;
        }
        String op = a[1].toLowerCase();
        if (op.equals("list")) {
            for (Arena ar : plugin.arenas().all()) {
                s.sendMessage(Msg.c((ar.isReady() ? "&a" : "&c") + ar.id + (ar.enabled ? "" : " &6[編集中]") + " &7mode=" + ar.modeId
                        + " world=" + ar.worldName + " center=" + ar.cx + "," + ar.cy + "," + ar.cz + " spawns=" + ar.spawns.size()
                        + (ar.map != null ? " map=" + ar.map : "")));
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
                ar.enabled = false;
                plugin.arenas().save();
                Msg.send(s, "&aアリーナ作成: 中心=" + ar.cx + "," + ar.cy + "," + ar.cz + " &7(/wars arena build " + id + " で自動生成)");
                sendEditing(s, ar);
            }
            case "build" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) {
                    Msg.send(s, "&cアリーナが見つかりません");
                    return;
                }
                ArenaBuilder.build(ar);
                ar.map = null;
                ar.enabled = false;
                plugin.arenas().save();
                Msg.send(s, "&aアリーナを生成しました (スポーン" + ar.spawns.size() + "個)");
                sendEditing(s, ar);
            }
            case "paste" -> {
                if (p == null) return;
                if (!MapStore.available()) {
                    Msg.send(s, "&cWorldEdit が必要です");
                    return;
                }
                if (a.length < 4) {
                    Msg.send(s, "&c/wars arena paste <id> <map> [mode] &7(立っている位置がマップの中心になります)");
                    return;
                }
                String map = a[3];
                if (!plugin.maps().exists(map)) {
                    Msg.send(s, "&cマップが見つかりません: " + map + " &7(/wars map list)");
                    return;
                }
                Arena ar = plugin.arenas().get(id);
                String mode = a.length >= 5 ? a[4].toLowerCase() : ar != null ? ar.modeId : "randomizer";
                if (!plugin.modes().hasArenaType(mode)) {
                    Msg.send(s, "&c不明なアリーナ種類です (randomizer)");
                    return;
                }
                if (plugin.match() != null && plugin.match().arena() == ar) {
                    Msg.send(s, "&cこのアリーナは試合中です");
                    return;
                }
                if (ar == null) ar = plugin.arenas().create(id, mode);
                ar.modeId = mode;
                Location l = p.getLocation();
                try {
                    plugin.maps().paste(p, map, l);
                } catch (Exception ex) {
                    Msg.send(s, "&c貼り付けに失敗しました: " + ex.getMessage());
                    return;
                }
                ar.worldName = l.getWorld().getName();
                ar.cx = l.getBlockX();
                ar.cy = l.getBlockY();
                ar.cz = l.getBlockZ();
                ar.map = map.toLowerCase();
                ar.enabled = false;
                ar.spawns.clear();
                ar.spawns.addAll(plugin.maps().spawnsFor(ar.map, ar));
                plugin.arenas().save();
                Msg.send(s, "&aマップ &e" + map + " &aを貼り付けました (スポーン" + ar.spawns.size() + "個) &7//undo で取り消し可");
                if (ar.spawns.isEmpty()) Msg.send(s, "&7スポーン地点に立って &f/wars arena addspawn " + ar.id + " &7を実行してください");
                sendEditing(s, ar);
            }
            case "enable" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) {
                    Msg.send(s, "&cアリーナが見つかりません");
                    return;
                }
                if (ar.spawns.isEmpty()) {
                    Msg.send(s, "&cスポーンがありません (/wars arena addspawn " + ar.id + ")");
                    return;
                }
                ar.enabled = true;
                plugin.arenas().save();
                Msg.send(s, "&aアリーナ &e" + ar.id + " &aを有効にしました (試合で使われます)");
            }
            case "disable" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) {
                    Msg.send(s, "&cアリーナが見つかりません");
                    return;
                }
                ar.enabled = false;
                plugin.arenas().save();
                Msg.send(s, "&eアリーナ &f" + ar.id + " &eを編集中にしました (試合で使われません)");
            }
            case "addspawn" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null) return;
                ar.spawns.add(net.gate88.wars.util.Pos.of(p.getLocation()));
                plugin.arenas().save();
                plugin.maps().storeSpawns(ar);
                Msg.send(s, "&aスポーン追加 (" + ar.spawns.size() + ")");
            }
            case "clearspawns" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) return;
                ar.spawns.clear();
                plugin.arenas().save();
                plugin.maps().storeSpawns(ar);
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

    private void sendEditing(CommandSender s, Arena ar) {
        Msg.send(s, "&6このアリーナは編集中です (自動で試合は始まりません)。準備ができたら &f/wars arena enable " + ar.id);
    }

    private void map(CommandSender s, String[] a) {
        if (!MapStore.available()) {
            Msg.send(s, "&cWorldEdit が必要です");
            return;
        }
        String op = a.length >= 2 ? a[1].toLowerCase() : "";
        switch (op) {
            case "list" -> {
                List<String> l = plugin.maps().list();
                Msg.send(s, l.isEmpty() ? "&7保存されたマップはありません" : "&eマップ: &f" + String.join(", ", l));
            }
            case "save" -> withPlayer(s, p -> {
                if (a.length < 3 || !MapStore.validName(a[2])) {
                    Msg.send(p, "&c/wars map save <name> &7(英数字_- のみ)");
                    return;
                }
                boolean overwrite = plugin.maps().exists(a[2]);
                try {
                    long n = plugin.maps().save(p, a[2]);
                    Msg.send(p, "&aマップ &e" + a[2] + " &aを" + (overwrite ? "上書き" : "") + "保存しました (" + n + "ブロック)");
                    Msg.send(p, "&7今立っている位置が中心になります。&f/wars arena paste <id> " + a[2] + " &7で貼り付け");
                    Sfx.success(p);
                } catch (Exception ex) {
                    Msg.send(p, "&c保存に失敗しました: " + ex.getMessage());
                }
            });
            case "delete" -> {
                if (a.length < 3) {
                    Msg.send(s, "&c/wars map delete <name>");
                    return;
                }
                Msg.send(s, plugin.maps().delete(a[2]) ? "&eマップを削除しました (貼り付け済みのアリーナは残ります)" : "&cマップが見つかりません");
            }
            default -> Msg.send(s, "&c/wars map save <name> | list | delete <name>");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        String cmdName = cmd.getName().toLowerCase();

        // /kit コマンドのタブ補完
        if (cmdName.equals("kit")) {
            completeKitArgs(a, out);
            String last = a[a.length - 1].toLowerCase();
            out.removeIf(x -> !x.toLowerCase().startsWith(last));
            return out;
        }

        if (a.length == 1) {
            out.addAll(PUBLIC_SUBS);
            if (s.hasPermission("wars.admin")) out.addAll(ADMIN_SUBS);
        } else if (a[0].equalsIgnoreCase("kit")) {
            String[] kitArgs = Arrays.copyOfRange(a, 1, a.length);
            completeKitArgs(kitArgs, out);
        } else if (a.length == 2 && a[0].equalsIgnoreCase("arena")) {
            out.addAll(List.of("create", "build", "paste", "addspawn", "clearspawns", "enable", "disable", "delete", "tp", "list"));
        } else if (a.length == 2 && a[0].equalsIgnoreCase("map")) {
            out.addAll(List.of("save", "list", "delete"));
        } else if (a.length == 3 && a[0].equalsIgnoreCase("map") && a[1].equalsIgnoreCase("delete")) {
            out.addAll(plugin.maps().list());
        } else if (a.length == 4 && a[0].equalsIgnoreCase("arena") && a[1].equalsIgnoreCase("paste")) {
            out.addAll(plugin.maps().list());
        } else if (a.length == 5 && a[0].equalsIgnoreCase("arena") && a[1].equalsIgnoreCase("paste")) {
            out.add("randomizer");
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

    private void completeKitArgs(String[] kitArgs, List<String> out) {
        if (kitArgs.length == 1) {
            out.addAll(List.of("area", "perm"));
        } else if (kitArgs.length == 2 && kitArgs[0].equalsIgnoreCase("area")) {
            out.addAll(List.of("pos1", "pos2", "clear", "info"));
        } else if (kitArgs.length == 2 && kitArgs[0].equalsIgnoreCase("perm")) {
            out.addAll(List.of("add", "remove", "list"));
        } else if (kitArgs.length == 3 && kitArgs[0].equalsIgnoreCase("perm")
                && (kitArgs[1].equalsIgnoreCase("add") || kitArgs[1].equalsIgnoreCase("remove"))) {
            for (Player online : Bukkit.getOnlinePlayers()) out.add(online.getName());
        } else if (kitArgs.length == 4 && kitArgs[0].equalsIgnoreCase("perm")
                && (kitArgs[1].equalsIgnoreCase("add") || kitArgs[1].equalsIgnoreCase("remove"))) {
            out.addAll(List.of("create", "creative"));
        }
    }
}