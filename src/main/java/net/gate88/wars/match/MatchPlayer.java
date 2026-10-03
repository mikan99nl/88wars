package net.gate88.wars.match;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class MatchPlayer {
    public final UUID uuid;
    public final String name;
    public MatchTeam team;
    public boolean alive = true;
    public boolean left;
    public int kills;
    public int points;
    public int survivedSeconds;
    public int eliminatedAtSecond = -1;
    public UUID lastAttacker;
    public long lastAttackMillis;

    public MatchPlayer(Player p) {
        this.uuid = p.getUniqueId();
        this.name = p.getName();
    }

    public Player player() {
        return Bukkit.getPlayer(uuid);
    }
}
