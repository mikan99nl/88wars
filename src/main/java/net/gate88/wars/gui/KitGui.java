package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
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
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class KitGui implements InventoryHolder {
    private final WarsPlugin plugin;
    private final Inventory inventory;
    private final Mode mode;
    private final String selectedKitId;
    private int page = 0;
    private String filterCategory = "ALL"; // ALL または 特定カテゴリ名

    public enum Mode { LIST, DETAIL, EFFECTS, SELECT_ICON, MANAGE_CATEGORIES }

    private static final List<PotionEffectType> AVAILABLE_EFFECTS = List.of(
            PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.RESISTANCE,
            PotionEffectType.REGENERATION, PotionEffectType.JUMP_BOOST, PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.ABSORPTION, PotionEffectType.NIGHT_VISION, PotionEffectType.INVISIBILITY,
            PotionEffectType.GLOWING, PotionEffectType.SLOW_FALLING, PotionEffectType.HASTE
    );

    private KitGui(WarsPlugin plugin, Mode mode, String selectedKitId, int page, String filterCategory) {
        this.plugin = plugin;
        this.mode = mode;
        this.selectedKitId = selectedKitId;
        this.page = page;
        this.filterCategory = filterCategory != null ? filterCategory : "ALL";

        String titleStr = switch (mode) {
            case LIST -> "&8[Kit一覧] &7Page " + (page + 1);
            case DETAIL -> "&8[Kit編集: &e" + selectedKitId + "&8]";
            case EFFECTS -> "&8[ポーション設定: &d" + selectedKitId + "&8]";
            case SELECT_ICON -> "&8[アイコン選択: &6" + selectedKitId + "&8]";
            case MANAGE_CATEGORIES -> "&8[カテゴリ管理]";
        };
        this.inventory = Bukkit.createInventory(this, (mode == Mode.DETAIL || mode == Mode.EFFECTS) ? 36 : 54, Msg.c(titleStr));
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public static void openList(WarsPlugin plugin, Player player) {
        openList(plugin, player, 0, "ALL");
    }

    public static void openList(WarsPlugin plugin, Player player, int page, String category) {
        KitGui gui = new KitGui(plugin, Mode.LIST, null, page, category);
        gui.renderList(player);
        player.openInventory(gui.getInventory());
    }

    public static void openDetail(WarsPlugin plugin, Player player, String kitId) {
        KitGui gui = new KitGui(plugin, Mode.DETAIL, kitId, 0, "ALL");
        gui.renderDetail();
        player.openInventory(gui.getInventory());
    }

    public static void openEffects(WarsPlugin plugin, Player player, String kitId) {
        KitGui gui = new KitGui(plugin, Mode.EFFECTS, kitId, 0, "ALL");
        gui.renderEffects();
        player.openInventory(gui.getInventory());
    }

    public static void openSelectIcon(WarsPlugin plugin, Player player, String kitId) {
        KitGui gui = new KitGui(plugin, Mode.SELECT_ICON, kitId, 0, "ALL");
        gui.renderSelectIcon();
        player.openInventory(gui.getInventory());
    }

    // ------------------------------------------------ ① 一覧表示 (LIST - ページネーション & カテゴリ絞り込み & お気に入り)
    private void renderList(Player viewer) {
        inventory.clear();
        List<String> allKits = plugin.kits().getKitNames();
        List<String> filteredKits = new ArrayList<>();

        for (String id : allKits) {
            if ("ALL".equalsIgnoreCase(filterCategory) || filterCategory.equalsIgnoreCase(plugin.kits().getKitCategory(id))) {
                filteredKits.add(id);
            }
        }

        int pageSize = 45;
        int maxPages = Math.max(1, (int) Math.ceil((double) filteredKits.size() / pageSize));
        if (page >= maxPages) page = maxPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, filteredKits.size());
        String forced = plugin.kits().getForcedKit();

        for (int i = start; i < end; i++) {
            String id = filteredKits.get(i);
            boolean enabled = plugin.kits().isEnabled(id);
            boolean isForced = id.equalsIgnoreCase(forced);
            boolean isFav = plugin.kits().isFavorite(viewer.getUniqueId(), id);
            boolean isNew = plugin.kits().getPlayCount(id) == 0;
            String category = plugin.kits().getKitCategory(id);
            String creator = plugin.kits().getKitCreator(id);

            ItemStack icon = plugin.kits().getKitIcon(id);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                String prefix = isFav ? "&e⭐ " : (isForced ? "&6★ " : "");
                meta.displayName(Msg.c(prefix + (enabled ? "&f" : "&8") + plugin.kits().getKitDisplayName(id) + " &7(" + id + ")"));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.c(enabled ? "&a● 有効 (ON)" : "&c● 無効 (OFF)"));
                lore.add(Msg.c("&7カテゴリ: &b" + category));
                lore.add(Msg.c("&7作成者: &3" + creator));

                if (isNew) {
                    lore.add(Msg.c("&d✨ 新着・未プレイKit!"));
                }
                if (isFav) {
                    lore.add(Msg.c("&e⭐ お気に入り登録中"));
                }
                if (isForced) {
                    lore.add(Msg.c("&6★ 指定キット (次回確定)"));
                }

                lore.add(Msg.c(""));
                lore.add(Msg.c("&e[左クリック] &f詳細・ポーション・アイコン編集"));
                lore.add(Msg.c("&b[右クリック] &fON / OFF 切り替え"));
                lore.add(Msg.c("&6[Shift＋右クリック] &f次回確定指定 / 解除"));
                lore.add(Msg.c("&e[Shift＋左クリック] &fお気に入り ⭐ 登録 / 解除"));

                meta.lore(lore);
                // ★ 未プレイKitまたは確定指定Kitは光沢(Glint)を付与
                if (isNew || isForced) {
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                icon.setItemMeta(meta);
            }
            inventory.setItem(i - start, icon);
        }

        // 下部バーメニュー
        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "&a◀ 前のページへ", List.of("&7Page " + page + " に戻る")));
        }

        // カテゴリ絞り込みボタン
        inventory.setItem(46, createItem(Material.HOPPER, "&b&lカテゴリ絞り込み: &e" + filterCategory,
                List.of("&7クリックでカテゴリを順次切り替えます")));

        // お気に入り限定モード切り替えボタン
        boolean favOnly = plugin.kits().isFavoriteOnly(viewer.getUniqueId());
        inventory.setItem(47, createItem(favOnly ? Material.NETHER_STAR : Material.FIREWORK_STAR,
                favOnly ? "&e&l【お気に入り限定: ON】" : "&7&l【お気に入り限定: OFF】",
                List.of("&7ONにすると、⭐お気に入りKitの中からのみ抽選されます")));

        // 試合後Kit継続設定ボタン
        boolean keep = plugin.kits().isKeepKit(viewer.getUniqueId());
        inventory.setItem(48, createItem(keep ? Material.REPEATER : Material.REDSTONE_TORCH,
                keep ? "&a&l【試合後Kit継続: ON】" : "&7&l【試合後Kit継続: 1回切り】",
                List.of("&7ONにすると、試合後も指定Kitがリセットされず維持されます")));

        // 指定キット解除
        if (forced != null) {
            inventory.setItem(49, createItem(Material.BARRIER, "&c指定キットを解除 (ランダムに戻す)", List.of("&7現在指定中: &e" + forced)));
        } else {
            inventory.setItem(49, createItem(Material.ENDER_EYE, "&a現在: 通常ランダム選択中", List.of("&7試合開始時に有効なKitから抽選されます")));
        }

        // ゲーム開始ボタン (OP限定)
        if (viewer.isOp()) {
            inventory.setItem(51, createItem(Material.EMERALD_BLOCK, "&a&l【ゲームを開始する】", List.of("&7クリックで即座に試合を開始します")));
        }

        if (page < maxPages - 1) {
            inventory.setItem(53, createItem(Material.ARROW, "&a次のページへ ▶", List.of("&7Page " + (page + 2) + " に進む")));
        }
    }

    // ------------------------------------------------ ② 詳細編集 (DETAIL)
    private void renderDetail() {
        inventory.clear();
        String id = selectedKitId;
        boolean enabled = plugin.kits().isEnabled(id);
        String category = plugin.kits().getKitCategory(id);
        String creator = plugin.kits().getKitCreator(id);

        ItemStack icon = plugin.kits().getKitIcon(id);
        ItemMeta iconMeta = icon.getItemMeta();
        if (iconMeta != null) {
            iconMeta.displayName(Msg.c("&e" + plugin.kits().getKitDisplayName(id) + " &7(" + id + ")"));
            iconMeta.lore(List.of(
                    Msg.c("&7カテゴリ: &b" + category),
                    Msg.c("&7作成者: &3" + creator),
                    Msg.c("&7プレイ回数: &f" + plugin.kits().getPlayCount(id) + "回")
            ));
            icon.setItemMeta(iconMeta);
        }
        inventory.setItem(4, icon);

        inventory.setItem(10, createItem(Material.CHEST, "&a【装備を受け取る】", List.of("&7インベントリにこのキットを展開します")));
        inventory.setItem(11, createItem(Material.WRITABLE_BOOK, "&e【手持ちで上書き保存】", List.of("&7手持ちの装備でこのKitを上書き更新します")));
        inventory.setItem(12, createItem(Material.ITEM_FRAME, "&6&l【アイコン変更】", List.of("&7Kitに含まれるアイテムからアイコンを選択します")));

        inventory.setItem(14, createItem(Material.BREWING_STAND, "&d&l【ポーション効果設定】", List.of("&7最初に付与されるポーション効果と時間を設定")));
        inventory.setItem(15, createItem(Material.NAME_TAG, "&b&lカテゴリ変更: &e" + category, List.of("&7クリックで次のカテゴリに変更")));

        inventory.setItem(16, createItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&a【有効中 (ON)】" : "&7【無効中 (OFF)】", List.of("&7クリックで選出対象を切り替えます")));

        inventory.setItem(22, createItem(Material.LAVA_BUCKET, "&c【このキットを削除】", List.of("&7完全に削除します")));
        inventory.setItem(31, createItem(Material.ARROW, "&7一覧に戻る", List.of()));
    }

    // ------------------------------------------------ ③ アイコン選定画面 (SELECT_ICON)
    private void renderSelectIcon() {
        inventory.clear();
        List<ItemStack> contents = plugin.kits().getKitContents(selectedKitId);

        for (int i = 0; i < Math.min(contents.size(), 45); i++) {
            ItemStack it = contents.get(i).clone();
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.c("&aこのアイテムをアイコンにする"));
                it.setItemMeta(meta);
            }
            inventory.setItem(i, it);
        }
        inventory.setItem(49, createItem(Material.ARROW, "&7Kit詳細に戻る", List.of()));
    }

    // ------------------------------------------------ ④ ポーション設定 (EFFECTS)
    private void renderEffects() {
        inventory.clear();
        String id = selectedKitId;

        for (int i = 0; i < AVAILABLE_EFFECTS.size(); i++) {
            PotionEffectType type = AVAILABLE_EFFECTS.get(i);
            int level = plugin.kits().getEffectLevel(id, type);
            int durationSec = plugin.kits().getEffectDuration(id, type);
            int defaultSec = plugin.kits().getDefaultVanillaDuration(type, Math.max(1, level));

            Material mat = getEffectMaterial(type);
            String statusStr = switch (level) {
                case 1 -> "&a【Lv 1 有効中】";
                case 2 -> "&6【Lv 2 有効中】";
                default -> "&7【無効 (OFF)】";
            };

            List<String> lore = new ArrayList<>();
            lore.add(statusStr);
            if (level > 0) {
                lore.add("&f効果時間: &b" + formatDuration(durationSec) + (durationSec == defaultSec ? " &7(バニラ延長)" : ""));
            }
            lore.add("");
            lore.add("&e[左クリック] &fレベル変更 (OFF → Lv1[延長] → Lv2 → OFF)");
            if (level > 0) {
                lore.add("&b[右クリック] &f時間を延長 (+30秒)");
                lore.add("&3[Shift＋クリック] &f時間を短縮 (-30秒)");
                lore.add("&d[Qキー] &f初期設定に戻す (バニラ延長時間)");
            }

            inventory.setItem(i, createItem(mat, "&f" + getEffectNameJp(type), lore));
        }

        inventory.setItem(31, createItem(Material.ARROW, "&7Kit詳細に戻る", List.of()));
    }

    // ------------------------------------------------ クリック処理
    public void click(Player p, int slot, ClickType click) {
        switch (mode) {
            case LIST -> handleListClick(p, slot, click);
            case DETAIL -> handleDetailClick(p, slot, click);
            case SELECT_ICON -> handleSelectIconClick(p, slot);
            case EFFECTS -> handleEffectsClick(p, slot, click);
            default -> {}
        }
    }

    private void handleListClick(Player p, int slot, ClickType click) {
        List<String> allKits = plugin.kits().getKitNames();
        List<String> filteredKits = new ArrayList<>();
        for (String id : allKits) {
            if ("ALL".equalsIgnoreCase(filterCategory) || filterCategory.equalsIgnoreCase(plugin.kits().getKitCategory(id))) {
                filteredKits.add(id);
            }
        }

        int pageSize = 45;
        int maxPages = Math.max(1, (int) Math.ceil((double) filteredKits.size() / pageSize));
        int index = page * pageSize + slot;

        if (slot >= 0 && slot < 45) {
            if (index >= filteredKits.size()) return;
            String kitId = filteredKits.get(index);

            // Shift＋左クリック: お気に入りトグル
            if (click.isShiftClick() && click.isLeftClick()) {
                plugin.kits().toggleFavorite(p.getUniqueId(), kitId);
                boolean fav = plugin.kits().isFavorite(p.getUniqueId(), kitId);
                Msg.send(p, "&e" + kitId + " &fをお気に入り" + (fav ? "&aに追加 ⭐" : "&cから解除") + "しました");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
                renderList(p);
                return;
            }

            // Shift＋右クリック: 指定キット
            if (click.isShiftClick() && click.isRightClick()) {
                if (kitId.equalsIgnoreCase(plugin.kits().getForcedKit())) {
                    plugin.kits().clearForcedKit();
                    Msg.send(p, "&e指定を解除しました (ランダムに戻ります)");
                } else {
                    plugin.kits().setForcedKit(kitId);
                    Msg.send(p, "&a次回のKitを &6[" + kitId + "] &aに指定しました");
                }
                Sfx.success(p);
                renderList(p);
                return;
            }

            // 右クリック: ON/OFF
            if (click.isRightClick()) {
                if (!p.isOp() && !p.hasPermission("wars.admin")) {
                    Msg.send(p, "&cON/OFF切り替えは管理者のみ実行できます");
                    return;
                }
                boolean cur = plugin.kits().isEnabled(kitId);
                plugin.kits().setEnabled(kitId, !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                renderList(p);
                return;
            }

            // 左クリック: 詳細へ
            openDetail(plugin, p, kitId);
        } else if (slot == 45 && page > 0) {
            page--;
            renderList(p);
        } else if (slot == 46) {
            // カテゴリ切り替え
            List<String> cats = new ArrayList<>(plugin.kits().getCategories());
            cats.add(0, "ALL");
            int cIdx = cats.indexOf(filterCategory);
            filterCategory = cats.get((cIdx + 1) % cats.size());
            page = 0;
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            renderList(p);
        } else if (slot == 47) {
            // お気に入り限定モードトグル
            boolean cur = plugin.kits().isFavoriteOnly(p.getUniqueId());
            plugin.kits().setFavoriteOnly(p.getUniqueId(), !cur);
            Msg.send(p, "&eお気に入り限定モード: " + (!cur ? "&a有効 (ON)" : "&c無効 (OFF)"));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            renderList(p);
        } else if (slot == 48) {
            // Kit継続フラグトグル
            boolean cur = plugin.kits().isKeepKit(p.getUniqueId());
            plugin.kits().setKeepKit(p.getUniqueId(), !cur);
            Msg.send(p, "&e試合後Kit継続設定: " + (!cur ? "&a継続する (ON)" : "&71回切り (OFF)"));
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            renderList(p);
        } else if (slot == 49) {
            plugin.kits().clearForcedKit();
            Msg.send(p, "&eキット指定を解除しました");
            renderList(p);
        } else if (slot == 51 && p.isOp()) {
            p.closeInventory();
            if (!plugin.lobby().forceStart(null)) Msg.send(p, "&c開始できません (試合中または人数不足)");
            else Msg.send(p, "&a試合を開始します！");
        } else if (slot == 53 && page < maxPages - 1) {
            page++;
            renderList(p);
        }
    }

    private void handleDetailClick(Player p, int slot, ClickType click) {
        String id = selectedKitId;
        switch (slot) {
            case 10 -> {
                plugin.kits().applyKit(p, id);
                Msg.send(p, "&aキット &e" + id + " &aの装備を受け取りました");
                Sfx.gearGive(p);
            }
            case 11 -> {
                if (!p.isOp() && !p.hasPermission("wars.admin")) {
                    Msg.send(p, "&c上書き保存は管理者のみ実行できます");
                    return;
                }
                plugin.kits().createKit(p, id);
                Msg.send(p, "&a現在のインベントリでキット &e" + id + " &aを上書き保存しました！");
                Sfx.success(p);
                renderDetail();
            }
            case 12 -> openSelectIcon(plugin, p, id);
            case 14 -> openEffects(plugin, p, id);
            case 15 -> {
                List<String> cats = plugin.kits().getCategories();
                String cur = plugin.kits().getKitCategory(id);
                int idx = cats.indexOf(cur);
                String next = cats.get((idx + 1) % cats.size());
                plugin.kits().setKitCategory(id, next);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                renderDetail();
            }
            case 16 -> {
                if (!p.isOp() && !p.hasPermission("wars.admin")) return;
                boolean cur = plugin.kits().isEnabled(id);
                plugin.kits().setEnabled(id, !cur);
                renderDetail();
            }
            case 22 -> {
                if (!p.isOp() && !p.hasPermission("wars.admin")) return;
                plugin.kits().deleteKit(id);
                Msg.send(p, "&cキット " + id + " を削除しました");
                openList(plugin, p);
            }
            case 31 -> openList(plugin, p, page, filterCategory);
        }
    }

    private void handleSelectIconClick(Player p, int slot) {
        List<ItemStack> contents = plugin.kits().getKitContents(selectedKitId);
        if (slot >= 0 && slot < contents.size()) {
            ItemStack chosen = contents.get(slot);
            plugin.kits().setCustomIcon(selectedKitId, chosen.getType());
            Msg.send(p, "&aキットのアイコンを &e" + chosen.getType().name() + " &aに変更しました！");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            openDetail(plugin, p, selectedKitId);
        } else if (slot == 49) {
            openDetail(plugin, p, selectedKitId);
        }
    }

    private void handleEffectsClick(Player p, int slot, ClickType click) {
        if (slot >= 0 && slot < AVAILABLE_EFFECTS.size()) {
            PotionEffectType type = AVAILABLE_EFFECTS.get(slot);
            int currentLevel = plugin.kits().getEffectLevel(selectedKitId, type);
            int currentDuration = plugin.kits().getEffectDuration(selectedKitId, type);

            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                int defaultSec = plugin.kits().getDefaultVanillaDuration(type, 1);
                plugin.kits().setEffect(selectedKitId, type, 1, defaultSec);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            } else if (click.isShiftClick()) {
                if (currentLevel == 0) {
                    currentLevel = 1;
                    currentDuration = plugin.kits().getDefaultVanillaDuration(type, 1);
                }
                int newDuration = Math.max(10, currentDuration - 30);
                plugin.kits().setEffect(selectedKitId, type, currentLevel, newDuration);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            } else if (click.isRightClick()) {
                if (currentLevel == 0) {
                    currentLevel = 1;
                    currentDuration = plugin.kits().getDefaultVanillaDuration(type, 1);
                }
                int newDuration = Math.min(3600, currentDuration + 30);
                plugin.kits().setEffect(selectedKitId, type, currentLevel, newDuration);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
            } else {
                int nextLevel = (currentLevel + 1) % 3;
                int duration = (currentLevel == 0 && nextLevel == 1) ? plugin.kits().getDefaultVanillaDuration(type, 1) : currentDuration;
                plugin.kits().setEffect(selectedKitId, type, nextLevel, duration);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
            renderEffects();
        } else if (slot == 31) {
            openDetail(plugin, p, selectedKitId);
        }
    }

    private static String formatDuration(int seconds) {
        if (seconds < 60) return seconds + "秒";
        int m = seconds / 60, s = seconds % 60;
        return (s == 0) ? m + "分" : m + "分" + s + "秒";
    }

    private static Material getEffectMaterial(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return Material.SUGAR;
        if (type == PotionEffectType.STRENGTH) return Material.BLAZE_POWDER;
        if (type == PotionEffectType.RESISTANCE) return Material.IRON_CHESTPLATE;
        if (type == PotionEffectType.REGENERATION) return Material.GHAST_TEAR;
        if (type == PotionEffectType.JUMP_BOOST) return Material.RABBIT_FOOT;
        if (type == PotionEffectType.FIRE_RESISTANCE) return Material.MAGMA_CREAM;
        if (type == PotionEffectType.ABSORPTION) return Material.GOLDEN_APPLE;
        if (type == PotionEffectType.NIGHT_VISION) return Material.GOLDEN_CARROT;
        if (type == PotionEffectType.INVISIBILITY) return Material.FERMENTED_SPIDER_EYE;
        if (type == PotionEffectType.GLOWING) return Material.GLOWSTONE_DUST;
        if (type == PotionEffectType.SLOW_FALLING) return Material.FEATHER;
        if (type == PotionEffectType.HASTE) return Material.GOLDEN_PICKAXE;
        return Material.POTION;
    }

    private static String getEffectNameJp(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return "移動速度上昇";
        if (type == PotionEffectType.STRENGTH) return "攻撃力増加";
        if (type == PotionEffectType.RESISTANCE) return "耐性";
        if (type == PotionEffectType.REGENERATION) return "再生能力";
        if (type == PotionEffectType.JUMP_BOOST) return "跳躍力上昇";
        if (type == PotionEffectType.FIRE_RESISTANCE) return "耐火";
        if (type == PotionEffectType.ABSORPTION) return "衝撃吸収";
        if (type == PotionEffectType.NIGHT_VISION) return "暗視";
        if (type == PotionEffectType.INVISIBILITY) return "透明化";
        if (type == PotionEffectType.GLOWING) return "発光";
        if (type == PotionEffectType.SLOW_FALLING) return "低速落下";
        if (type == PotionEffectType.HASTE) return "採掘速度上昇";
        return type.getName();
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c(name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.c(s));
            meta.lore(l);
            item.setItemMeta(meta);
        }
        return item;
    }
}