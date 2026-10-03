package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

public final class KitGui implements InventoryHolder {
    private final WarsPlugin plugin;
    private final Inventory inventory;
    private final Mode mode;
    private final String selectedKitId;

    public enum Mode { LIST, DETAIL }

    private KitGui(WarsPlugin plugin, Mode mode, String selectedKitId) {
        this.plugin = plugin;
        this.mode = mode;
        this.selectedKitId = selectedKitId;
        this.inventory = Bukkit.createInventory(this, mode == Mode.LIST ? 54 : 36,
                Msg.c(mode == Mode.LIST ? "&8[&eKit一覧&8]" : "&8[&eKit編集: &f" + selectedKitId + "&8]"));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** 一覧GUIを開く */
    public static void openList(WarsPlugin plugin, Player player) {
        KitGui gui = new KitGui(plugin, Mode.LIST, null);
        gui.renderList();
        player.openInventory(gui.getInventory());
    }

    /** 詳細・編集GUIを開く */
    public static void openDetail(WarsPlugin plugin, Player player, String kitId) {
        KitGui gui = new KitGui(plugin, Mode.DETAIL, kitId);
        gui.renderDetail();
        player.openInventory(gui.getInventory());
    }

    // ------------------------------------------------ 一覧の描画
    private void renderList() {
        inventory.clear();
        List<String> kits = plugin.kits().getKitNames();
        String forced = plugin.kits().getForcedKit();

        for (int i = 0; i < Math.min(kits.size(), 45); i++) {
            String id = kits.get(i);
            boolean enabled = plugin.kits().isEnabled(id);
            boolean isForced = id.equalsIgnoreCase(forced);

            // アイコンの取得（null や AIR の場合は CHEST にフォールバック）
            ItemStack icon = plugin.kits().getKitIcon(id);
            if (icon == null || icon.getType().isAir()) {
                icon = new ItemStack(Material.CHEST);
            }

            ItemMeta meta = icon.getItemMeta();
            if (meta == null) {
                icon = new ItemStack(Material.CHEST);
                meta = icon.getItemMeta();
            }

            if (meta != null) {
                meta.displayName(Msg.c((isForced ? "&6★ &e" : "&f") + plugin.kits().getKitDisplayName(id) + " &7(" + id + ")"));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.c(enabled ? "&a● 有効 (ON)" : "&c● 無効 (OFF)"));
                if (isForced) {
                    lore.add(Msg.c("&6★ 現在【指定キット】に設定中 (次回確定)"));
                }
                lore.add(Msg.c(""));
                lore.add(Msg.c("&e[左クリック] &f詳細・編集メニュー"));
                lore.add(Msg.c("&b[右クリック] &fON / OFF 切り替え"));
                lore.add(Msg.c("&6[シフト右クリック] &fこのKitを指定/解除"));

                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(i, icon);
        }

        // 下部メニュー
        inventory.setItem(48, createItem(Material.ANVIL, "&a手持ちの装備で新規キット作成", List.of("&7インベントリの内容を", "&7/createkit <名前> で保存します")));
        if (forced != null) {
            inventory.setItem(49, createItem(Material.BARRIER, "&c指定キットを解除 (ランダムに戻す)", List.of("&7現在指定中: &e" + forced)));
        } else {
            inventory.setItem(49, createItem(Material.ENDER_EYE, "&a現在: ランダム選択中", List.of("&7試合開始時に有効なKitから抽選されます")));
        }
        inventory.setItem(53, createItem(Material.ARROW, "&c閉じる", List.of()));
    }

    // ------------------------------------------------ 詳細・編集の描画
    private void renderDetail() {
        inventory.clear();
        String id = selectedKitId;
        boolean enabled = plugin.kits().isEnabled(id);
        boolean isForced = id.equalsIgnoreCase(plugin.kits().getForcedKit());

        // キットのアイコンプレビュー
        ItemStack icon = plugin.kits().getKitIcon(id);
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(Material.CHEST);
        }
        inventory.setItem(4, icon);

        // 操作ボタン
        inventory.setItem(10, createItem(Material.CHEST, "&a【装備を受け取る】", List.of("&7このキットの防具・アイテムを", "&7自分のインベントリに展開します")));
        inventory.setItem(12, createItem(Material.WRITABLE_BOOK, "&e【手持ちで上書き保存】", List.of("&7現在自分が持っている装備・アイテムで", "&7このキット &f" + id + " &7を更新保存します")));
        inventory.setItem(14, createItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&a【有効中 (ON)】" : "&7【無効中 (OFF)】", List.of("&7クリックで試合の選出対象を切り替えます")));
        inventory.setItem(16, createItem(isForced ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                isForced ? "&6★ 【指定中】次回このKitで対戦" : "&f【このKitを指定して遊ぶ】",
                List.of(isForced ? "&7クリックで指定解除 (ランダムに戻す)" : "&7次回の試合でこのキットを確定配布にします")));

        inventory.setItem(22, createItem(Material.LAVA_BUCKET, "&c【このキットを削除】", List.of("&7クリックすると完全に削除されます")));
        inventory.setItem(31, createItem(Material.ARROW, "&7一覧に戻る", List.of()));
    }

    // ------------------------------------------------ クリック処理
    public void click(Player p, int slot, ClickType click) {
        if (!p.hasPermission("wars.admin") && !p.isOp()) {
            Msg.send(p, "&c権限がありません");
            return;
        }

        if (mode == Mode.LIST) {
            handleListClick(p, slot, click);
        } else {
            handleDetailClick(p, slot);
        }
    }

    private void handleListClick(Player p, int slot, ClickType click) {
        if (slot >= 0 && slot < 45) {
            List<String> kits = plugin.kits().getKitNames();
            if (slot >= kits.size()) return;
            String kitId = kits.get(slot);

            if (click.isShiftClick() && click.isRightClick()) {
                if (kitId.equalsIgnoreCase(plugin.kits().getForcedKit())) {
                    plugin.kits().clearForcedKit();
                    Msg.send(p, "&eキット指定を解除しました (ランダムに戻ります)");
                } else {
                    plugin.kits().setForcedKit(kitId);
                    Msg.send(p, "&a次回の試合キットを &6[" + kitId + "] &aに指定しました！");
                }
                Sfx.success(p);
                renderList();
            } else if (click.isRightClick()) {
                boolean cur = plugin.kits().isEnabled(kitId);
                plugin.kits().setEnabled(kitId, !cur);
                Msg.send(p, "&e" + kitId + " &7の有効状態: " + (!cur ? "&aON" : "&cOFF"));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                renderList();
            } else {
                openDetail(plugin, p, kitId);
            }
        } else if (slot == 48) {
            p.closeInventory();
            Msg.send(p, "&e/createkit <キット名> &fで現在のインベントリを保存できます");
        } else if (slot == 49) {
            plugin.kits().clearForcedKit();
            Msg.send(p, "&eキット指定を解除しました (ランダムに戻ります)");
            Sfx.success(p);
            renderList();
        } else if (slot == 53) {
            p.closeInventory();
        }
    }

    private void handleDetailClick(Player p, int slot) {
        String id = selectedKitId;
        switch (slot) {
            case 10 -> { // 装備受け取り
                plugin.kits().applyKit(p, id);
                Msg.send(p, "&aキット &e" + id + " &aの装備を受け取りました");
                Sfx.gearGive(p);
            }
            case 12 -> { // 手持ちで上書き
                plugin.kits().createKit(p, id);
                Msg.send(p, "&a現在のインベントリでキット &e" + id + " &aを上書き保存しました！");
                Sfx.success(p);
                renderDetail();
            }
            case 14 -> { // ON/OFF
                boolean cur = plugin.kits().isEnabled(id);
                plugin.kits().setEnabled(id, !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                renderDetail();
            }
            case 16 -> { // 指定トグル
                if (id.equalsIgnoreCase(plugin.kits().getForcedKit())) {
                    plugin.kits().clearForcedKit();
                    Msg.send(p, "&eキット指定を解除しました");
                } else {
                    plugin.kits().setForcedKit(id);
                    Msg.send(p, "&a次回の試合キットを &6[" + id + "] &aに指定しました！");
                }
                Sfx.success(p);
                renderDetail();
            }
            case 22 -> { // 削除
                plugin.kits().deleteKit(id);
                Msg.send(p, "&cキット " + id + " を削除しました");
                Sfx.deny(p);
                openList(plugin, p);
            }
            case 31 -> openList(plugin, p);
        }
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