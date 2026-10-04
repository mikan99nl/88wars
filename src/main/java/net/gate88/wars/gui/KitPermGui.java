package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

/** OP限定: Kit権限管理GUI (オフラインプレイヤー対応・閲覧・編集・削除) */
public final class KitPermGui implements InventoryHolder {
    private final WarsPlugin plugin;
    private final Inventory inventory;
    private final Mode mode;

    public enum Mode { LIST, SELECT_ONLINE }

    private KitPermGui(WarsPlugin plugin, Mode mode) {
        this.plugin = plugin;
        this.mode = mode;
        this.inventory = Bukkit.createInventory(this, 54, Msg.c(
                mode == Mode.LIST ? "&8[&4OP限定&8] &eKit権限管理" : "&8[&4OP限定&8] &bプレイヤーを選択して追加"
        ));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** OPのみ開くことができる */
    public static void open(WarsPlugin plugin, Player player) {
        if (!player.isOp()) {
            Msg.send(player, "&cこの機能はOP権限を持っているプレイヤーのみ利用できます");
            Sfx.deny(player);
            return;
        }
        KitPermGui gui = new KitPermGui(plugin, Mode.LIST);
        gui.renderList();
        player.openInventory(gui.getInventory());
    }

    public static void openSelectOnline(WarsPlugin plugin, Player player) {
        if (!player.isOp()) return;
        KitPermGui gui = new KitPermGui(plugin, Mode.SELECT_ONLINE);
        gui.renderSelectOnline();
        player.openInventory(gui.getInventory());
    }

    // ------------------------------------------------ 権限保持者一覧の描画 (LIST)
    private void renderList() {
        inventory.clear();
        Map<UUID, String> holders = plugin.kits().getAllPermHolders();
        List<UUID> uuids = new ArrayList<>(holders.keySet());

        for (int i = 0; i < Math.min(uuids.size(), 45); i++) {
            UUID uuid = uuids.get(i);
            String name = holders.get(uuid);
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            boolean isOnline = op.isOnline();

            boolean hasCreate = plugin.kits().hasCreatePerm(uuid);
            boolean hasCreative = plugin.kits().hasCreativePerm(uuid);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(op);
                meta.displayName(Msg.c((isOnline ? "&a● " : "&8● ") + "&e&l" + (op.getName() != null ? op.getName() : name)));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.c(isOnline ? "&7ステータス: &aオンライン" : "&7ステータス: &8オフライン"));
                lore.add(Msg.c("&8UUID: " + uuid));
                lore.add(Msg.c(""));
                lore.add(Msg.c("&fKit追加権限 (create): " + (hasCreate ? "&a【有効】" : "&c【無効】")));
                lore.add(Msg.c("&f自動Creative権限 (creative): " + (hasCreative ? "&b【有効】" : "&c【無効】")));
                lore.add(Msg.c(""));
                lore.add(Msg.c("&e[左クリック] &fKit追加権限を切り替え"));
                lore.add(Msg.c("&b[右クリック] &f自動Creative権限を切り替え"));
                lore.add(Msg.c("&c[Qキー(ドロップ)] &fこのプレイヤーの権限を完全削除"));

                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            inventory.setItem(i, skull);
        }

        // 下部ボタン
        inventory.setItem(48, createItem(Material.EMERALD, "&a&l【オンラインプレイヤーに権限を付与】", List.of("&7サーバー内のプレイヤーを選んで権限を与えます")));
        inventory.setItem(49, createItem(Material.BOOK, "&e&l【権限の説明】", List.of(
                "&a・create: &7/createkit で新規Kitの追加のみ可能",
                "&b・creative: &7Kit制作エリアに入った際自動でCreative化",
                "&c・OP所持者: &7常にすべての操作・権限が有効です"
        )));
        inventory.setItem(53, createItem(Material.ARROW, "&c閉じる", List.of()));
    }

    // ------------------------------------------------ オンラインプレイヤー選択画面 (SELECT_ONLINE)
    private void renderSelectOnline() {
        inventory.clear();
        List<Player> onlines = new ArrayList<>(Bukkit.getOnlinePlayers());

        for (int i = 0; i < Math.min(onlines.size(), 45); i++) {
            Player p = onlines.get(i);
            boolean hasCreate = plugin.kits().hasCreatePerm(p.getUniqueId());
            boolean hasCreative = plugin.kits().hasCreativePerm(p.getUniqueId());

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(p);
                meta.displayName(Msg.c("&e&l" + p.getName()));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.c("&fKit追加権限: " + (hasCreate ? "&a有効" : "&c無効")));
                lore.add(Msg.c("&f自動Creative権限: " + (hasCreative ? "&b有効" : "&c無効")));
                lore.add(Msg.c(""));
                lore.add(Msg.c("&e[左クリック] &fKit追加権限 (create) を付与"));
                lore.add(Msg.c("&b[右クリック] &f自動Creative (creative) を付与"));

                meta.lore(lore);
                skull.setItemMeta(meta);
            }
            inventory.setItem(i, skull);
        }

        inventory.setItem(49, createItem(Material.ARROW, "&7一覧に戻る", List.of()));
    }

    // ------------------------------------------------ クリック処理
    public void click(Player p, int slot, ClickType click) {
        if (!p.isOp()) {
            p.closeInventory();
            Msg.send(p, "&cこの操作はOP権限が必要です");
            return;
        }

        if (mode == Mode.LIST) {
            handleListClick(p, slot, click);
        } else {
            handleSelectOnlineClick(p, slot, click);
        }
    }

    private void handleListClick(Player p, int slot, ClickType click) {
        Map<UUID, String> holders = plugin.kits().getAllPermHolders();
        List<UUID> uuids = new ArrayList<>(holders.keySet());

        if (slot >= 0 && slot < uuids.size()) {
            UUID target = uuids.get(slot);
            String name = holders.get(target);

            // Qキー (ドロップ): 完全削除
            if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {
                plugin.kits().removeAllPerms(target);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
                Msg.send(p, "&c" + name + " の全権限を削除しました");
                renderList();
                return;
            }

            // 右クリック: creative 権限切り替え
            if (click.isRightClick()) {
                boolean cur = plugin.kits().hasCreativePerm(target);
                plugin.kits().setCreativePerm(target, name, !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                Msg.send(p, "&e" + name + " &fの自動Creative権限: " + (!cur ? "&a付与" : "&c剥奪"));
                renderList();
            }
            // 左クリック: create 権限切り替え
            else {
                boolean cur = plugin.kits().hasCreatePerm(target);
                plugin.kits().setCreatePerm(target, name, !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                Msg.send(p, "&e" + name + " &fのKit追加権限: " + (!cur ? "&a付与" : "&c剥奪"));
                renderList();
            }
        } else if (slot == 48) {
            openSelectOnline(plugin, p);
        } else if (slot == 53) {
            p.closeInventory();
        }
    }

    private void handleSelectOnlineClick(Player p, int slot, ClickType click) {
        List<Player> onlines = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (slot >= 0 && slot < onlines.size()) {
            Player target = onlines.get(slot);
            if (click.isRightClick()) {
                plugin.kits().setCreativePerm(target.getUniqueId(), target.getName(), true);
                Msg.send(p, "&a" + target.getName() + " に自動Creative権限を付与しました");
                Msg.send(target, "&bKit制作エリアでの自動クリエイティブ権限が付与されました！");
            } else {
                plugin.kits().setCreatePerm(target.getUniqueId(), target.getName(), true);
                Msg.send(p, "&a" + target.getName() + " にKit追加権限を付与しました");
                Msg.send(target, "&aKit追加権限 (/createkit) が付与されました！");
            }
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
            open(plugin, p);
        } else if (slot == 49) {
            open(plugin, p);
        }
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        var meta = item.getItemMeta();
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