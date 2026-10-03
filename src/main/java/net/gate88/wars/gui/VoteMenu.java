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
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/** ホットバー投票システム (各モードに1票 / ランダム抽選に1票) */
public final class VoteMenu {
    public static final int RANDOM_SLOT = 8; // ホットバーの一番右 (スロット9)

    private VoteMenu() {}

    /** プレイヤーのホットバーに投票用アイテムを配布・更新 */
    public static void giveItems(WarsPlugin plugin, Player p) {
        PlayerInventory inv = p.getInventory();
        LobbyManager lobby = plugin.lobby();
        String myVote = lobby.voteOf(p);
        List<WarsMode> modes = plugin.modes().all();

        // スロット 0〜6: 各ゲームモード
        for (int i = 0; i < 7; i++) {
            if (i < modes.size()) {
                WarsMode md = modes.get(i);
                inv.setItem(i, createModeItem(lobby, md, myVote));
            } else {
                inv.setItem(i, null);
            }
        }

        // スロット 7: 空き
        inv.setItem(7, null);

        // スロット 8 (一番右): ランダム抽選
        boolean rnd = LobbyManager.RANDOM.equals(myVote);
        inv.setItem(RANDOM_SLOT, createRandomItem(lobby, rnd));

        p.updateInventory();
    }

    /** ロビーにいる全プレイヤーのホットバー投票表示を最新化 */
    public static void refreshAll(WarsPlugin plugin) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            // 試合中ではない（ロビーにいる）プレイヤーのホットバーを更新
            if (plugin.match() == null || plugin.match().participant(p) == null) {
                giveItems(plugin, p);
            }
        }
    }

    /** 互換用: 旧コードから open が呼ばれた場合もホットバーを更新 */
    public static void open(WarsPlugin plugin, Player p) {
        giveItems(plugin, p);
        Msg.actionBar(p, "&eホットバーのアイテムを右クリックして投票してください！");
    }

    /** ホットバーを右クリックした時の投票処理 */
    public static void handleClick(WarsPlugin plugin, Player p, int slot) {
        LobbyManager lobby = plugin.lobby();

        // スロット 8: ランダム抽選
        if (slot == RANDOM_SLOT) {
            lobby.toggleVote(p, LobbyManager.RANDOM);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            refreshAll(plugin);
            return;
        }

        // スロット 0〜6: 各モード
        List<WarsMode> modes = plugin.modes().all();
        if (slot >= 0 && slot < modes.size() && slot < 7) {
            WarsMode md = modes.get(slot);
            if (!md.implemented()) {
                Msg.actionBar(p, "&7このモードは準備中です");
                Sfx.deny(p);
            } else if (!lobby.isPlayable(md)) {
                Msg.actionBar(p, "&cこのモードは現在プレイできません (アリーナ未設定)");
                Sfx.deny(p);
            } else {
                lobby.toggleVote(p, md.id);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                refreshAll(plugin);
            }
        }
    }

    private static ItemStack createModeItem(LobbyManager lobby, WarsMode md, String myVote) {
        List<String> lore = new ArrayList<>(md.description);
        lore.add("");

        boolean playable = lobby.isPlayable(md);
        if (!md.implemented()) {
            lore.add("&8準備中");
            return named(Material.BARRIER, "&8" + md.displayName, lore, false);
        }
        if (!playable) {
            lore.add("&cアリーナ未設定/無効");
            return named(md.icon, "&c" + md.displayName, lore, false);
        }

        lore.add("&7現在の得票: &e" + lobby.votesFor(md.id) + "票");
        boolean mine = md.id.equalsIgnoreCase(myVote);
        lore.add(mine ? "&a✔ 投票中 &7(右クリックで取り消し)" : "&e右クリックで1票投じる");

        return named(md.icon, (mine ? "&6★ &a&l" : "&a&l") + md.displayName, lore, mine);
    }

    private static ItemStack createRandomItem(LobbyManager lobby, boolean isVoted) {
        List<String> lore = List.of(
                "&7ルーレットでゲームを抽選します",
                "",
                "&7現在の得票: &e" + lobby.votesFor(LobbyManager.RANDOM) + "票",
                isVoted ? "&a✔ 投票中 &7(右クリックで取り消し)" : "&e右クリックで1票投じる"
        );
        return named(Material.DISPENSER, (isVoted ? "&6★ &d&l" : "&d&l") + "ランダム抽選", lore, isVoted);
    }

    private static ItemStack named(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c(name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
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
}