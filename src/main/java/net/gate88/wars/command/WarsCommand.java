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
import net.gate88.wars.gui.KitPermGui;
import net.gate88.wars.gui.KitReviewGui;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.points.PointsManager;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Pos;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class WarsCommand implements CommandExecutor, TabCompleter {
    private final WarsPlugin plugin;
    private final Map<UUID, Map<Integer, Location>> tempAreaPos = new HashMap<>();

    public WarsCommand(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final List<String> ADMIN_SUBS = List.of("admin", "adomin", "kit", "start", "stop", "setlobby", "sethologram",
            "setpodium", "arena", "map", "setpoints", "addpoints", "reload");
    private static final List<String> PUBLIC_SUBS = List.of("help", "vote", "top", "points");

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("kitsuggest")) {
            if (!(s instanceof Player p)) return true;
            handleKitSuggest(p, a);
            return true;
        }

        if (cmdName.equals("kitreview")) {
            if (!(s instanceof Player p)) return true;
            KitReviewGui.open(plugin, p);
            return true;
        }

        if (cmdName.equals("kit")) {
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

        if (cmdName.equals("createkit")) {
            if (!(s instanceof Player p)) {
                Msg.send(s, "ゲーム内で実行してください");
                return true;
            }
            if (!plugin.kits().canCreateKit(p)) {
                Msg.send(p, "&cキットを追加する権限がありません (一般プレイヤーは /kit suggest を使用してください)");
                return true;
            }
            if (a.length < 1) {
                Msg.send(p, "&c使用方法: /createkit <kitName>");
                return true;
            }
            String kitName = a[0];

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
                if (a.length >= 2 && a[1].equalsIgnoreCase("toggle")) {
                    if (!s.hasPermission("wars.admin") && !s.isOp()) {
                        Msg.send(s, "&c権限がありません");
                        return true;
                    }
                    boolean next = !VoteMenu.isVoteItemsEnabled();
                    VoteMenu.setVoteItemsEnabled(plugin, next);
                    Msg.send(s, "&e投票アイテムの配布状態: " + (next ? "&a有効 (配布中)" : "&c無効 (停止中)"));
                    return true;
                }
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
            case "admin", "adomin" -> {
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

    private void handleKitCommand(Player p, String[] a) {
        if (a.length == 0) {
            KitGui.openList(plugin, p);
            return;
        }

        String sub = a[0].toLowerCase();

        if (sub.equals("suggest")) {
            String[] sArgs = a.length > 1 ? Arrays.copyOfRange(a, 1, a.length) : new String[0];
            handleKitSuggest(p, sArgs);
            return;
        }

        if (sub.equals("review")) {
            KitReviewGui.open(plugin, p);
            return;
        }

        if (sub.equals("vote") && a.length >= 2 && a[1].equalsIgnoreCase("toggle")) {
            if (!p.isOp() && !p.hasPermission("wars.admin")) return;
            boolean next = !VoteMenu.isVoteItemsEnabled();
            VoteMenu.setVoteItemsEnabled(plugin, next);
            Msg.send(p, "&e投票アイテムの配布状態: " + (next ? "&a有効 (配布中)" : "&c無効 (停止中)"));
            return;
        }

        if (sub.equals("perm")) {
            if (!p.isOp()) {
                Msg.send(p, "&cこの機能はOP権限を持っているプレイヤーのみ利用できます");
                Sfx.deny(p);
                return;
            }

            if (a.length == 1 || a[1].equalsIgnoreCase("list")) {
                KitPermGui.open(plugin, p);
                return;
            }

            // ★ クリエイティブ化禁止コマンド: /kit perm block|unblock <player>
            if (a[1].equalsIgnoreCase("block") || a[1].equalsIgnoreCase("unblock")) {
                if (a.length < 3) {
                    Msg.send(p, "&c使用方法: /kit perm <block|unblock> <player>");
                    return;
                }
                boolean block = a[1].equalsIgnoreCase("block");
                String tName = a[2];
                Player tOnline = Bukkit.getPlayerExact(tName);
                OfflinePlayer tOp = tOnline != null ? tOnline : Bukkit.getOfflinePlayer(tName);
                plugin.kits().setCreativeBlocked(tOp.getUniqueId(), tOp.getName(), block);
                Msg.send(p, "&e" + tName + " &fのクリエイティブ化コマンド使用を &c" + (block ? "【禁止】" : "【許可】") + " &fに設定しました");
                Sfx.success(p);
                return;
            }

            if (a.length < 4) {
                Msg.send(p, "&c使用方法: /kit perm <add|remove> <player> <create|creative>");
                return;
            }
            String action = a[1].toLowerCase();
            String targetName = a[2];
            String type = a[3].toLowerCase();
            Player onlineTarget = Bukkit.getPlayerExact(targetName);
            OfflinePlayer target = onlineTarget != null ? onlineTarget : Bukkit.getOfflinePlayer(targetName);
            UUID uuid = target.getUniqueId();
            String finalName = target.getName() != null ? target.getName() : targetName;

            boolean allow = action.equals("add") || action.equals("grant");
            if (type.equals("create")) {
                plugin.kits().setCreatePerm(uuid, finalName, allow);
                Msg.send(p, "&e" + finalName + " &fの &a[Kit追加のみ権限] &fを " + (allow ? "&a付与" : "&c剥奪") + " &fしました");
                if (onlineTarget != null) Msg.send(onlineTarget, allow ? "&aKit追加権限が付与されました！" : "&cKit追加権限が解除されました");
                Sfx.success(p);
            } else if (type.equals("creative")) {
                plugin.kits().setCreativePerm(uuid, finalName, allow);
                Msg.send(p, "&e" + finalName + " &fの &b[Kitエリア自動クリエイティブ権限] &fを " + (allow ? "&a付与" : "&c剥奪") + " &fしました");
                if (onlineTarget != null) Msg.send(onlineTarget, allow ? "&bKit制作エリアでの自動クリエイティブ権限が付与されました！" : "&cKit制作エリアでの自動クリエイティブ権限が解除されました");
                Sfx.success(p);
            }
            return;
        }

        if (sub.equals("area")) {
            if (!p.isOp() && !p.hasPermission("wars.admin")) return;
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
                    Msg.send(p, plugin.kits().hasArea() ? "&aKit制作エリアは有効です" : "&7Kit制作エリアは現在設定されていません");
                }
            }
            return;
        }

        KitGui.openList(plugin, p);
    }

    /** 一般向けKit提案処理 */
    private void handleKitSuggest(Player p, String[] a) {
        // ★ 条件1: サーバー内にOPが1人以上オンラインであること
        if (!plugin.kits().isOpOnline()) {
            Msg.send(p, "&c現在OP権限を持つプレイヤーがオンラインにいないため、Kitの提案・制作はできません");
            Sfx.deny(p);
            return;
        }

        // ★ 条件2: クリエイティブ化禁止リストに入っていないか
        if (plugin.kits().isCreativeBlocked(p.getUniqueId())) {
            Msg.send(p, "&cあなたはこのコマンドの使用が禁止されています");
            Sfx.deny(p);
            return;
        }

        // ★ 条件3: Kit制作エリア内にいること
        if (!plugin.kits().isInKitArea(p.getLocation())) {
            Msg.send(p, "&cKit制作エリアの中で実行してください");
            Sfx.deny(p);
            return;
        }

        if (a.length == 0) {
            Msg.send(p, "&e--- Kit提案システム ---");
            Msg.send(p, "&f1. &e/kit suggest start &7- 制作モード(Creative)を開始");
            Msg.send(p, "&f2. &7装備を整えたら &e/kit suggest <Kit名> [カテゴリ] &7で提出");
            return;
        }

        if (a[0].equalsIgnoreCase("start")) {
            p.setGameMode(GameMode.CREATIVE);
            plugin.kits().startSuggesting(p); // ★ 移動監視＆押し出し防止開始
            Msg.send(p, "&a[Kit提案] 制作モード(Creative)になりました。インベントリを開いて装備を組んでください。");
            Msg.send(p, "&c&l【警告】その場から動くと強制的にモード解除＆全アイテム消去されます！");
            Sfx.success(p);
            return;
        }

        String kitName = a[0];
        String category = a.length > 1 ? a[1] : "その他";

        // 重複チェック
        if (plugin.kits().exists(kitName) || plugin.kits().isSuggested(kitName)) {
            Msg.send(p, "&cそのKit名 (" + kitName + ") は既に存在するか、他者が提案中です。別の名前にしてください。");
            Sfx.deny(p);
            return;
        }

        // 1人10個制限
        if (plugin.kits().getPlayerSuggestionCount(p.getUniqueId()) >= 10) {
            Msg.send(p, "&c提案中のKitが上限 (10個) に達しています。OPの審査をお待ちください。");
            Sfx.deny(p);
            return;
        }

        // 提出完了
        plugin.kits().suggestKit(p, kitName, category);
        plugin.kits().stopSuggesting(p);
        p.getInventory().clear();
        p.setGameMode(GameMode.ADVENTURE);

        Msg.send(p, "&6&l[完了] &aKit &e" + kitName + " &aの提案を送信しました！(残り枠: " + (9 - plugin.kits().getPlayerSuggestionCount(p.getUniqueId())) + "個)");
        Sfx.success(p);

        for (Player op : Bukkit.getOnlinePlayers()) {
            if (op.isOp()) {
                Msg.send(op, "&d[通知] &b" + p.getName() + " &fが新しいKit &e" + kitName + " &fを提案しました (/kit review で確認)");
            }
        }
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
        s.sendMessage(Msg.c("&7/kit suggest <名前> &f- Kitの提案 (要OPオンライン)"));
        if (s.hasPermission("wars.admin") || s.isOp()) {
            s.sendMessage(Msg.c("&6[管理] &7/wars admin &f- 総合管理ダッシュボードGUI"));
            s.sendMessage(Msg.c("&6[管理] &7/kit &f- Kit一覧・管理GUI"));
            s.sendMessage(Msg.c("&6[管理] &7/kit review &f- 提案Kit審査GUI"));
            s.sendMessage(Msg.c("&6[管理] &7/kit perm &f- Kit権限管理GUI (OP限定)"));
            s.sendMessage(Msg.c("&6[管理] &7/kit perm block|unblock <player> &f- クリエイティブ化禁止"));
            s.sendMessage(Msg.c("&6[管理] &7/kit area pos1|pos2|clear &f- Kit制作エリアの設定"));
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
            Msg.send(s, "&c/wars arena create|build|paste|addspawn|setspawn|setteams|setteamsize|addbreak|removebreak|clearbreak|enable|disable|delete|tp|list");
            return;
        }
        String op = a[1].toLowerCase();
        if (op.equals("list")) {
            for (Arena ar : plugin.arenas().all()) {
                s.sendMessage(Msg.c((ar.isReady() ? "&a" : "&c") + ar.id + (ar.enabled ? "" : " &6[編集中]") + " &7mode=" + ar.modeId
                        + " world=" + ar.worldName + " center=" + ar.cx + "," + ar.cy + "," + ar.cz + " spawns=" + ar.spawns.size()
                        + " teams=" + (ar.maxTeams == 0 ? "auto" : ar.maxTeams) + " teamSize=" + (ar.teamSize == 0 ? "auto" : ar.teamSize)
                        + " breakBlocks=" + ar.breakOnStart.size()
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
                ar.cx = l.getBlockX(); ar.cy = l.getBlockY(); ar.cz = l.getBlockZ();
                ar.enabled = false;
                plugin.arenas().save();
                Msg.send(s, "&aアリーナ作成: 中心=" + ar.cx + "," + ar.cy + "," + ar.cz);
                sendEditing(s, ar);
            }
            case "build" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) { Msg.send(s, "&cアリーナが見つかりません"); return; }
                ArenaBuilder.build(ar);
                ar.map = null; ar.enabled = false;
                plugin.arenas().save();
                Msg.send(s, "&aアリーナを生成しました (スポーン" + ar.spawns.size() + "個)");
                sendEditing(s, ar);
            }
            case "paste" -> {
                if (p == null) return;
                if (!MapStore.available()) { Msg.send(s, "&cWorldEdit が必要です"); return; }
                if (a.length < 4) { Msg.send(s, "&c/wars arena paste <id> <map> [mode]"); return; }
                String map = a[3];
                if (!plugin.maps().exists(map)) { Msg.send(s, "&cマップが見つかりません: " + map); return; }
                Arena ar = plugin.arenas().get(id);
                String mode = a.length >= 5 ? a[4].toLowerCase() : ar != null ? ar.modeId : "randomizer";
                if (!plugin.modes().hasArenaType(mode)) { Msg.send(s, "&c不明なアリーナ種類です (randomizer)"); return; }
                if (ar == null) ar = plugin.arenas().create(id, mode);
                ar.modeId = mode;
                Location l = p.getLocation();
                try { plugin.maps().paste(p, map, l); } catch (Exception ex) { Msg.send(s, "&c貼り付けに失敗: " + ex.getMessage()); return; }
                ar.worldName = l.getWorld().getName();
                ar.cx = l.getBlockX(); ar.cy = l.getBlockY(); ar.cz = l.getBlockZ();
                ar.map = map.toLowerCase(); ar.enabled = false; ar.spawns.clear();
                ar.spawns.addAll(plugin.maps().spawnsFor(ar.map, ar));
                plugin.arenas().save();
                Msg.send(s, "&aマップ &e" + map + " &aを貼り付けました");
                sendEditing(s, ar);
            }
            case "enable" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) { Msg.send(s, "&cアリーナが見つかりません"); return; }
                if (ar.spawns.isEmpty()) { Msg.send(s, "&cスポーンがありません"); return; }
                ar.enabled = true;
                plugin.arenas().save();
                Msg.send(s, "&aアリーナ &e" + ar.id + " &aを有効にしました");
            }
            case "disable" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) { Msg.send(s, "&cアリーナが見つかりません"); return; }
                ar.enabled = false;
                plugin.arenas().save();
                Msg.send(s, "&eアリーナ &f" + ar.id + " &eを編集中にしました");
            }
            case "addspawn" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null) return;
                ar.spawns.add(Pos.of(p.getLocation()));
                plugin.arenas().save();
                plugin.maps().storeSpawns(ar);
                Msg.send(s, "&aスポーン追加 (" + ar.spawns.size() + ")");
            }
            case "setspawn" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null) return;
                if (a.length < 4) { Msg.send(s, "&c使用方法: /wars arena setspawn <id> <チーム番号 1〜N>"); return; }
                int idx = Integer.parseInt(a[3]);
                ar.setSpawn(idx - 1, Pos.of(p.getLocation()));
                plugin.arenas().save();
                plugin.maps().storeSpawns(ar);
                Msg.send(s, "&aチーム " + idx + " スポーン地点を設定しました");
            }
            case "setteams" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) return;
                ar.maxTeams = Integer.parseInt(a[3]);
                plugin.arenas().save();
                Msg.send(s, "&a最大チーム数を " + ar.maxTeams + " に設定しました");
            }
            case "setteamsize" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) return;
                ar.teamSize = Integer.parseInt(a[3]);
                plugin.arenas().save();
                Msg.send(s, "&a1チーム人数を " + ar.teamSize + " に設定しました");
            }
            case "addbreak" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null) return;
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir() || !hand.getType().isBlock()) return;
                ar.addBreakBlock(hand.getType());
                plugin.arenas().save();
                Msg.send(p, "&a自動破壊ブロックに " + hand.getType().name() + " を追加しました");
            }
            case "removebreak" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null || p == null) return;
                ItemStack hand = p.getInventory().getItemInMainHand();
                ar.removeBreakBlock(hand.getType());
                plugin.arenas().save();
                Msg.send(p, "&e自動破壊ブロックから解除しました");
            }
            case "clearbreak" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar == null) return;
                ar.clearBreakBlocks();
                plugin.arenas().save();
                Msg.send(s, "&e自動破壊ブロックを全消去しました");
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
                plugin.arenas().delete(id);
                plugin.arenas().save();
                Msg.send(s, "&e削除しました");
            }
            case "tp" -> {
                Arena ar = plugin.arenas().get(id);
                if (ar != null && ar.center() != null && p != null) p.teleport(ar.center().add(0, 1, 0));
            }
        }
    }

    private void sendEditing(CommandSender s, Arena ar) {
        Msg.send(s, "&6編集中です。準備ができたら &f/wars arena enable " + ar.id);
    }

    private void map(CommandSender s, String[] a) {
        if (!MapStore.available()) return;
        String op = a.length >= 2 ? a[1].toLowerCase() : "";
        switch (op) {
            case "list" -> Msg.send(s, "&eマップ: &f" + String.join(", ", plugin.maps().list()));
            case "save" -> withPlayer(s, p -> {
                try {
                    plugin.maps().save(p, a[2]);
                    Msg.send(p, "&aマップ &e" + a[2] + " &aを保存しました");
                } catch (Exception ex) { Msg.send(p, "&c保存失敗: " + ex.getMessage()); }
            });
            case "delete" -> Msg.send(s, plugin.maps().delete(a[2]) ? "&e削除しました" : "&c見つかりません");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        String cmdName = cmd.getName().toLowerCase();

        if (cmdName.equals("kit") || cmdName.equals("kitsuggest")) {
            completeKitArgs(s, a, out);
            String last = a[a.length - 1].toLowerCase();
            out.removeIf(x -> !x.toLowerCase().startsWith(last));
            return out;
        }

        if (a.length == 1) {
            out.addAll(PUBLIC_SUBS);
            if (s.hasPermission("wars.admin") || s.isOp()) out.addAll(ADMIN_SUBS);
        }
        return out;
    }

    private void completeKitArgs(CommandSender s, String[] kitArgs, List<String> out) {
        if (kitArgs.length == 1) {
            out.add("suggest");
            if (s.isOp() || s.hasPermission("wars.admin")) {
                out.addAll(List.of("review", "area", "perm", "vote"));
            }
        } else if (kitArgs.length == 2 && kitArgs[0].equalsIgnoreCase("suggest")) {
            out.add("start");
        } else if (kitArgs.length == 2 && kitArgs[0].equalsIgnoreCase("perm") && s.isOp()) {
            out.addAll(List.of("add", "remove", "block", "unblock", "list"));
        } else if (kitArgs.length == 3 && kitArgs[0].equalsIgnoreCase("perm") && s.isOp()) {
            for (Player online : Bukkit.getOnlinePlayers()) out.add(online.getName());
        } else if (kitArgs.length == 4 && kitArgs[0].equalsIgnoreCase("perm") && s.isOp()
                && (kitArgs[1].equalsIgnoreCase("add") || kitArgs[1].equalsIgnoreCase("remove"))) {
            out.addAll(List.of("create", "creative"));
        }
    }
}