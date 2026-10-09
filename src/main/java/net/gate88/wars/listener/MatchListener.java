package net.gate88.wars.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.mode.SurvivalGamesMode;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class MatchListener implements Listener {
    private final WarsPlugin plugin;

    public MatchListener(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    private MatchPlayer mp(Player p) {
        Match m = plugin.match();
        return m == null ? null : m.participant(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity() instanceof Trident trident) {
            plugin.trackTrident(trident.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Match m = plugin.match();
        MatchPlayer mp = (m != null) ? m.participant(p) : null;

        e.renderer((source, sourceDisplayName, message, viewer) -> {
            if (mp != null && mp.team != null) {
                String colorName = Colors.en(mp.team.color);
                NamedTextColor teamColor = Colors.textColor(mp.team.color);

                return Component.text()
                        .append(Component.text(colorName + " ", teamColor, TextDecoration.BOLD))
                        .append(Component.text(source.getName(), NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.color(NamedTextColor.WHITE))
                        .build();
            } else {
                return Component.text()
                        .append(Component.text(source.getName(), NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.color(NamedTextColor.WHITE))
                        .build();
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        Match m = plugin.match();
        MatchPlayer v = mp(e.getPlayer());
        if (m == null || v == null) return;
        if (m.isOver() || !v.alive) {
            e.setCancelled(true);
            return;
        }

        Block placedBlock = e.getBlockClicked().getRelative(e.getBlockFace());
        if (!m.mode().canPlace(m, v, placedBlock)) {
            e.setCancelled(true);
            return;
        }

        boolean exempt = m.mode().decayExempt(m, placedBlock);
        int sec = m.arena().getBlockDecaySeconds(m.mode().blockDecaySeconds(), plugin.arenas());
        m.blocks().track(placedBlock, placedBlock.getState(), !exempt && sec > 0, sec);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        Match m = plugin.match();
        MatchPlayer v = mp(e.getPlayer());
        if (m == null || v == null) return;
        if (m.isOver() || !v.alive) {
            e.setCancelled(true);
            return;
        }
        Block block = e.getBlockClicked();
        m.blocks().trackFixed(block, block.getState());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        Match m = plugin.match();
        if (m == null) return;
        if (!e.getBlock().getWorld().equals(m.world())) return;

        Block toBlock = e.getToBlock();
        if (toBlock.getType() == Material.AIR) {
            m.blocks().trackFluid(toBlock, toBlock.getState());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        Match m = plugin.match();
        if (m == null || m.isOver()) return;

        // コアエンティティへの攻撃処理
        if (m.mode() instanceof SurvivalGamesMode sg && sg.isCoreEntity(e.getEntity())) {
            if (e instanceof EntityDamageByEntityEvent byEntity) {
                Player attacker = null;
                if (byEntity.getDamager() instanceof Player pl) attacker = pl;
                else if (byEntity.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sp) attacker = sp;

                if (attacker != null) {
                    MatchPlayer mp = m.participant(attacker);
                    if (mp != null && mp.alive) {
                        sg.damageCore(m, attacker, e.getFinalDamage());
                    }
                }
            }
            e.setCancelled(true);
            return;
        }

        if (!(e.getEntity() instanceof Player victim)) return;
        MatchPlayer v = mp(victim);
        if (v == null || !v.alive) {
            e.setCancelled(true);
            return;
        }

        MatchPlayer attacker = null;
        if (e instanceof EntityDamageByEntityEvent byEntity) {
            Player ap = null;
            Entity d = byEntity.getDamager();
            if (d instanceof Player pl) ap = pl;
            else if (d instanceof Projectile pr && pr.getShooter() instanceof Player sp) ap = sp;
            if (ap != null) {
                attacker = m.participant(ap);
                if (attacker == null || !attacker.alive) {
                    e.setCancelled(true);
                    return;
                }
                if (attacker != v && attacker.team == v.team) {
                    e.setCancelled(true);
                    return;
                }
                if (!m.pvpEnabled()) {
                    e.setCancelled(true);
                    Msg.actionBar(ap, "&cまだPvPはできません");
                    Sfx.deny(ap);
                    return;
                }
                if (attacker != v) m.recordAttack(victim, ap);
            }
        }

        if (e.isCancelled()) return;
        if (victim.getHealth() - e.getFinalDamage() <= 0.0) {
            e.setCancelled(true);
            String cause = e.getCause().name().toLowerCase().replace('_', ' ');
            m.eliminate(v, attacker == v ? null : attacker, cause);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Match m = plugin.match();
        MatchPlayer v = mp(e.getEntity());
        if (m == null || v == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(true);
        Player killer = e.getEntity().getKiller();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            e.getEntity().spigot().respawn();
            m.eliminate(v, killer == null ? null : m.participant(killer), "死亡");
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Match m = plugin.match();
        MatchPlayer v = mp(e.getPlayer());
        if (m == null || v == null) return;
        if (m.isOver() || !v.alive) {
            e.setCancelled(true);
            return;
        }
        if (!m.mode().canPlace(m, v, e.getBlockPlaced())) {
            e.setCancelled(true);
            return;
        }
        boolean exempt = m.mode().decayExempt(m, e.getBlockPlaced());
        int sec = m.arena().getBlockDecaySeconds(m.mode().blockDecaySeconds(), plugin.arenas());
        m.blocks().track(e.getBlockPlaced(), e.getBlockReplacedState(), !exempt && sec > 0, sec);
        m.mode().onBlockPlaced(m, v, e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Match m = plugin.match();
        MatchPlayer v = mp(e.getPlayer());
        if (m == null || v == null) return;
        if (m.isOver() || !v.alive) {
            e.setCancelled(true);
            return;
        }

        Block b = e.getBlock();
        boolean isPlayerBlock = m.blocks().isActive(b);
        boolean isSpecialBreakable = (b.getType() == Material.WHITE_CONCRETE && m.mode().decayExempt(m, b));
        boolean canModeBreak = m.mode().canBreak(m, v, b);

        if (!isPlayerBlock && !isSpecialBreakable && !canModeBreak) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "&cアリーナの既存ブロックは破壊できません");
            return;
        }

        // SGモードなどの既存ブロック破壊時は復元追跡に登録
        if (!isPlayerBlock) {
            m.blocks().trackBreak(b);
        }

        e.setDropItems(false);
        e.setExpToDrop(0);
        m.blocks().onBroken(b);
        m.mode().onBlockBroken(m, v, b);
    }

    /** チェストが空になったら即座にシーランタンに置換 */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        Match m = plugin.match();
        if (m == null || m.isOver()) return;
        if (m.mode() instanceof SurvivalGamesMode sg) {
            sg.checkChestEmpty(e.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (mp(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        Match m = plugin.match();
        if (m != null && e.getEntity().getWorld().equals(m.world())) e.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        Match m = plugin.match();
        if (m != null && e.getBlock().getWorld().equals(m.world())) e.blockList().clear();
    }
}