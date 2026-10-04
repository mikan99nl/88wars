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
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** ホットバー投票システム ＆ OP管理ツール */
public final class VoteMenu {
    public static final int RANDOM_SLOT = 8;
    public static final int OP_TOOL_SLOT = 17; // インベントリの右上 (最上段右端)

    // ★ 起動時は常に true (通常通り配布)。サーバー再起動で自動的に通常状態に戻る
    private static boolean voteItemsEnabled = true;

    private VoteMenu() {}

    public static boolean isVoteItemsEnabled() {
        return voteItemsEnabled;
    }

    /** 投票アイテムの配布有効/無効を切り替える */
    public static void setVoteItemsEnabled(WarsPlugin plugin, boolean enabled) {
        voteItemsEnabled = enabled;
        if (enabled) {
            // 有効化時: ロビーの全員に自動で配り直す
            refreshAll(plugin);
            Msg.broadcast("&a[88WARS] 投票用アイテムの配布が再開されました。");
        } else {
            // 無効化時: 全員の手持ちから投票アイテムおよび管理ツールを消去
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (plugin.match() == null || plugin.match().participant(p) == null) {
                    clearLobbyItems(p);
                }
            }
            Msg.broadcast("&c[88WARS] 投票用アイテムの配布が一時的に無効化されました。");
        }
    }

    /** プレイヤーのインベントリに投票用アイテムおよびOP管理ツールを配布 */
    public static void giveItems(WarsPlugin plugin, Player p) {
        PlayerInventory inv = p.getInventory();

        // 配布無効化時、またはKit制作エリア内は配布しない
        if (!voteItemsEnabled || plugin.kits().isInKitArea(p.getLocation())) {
            return;
        }

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

        inv.setItem(7, null);

        // スロット 8: ランダム抽選
        boolean rnd = LobbyManager.RANDOM.equals(myVote);
        inv.setItem(RANDOM_SLOT, createRandomItem(lobby, rnd));

        // ★ OP持ち限定: インベントリ右上 (スロット17) に管理ツールを配置
        if (p.isOp()) {
            inv.setItem(OP_TOOL_SLOT, createAdminToolItem(plugin));
        } else {
            inv.setItem(OP_TOOL_SLOT, null);
        }

        p.updateInventory();
    }

    /** ロビーの投票アイテムおよびOPツールを消去 */
    public static void clearLobbyItems(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, null);
        }
        inv.setItem(OP_TOOL_SLOT, null);
        p.updateInventory();
    }

    public static void refreshAll(WarsPlugin plugin) {
        if (!voteItemsEnabled) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.match() == null || plugin.match().participant(p) == null) {
                if (!plugin.kits().isInKitArea(p.getLocation())) {
                    giveItems(plugin, p);
                }
            }
        }
    }

    public static void open(WarsPlugin plugin, Player p) {
        giveItems(plugin, p);
        Msg.actionBar(p, "&eホットバーのアイテムを右クリックして投票してください！");
    }

    public static void handleClick(WarsPlugin plugin, Player p, int slot) {
        if (!voteItemsEnabled || plugin.kits().isInKitArea(p.getLocation())) return;

        LobbyManager lobby = plugin.lobby();

        if (slot == RANDOM_SLOT) {
            lobby.toggleVote(p, LobbyManager.RANDOM);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            refreshAll(plugin);
            return;
        }

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

    /** ★ OP管理ツールをクリックした時の処理 */
    public static void handleAdminTool(WarsPlugin plugin, Player p, boolean isLeftClick) {
        if (!p.isOp()) return;
        if (isLeftClick) {
            // 左クリック: ゲーム即時スタート
            if (!plugin.lobby().forceStart(null)) {
                Msg.send(p, "&c開始できません (試合中 または 対象プレイヤーがいません)");
            } else {
                Msg.send(p, "&a試合を開始します！");
            }
        } else {
            // 右クリック: Kit管理GUIを開く
            KitGui.openList(plugin, p);
        }
    }

    /** OP管理ツールアイテムの生成 */
    public static ItemStack createAdminToolItem(WarsPlugin plugin) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c("&6&l【OP管理ツール】"));
            meta.lore(List.of(
                    Msg.c("&a[左クリック] &fゲームを即時スタート"),
                    Msg.c("&b[右クリック] &fKit管理GUIを開く"),
                    Msg.c("&7※インベントリ右上(スロット17)常駐")
            ));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "op_tool"), PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean isAdminTool(WarsPlugin plugin, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        return it.getItemMeta().getPersistentDataContainer().has(new NamespacedKey(plugin, "op_tool"), PersistentDataType.BYTE);
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