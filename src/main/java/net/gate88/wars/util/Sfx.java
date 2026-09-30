package net.gate88.wars.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** 効果音まとめ (どの操作でどの音が鳴るかをここで一元管理) */
public final class Sfx {
    private Sfx() {}

    /** 抽選ルーレット: ディスペンサーがアイテムを出す音を連続で鳴らす */
    public static void lotteryTick(Player p, float progress) {
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.0f, 0.9f + progress * 0.7f);
    }

    /** 抽選結果決定 */
    public static void lotteryResult(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 1.5f, 1.6f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    /** 装備配布: 中身が空のディスペンサーが放つ音を大きく */
    public static void gearGive(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 4.0f, 0.8f);
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    public static void vote(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
    }

    public static void menuOpen(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    public static void click(Player p) {
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    public static void deny(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
    }

    public static void success(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    /** ロビーカウントダウン */
    public static void countdown(Player p, int remain) {
        if (remain <= 3) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.8f + (3 - remain) * 0.3f);
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
        }
    }

    public static void countdownStart(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.3f);
    }

    public static void countdownCancel(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.6f);
    }

    public static void teleport(Player p) {
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
    }
}
