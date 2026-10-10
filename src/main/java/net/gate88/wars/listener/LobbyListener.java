package net.gate88.wars.listener;

import net.gate88.wars.WarsPlugin;
import net.gate88.wars.gui.AdminGui;
import net.gate88.wars.gui.KitGui;
import net.gate88.wars.gui.KitPermGui;
import net.gate88.wars.gui.KitReviewGui;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.match.Match;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** ロビーの保護・参加/退出・ホットバー投票・Kit制作エリア管理 */
public final class LobbyListener implements Listener {
    private final WarsPlugin plugin;

    public LobbyListener(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean inLobby(Player p) {
        Match m = plugin.match();
        return m == null || m.participant(p) == null;
    }

    private boolean bypass(Player p) {
        return (p.isOp() || p.hasPermission("wars.admin")) && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            Match m = plugin.match();
            if (m != null && m.participant(p) != null) return;
            plugin.lobby().joinSetup(p);

            if (!plugin.kits().isInKitArea(p.getLocation())) {
                VoteMenu.giveItems(plugin, p);
            }
            Colors.applyLobbyDisplay(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();

        // 一時OPを付与した本人がログアウトした時、相手の一時OPを自動剥奪
        plugin.onGranterQuit(p.getUniqueId());

        Match m = plugin.match();
        if (m != null) m.onQuit(p);
        plugin.lobby().clearVote(p.getUniqueId());
        plugin.kits().stopSuggesting(p);
        plugin.removeSidebar(p);
        Colors.applyLobbyDisplay(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        plugin.clearTrackedTridents();
        e.setRespawnLocation(plugin.lobby().lobbyLocation());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (inLobby(e.getPlayer()) && !plugin.kits().isInKitArea(e.getPlayer().getLocation())) {
                VoteMenu.giveItems(plugin, e.getPlayer());
            }
        });
    }

    /** Kit制作エリアへの進入・退出・移動監視 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!inLobby(p)) return;

        boolean wasIn = plugin.kits().isInKitArea(e.getFrom());
        boolean nowIn = plugin.kits().isInKitArea(e.getTo());

        // 1. エリア進入時
        if (!wasIn && nowIn) {
            p.getInventory().clear();

            if (plugin.kits().canAutoCreative(p)) {
                p.setGameMode(GameMode.CREATIVE);
                Msg.send(p, "&a[Kit制作エリア] &fエリアに入りました。投票アイテムを消去しました。");
            } else {
                Msg.send(p, "&6&l[Kit制作エリア] &aエリアに入りました！");
                Msg.send(p, "&f・クリエイティブ化: &e/kitsuggest start &7(※その場から動くと解除されます)");
                Msg.send(p, "&f・Kitの提案提出: &e/kitsuggest <Kit名>");
                Msg.send(p, "&7※エリア内でのブロック設置・破壊・アイテム破棄は禁止されています。");
                if (!plugin.kits().isOpOnline()) {
                    Msg.send(p, "&c※現在OPがオフラインのため、クリエイティブ化・提案は行えません。");
                }
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        }
        // 2. エリア退出時: 強制的にアドベンチャーモードに上書き
        else if (wasIn && !nowIn) {
            plugin.kits().stopSuggesting(p);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || !inLobby(p)) return;
                p.getInventory().clear();
                p.setGameMode(GameMode.ADVENTURE);
                VoteMenu.giveItems(plugin, p);
                p.updateInventory();
                Msg.send(p, "&e[Kit制作エリア] &fエリアから出ました。アドベンチャーモードに戻し、投票アイテムを付与しました。");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 1.0f);
            });
        }
        // 3. エリア内で /kitsuggest start 中の一般プレイヤーが「動いた」場合のペナルティ
        else if (wasIn && plugin.kits().isSuggesting(p)) {
            if (e.getFrom().getX() != e.getTo().getX()
                    || e.getFrom().getY() != e.getTo().getY()
                    || e.getFrom().getZ() != e.getTo().getZ()) {

                plugin.kits().stopSuggesting(p);
                p.setGameMode(GameMode.ADVENTURE);
                p.getInventory().clear();

                Msg.send(p, "&c&l【警告】移動が検知されたため、クリエイティブモードを強制解除しアイテムを全消去しました。");
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
            } else {
                Msg.actionBar(p, "&c&l⚠ 動くとクリエイティブ解除＆アイテム全消去 ⚠");
            }
        }
    }

    // ------------------------------------------------ 一般プレイヤーのエリア内行動制限
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (inLobby(p)) {
            if (bypass(p)) return;
            e.setCancelled(true);
            if (plugin.kits().isInKitArea(p.getLocation())) {
                Msg.actionBar(p, "&cKit制作エリア内でのブロック破壊は禁止されています");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (inLobby(p)) {
            if (bypass(p)) return;
            e.setCancelled(true);
            if (plugin.kits().isInKitArea(p.getLocation())) {
                Msg.actionBar(p, "&cKit制作エリア内でのブロック設置は禁止されています");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (inLobby(p)) {
            if (bypass(p)) return;
            e.setCancelled(true);
            if (plugin.kits().isInKitArea(p.getLocation())) {
                Msg.actionBar(p, "&cKit制作エリア内でのアイテム破棄は禁止されています");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (inLobby(p)) {
            if (plugin.kits().isInKitArea(p.getLocation())) return;
            if (!bypass(p)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!inLobby(p)) return;
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            p.teleport(plugin.lobby().lobbyLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && inLobby(p) && !bypass(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!inLobby(p)) return;

        if (plugin.kits().isInKitArea(p.getLocation())) {
            if (!bypass(p) && (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.PHYSICAL)) {
                e.setCancelled(true);
            }
            return;
        }

        if (VoteMenu.isAdminTool(plugin, e.getItem())) {
            e.setCancelled(true);
            VoteMenu.handleAdminTool(plugin, p, e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK);
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (VoteMenu.isVoteItemsEnabled() && VoteMenu.isVoteItem(plugin, e.getItem())) {
            int slot = p.getInventory().getHeldItemSlot();
            if ((slot >= 0 && slot <= 6) || slot == VoteMenu.RANDOM_SLOT) {
                e.setCancelled(true);
                VoteMenu.handleClick(plugin, p, slot);
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (VoteMenu.isAdminTool(plugin, e.getCurrentItem())) {
            e.setCancelled(true);
            VoteMenu.handleAdminTool(plugin, p, e.isLeftClick());
            return;
        }

        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof KitReviewGui krg) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) krg.click(p, e.getSlot(), e.getClick());
            return;
        }
        if (holder instanceof KitPermGui kpg) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) kpg.click(p, e.getSlot(), e.getClick());
            return;
        }
        if (holder instanceof KitGui kg) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) kg.click(p, e.getSlot(), e.getClick());
            return;
        }
        // ★ AdminGui の処理: 上部・下部インベントリ両方を考慮して渡す
        if (holder instanceof AdminGui ag) {
            e.setCancelled(true);
            if (!p.hasPermission("wars.admin")) return;
            ag.click(p, e.getRawSlot(), e.getClick(), e.getCurrentItem());
            return;
        }

        if (inLobby(p) && !bypass(p) && !plugin.kits().isInKitArea(p.getLocation())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof AdminGui || holder instanceof KitGui || holder instanceof KitPermGui || holder instanceof KitReviewGui) {
            e.setCancelled(true);
        }
    }
}