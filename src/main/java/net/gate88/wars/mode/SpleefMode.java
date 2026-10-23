package net.gate88.wars.mode;

import java.util.*;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.arena.Arena;
import net.gate88.wars.match.Match;
import net.gate88.wars.match.MatchPlayer;
import net.gate88.wars.match.MatchTeam;
import net.gate88.wars.util.Colors;
import net.gate88.wars.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

public final class SpleefMode extends WarsMode {
    private int currentRound = 1;
    private final Map<UUID, Integer> roundWins = new HashMap<>();
    private final Map<UUID, Integer> roundMined = new HashMap<>();

    private boolean skipVotingActive = false;
    private final Set<UUID> skipVoters = new HashSet<>();
    private BukkitTask nextRoundTask = null; // ★ 次ラウンド待機タスクの追跡

    public SpleefMode(WarsPlugin plugin, String id, String displayName, Material icon, List<String> description) {
        super(plugin, id, displayName, icon, description);
    }

    @Override
    public String arenaType() {
        return "spleef";
    }

    public int upgradeMineCount() { return cfg().getInt("upgrade-mine-count", 20); }
    public int snowballMineCount() { return cfg().getInt("snowball-mine-count", 25); }
    public double borderStartRadius() { return cfg().getDouble("border.start-radius", 30.0); }
    public int borderShrinkStartSeconds() { return cfg().getInt("border.shrink-start", 30); }
    public double borderSpeed() { return cfg().getDouble("border.speed", 0.2); }
    public int autoSkipSeconds() { return cfg().getInt("auto-skip-seconds", 180); }
    public int hardLimitSeconds() { return cfg().getInt("hard-limit-seconds", 300); }
    public int skipMinVotes() { return cfg().getInt("skip.min-votes", 3); }
    public int skipPercent() { return cfg().getInt("skip.percent", 50); }

    @Override
    public int teamSize() { return 1; }

    @Override
    public double customBorderRadius(Match m, int elapsed) {
        double startRadius = borderStartRadius();
        double endRadius = 7.5;
        int shrinkStart = borderShrinkStartSeconds();
        double speed = borderSpeed();

        int shrinkDuration = Math.max(5, (int) Math.ceil((startRadius - endRadius) / speed));

        if (elapsed < shrinkStart) return startRadius;
        if (elapsed < shrinkStart + shrinkDuration) {
            double prog = (double) (elapsed - shrinkStart) / shrinkDuration;
            return startRadius + (endRadius - startRadius) * prog;
        }
        return endRadius;
    }

    @Override
    public void onStart(Match m) {
        // ★ 初回以外にRound 3から始まる問題を根本修正: 試合の新規開始時は確実にRound 1へリセット
        if (nextRoundTask == null) {
            currentRound = 1;
            roundWins.clear();
        }

        cancelNextRoundTask();
        roundMined.clear();
        skipVotingActive = false;
        skipVoters.clear();

        m.broadcastToMatch("&6&l[Spleef] &e第 " + currentRound + " / 3 ラウンド開始！");
        m.broadcastToMatch("&7※ 足元の雪を掘り、相手を落下させろ！ (PvPダメージ無効)");

        giveStarterShovel(m);
    }

    private void giveStarterShovel(Match m) {
        ItemStack shovel = new ItemStack(Material.STONE_SHOVEL);
        ItemMeta meta = shovel.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c("&f&lスプリーフ用 石スコップ"));
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            shovel.setItemMeta(meta);
        }

        for (MatchPlayer mp : m.allPlayers()) {
            Player p = mp.player();
            if (p != null && mp.alive) {
                p.getInventory().clear();
                p.getInventory().setItem(0, shovel.clone());
                p.updateInventory();
            }
        }
    }

    @Override
    public void onSecond(Match m) {
        int elapsed = m.elapsed();
        Arena a = m.arena();

        // cy - 40 落下脱落判定
        double fallThresholdY = a.cy - 40.0;
        for (MatchPlayer mp : new ArrayList<>(m.allPlayers())) {
            if (!mp.alive || mp.left) continue;
            Player p = mp.player();
            if (p != null && p.getLocation().getY() <= fallThresholdY) {
                m.eliminate(mp, null, "最下層より落下");
            }
        }

        int autoSkipTime = autoSkipSeconds();
        if (autoSkipTime > 0 && !skipVotingActive && elapsed >= autoSkipTime) {
            startSkipVote(m, "膠着防止 (自動発動)");
        }

        int hardLimit = hardLimitSeconds();
        if (hardLimit > 0 && elapsed >= hardLimit) {
            m.broadcastToMatch("&c&l【時間切れ】 &e制限時間に達したためラウンドを強制終了します！");
            triggerSkipSuccess(m);
        }
    }

    @Override
    public boolean canBreak(Match m, MatchPlayer p, Block b) {
        return b.getType() == Material.SNOW_BLOCK;
    }

    @Override
    public boolean canPlace(Match m, MatchPlayer p, Block b) {
        return false;
    }

    @Override
    public void onBlockBroken(Match m, MatchPlayer p, Block b) {
        if (b.getType() != Material.SNOW_BLOCK) return;
        Player pl = p.player();
        if (pl == null) return;

        int count = roundMined.merge(p.uuid, 1, Integer::sum);

        if (count == upgradeMineCount()) {
            upgradeToDiamondShovel(pl);
        }

        if (count % snowballMineCount() == 0) {
            pl.getInventory().addItem(new ItemStack(Material.SNOWBALL, 1));
            pl.playSound(pl.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.4f);
            Msg.actionBar(pl, "&b雪玉を獲得しました！ &7(" + count + "ブロック採掘)");
        }
    }

    private void upgradeToDiamondShovel(Player p) {
        ItemStack diamondShovel = new ItemStack(Material.DIAMOND_SHOVEL);
        ItemMeta meta = diamondShovel.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c("&b&l強化ダイヤスコップ (効率V)"));
            meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            diamondShovel.setItemMeta(meta);
        }

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it != null && it.getType() == Material.STONE_SHOVEL) {
                slot = i;
                break;
            }
        }
        if (slot >= 0) {
            p.getInventory().setItem(slot, diamondShovel);
        } else {
            p.getInventory().addItem(diamondShovel);
        }

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        Msg.title(p, "&b&lスコップ進化！", "&e効率V ダイヤスコップに昇格", 5, 40, 10);
    }

    public void handleSnowballHit(Match m, ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball)) return;

        if (e.getHitBlock() != null) {
            Block b = e.getHitBlock();
            if (b.getType() == Material.SNOW_BLOCK) {
                m.blocks().trackBreak(b);
                b.setType(Material.AIR, false);
                b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, Material.SNOW_BLOCK.createBlockData());
                b.getWorld().playSound(b.getLocation(), Sound.BLOCK_SNOW_BREAK, 1.0f, 1.0f);
            }
        }

        if (e.getHitEntity() instanceof Player hitPlayer) {
            hitPlayer.playHurtAnimation(0f);
            hitPlayer.playSound(hitPlayer.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.0f, 1.2f);
            hitPlayer.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
        }
    }

    public void startSkipVote(Match m, String initiatedBy) {
        if (skipVotingActive) return;
        skipVotingActive = true;
        skipVoters.clear();

        m.broadcastToMatch("&e&l【強制終了 (Skip) 投票開始】 &7(発議: " + initiatedBy + ")");
        m.broadcastToMatch("&f試合を終了し、現在の生存者全員に1勝を付与します。");

        Component voteBtn = Component.text("[ここをクリックして賛成投票]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .hoverEvent(HoverEvent.showText(Component.text("クリックしてSkipに賛成する", NamedTextColor.YELLOW)))
                .clickEvent(ClickEvent.runCommand("/wars spleef vote"));

        for (MatchPlayer mp : m.allPlayers()) {
            Player p = mp.player();
            if (p != null) {
                p.sendMessage(voteBtn);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            }
        }
    }

    public void registerSkipVote(Match m, Player p) {
        if (!skipVotingActive) {
            Msg.send(p, "&c現在Skip投票は行われていません");
            return;
        }
        if (skipVoters.contains(p.getUniqueId())) {
            Msg.send(p, "&eあなたは既に投票済みです");
            return;
        }

        skipVoters.add(p.getUniqueId());
        int votes = skipVoters.size();
        int alive = m.aliveCount();

        int minVotes = skipMinVotes();
        int percentReq = (int) Math.ceil(alive * (skipPercent() / 100.0));
        int required = Math.max(minVotes, percentReq);
        if (alive < minVotes) required = alive;

        m.broadcastToMatch("&a" + p.getName() + " がSkipに賛成投票しました！ &7(" + votes + "/" + required + "票)");

        if (votes >= required) {
            m.broadcastToMatch("&a&l【Skip成立】 &f規定票数に達したため、ラウンドを強制終了します！");
            triggerSkipSuccess(m);
        }
    }

    private void triggerSkipSuccess(Match m) {
        skipVotingActive = false;
        for (MatchPlayer mp : m.allPlayers()) {
            if (mp.alive) {
                roundWins.merge(mp.uuid, 1, Integer::sum);
            }
        }
        progressRound(m, null);
    }

    @Override
    public void onEliminated(Match m, MatchPlayer victim) {
        if (m.aliveCount() <= 1 && !skipVotingActive) {
            MatchPlayer roundWinner = null;
            for (MatchPlayer mp : m.allPlayers()) {
                if (mp.alive) {
                    roundWinner = mp;
                    break;
                }
            }
            if (roundWinner != null) {
                roundWins.merge(roundWinner.uuid, 1, Integer::sum);
                m.broadcastToMatch("&6&l第 " + currentRound + " ラウンド勝者: &e" + roundWinner.name + " &7(1勝獲得)");
            }
            progressRound(m, roundWinner);
        }
    }

    private void progressRound(Match m, MatchPlayer roundWinner) {
        m.blocks().restoreAll();
        cancelNextRoundTask();

        if (currentRound < 3) {
            currentRound++;
            m.broadcastToMatch("&e10秒後に第 " + currentRound + " ラウンドを開始します...");

            nextRoundTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                nextRoundTask = null;
                m.resetForNextRound();
                m.start();
            }, 200L);
        } else {
            finishTournament(m);
        }
    }

    private void finishTournament(Match m) {
        cancelNextRoundTask();

        int maxWins = 0;
        for (int w : roundWins.values()) {
            if (w > maxWins) maxWins = w;
        }

        List<MatchPlayer> champions = new ArrayList<>();
        if (maxWins > 0) {
            for (MatchPlayer mp : m.allPlayers()) {
                if (roundWins.getOrDefault(mp.uuid, 0) == maxWins) {
                    champions.add(mp);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n&6&l==== Spleef 大会 最終結果 ====\n");
        if (champions.isEmpty()) {
            sb.append("&7優勝者なし\n");
        } else if (champions.size() == 1) {
            MatchPlayer champ = champions.get(0);
            sb.append("&e&l🏆 総合優勝: &f&l").append(champ.name).append(" &e(").append(maxWins).append("勝)\n");
        } else {
            sb.append("&e&l🏆 総合優勝 (同率優勝): \n");
            for (MatchPlayer champ : champions) {
                sb.append(" &6★ &f&l").append(champ.name).append(" &e(").append(maxWins).append("勝)\n");
            }
        }

        sb.append("\n&6&l--- 勝利数一覧 ---\n");
        List<MatchPlayer> all = new ArrayList<>(m.allPlayers());
        all.sort((a, b) -> Integer.compare(roundWins.getOrDefault(b.uuid, 0), roundWins.getOrDefault(a.uuid, 0)));
        for (MatchPlayer mp : all) {
            sb.append("&f").append(mp.name).append(" &8- &e").append(roundWins.getOrDefault(mp.uuid, 0)).append("勝\n");
        }
        sb.append("&6&l==============================");

        for (String line : sb.toString().split("\n")) {
            Bukkit.broadcast(Msg.c(line));
        }

        currentRound = 1; // リセット
        MatchTeam champTeam = !champions.isEmpty() ? champions.get(0).team : null;
        m.finish(champTeam, champions.size() > 1 ? "Spleef同率優勝" : "Spleef総合優勝");
    }

    private void cancelNextRoundTask() {
        if (nextRoundTask != null) {
            nextRoundTask.cancel();
            nextRoundTask = null;
        }
    }

    // ★ 中止・強制終了時に次ラウンドタスクを破棄し、Round 1へ完全初期化
    @Override
    public void onEnd(Match m) {
        cancelNextRoundTask();
        currentRound = 1;
        roundWins.clear();
        roundMined.clear();
        skipVotingActive = false;
        skipVoters.clear();
    }

    @Override
    public boolean handleRoundEnd(Match m, MatchTeam winner) {
        return currentRound <= 3;
    }

    @Override
    public MatchTeam pickTimeUpWinner(Match m) {
        return null;
    }

    @Override
    public List<String> extraSidebar(Match m, MatchPlayer viewer) {
        int wins = roundWins.getOrDefault(viewer.uuid, 0);
        int mined = roundMined.getOrDefault(viewer.uuid, 0);
        return List.of(
                "&fラウンド: &6" + currentRound + "&7/3",
                "&f勝利数: &e" + wins + "勝",
                "&f雪採掘数: &b" + mined + "個",
                skipVotingActive ? "&aSkip投票中 &7(/spleef vote)" : "&f生存者: &a" + m.aliveCount() + "人"
        );
    }
}