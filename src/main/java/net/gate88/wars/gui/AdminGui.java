package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.arena.ArenaBuilder;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** 管理者用設定GUI (/wars admin) */
public final class AdminGui implements InventoryHolder {
    public enum Page { MAIN, ARENAS }

    private record Num(String path, String label, Material icon, double step, double min, double max, boolean isInt) {}

    private record Toggle(String path, String label, Material icon, String[] values) {}

    private static final List<Num> NUMS = List.of(
            new Num("lobby.min-players", "最低開始人数", Material.PLAYER_HEAD, 1, 1, 16, true),
            new Num("lobby.max-players", "最大参加人数", Material.PLAYER_HEAD, 1, 1, 16, true),
            new Num("lobby.countdown-seconds", "開始カウントダウン(秒)", Material.CLOCK, 5, 5, 300, true),
            new Num("lobby.full-countdown-seconds", "満員時カウントダウン(秒)", Material.CLOCK, 5, 3, 120, true),
            new Num("lobby.return-delay-seconds", "ロビー転送までの秒数", Material.ENDER_PEARL, 1, 1, 30, true),
            new Num("points.kill", "キルpoint", Material.IRON_SWORD, 5, 0, 500, true),
            new Num("points.survival", "生存point(1回あたり)", Material.GOLDEN_APPLE, 1, 0, 50, true),
            new Num("points.survival-interval-seconds", "生存point間隔(秒)", Material.CLOCK, 1, 1, 60, true),
            // ---- Randomizer DUO
            new Num("modes.randomizer_duo.duration-seconds", "[DUO] 試合時間(秒)", Material.DISPENSER, 30, 60, 900, true),
            new Num("modes.randomizer_duo.grace-seconds", "[DUO] 装備配布までの秒数", Material.CHEST, 1, 0, 30, true),
            new Num("modes.randomizer_duo.block-decay-seconds", "[DUO] 設置ブロック消滅(秒)", Material.WHITE_WOOL, 1, 0, 60, true),
            new Num("modes.randomizer_duo.wool-stacks", "[DUO] 羊毛スタック数", Material.WHITE_WOOL, 1, 1, 6, true),
            new Num("modes.randomizer_duo.min-players", "[DUO] 最低人数", Material.PLAYER_HEAD, 1, 2, 16, true),
            new Num("modes.randomizer_duo.border.start-radius", "[DUO] ボーダー初期半径", Material.RED_STAINED_GLASS, 1, 12, 60, true),
            new Num("modes.randomizer_duo.border.end-radius", "[DUO] ボーダー最終半径", Material.RED_STAINED_GLASS, 1, 6, 30, true),
            new Num("modes.randomizer_duo.border.damage-per-second", "[DUO] ボーダーダメージ/秒(1=0.5♥)", Material.REDSTONE, 0.5, 0, 10, false),
            // ---- Randomizer TEAM
            new Num("modes.randomizer_team.duration-seconds", "[TEAM] 試合時間(秒)", Material.CHEST_MINECART, 30, 60, 900, true),
            new Num("modes.randomizer_team.grace-seconds", "[TEAM] 装備配布までの秒数", Material.CHEST, 1, 0, 30, true),
            new Num("modes.randomizer_team.block-decay-seconds", "[TEAM] 設置ブロック消滅(秒)", Material.WHITE_WOOL, 1, 0, 60, true),
            new Num("modes.randomizer_team.wool-stacks", "[TEAM] 羊毛スタック数", Material.WHITE_WOOL, 1, 1, 6, true),
            new Num("modes.randomizer_team.team-count", "[TEAM] チーム数", Material.WHITE_BANNER, 1, 2, 8, true),
            new Num("modes.randomizer_team.min-players", "[TEAM] 最低人数", Material.PLAYER_HEAD, 1, 2, 16, true));

    private static final List<Toggle> TOGGLES = List.of(
            new Toggle("lobby.mode-select", "モード選択方式", Material.COMPARATOR, new String[]{"VOTE", "RANDOM"}),
            new Toggle("modes.randomizer_duo.enabled", "[DUO] 有効", Material.DISPENSER, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_team.enabled", "[TEAM] 有効", Material.CHEST_MINECART, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_duo.wool-fill", "[DUO] 羊毛5x5制圧の勝利条件", Material.WHITE_WOOL, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_duo.death-chest", "[DUO] 遺品チェスト", Material.CHEST, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_team.wool-fill", "[TEAM] 羊毛5x5制圧の勝利条件", Material.WHITE_WOOL, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_team.death-chest", "[TEAM] 遺品チェスト", Material.CHEST, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_duo.border.enabled", "[DUO] 特殊ボーダー", Material.RED_STAINED_GLASS_PANE, new String[]{"true", "false"}),
            new Toggle("modes.randomizer_team.border.enabled", "[TEAM] 特殊ボーダー", Material.RED_STAINED_GLASS_PANE, new String[]{"true", "false"}),
            new Toggle("scoreboard.extra-lines", "スコアボード詳細行", Material.OAK_SIGN, new String[]{"true", "false"}),
            new Toggle("podium.type", "表彰台の種類", Material.ARMOR_STAND, new String[]{"MANNEQUIN", "ARMOR_STAND"}));

    private final WarsPlugin plugin;
    private final Page page;
    private final Inventory inv;
    /** slot -> action id */
    private final java.util.Map<Integer, String> actions = new java.util.HashMap<>();

    private AdminGui(WarsPlugin plugin, Page page) {
        this.plugin = plugin;
        this.page = page;
        this.inv = Bukkit.createInventory(this, 54, Msg.c(page == Page.MAIN ? "&8[88WARS] 管理設定" : "&8[88WARS] アリーナ一覧"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static void open(WarsPlugin plugin, Player p, Page page) {
        AdminGui g = new AdminGui(plugin, page);
        g.render();
        p.openInventory(g.inv);
        Sfx.menuOpen(p);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.c(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private void put(int slot, ItemStack it, String action) {
        inv.setItem(slot, it);
        if (action != null) actions.put(slot, action);
    }

    private String fmt(Num n) {
        return n.isInt() ? String.valueOf(plugin.getConfig().getInt(n.path())) : String.valueOf(plugin.getConfig().getDouble(n.path()));
    }

    private void render() {
        inv.clear();
        actions.clear();
        if (page == Page.MAIN) renderMain();
        else renderArenas();
    }

    private void renderMain() {
        int slot = 0;
        for (int i = 0; i < NUMS.size(); i++) {
            Num n = NUMS.get(i);
            put(slot++, item(n.icon(), "&e" + n.label(), List.of("&7現在: &a" + fmt(n), "",
                    "&7左クリック: &f+" + n.step(), "&7右クリック: &f-" + n.step(), "&7Shift: &f×5")), "num:" + i);
        }
        slot = 22;
        for (int i = 0; i < TOGGLES.size(); i++) {
            Toggle t = TOGGLES.get(i);
            put(slot++, item(t.icon(), "&b" + t.label(), List.of("&7現在: &a" + plugin.getConfig().getString(t.path()), "",
                    "&7クリックで切り替え")), "tog:" + i);
        }
        // 位置設定
        put(36, item(Material.RED_BED, "&aロビー地点を現在地に設定", List.of("&7参加/試合後の転送先")), "setlobby");
        put(37, item(Material.OAK_SIGN, "&aランキングホログラムを現在地に設定", List.of("&7足元より少し上に表示されます")), "sethologram");
        put(38, item(Material.GOLD_BLOCK, "&6表彰台 1位 を現在地に設定", List.of()), "setpodium:1");
        put(39, item(Material.IRON_BLOCK, "&f表彰台 2位 を現在地に設定", List.of()), "setpodium:2");
        put(40, item(Material.COPPER_BLOCK, "&c表彰台 3位 を現在地に設定", List.of()), "setpodium:3");
        put(45, item(Material.MAP, "&dアリーナ一覧 / 生成", List.of("&7アリーナのTP・自動生成")), "arenas");
        put(47, item(Material.LIME_CONCRETE, "&a試合を今すぐ開始", List.of("&7ロビーの全員で開始 (人数不問)")), "start");
        put(48, item(Material.RED_CONCRETE, "&c進行中の試合を中止", List.of("&7ポイントは付与されません")), "stop");
        put(50, item(Material.BOOK, "&fconfig.yml を再読込", List.of()), "reload");
        put(53, item(Material.BARRIER, "&c閉じる", List.of()), "close");
    }

    private void renderArenas() {
        int slot = 0;
        for (Arena a : plugin.arenas().all()) {
            if (slot >= 45) break;
            WarsMode md = plugin.modes().get(a.modeId);
            put(slot++, item(a.isReady() ? Material.GRASS_BLOCK : Material.DEAD_BUSH,
                    (a.isReady() ? "&a" : "&c") + a.id,
                    List.of("&7モード: &f" + (md == null ? a.modeId : md.displayName),
                            "&7ワールド: &f" + a.worldName,
                            "&7中心: &f" + a.cx + ", " + a.cy + ", " + a.cz,
                            "&7スポーン: &f" + a.spawns.size(), "",
                            "&e左クリック: &fテレポート",
                            "&eShift+左クリック: &f現在地を中心に再生成(上書き注意!)")), "arena:" + a.id);
        }
        put(45, item(Material.EMERALD, "&a現在地に Randomizer アリーナを新規生成",
                List.of("&7id は自動採番 (randomizer1, 2, ...)", "&c61x61 の範囲の地形を上書きします!")), "newarena");
        put(49, item(Material.ARROW, "&f戻る", List.of()), "main");
    }

    public void click(Player p, int slot, ClickType type) {
        String act = actions.get(slot);
        if (act == null) return;
        Sfx.click(p);
        boolean left = type.isLeftClick();
        boolean shift = type.isShiftClick();
        if (act.startsWith("num:")) {
            Num n = NUMS.get(Integer.parseInt(act.substring(4)));
            double d = n.step() * (shift ? 5 : 1) * (left ? 1 : -1);
            if (n.isInt()) {
                int v = (int) Math.max(n.min(), Math.min(n.max(), plugin.getConfig().getInt(n.path()) + d));
                plugin.getConfig().set(n.path(), v);
            } else {
                double v = Math.max(n.min(), Math.min(n.max(), plugin.getConfig().getDouble(n.path()) + d));
                plugin.getConfig().set(n.path(), Math.round(v * 10) / 10.0);
            }
            plugin.saveConfig();
            render();
            return;
        }
        if (act.startsWith("tog:")) {
            Toggle t = TOGGLES.get(Integer.parseInt(act.substring(4)));
            String cur = plugin.getConfig().getString(t.path());
            int idx = 0;
            for (int i = 0; i < t.values().length; i++) if (t.values()[i].equalsIgnoreCase(cur)) idx = i;
            String next = t.values()[(idx + 1) % t.values().length];
            if (next.equals("true") || next.equals("false")) plugin.getConfig().set(t.path(), Boolean.parseBoolean(next));
            else plugin.getConfig().set(t.path(), next);
            plugin.saveConfig();
            render();
            return;
        }
        if (act.startsWith("arena:")) {
            Arena a = plugin.arenas().get(act.substring(6));
            if (a == null) return;
            if (shift && left) {
                Location l = p.getLocation();
                a.worldName = l.getWorld().getName();
                a.cx = l.getBlockX();
                a.cy = l.getBlockY();
                a.cz = l.getBlockZ();
                ArenaBuilder.build(a);
                plugin.arenas().save();
                Msg.send(p, "&aアリーナ &e" + a.id + " &aを現在地に再生成しました");
                Sfx.success(p);
                render();
            } else if (a.center() != null) {
                p.teleport(a.center().add(0, 1, 0));
                Sfx.teleport(p);
            } else {
                Msg.send(p, "&cワールドが読み込まれていません");
                Sfx.deny(p);
            }
            return;
        }
        switch (act) {
            case "newarena" -> {
                int i = 1;
                while (plugin.arenas().get("randomizer" + i) != null) i++;
                Arena a = plugin.arenas().create("randomizer" + i, "randomizer");
                Location l = p.getLocation();
                a.worldName = l.getWorld().getName();
                a.cx = l.getBlockX();
                a.cy = l.getBlockY();
                a.cz = l.getBlockZ();
                ArenaBuilder.build(a);
                plugin.arenas().save();
                p.teleport(a.center().add(0, 1, 0));
                Msg.send(p, "&aアリーナ &e" + a.id + " &aを生成しました (スポーン16個)");
                Sfx.success(p);
                render();
            }
            case "main" -> open(plugin, p, Page.MAIN);
            case "arenas" -> open(plugin, p, Page.ARENAS);
            case "setlobby" -> {
                plugin.setLobbyLocation(p.getLocation());
                Msg.send(p, "&aロビー地点を設定しました");
                Sfx.success(p);
            }
            case "sethologram" -> {
                plugin.setHologramLocation(p.getLocation().add(0, 2.5, 0));
                plugin.holograms().refresh();
                Msg.send(p, "&aランキングホログラムを設定しました");
                Sfx.success(p);
            }
            case "start" -> {
                p.closeInventory();
                if (!plugin.lobby().forceStart(null)) Msg.send(p, "&c開始できません (試合中/対象プレイヤーなし)");
            }
            case "stop" -> {
                if (plugin.match() != null) {
                    plugin.match().abort();
                    Msg.send(p, "&e試合を中止しました");
                } else {
                    Msg.send(p, "&7進行中の試合はありません");
                }
            }
            case "reload" -> {
                plugin.reloadAll();
                Msg.send(p, "&a再読込しました");
                render();
            }
            case "close" -> p.closeInventory();
            default -> {
                if (act.startsWith("setpodium:")) {
                    int r = Integer.parseInt(act.substring(10));
                    plugin.setPodiumLocation(r, p.getLocation());
                    Msg.send(p, "&a表彰台 " + r + "位 を設定しました");
                    Sfx.success(p);
                }
            }
        }
    }
}
