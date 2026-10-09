package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.arena.ArenaBuilder;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Pos;
import net.gate88.wars.util.Sfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class AdminGui implements InventoryHolder {
    public enum Page {
        MAIN,
        MODES,
        MODE_SETTINGS,
        ARENAS,
        ARENA_SETTINGS,
        ARENA_TIMING_SETTINGS,
        LOBBY_SETTINGS,
        LOCATIONS,
        POINTS_SETTINGS
    }

    private record Num(String path, String label, Material icon, double step, double min, double max, boolean isInt, double def) {}
    private record Toggle(String path, String label, Material icon, String[] values, String def) {}

    private final WarsPlugin plugin;
    private final Page page;
    private final Inventory inv;
    private final String targetId;
    private final java.util.Map<Integer, String> actions = new java.util.HashMap<>();

    private AdminGui(WarsPlugin plugin, Page page, String targetId) {
        this.plugin = plugin;
        this.page = page;
        this.targetId = targetId;
        String titleStr = switch (page) {
            case MAIN -> "&8[88WARS] 総合管理メニュー";
            case MODES -> "&8[88WARS] ゲームモード選択";
            case MODE_SETTINGS -> "&8[88WARS] モード設定: &e" + (targetId != null ? targetId : "");
            case ARENAS -> "&8[88WARS] アリーナ一覧";
            case ARENA_SETTINGS -> "&8[88WARS] アリーナ設定: &e" + (targetId != null ? targetId : "");
            case ARENA_TIMING_SETTINGS -> "&8[88WARS] 個別時間設定: &e" + (targetId != null ? targetId : "");
            case LOBBY_SETTINGS -> "&8[88WARS] ロビー＆システム設定";
            case LOCATIONS -> "&8[88WARS] 位置・ホログラム設定";
            case POINTS_SETTINGS -> "&8[88WARS] ポイント＆ランキング設定";
        };
        this.inv = Bukkit.createInventory(this, 54, Msg.c(titleStr));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inv;
    }

    public static void open(WarsPlugin plugin, Player p, Page page) {
        open(plugin, p, page, null);
    }

    public static void open(WarsPlugin plugin, Player p, Page page, String targetId) {
        AdminGui g = new AdminGui(plugin, page, targetId);
        g.render();
        p.openInventory(g.inv);
        Sfx.menuOpen(p);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        return item(m, name, lore, false);
    }

    private ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c(name));
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.c(s));
            meta.lore(l);
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            it.setItemMeta(meta);
        }
        return it;
    }

    private void put(int slot, ItemStack it) {
        put(slot, it, null);
    }

    private void put(int slot, ItemStack it, String action) {
        inv.setItem(slot, it);
        if (action != null) actions.put(slot, action);
    }

    private void render() {
        inv.clear();
        actions.clear();
        switch (page) {
            case MAIN -> renderMain();
            case MODES -> renderModes();
            case MODE_SETTINGS -> renderModeSettings();
            case ARENAS -> renderArenas();
            case ARENA_SETTINGS -> renderArenaSettings();
            case ARENA_TIMING_SETTINGS -> renderArenaTimingSettings();
            case LOBBY_SETTINGS -> renderLobbySettings();
            case LOCATIONS -> renderLocations();
            case POINTS_SETTINGS -> renderPointsSettings();
        }
    }

    private void renderMain() {
        put(10, item(Material.DIAMOND_SWORD, "&e&l【ゲームモード設定】", List.of("&7各モードの試合時間・猶予時間・ルール等を設定", "&eクリックして開く")), "page:MODES");
        put(12, item(Material.GRASS_BLOCK, "&a&l【アリーナ管理】", List.of("&7アリーナ一覧・親子設定・スポーン・生成", "&eクリックして開く")), "page:ARENAS");
        put(14, item(Material.CHEST, "&6&l【Kit管理・作成】", List.of("&7Kit一覧・編集・ポーション効果設定 (/kit)", "&eクリックして開く")), "open_kitgui");
        put(16, item(Material.PLAYER_HEAD, "&b&l【Kit権限管理】 &c[OP限定]", List.of("&7誰がKit追加/クリエイティブ権限を持つか管理 (/kit perm)", "&eクリックして開く")), "open_perm_gui");

        put(28, item(Material.REDSTONE_TORCH, "&d&l【ロビー＆システム設定】", List.of("&7開始人数、カウントダウン秒数、投票配布ON/OFF等", "&eクリックして開く")), "page:LOBBY_SETTINGS");
        put(30, item(Material.COMPASS, "&9&l【ロビー位置・ホログラム設定】", List.of("&7ロビー地点、ランキング、表彰台1〜3位の現在地設定", "&eクリックして開く")), "page:LOCATIONS");
        put(32, item(Material.EMERALD, "&a&l【ポイント＆ランキング設定】", List.of("&7キルポイント、生存ポイント、間隔の設定", "&eクリックして開く")), "page:POINTS_SETTINGS");
        put(34, item(Material.NETHER_STAR, "&c&l【試合コントロール】", List.of("&a左クリック: 試合を今すぐ開始", "&c右クリック: 進行中の試合を中止")), "match_control");

        put(49, item(Material.BOOK, "&f&l全設定・データ再読込 (Reload)", List.of("&7config.yml, data.yml, kits.yml を再読み込み")), "reload");
        put(53, item(Material.BARRIER, "&c閉じる", List.of()), "close");
    }

    private void renderModes() {
        int slot = 10;
        for (WarsMode m : plugin.modes().all()) {
            if (slot > 34) break;
            boolean enabled = m.enabled();
            List<String> lore = new ArrayList<>(m.description);
            lore.add("");
            lore.add("&7状態: " + (enabled ? "&a有効" : "&c無効"));
            lore.add("&7試合時間: &f" + m.durationSeconds() + "秒");
            lore.add("&7ブロック開放時間: &e" + m.breakDelaySeconds() + "秒");
            lore.add("&7猶予時間: &f" + m.graceSeconds() + "秒");
            lore.add("");
            lore.add("&eクリックして詳細設定を変更");

            put(slot++, item(m.icon, (enabled ? "&a&l" : "&c&l") + m.displayName + " &7(" + m.id + ")", lore), "mode_sel:" + m.id);
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
        }

        put(49, item(Material.ARROW, "&fメインメニューに戻る", List.of()), "page:MAIN");
    }

    private void renderModeSettings() {
        if (targetId == null) { renderModes(); return; }
        String prefix = "modes." + targetId + ".";
        boolean isSG = targetId.equalsIgnoreCase("survivalgames") || targetId.equalsIgnoreCase("survival_games");

        List<Num> modeNums = new ArrayList<>();
        List<Toggle> modeToggles = new ArrayList<>();

        if (isSG) {
            // Survival Games 専用設定
            modeNums.add(new Num(prefix + "duration-seconds", "1ラウンド試合時間(秒)", Material.CLOCK, 30, 60, 1800, true, 900));
            modeNums.add(new Num(prefix + "pvp-grace-seconds", "PvP解禁猶予(秒)", Material.SHIELD, 15, 0, 600, true, 90));
            modeNums.add(new Num(prefix + "chest-count", "初期チェスト生成数", Material.CHEST, 10, 10, 500, true, 200));
            modeNums.add(new Num(prefix + "chest-refresh-seconds", "チェスト再補充(秒)", Material.ENDER_CHEST, 30, 60, 1800, true, 720));
            modeNums.add(new Num(prefix + "core-spawn-seconds", "コア出現時間(秒)", Material.LODESTONE, 30, 60, 1800, true, 600));
            modeNums.add(new Num(prefix + "team-count", "チーム数 (8〜10想定)", Material.WHITE_BANNER, 1, 0, 16, true, 10));
            modeNums.add(new Num(prefix + "team-size", "1チームの人数 (5名想定)", Material.ARMOR_STAND, 1, 1, 16, true, 5));
            modeNums.add(new Num(prefix + "min-players", "最低必要人数", Material.PLAYER_HEAD, 1, 1, 50, true, 2));

            modeToggles.add(new Toggle(prefix + "enabled", "モード有効化", Material.REPEATER, new String[]{"true", "false"}, "true"));
        } else {
            // 通常モード (Randomizer 等)
            modeNums.addAll(List.of(
                    new Num(prefix + "duration-seconds", "試合時間(秒)", Material.CLOCK, 30, 30, 900, true, 180),
                    new Num(prefix + "break-delay-seconds", "ブロック開放までの待機(秒)", Material.IRON_BARS, 1, 0, 60, true, 0),
                    new Num(prefix + "grace-seconds", "装備配布までの猶予(秒)", Material.CHEST, 1, 0, 30, true, 5),
                    new Num(prefix + "block-decay-seconds", "設置ブロック崩壊(秒)", Material.WHITE_WOOL, 1, 0, 60, true, 12),
                    new Num(prefix + "min-players", "最低必要人数", Material.PLAYER_HEAD, 1, 1, 16, true, 2),
                    new Num(prefix + "team-count", "チーム数 (0=自動/team-size準拠)", Material.WHITE_BANNER, 1, 0, 8, true, 0),
                    new Num(prefix + "team-size", "1チームの人数", Material.ARMOR_STAND, 1, 1, 8, true, 1),
                    new Num(prefix + "wool-stacks", "羊毛スタック数", Material.SHEARS, 1, 1, 6, true, 3),
                    new Num(prefix + "border.start-radius", "ボーダー初期半径", Material.RED_STAINED_GLASS, 2, 10, 60, true, 28),
                    new Num(prefix + "border.end-radius", "ボーダー最終半径", Material.RED_STAINED_GLASS, 1, 4, 30, true, 9),
                    new Num(prefix + "border.damage-per-second", "ボーダーダメージ/秒", Material.REDSTONE, 0.5, 0.5, 10, false, 1.0)
            ));

            modeToggles.addAll(List.of(
                    new Toggle(prefix + "enabled", "モード有効化", Material.REPEATER, new String[]{"true", "false"}, "true"),
                    new Toggle(prefix + "wool-fill", "中央5x5制圧の勝利条件", Material.WHITE_CONCRETE, new String[]{"true", "false"}, "true"),
                    new Toggle(prefix + "death-chest", "遺品チェスト生成", Material.ENDER_CHEST, new String[]{"true", "false"}, "false"),
                    new Toggle(prefix + "border.enabled", "特殊ボーダー有効化", Material.RED_STAINED_GLASS_PANE, new String[]{"true", "false"}, "true")
            ));
        }

        int slot = 10;
        for (Num n : modeNums) {
            if (slot == 17) slot = 19;
            if (slot == 26) slot = 28;
            put(slot++, item(n.icon(), "&e" + n.label(), List.of(
                    "&7現在: &a" + (n.isInt() ? plugin.getConfig().getInt(n.path(), (int)n.def()) : plugin.getConfig().getDouble(n.path(), n.def())),
                    "",
                    "&7左クリック: &f+" + n.step(),
                    "&7右クリック: &f-" + n.step(),
                    "&7Shift: &f×5",
                    "&d[Qキー(ドロップ)] 初期値(" + (n.isInt() ? (int)n.def() : n.def()) + ")に戻す"
            )), "mnum:" + n.path() + ":" + n.step() + ":" + n.min() + ":" + n.max() + ":" + n.isInt() + ":" + n.def());
        }

        slot = 37;
        for (Toggle t : modeToggles) {
            String val = plugin.getConfig().getString(t.path(), t.def());
            put(slot++, item(t.icon(), "&b" + t.label(), List.of(
                    "&7現在: " + (val.equalsIgnoreCase("true") ? "&a有効 (true)" : "&c無効 (false)"),
                    "",
                    "&7クリックで切り替え",
                    "&d[Qキー(ドロップ)] 初期値(" + t.def() + ")に戻す"
            )), "mtog:" + t.path() + ":" + t.def());
        }

        put(49, item(Material.ARROW, "&fモード選択に戻る", List.of()), "page:MODES");
    }

    private void renderArenas() {
        int slot = 0;
        for (Arena a : plugin.arenas().all()) {
            if (slot >= 45) break;
            WarsMode md = plugin.modes().get(a.modeId);
            boolean timingCustom = a.customTimingEnabled;

            List<String> lore = new ArrayList<>();
            lore.add("&7モード: &f" + (md == null ? a.modeId : md.displayName));
            lore.add("&7状態: " + (a.enabled ? "&a有効" : "&6編集中"));
            if (a.isChild()) {
                lore.add("&d★ 子アリーナ (親: " + a.parentId + ")");
                lore.add("&7※親アリーナの設定を参照・同期中");
            } else {
                lore.add("&b★ 親アリーナ (子: " + a.childIds.size() + "個)");
            }
            lore.add("&7中心: &f" + a.cx + ", " + a.cy + ", " + a.cz);
            lore.add("&7スポーン地点数: &f" + a.spawns.size() + "個");
            lore.add("");
            lore.add("&e左クリック: &fアリーナ設定・子アリーナ管理を開く");
            lore.add("&b右クリック: &fアリーナへテレポート");

            put(slot++, item(a.isReady() ? Material.GRASS_BLOCK : Material.DEAD_BUSH,
                    (a.isReady() ? "&a" : "&c") + a.id, lore, a.isChild() || timingCustom), "arena_select:" + a.id);
        }

        put(45, item(Material.EMERALD, "&a現在地に親アリーナを新規自動生成", List.of("&7id は自動採番 (randomizer1, 2, ...)")), "newarena");
        put(49, item(Material.ARROW, "&fメインメニューに戻る", List.of()), "page:MAIN");
    }

    private void renderArenaSettings() {
        if (targetId == null) { renderArenas(); return; }
        Arena a = plugin.arenas().get(targetId);
        if (a == null) { renderArenas(); return; }

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&7ワールド: &f" + a.worldName);
        infoLore.add("&7中心座標: &f" + a.cx + ", " + a.cy + ", " + a.cz);
        if (a.isChild()) {
            infoLore.add("&d★ 子アリーナ (親アリーナ: " + a.parentId + " の設定を同期中)");
        } else {
            infoLore.add("&b★ 親アリーナ (登録されている子アリーナ: " + a.childIds.size() + "個)");
        }
        put(4, item(a.isReady() ? Material.GRASS_BLOCK : Material.DEAD_BUSH, "&e&lアリーナ: " + a.id, infoLore), null);

        put(19, item(Material.ENDER_PEARL, "&bアリーナの中心へテレポート", List.of("&7中心座標へワープします")), "atp:" + a.id);
        put(21, item(a.enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                a.enabled ? "&a【有効中】 (試合で使用されます)" : "&6【編集中】 (試合で使用されません)",
                List.of("&7クリックで 有効 / 編集中 を切り替え")), "atog_enabled:" + a.id);

        put(22, item(Material.CLOCK, "&e&l【アリーナ個別時間・ルール設定】",
                List.of((a.customTimingEnabled ? "&a● 個別設定: 有効中" : "&7● 個別設定: 無効中 (大元設定を使用)"), "", "&eクリックして開く"), a.customTimingEnabled), "page:ARENA_TIMING_SETTINGS:" + a.id);

        put(23, item(Material.WHITE_BANNER, "&e最大チーム数: &6" + (a.maxTeams == 0 ? "自動(モード準拠)" : a.maxTeams + "チーム"),
                List.of("&7左クリック: +1チーム", "&7右クリック: -1チーム", "&d[Qキー] リセット")), "ateams:" + a.id);

        put(25, item(Material.PLAYER_HEAD, "&e1チームの最大人数: &6" + (a.teamSize == 0 ? "自動(モード準拠)" : a.teamSize + "人"),
                List.of("&7左クリック: +1人", "&7右クリック: -1人", "&d[Qキー] リセット")), "ateamsize:" + a.id);

        put(29, item(Material.BEACON, "&a現在地にチームスポーンを追加",
                List.of("&7現在のスポーン数: &e" + a.spawns.size() + "個", "&7立っている位置を次のチームスポーン地点として登録")), "aaddspawn:" + a.id);

        put(31, item(Material.LAVA_BUCKET, "&c全スポーン地点を削除", List.of("&7登録されたスポーン座標をすべてクリアします")), "aclearspawns:" + a.id);

        if (!a.isChild()) {
            put(32, item(Material.DISPENSER, "&d&l【子アリーナを現在地に作成】",
                    List.of("&7親アリーナ &e" + a.id + " &7の設定を完全に同期する", "&7子アリーナを現在地を中心に新規作成します")), "acreate_child:" + a.id);
        }

        put(33, item(Material.ANVIL, "&6現在地を中心にマップ再生成", List.of("&c現在地を中心にマップを再ビルドします")), "arebuild:" + a.id);

        List<String> breakLore = new ArrayList<>();
        breakLore.add("&7試合開始時にアリーナ内で自動破壊されるブロックを設定します");
        if (a.breakOnStart.isEmpty()) {
            breakLore.add("&8(現在登録されているブロックはありません)");
        } else {
            breakLore.add("&c現在の登録ブロック:");
            for (Material bm : a.breakOnStart) breakLore.add(" &7- &f" + bm.name());
        }
        breakLore.add("");
        breakLore.add("&e[左クリック] 手持ちブロックを追加");
        breakLore.add("&b[右クリック] 手持ちブロックを解除");
        breakLore.add("&d[Qキー] 全消去");
        put(35, item(Material.IRON_PICKAXE, "&c&l【開始時自動破壊ブロック設定】", breakLore), "abreak_block:" + a.id);

        put(40, item(Material.BARRIER, "&4&l【このアリーナを完全削除】", List.of("&7アリーナ登録を完全に抹消します")), "adelete:" + a.id);
        put(49, item(Material.ARROW, "&fアリーナ一覧に戻る", List.of()), "page:ARENAS");
    }

    private void renderArenaTimingSettings() {
        if (targetId == null) { renderArenas(); return; }
        Arena a = plugin.arenas().get(targetId);
        if (a == null) { renderArenas(); return; }

        boolean custom = a.customTimingEnabled;

        put(10, item(custom ? Material.REPEATER : Material.LEVER,
                custom ? "&a&l【個別カスタム時間: 有効中 (ON)】" : "&7&l【個別カスタム時間: 無効中 (OFF)】",
                List.of("&7有効にすると、大元設定を上書きして", "&7このアリーナ独自の時間設定が適用されます", "", "&e[クリック] 切り替え", "&d[Qキー] OFF にリセット"), custom), "atog_custom:" + a.id);

        put(12, item(Material.IRON_BARS,
                "&e開始時ブロック破壊までの秒数: &6" + a.breakDelaySeconds + "秒",
                List.of("&7試合開始から何秒後に指定ブロックを壊すか設定", "&7(0秒 = 試合開始と同時に即座に破壊)", "", "&7左クリック: +1秒 / 右クリック: -1秒", "&d[Qキー] 0秒にリセット"), custom && a.breakDelaySeconds > 0), "atime_break:" + a.id);

        put(14, item(Material.CHEST,
                "&e破壊後アイテム配布までの秒数: &6" + a.customGraceSeconds + "秒",
                List.of("&7ブロック破壊後から装備配布＆PvP開始までの猶予時間", "", "&7左クリック: +1秒 / 右クリック: -1秒", "&d[Qキー] 5秒(初期値)にリセット"), custom), "atime_grace:" + a.id);

        put(16, item(Material.CLOCK,
                "&eアリーナ個別 試合時間: &6" + (a.customDurationSeconds > 0 ? a.customDurationSeconds + "秒" : "自動(モード準拠)"),
                List.of("&70秒の場合はモード設定が使われます", "", "&7左クリック: +30秒 / 右クリック: -30秒", "&d[Qキー] リセット"), custom && a.customDurationSeconds > 0), "atime_duration:" + a.id);

        put(28, item(Material.WHITE_WOOL,
                "&e設置ブロック崩壊秒数: &6" + (a.customBlockDecaySeconds > 0 ? a.customBlockDecaySeconds + "秒" : "自動(モード準拠)"),
                List.of("&70秒の場合はモード設定が使われます", "", "&7左クリック: +1秒 / 右クリック: -1秒", "&d[Qキー] リセット"), custom && a.customBlockDecaySeconds > 0), "atime_decay:" + a.id);

        put(49, item(Material.ARROW, "&fアリーナ設定に戻る", List.of()), "page:ARENA_SETTINGS:" + a.id);
    }

    private void renderLobbySettings() {
        List<Num> lobbyNums = List.of(
                new Num("lobby.min-players", "最低開始人数", Material.PLAYER_HEAD, 1, 1, 16, true, 2),
                new Num("lobby.max-players", "最大参加人数", Material.PLAYER_HEAD, 1, 1, 16, true, 16),
                new Num("lobby.countdown-seconds", "開始カウントダウン(秒)", Material.CLOCK, 5, 5, 300, true, 30),
                new Num("lobby.full-countdown-seconds", "満員時カウントダウン(秒)", Material.CLOCK, 5, 3, 120, true, 10),
                new Num("lobby.return-delay-seconds", "ロビー転送までの秒数", Material.ENDER_PEARL, 1, 1, 30, true, 5)
        );

        int slot = 11;
        for (Num n : lobbyNums) {
            put(slot++, item(n.icon(), "&e" + n.label(), List.of(
                    "&7現在: &a" + plugin.getConfig().getInt(n.path(), (int)n.def()), "",
                    "&7左クリック: &f+" + (int)n.step(), "&7右クリック: &f-" + (int)n.step(), "&7Shift: &f×5",
                    "&d[Qキー] 初期値(" + (int)n.def() + ")に戻す"
            )), "lnum:" + n.path() + ":" + n.step() + ":" + n.min() + ":" + n.max() + ":" + n.def());
        }

        List<Toggle> lobbyToggles = List.of(
                new Toggle("lobby.start-mode", "試合開始モード", Material.REDSTONE_TORCH, new String[]{"AUTO", "MANUAL"}, "AUTO"),
                new Toggle("lobby.auto-start", "人数が揃ったら自動開始", Material.REPEATER, new String[]{"true", "false"}, "true"),
                new Toggle("lobby.mode-select", "モード選出方式", Material.COMPARATOR, new String[]{"VOTE", "RANDOM"}, "VOTE"),
                new Toggle("scoreboard.extra-lines", "スコアボード詳細行", Material.OAK_SIGN, new String[]{"true", "false"}, "true"),
                new Toggle("podium.type", "表彰台の種類", Material.ARMOR_STAND, new String[]{"MANNEQUIN", "ARMOR_STAND"}, "MANNEQUIN")
        );

        slot = 20;
        for (Toggle t : lobbyToggles) {
            String val = plugin.getConfig().getString(t.path(), t.def());
            put(slot++, item(t.icon(), "&b" + t.label(), List.of("&7現在: &a" + val, "", "&7クリックで切り替え", "&d[Qキー] リセット")), "ltog:" + t.path() + ":" + t.def());
        }

        boolean voteItems = VoteMenu.isVoteItemsEnabled();
        put(31, item(voteItems ? Material.LIME_DYE : Material.GRAY_DYE,
                voteItems ? "&a【投票アイテム配布: 有効 (配布中)】" : "&c【投票アイテム配布: 無効 (停止中)】",
                List.of("&7クリックで切り替え")), "toggle_vote_items");

        put(49, item(Material.ARROW, "&fメインメニューに戻る", List.of()), "page:MAIN");
    }

    private void renderLocations() {
        put(20, item(Material.RED_BED, "&a&lロビー地点を現在地に設定", List.of("&7参加時や試合終了後の転送先")), "loc:setlobby");
        put(22, item(Material.OAK_SIGN, "&a&lランキングホログラムを現在地に設定", List.of("&7現在立っている足元の上に表示")), "loc:sethologram");
        put(24, item(Material.GOLD_BLOCK, "&6&l表彰台 1位 を現在地に設定", List.of("&7優勝者のスタンド位置")), "loc:setpodium:1");
        put(32, item(Material.IRON_BLOCK, "&f&l表彰台 2位 を現在地に設定", List.of("&72位のスタンド位置")), "loc:setpodium:2");
        put(34, item(Material.COPPER_BLOCK, "&c&l表彰台 3位 を現在地に設定", List.of("&73位のスタンド位置")), "loc:setpodium:3");

        put(49, item(Material.ARROW, "&fメインメニューに戻る", List.of()), "page:MAIN");
    }

    private void renderPointsSettings() {
        List<Num> ptsNums = List.of(
                new Num("points.kill", "キル獲得point", Material.IRON_SWORD, 5, 0, 500, true, 30),
                new Num("points.survival", "生存point (1回あたり)", Material.GOLDEN_APPLE, 1, 0, 50, true, 1),
                new Num("points.survival-interval-seconds", "生存point付与間隔(秒)", Material.CLOCK, 1, 1, 60, true, 5)
        );

        int slot = 21;
        for (Num n : ptsNums) {
            put(slot++, item(n.icon(), "&e" + n.label(), List.of(
                    "&7現在: &a" + plugin.getConfig().getInt(n.path(), (int)n.def()) + " pt", "",
                    "&7左クリック: &f+" + (int)n.step(), "&7右クリック: &f-" + (int)n.step(), "&7Shift: &f×5",
                    "&d[Qキー] リセット"
            )), "pnum:" + n.path() + ":" + n.step() + ":" + n.min() + ":" + n.max() + ":" + n.def());
        }

        put(49, item(Material.ARROW, "&fメインメニューに戻る", List.of()), "page:MAIN");
    }

    public void click(Player p, int slot, ClickType type) {
        String act = actions.get(slot);
        if (act == null) return;
        Sfx.click(p);

        boolean isDrop = (type == ClickType.DROP || type == ClickType.CONTROL_DROP);
        boolean left = type.isLeftClick();
        boolean shift = type.isShiftClick();

        if (act.startsWith("page:")) {
            String[] pParts = act.substring(5).split(":");
            Page targetPage = Page.valueOf(pParts[0]);
            String tId = pParts.length > 1 ? pParts[1] : null;
            open(plugin, p, targetPage, tId);
            return;
        }

        if (act.startsWith("mnum:") || act.startsWith("lnum:") || act.startsWith("pnum:")) {
            String[] parts = act.split(":");
            String path = parts[1];
            double step = Double.parseDouble(parts[2]);
            double min = Double.parseDouble(parts[3]);
            double max = Double.parseDouble(parts[4]);
            boolean isInt = parts.length > 5 && Boolean.parseBoolean(parts[5]);
            double def = Double.parseDouble(parts[parts.length - 1]);

            if (isDrop) {
                if (isInt) plugin.getConfig().set(path, (int) def);
                else plugin.getConfig().set(path, def);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            } else {
                double delta = step * (shift ? 5 : 1) * (left ? 1 : -1);
                if (isInt) {
                    int cur = plugin.getConfig().getInt(path, (int) def);
                    plugin.getConfig().set(path, (int) Math.max(min, Math.min(max, cur + delta)));
                } else {
                    double cur = plugin.getConfig().getDouble(path, def);
                    plugin.getConfig().set(path, Math.round(Math.max(min, Math.min(max, cur + delta)) * 10) / 10.0);
                }
            }
            plugin.saveConfig();
            render();
            return;
        }

        if (act.startsWith("mtog:") || act.startsWith("ltog:")) {
            String[] parts = act.split(":");
            String path = parts[1];
            String def = parts[2];

            if (isDrop) {
                if (def.equals("true") || def.equals("false")) plugin.getConfig().set(path, Boolean.parseBoolean(def));
                else plugin.getConfig().set(path, def);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            } else {
                String cur = plugin.getConfig().getString(path, def);
                if (cur.equalsIgnoreCase("true") || cur.equalsIgnoreCase("false")) {
                    plugin.getConfig().set(path, !Boolean.parseBoolean(cur));
                } else if (path.equals("lobby.start-mode")) {
                    plugin.getConfig().set(path, cur.equalsIgnoreCase("AUTO") ? "MANUAL" : "AUTO");
                } else if (path.equals("lobby.mode-select")) {
                    plugin.getConfig().set(path, cur.equalsIgnoreCase("VOTE") ? "RANDOM" : "VOTE");
                } else if (path.equals("podium.type")) {
                    plugin.getConfig().set(path, cur.equalsIgnoreCase("MANNEQUIN") ? "ARMOR_STAND" : "MANNEQUIN");
                }
            }
            plugin.saveConfig();
            render();
            return;
        }

        if (act.startsWith("mode_sel:")) {
            open(plugin, p, Page.MODE_SETTINGS, act.substring(9));
            return;
        }

        if (act.startsWith("arena_select:")) {
            String aId = act.substring(13);
            if (!left) {
                Arena a = plugin.arenas().get(aId);
                if (a != null && a.center() != null) {
                    p.teleport(a.center().add(0, 1, 0));
                    Sfx.teleport(p);
                }
            } else {
                open(plugin, p, Page.ARENA_SETTINGS, aId);
            }
            return;
        }

        if (act.startsWith("atp:")) {
            Arena a = plugin.arenas().get(act.substring(4));
            if (a != null && a.center() != null) {
                p.teleport(a.center().add(0, 1, 0));
                Sfx.teleport(p);
            }
            return;
        }
        if (act.startsWith("atog_enabled:")) {
            Arena a = plugin.arenas().get(act.substring(13));
            if (a != null) {
                if (!a.enabled && a.spawns.isEmpty()) {
                    Msg.send(p, "&cスポーン地点がありません");
                    Sfx.deny(p);
                    return;
                }
                a.enabled = !a.enabled;
                plugin.arenas().save();
                Msg.send(p, a.enabled ? "&aアリーナを有効にしました" : "&6アリーナを編集中にしました");
                render();
            }
            return;
        }

        if (act.startsWith("acreate_child:")) {
            String pId = act.substring(14);
            int idx = 1;
            while (plugin.arenas().get(pId + "_c" + idx) != null) idx++;
            String childId = pId + "_c" + idx;

            Arena child = plugin.arenas().createChild(pId, childId);
            if (child != null) {
                Location l = p.getLocation();
                child.worldName = l.getWorld().getName();
                child.cx = l.getBlockX(); child.cy = l.getBlockY(); child.cz = l.getBlockZ();
                child.enabled = false;
                plugin.arenas().save();
                Msg.send(p, "&a親 &e" + pId + " &aの下位に子アリーナ &b" + childId + " &aを作成しました！");
                open(plugin, p, Page.ARENA_SETTINGS, childId);
            }
            return;
        }

        if (act.startsWith("atog_custom:")) {
            Arena a = plugin.arenas().get(act.substring(12));
            if (a != null) {
                a.customTimingEnabled = !isDrop && !a.customTimingEnabled;
                plugin.arenas().save();
                render();
            }
            return;
        }
        if (act.startsWith("atime_break:")) {
            Arena a = plugin.arenas().get(act.substring(12));
            if (a != null) {
                if (isDrop) a.breakDelaySeconds = 0;
                else {
                    int d = (shift ? 5 : 1) * (left ? 1 : -1);
                    a.breakDelaySeconds = Math.max(0, Math.min(60, a.breakDelaySeconds + d));
                }
                plugin.arenas().save();
                render();
            }
            return;
        }
        if (act.startsWith("atime_grace:")) {
            Arena a = plugin.arenas().get(act.substring(12));
            if (a != null) {
                if (isDrop) a.customGraceSeconds = 5;
                else {
                    int d = (shift ? 5 : 1) * (left ? 1 : -1);
                    a.customGraceSeconds = Math.max(0, Math.min(60, a.customGraceSeconds + d));
                }
                plugin.arenas().save();
                render();
            }
            return;
        }
        if (act.startsWith("atime_duration:")) {
            Arena a = plugin.arenas().get(act.substring(15));
            if (a != null) {
                if (isDrop) a.customDurationSeconds = 0;
                else {
                    int d = 30 * (shift ? 5 : 1) * (left ? 1 : -1);
                    a.customDurationSeconds = Math.max(0, Math.min(900, a.customDurationSeconds + d));
                }
                plugin.arenas().save();
                render();
            }
            return;
        }
        if (act.startsWith("atime_decay:")) {
            Arena a = plugin.arenas().get(act.substring(12));
            if (a != null) {
                if (isDrop) a.customBlockDecaySeconds = 0;
                else {
                    int d = (shift ? 5 : 1) * (left ? 1 : -1);
                    a.customBlockDecaySeconds = Math.max(0, Math.min(60, a.customBlockDecaySeconds + d));
                }
                plugin.arenas().save();
                render();
            }
            return;
        }

        if (act.startsWith("ateams:")) {
            Arena a = plugin.arenas().get(act.substring(7));
            if (a != null) {
                if (isDrop) a.maxTeams = 0;
                else a.maxTeams = Math.max(0, a.maxTeams + (left ? 1 : -1));
                plugin.arenas().save();
                render();
            }
            return;
        }
        if (act.startsWith("ateamsize:")) {
            Arena a = plugin.arenas().get(act.substring(10));
            if (a != null) {
                if (isDrop) a.teamSize = 0;
                else a.teamSize = Math.max(0, a.teamSize + (left ? 1 : -1));
                plugin.arenas().save();
                render();
            }
            return;
        }
        if (act.startsWith("aaddspawn:")) {
            Arena a = plugin.arenas().get(act.substring(10));
            if (a != null) {
                a.spawns.add(Pos.of(p.getLocation()));
                plugin.arenas().save();
                plugin.maps().storeSpawns(a);
                Msg.send(p, "&a現在地にチーム " + a.spawns.size() + " のスポーン地点を登録しました！");
                Sfx.success(p);
                render();
            }
            return;
        }
        if (act.startsWith("aclearspawns:")) {
            Arena a = plugin.arenas().get(act.substring(13));
            if (a != null) {
                a.spawns.clear();
                plugin.arenas().save();
                plugin.maps().storeSpawns(a);
                Msg.send(p, "&eスポーン地点を全消去しました");
                Sfx.deny(p);
                render();
            }
            return;
        }
        if (act.startsWith("arebuild:")) {
            Arena a = plugin.arenas().get(act.substring(9));
            if (a != null) {
                Location l = p.getLocation();
                a.worldName = l.getWorld().getName();
                a.cx = l.getBlockX(); a.cy = l.getBlockY(); a.cz = l.getBlockZ();
                ArenaBuilder.build(a);
                a.map = null;
                a.enabled = false;
                plugin.arenas().save();
                Msg.send(p, "&a現在地を中心にアリーナマップを再生成しました (編集中)");
                Sfx.success(p);
                render();
            }
            return;
        }
        if (act.startsWith("adelete:")) {
            String aId = act.substring(8);
            plugin.arenas().delete(aId);
            plugin.arenas().save();
            Msg.send(p, "&cアリーナ " + aId + " を削除しました");
            open(plugin, p, Page.ARENAS);
            return;
        }
        if (act.startsWith("abreak_block:")) {
            Arena a = plugin.arenas().get(act.substring(13));
            if (a != null) {
                if (isDrop) {
                    a.clearBreakBlocks();
                    plugin.arenas().save();
                    Msg.send(p, "&e開始時自動破壊ブロックを全消去しました");
                    Sfx.deny(p);
                } else {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (hand.getType().isAir() || !hand.getType().isBlock()) {
                        Msg.send(p, "&c設定したいブロックを手にもってクリックしてください");
                        Sfx.deny(p);
                        return;
                    }
                    if (left) {
                        a.addBreakBlock(hand.getType());
                        plugin.arenas().save();
                        Msg.send(p, "&a自動破壊ブロックに &b" + hand.getType().name() + " &aを追加しました！");
                        Sfx.success(p);
                    } else {
                        if (a.removeBreakBlock(hand.getType())) {
                            plugin.arenas().save();
                            Msg.send(p, "&e自動破壊ブロックから &b" + hand.getType().name() + " &eを解除しました");
                            Sfx.success(p);
                        } else {
                            Msg.send(p, "&cそのブロックは登録されていません");
                        }
                    }
                }
                render();
            }
            return;
        }

        if (act.startsWith("loc:")) {
            String locAct = act.substring(4);
            if (locAct.equals("setlobby")) {
                plugin.setLobbyLocation(p.getLocation());
                Msg.send(p, "&aロビー地点を設定しました");
            } else if (locAct.equals("sethologram")) {
                plugin.setHologramLocation(p.getLocation().add(0, 2.5, 0));
                plugin.holograms().refresh();
                Msg.send(p, "&aランキングホログラムを設定しました");
            } else if (locAct.startsWith("setpodium:")) {
                int r = Integer.parseInt(locAct.substring(10));
                plugin.setPodiumLocation(r, p.getLocation());
                Msg.send(p, "&a表彰台 " + r + "位 を設定しました");
            }
            Sfx.success(p);
            return;
        }

        switch (act) {
            case "open_kitgui" -> KitGui.openList(plugin, p);
            case "open_perm_gui" -> {
                if (p.isOp()) KitPermGui.open(plugin, p);
                else Msg.send(p, "&cこの機能はOP権限が必要です");
            }
            case "toggle_vote_items" -> {
                boolean next = !VoteMenu.isVoteItemsEnabled();
                VoteMenu.setVoteItemsEnabled(plugin, next);
                render();
            }
            case "match_control" -> {
                if (left) {
                    p.closeInventory();
                    if (!plugin.lobby().forceStart(null)) Msg.send(p, "&c開始できません (試合中 または 人数不足)");
                    else Msg.send(p, "&a試合を開始します！");
                } else {
                    if (plugin.match() != null) {
                        plugin.match().abort();
                        Msg.send(p, "&e試合を中止しました");
                    } else {
                        Msg.send(p, "&7進行中の試合はありません");
                    }
                }
            }
            case "newarena" -> {
                int i = 1;
                while (plugin.arenas().get("randomizer" + i) != null) i++;
                Arena a = plugin.arenas().create("randomizer" + i, "randomizer");
                Location l = p.getLocation();
                a.worldName = l.getWorld().getName();
                a.cx = l.getBlockX(); a.cy = l.getBlockY(); a.cz = l.getBlockZ();
                ArenaBuilder.build(a);
                a.map = null;
                a.enabled = false;
                plugin.arenas().save();
                p.teleport(a.center().add(0, 1, 0));
                Msg.send(p, "&aアリーナ &e" + a.id + " &aを生成しました (スポーン16個)");
                Sfx.success(p);
                open(plugin, p, Page.ARENA_SETTINGS, a.id);
            }
            case "reload" -> {
                plugin.reloadAll();
                Msg.send(p, "&a全設定・データを再読み込みしました");
                render();
            }
            case "close" -> p.closeInventory();
        }
    }
}