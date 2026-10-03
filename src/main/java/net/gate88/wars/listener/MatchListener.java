package net.gate88.wars.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

/** 試合中のルール */
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

    // チャットフォーマットの修正（MCID二重出力の完全防止）
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        Match m = plugin.match();
        MatchPlayer mp = (m != null) ? m.participant(p) : null;

        e.renderer((source, sourceDisplayName, message, viewer) -> {
            if (mp != null && mp.team != null) {
                NamedTextColor nameColor = Colors.textColor(mp.team.color);
                String label = mp.team.label();

                Component prefix = Component.empty();

                // ★ ここで判定: チーム名が自分の名前と同じでない場合のみ [チーム名] を前につける
                if (!label.equalsIgnoreCase(source.getName())) {
                    prefix = Msg.c(Colors.code(mp.team.color) + "[" + label + "] ");
                }

                // 結合: (チーム名がある場合のみ[チーム名]) + チーム色のMCID + : + メッセージ
                return prefix
                        .append(Component.text(source.getName(), nameColor))
                        .append(Msg.c("&7: &f"))
                        .append(message);
            } else {
                // ロビー時: [Lobby] MCID: メッセージ
                return Msg.c("&8[&7Lobby&8] ")
                        .append(Component.text(source.getName(), NamedTextColor.GRAY))
                        .append(Msg.c("&7: &f"))
                        .append(message);
            }
        });
    }

    // ---------------------------------------------------------------- ダメージ / 脱落
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Match m = plugin.match();
        MatchPlayer v = mp(victim);
        if (m == null || v == null) return;
        if (m.isOver() || !v.alive) {
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

    // ---------------------------------------------------------------- ブロック
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
        int sec = m.mode().blockDecaySeconds();
        m.blocks().track(e.getBlockPlaced(), e.getBlockReplacedState(), !exempt && sec > 0, sec);
        m.mode().onBlockPlaced(m, v, e.getBlockPlaced());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Match m = plugin.match();
        MatchPlayer v = mp(e.getPlayer());
        if (m == null || v == null) return;
        if (m.isOver() || !v.alive || !m.blocks().isActive(e.getBlock())) {
            e.setCancelled(true);
            return;
        }
        e.setDropItems(false);
        e.setExpToDrop(0);
        m.blocks().onBroken(e.getBlock());
        m.mode().onBlockBroken(m, v, e.getBlock());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (mp(e.getPlayer()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (mp(e.getPlayer()) != null) e.setCancelled(true);
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