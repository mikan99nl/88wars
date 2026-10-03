package net.gate88.wars.listener;

import net.gate88.wars.WarsPlugin;
import net.gate88.wars.gui.AdminGui;
import net.gate88.wars.gui.KitGui;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.match.Match;
import net.gate88.wars.util.Colors;
import org.bukkit.GameMode;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

/** ロビーの保護・参加/退出・ホットバー投票 */
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
        return p.hasPermission("wars.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            Match m = plugin.match();
            if (m != null && m.participant(p) != null) return;
            plugin.lobby().joinSetup(p);

            // ロビー参加時にホットバー投票アイテムをセット
            VoteMenu.giveItems(plugin, p);

            // ロビー時の表示にリセット
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
            if (inLobby(e.getPlayer())) {
                VoteMenu.giveItems(plugin, e.getPlayer());
            }
        });
    }

    /** ホットバーのアイテムを右クリックして直接投票 */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!inLobby(p)) return;

        int slot = p.getInventory().getHeldItemSlot();
        if ((slot >= 0 && slot <= 6) || slot == VoteMenu.RANDOM_SLOT) {
            e.setCancelled(true);
            VoteMenu.handleClick(plugin, p, slot);
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
        var holder = e.getView().getTopInventory().getHolder();

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

        // ロビーではアイテム移動を禁止
        if (inLobby(p) && !bypass(p)) e.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        var holder = e.getView().getTopInventory().getHolder();
        if (holder instanceof AdminGui || holder instanceof KitGui) e.setCancelled(true);
    }
}