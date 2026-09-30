package net.gate88.wars.lobby;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** 試合終了時、ロビーの表彰台にTOP3のマネキン(ネームタグ付き)を召喚する */
public final class PodiumManager {
    public static final String TAG = "88wars_podium";

    public record Entry(int rank, UUID uuid, String name, int points) {}

    private final WarsPlugin plugin;
    private final List<Entity> spawned = new ArrayList<>();

    public PodiumManager(WarsPlugin plugin) {
        this.plugin = plugin;
    }

    public void show(List<Entry> entries) {
        clear();
        boolean mannequin = "MANNEQUIN".equalsIgnoreCase(plugin.getConfig().getString("podium.type", "MANNEQUIN"));
        for (Entry e : entries) {
            Location loc = plugin.podiumLocation(e.rank());
            if (loc == null || loc.getWorld() == null) continue;
            loc.getChunk().load();
            Entity ent = null;
            if (mannequin) {
                try {
                    ent = spawnMannequin(loc, e);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Mannequin の召喚に失敗 -> ArmorStand で代替: " + t.getMessage());
                }
            }
            if (ent == null) ent = spawnStand(loc, e);
            spawned.add(ent);
            World w = loc.getWorld();
            w.spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1.2, 0), 40, 0.4, 0.8, 0.4, 0.05);
            w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.5f, 1.0f);
            if (e.rank() == 1) w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.5f, 1.0f);
        }
    }

    private static String rankColor(int rank) {
        return rank == 1 ? "&6&l" : rank == 2 ? "&f&l" : "&c&l";
    }

    private Entity spawnMannequin(Location loc, Entry e) {
        return loc.getWorld().spawn(loc, Mannequin.class, mq -> {
            Player online = Bukkit.getPlayer(e.uuid());
            ResolvableProfile prof = online != null
                    ? ResolvableProfile.resolvableProfile(online.getPlayerProfile())
                    : ResolvableProfile.resolvableProfile().uuid(e.uuid()).name(e.name()).build();
            mq.setProfile(prof);
            mq.setImmovable(true);
            mq.setAI(false);
            mq.setInvulnerable(true);
            mq.setSilent(true);
            mq.setPersistent(false);
            mq.setRemoveWhenFarAway(false);
            mq.customName(Msg.c(rankColor(e.rank()) + e.rank() + "位 &f" + e.name()));
            mq.setCustomNameVisible(true);
            mq.setDescription(Msg.c("&e" + e.points() + "pt"));
            mq.addScoreboardTag(TAG);
        });
    }

    private Entity spawnStand(Location loc, Entry e) {
        return loc.getWorld().spawn(loc, ArmorStand.class, as -> {
            as.setArms(true);
            as.setBasePlate(false);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setCanPickupItems(false);
            as.customName(Msg.c(rankColor(e.rank()) + e.rank() + "位 &f" + e.name() + " &e" + e.points() + "pt"));
            as.setCustomNameVisible(true);
            as.addScoreboardTag(TAG);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            Player online = Bukkit.getPlayer(e.uuid());
            if (online != null) sm.setPlayerProfile(online.getPlayerProfile());
            else sm.setOwningPlayer(Bukkit.getOfflinePlayer(e.uuid()));
            head.setItemMeta(sm);
            as.getEquipment().setHelmet(head);
            Material[] set = e.rank() == 1
                    ? new Material[]{Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS}
                    : e.rank() == 2
                    ? new Material[]{Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS}
                    : new Material[]{Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
            as.getEquipment().setChestplate(new ItemStack(set[0]));
            as.getEquipment().setLeggings(new ItemStack(set[1]));
            as.getEquipment().setBoots(new ItemStack(set[2]));
        });
    }

    public void clear() {
        for (Entity e : spawned) {
            if (e != null && e.isValid()) e.remove();
        }
        spawned.clear();
        removeStale();
    }

    /** 前回起動時などに残ったものを掃除 */
    public void removeStale() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getScoreboardTags().contains(TAG)) e.remove();
            }
        }
    }
}
