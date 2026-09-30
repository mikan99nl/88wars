package net.gate88.wars.listener;

import net.gate88.wars.WarsPlugin;
import net.gate88.wars.gui.AdminGui;
import net.gate88.wars.gui.VoteMenu;
import net.gate88.wars.match.Match;
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

/** ロビーの保護・参加/退出・GUI */
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
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        Match m = plugin.match();
        if (m != null) m.onQuit(p);
        plugin.lobby().clearVote(p.getUniqueId());
        plugin.removeSidebar(p);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.setRespawnLocation(plugin.lobby().lobbyLocation());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!inLobby(p)) return;
        if (plugin.lobby().isLobbyItem(e.getItem())) {
            e.setCancelled(true);
            VoteMenu.open(plugin, p);
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
            Match m = plugin.match();
            // ロビー・試合中(短時間ゲーム)とも空腹にしない
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
        if (holder instanceof VoteMenu vm) {
            e.setCancelled(true);
            if (e.getClickedInventory() == e.getView().getTopInventory()) vm.click(p, e.getSlot());
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
        if (holder instanceof VoteMenu || holder instanceof AdminGui) e.setCancelled(true);
    }
}
