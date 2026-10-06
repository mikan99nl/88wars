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

/** OP限定: 提案されたKitの審査GUI (/kit review) */
public final class KitReviewGui implements InventoryHolder {
    private final WarsPlugin plugin;
    private final Inventory inventory;

    public KitReviewGui(WarsPlugin plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, 54, Msg.c("&8[&4OP審査&8] &dKit提案一覧"));
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }

    public static void open(WarsPlugin plugin, Player p) {
        if (!p.isOp() && !p.hasPermission("wars.admin")) {
            Msg.send(p, "&cこの機能はOP権限が必要です");
            return;
        }
        KitReviewGui gui = new KitReviewGui(plugin);
        gui.render();
        p.openInventory(gui.getInventory());
        Sfx.menuOpen(p);
    }

    private void render() {
        inventory.clear();
        List<String> suggestions = plugin.kits().getSuggestionIds();

        for (int i = 0; i < Math.min(suggestions.size(), 45); i++) {
            String id = suggestions.get(i);
            String name = plugin.kits().getKitDisplayName(id);
            String creator = plugin.kits().getKitCreator(id);
            String category = plugin.kits().getKitCategory(id);

            ItemStack icon = new ItemStack(Material.PAPER);
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.displayName(Msg.c("&e&l" + name));
                meta.lore(List.of(
                        Msg.c("&7提案者: &b" + creator),
                        Msg.c("&7カテゴリ: &f" + category),
                        Msg.c(""),
                        Msg.c("&a[左クリック] &f承認して正式Kitに追加"),
                        Msg.c("&c[右クリック] &f却下（拒否・削除）")
                ));
                icon.setItemMeta(meta);
            }
            inventory.setItem(i, icon);
        }

        inventory.setItem(49, createItem(Material.ARROW, "&c閉じる", List.of()));
    }

    public void click(Player p, int slot, ClickType click) {
        List<String> suggestions = plugin.kits().getSuggestionIds();
        if (slot >= 0 && slot < suggestions.size()) {
            String id = suggestions.get(slot);
            if (click.isLeftClick()) {
                // 承認
                plugin.kits().approveSuggestion(id);
                Msg.send(p, "&a提案Kit &e" + id + " &aを承認し、正式Kitに追加しました！");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            } else if (click.isRightClick()) {
                // 拒否
                plugin.kits().rejectSuggestion(id);
                Msg.send(p, "&c提案Kit &e" + id + " &cを却下・削除しました");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
            }
            render();
        } else if (slot == 49) {
            p.closeInventory();
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