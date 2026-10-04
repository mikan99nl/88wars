package net.gate88.wars.listener;

import net.gate88.wars.WarsPlugin;
import net.gate88.wars.gui.AdminGui;
import net.gate88.wars.gui.KitGui;
import net.gate88.wars.gui.KitPermGui;
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
        return (p.hasPermission("wars.admin") && p.getGameMode() == GameMode.CREATIVE)
                || plugin.kits().isInKitArea(p.getLocation());
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
        Match m = plugin.match();
        if (m != null) m.onQuit(p);
        plugin.lobby().clearVote(p.getUniqueId());
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        if (!inLobby(p)) return;

        boolean wasIn = plugin.kits().isInKitArea(e.getFrom());
        boolean nowIn = plugin.kits().isInKitArea(e.getTo());

        if (!wasIn && nowIn) {
            p.getInventory().clear();
            if (plugin.kits().canAutoCreative(p)) {
                p.setGameMode(GameMode.CREATIVE);
            }
            Msg.send(p, "&a[Kit制作エリア] &fエリアに入りました。投票アイテムを消去しました。");
            if (plugin.kits().canCreateKit(p)) {
                Msg.send(p, "&7装備を整えたら &e/createkit <キット名> &7で追加できます！");
            }
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
        } else if (wasIn && !nowIn) {
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
    }

    /** ホットバーアイテムの右クリック・左クリック処理 */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!inLobby(p)) return;

        if (plugin.kits().isInKitArea(p.getLocation())) return;

        // OP管理ツールの処理 (スロット17 / ネザースター)
        if (VoteMenu.isAdminTool(plugin, e.getItem())) {
            e.setCancelled(true);
            boolean isLeft = (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK);
            VoteMenu.handleAdminTool(plugin, p, isLeft);
            return;
        }

        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // ★ 修正: 投票配布が有効で、かつ「実際に投票アイテムを持っている時」のみ投票処理を実行
        // （アイテムを消した場所や、別のアイテムを置いた場合はキャンセルせず通常使用させる）
        if (VoteMenu.isVoteItemsEnabled() && VoteMenu.isVoteItem(plugin, e.getItem())) {
            int slot = p.getInventory().getHeldItemSlot();
            if ((slot >= 0 && slot <= 6) || slot == VoteMenu.RANDOM_SLOT) {
                e.setCancelled(true);
                VoteMenu.handleClick(plugin, p, slot);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inLobby(e.getPlayer()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inLobby(e.getPlayer()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (inLobby(e.getPlayer()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (inLobby(e.getPlayer()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!inLobby(p)) return;
        e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) {
            p.teleport(plugin.lobby().lobbyLocation());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (VoteMenu.isAdminTool(plugin, e.getCurrentItem())) {
            e.setCancelled(true);
            boolean isLeft = e.isLeftClick();
            VoteMenu.handleAdminTool(plugin, p, isLeft);
            return;
        }

        var holder = e.getView().getTopInventory().getHolder();

        if (holder instanceof KitPermGui kpg) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                kpg.click(p, e.getSlot(), e.getClick());
            }
            return;
        }

        if (holder instanceof KitGui kg) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) {
                kg.click(p, e.getSlot(), e.getClick());
            }
            return;
        }

        if (holder instanceof AdminGui ag) {
            e.setCancelled(true);
            if (!p.hasPermission("wars.admin")) return;
            if (e.getClickedInventory() == e.getView().getTopInventory()) ag.click(p, e.getSlot(), e.getClick());
            return;
        }

        if (inLobby(p) && !bypass(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof AdminGui || holder instanceof KitGui || holder instanceof KitPermGui) e.setCancelled(true);
    }
}