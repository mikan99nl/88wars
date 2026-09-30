package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.lobby.LobbyManager;
import net.gate88.wars.mode.WarsMode;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** ゲームモード投票メニュー (各モードに1票 / ランダム抽選に1票) */
public final class VoteMenu implements InventoryHolder {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int RANDOM_SLOT = 22;

    private final WarsPlugin plugin;
    private final Inventory inv;

    public VoteMenu(WarsPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 27, Msg.c("&8ゲームモード投票"));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static void open(WarsPlugin plugin, Player p) {
        VoteMenu m = new VoteMenu(plugin);
        m.render(p);
        p.openInventory(m.inv);
        Sfx.menuOpen(p);
    }

    public static void refreshAll(WarsPlugin plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof VoteMenu vm) vm.render(p);
        }
    }

    private static ItemStack named(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.c(name));
        List<net.kyori.adventure.text.Component> l = new ArrayList<>();
        for (String s : lore) l.add(Msg.c(s));
        meta.lore(l);
        if (glow) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        it.setItemMeta(meta);
        return it;
    }

    public void render(Player viewer) {
        LobbyManager lobby = plugin.lobby();
        ItemStack pane = named(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        String myVote = lobby.voteOf(viewer);
        List<WarsMode> modes = plugin.modes().all();
        for (int i = 0; i < modes.size() && i < SLOTS.length; i++) {
            WarsMode md = modes.get(i);
            List<String> lore = new ArrayList<>(md.description);
            lore.add("");
            boolean playable = lobby.isPlayable(md);
            if (!md.implemented()) {
                lore.add("&8準備中");
                inv.setItem(SLOTS[i], named(Material.BARRIER, "&8" + md.displayName, lore, false));
                continue;
            }
            if (!playable) {
                lore.add("&cアリーナ未設定/無効");
                inv.setItem(SLOTS[i], named(md.icon, "&c" + md.displayName, lore, false));
                continue;
            }
            lore.add("&7現在の得票: &e" + lobby.votesFor(md.id) + "票");
            boolean mine = md.id.equalsIgnoreCase(myVote);
            lore.add(mine ? "&a✔ 投票済み &7(クリックで取り消し)" : "&eクリックで1票投じる");
            inv.setItem(SLOTS[i], named(md.icon, "&a&l" + md.displayName, lore, mine));
        }
        boolean rnd = LobbyManager.RANDOM.equals(myVote);
        inv.setItem(RANDOM_SLOT, named(Material.DISPENSER, "&d&lランダム抽選",
                List.of("&7ディスペンサールーレットで", "&7ゲームを抽選します", "",
                        "&7現在の得票: &e" + lobby.votesFor(LobbyManager.RANDOM) + "票",
                        rnd ? "&a✔ 投票済み &7(クリックで取り消し)" : "&eクリックで1票投じる"), rnd));
    }

    public void click(Player p, int slot) {
        if (slot == RANDOM_SLOT) {
            plugin.lobby().toggleVote(p, LobbyManager.RANDOM);
            return;
        }
        List<WarsMode> modes = plugin.modes().all();
        for (int i = 0; i < SLOTS.length && i < modes.size(); i++) {
            if (SLOTS[i] == slot) {
                WarsMode md = modes.get(i);
                if (!md.implemented()) {
                    Msg.send(p, "&7このモードは準備中です");
                    Sfx.deny(p);
                } else if (!plugin.lobby().isPlayable(md)) {
                    Msg.send(p, "&cこのモードは現在プレイできません (アリーナ未設定)");
                    Sfx.deny(p);
                } else {
                    plugin.lobby().toggleVote(p, md.id);
                }
                return;
            }
        }
    }
}
