package net.gate88.wars.gui;

import java.util.ArrayList;
import java.util.List;
import net.gate88.wars.WarsPlugin;
import net.gate88.wars.util.Msg;
import net.gate88.wars.util.Sfx;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

public final class KitGui implements InventoryHolder {
    private final WarsPlugin plugin;
    private final Inventory inventory;
    private final Mode mode;
    private final String selectedKitId;

    public enum Mode { LIST, DETAIL, EFFECTS }

    // 設定可能な主要ポーション効果一覧
    private static final List<PotionEffectType> AVAILABLE_EFFECTS = List.of(
            PotionEffectType.SPEED,
            PotionEffectType.STRENGTH,
            PotionEffectType.RESISTANCE,
            PotionEffectType.REGENERATION,
            PotionEffectType.JUMP_BOOST,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectType.ABSORPTION,
            PotionEffectType.NIGHT_VISION,
            PotionEffectType.INVISIBILITY,
            PotionEffectType.GLOWING,
            PotionEffectType.SLOW_FALLING,
            PotionEffectType.HASTE
    );

    private KitGui(WarsPlugin plugin, Mode mode, String selectedKitId) {
        this.plugin = plugin;
        this.mode = mode;
        this.selectedKitId = selectedKitId;
        String titleStr = switch (mode) {
            case LIST -> "&8[&eKit一覧&8]";
            case DETAIL -> "&8[&eKit編集: &f" + selectedKitId + "&8]";
            case EFFECTS -> "&8[&dポーション効果: &f" + selectedKitId + "&8]";
        };
        this.inventory = Bukkit.createInventory(this, mode == Mode.LIST ? 54 : 36, Msg.c(titleStr));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public static void openList(WarsPlugin plugin, Player player) {
        KitGui gui = new KitGui(plugin, Mode.LIST, null);
        gui.renderList();
        player.openInventory(gui.getInventory());
    }

    public static void openDetail(WarsPlugin plugin, Player player, String kitId) {
        KitGui gui = new KitGui(plugin, Mode.DETAIL, kitId);
        gui.renderDetail();
        player.openInventory(gui.getInventory());
    }

    public static void openEffects(WarsPlugin plugin, Player player, String kitId) {
        KitGui gui = new KitGui(plugin, Mode.EFFECTS, kitId);
        gui.renderEffects();
        player.openInventory(gui.getInventory());
    }

    // ------------------------------------------------ 一覧の描画 (LIST)
    private void renderList() {
        inventory.clear();
        List<String> kits = plugin.kits().getKitNames();
        String forced = plugin.kits().getForcedKit();

        for (int i = 0; i < Math.min(kits.size(), 45); i++) {
            String id = kits.get(i);
            boolean enabled = plugin.kits().isEnabled(id);
            boolean isForced = id.equalsIgnoreCase(forced);

            ItemStack icon = plugin.kits().getKitIcon(id);
            if (icon == null || icon.getType().isAir()) {
                icon = new ItemStack(Material.CHEST);
            }

            ItemMeta meta = icon.getItemMeta();
            if (meta == null) {
                icon = new ItemStack(Material.CHEST);
                meta = icon.getItemMeta();
            }

            if (meta != null) {
                meta.displayName(Msg.c((isForced ? "&6★ &e" : "&f") + plugin.kits().getKitDisplayName(id) + " &7(" + id + ")"));

                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.c(enabled ? "&a● 有効 (ON)" : "&c● 無効 (OFF)"));
                if (isForced) {
                    lore.add(Msg.c("&6★ 現在【指定キット】に設定中 (次回確定)"));
                }

                // 設定されているポーション効果のサマリー表示
                var effects = plugin.kits().getKitEffects(id);
                if (!effects.isEmpty()) {
                    lore.add(Msg.c("&d[ポーション効果]"));
                    for (var eff : effects) {
                        lore.add(Msg.c(" &7- &f" + getEffectNameJp(eff.getType()) + " &eLv" + (eff.getAmplifier() + 1)));
                    }
                }

                lore.add(Msg.c(""));
                lore.add(Msg.c("&e[左クリック] &f詳細・ポーション編集"));
                lore.add(Msg.c("&b[右クリック] &fON / OFF 切り替え"));
                lore.add(Msg.c("&6[シフト右クリック] &fこのKitを指定/解除"));

                meta.lore(lore);
                icon.setItemMeta(meta);
            }
            inventory.setItem(i, icon);
        }

        // 下部メニュー
        inventory.setItem(47, createItem(Material.ANVIL, "&a手持ちの装備で新規キット作成", List.of("&7インベントリの内容を", "&7/createkit <名前> で保存します")));

        if (forced != null) {
            inventory.setItem(49, createItem(Material.BARRIER, "&c指定キットを解除 (ランダムに戻す)", List.of("&7現在指定中: &e" + forced)));
        } else {
            inventory.setItem(49, createItem(Material.ENDER_EYE, "&a現在: ランダム選択中", List.of("&7試合開始時に有効なKitから抽選されます")));
        }

        // ★ ゲームスタートボタン
        inventory.setItem(51, createItem(Material.EMERALD_BLOCK, "&a&l【ゲームを開始する】", List.of(
                "&7クリックすると試合を即座に開始します",
                (forced != null ? "&6★ 指定キット: &e" + forced : "&7キット: &aランダム抽選")
        )));

        inventory.setItem(53, createItem(Material.ARROW, "&c閉じる", List.of()));
    }

    // ------------------------------------------------ 詳細・編集の描画 (DETAIL)
    private void renderDetail() {
        inventory.clear();
        String id = selectedKitId;
        boolean enabled = plugin.kits().isEnabled(id);
        boolean isForced = id.equalsIgnoreCase(plugin.kits().getForcedKit());

        ItemStack icon = plugin.kits().getKitIcon(id);
        if (icon == null || icon.getType().isAir()) {
            icon = new ItemStack(Material.CHEST);
        }
        inventory.setItem(4, icon);

        inventory.setItem(10, createItem(Material.CHEST, "&a【装備を受け取る】", List.of("&7このキットの防具・アイテムを自分のインベントリに展開します")));
        inventory.setItem(12, createItem(Material.WRITABLE_BOOK, "&e【手持ちで上書き保存】", List.of("&7現在自分が持っている装備・アイテムでこのキットを更新保存します")));

        // ★ ポーション効果設定ボタン
        List<String> effectLore = new ArrayList<>();
        effectLore.add("&7このキットで最初に付与されるポーション効果を設定します");
        var effects = plugin.kits().getKitEffects(id);
        if (effects.isEmpty()) {
            effectLore.add("&8(現在設定されている効果はありません)");
        } else {
            effectLore.add("&d現在の付与効果:");
            for (var eff : effects) {
                effectLore.add(" &7- &f" + getEffectNameJp(eff.getType()) + " &eLv" + (eff.getAmplifier() + 1));
            }
        }
        effectLore.add("");
        effectLore.add("&e[クリック] &f効果を設定・変更する");
        inventory.setItem(14, createItem(Material.BREWING_STAND, "&d&l【ポーション効果設定】", effectLore));

        inventory.setItem(16, createItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                enabled ? "&a【有効中 (ON)】" : "&7【無効中 (OFF)】", List.of("&7クリックで試合の選出対象を切り替えます")));

        inventory.setItem(20, createItem(isForced ? Material.GOLD_BLOCK : Material.IRON_BLOCK,
                isForced ? "&6★ 【指定中】次回このKitで対戦" : "&f【このKitを指定して遊ぶ】",
                List.of(isForced ? "&7クリックで指定解除 (ランダムに戻す)" : "&7次回の試合でこのキットを確定配布にします")));

        inventory.setItem(24, createItem(Material.LAVA_BUCKET, "&c【このキットを削除】", List.of("&7クリックすると完全に削除されます")));
        inventory.setItem(31, createItem(Material.ARROW, "&7一覧に戻る", List.of()));
    }

    // ------------------------------------------------ ポーション効果一覧の描画 (EFFECTS)
    private void renderEffects() {
        inventory.clear();
        String id = selectedKitId;

        for (int i = 0; i < AVAILABLE_EFFECTS.size(); i++) {
            PotionEffectType type = AVAILABLE_EFFECTS.get(i);
            int level = plugin.kits().getEffectLevel(id, type);

            Material mat = getEffectMaterial(type);
            String statusStr = switch (level) {
                case 1 -> "&a【Lv 1 有効中】";
                case 2 -> "&6【Lv 2 有効中】";
                default -> "&7【無効 (OFF)】";
            };

            List<String> lore = List.of(
                    statusStr,
                    "",
                    "&e[クリック] &f切り替え (OFF → Lv1 → Lv2 → OFF)"
            );

            inventory.setItem(i, createItem(mat, "&f" + getEffectNameJp(type), lore));
        }

        inventory.setItem(31, createItem(Material.ARROW, "&7Kit編集に戻る", List.of()));
    }

    // ------------------------------------------------ クリック処理
    public void click(Player p, int slot, ClickType click) {
        if (!p.hasPermission("wars.admin") && !p.isOp()) {
            Msg.send(p, "&c権限がありません");
            return;
        }

        switch (mode) {
            case LIST -> handleListClick(p, slot, click);
            case DETAIL -> handleDetailClick(p, slot);
            case EFFECTS -> handleEffectsClick(p, slot);
        }
    }

    private void handleListClick(Player p, int slot, ClickType click) {
        if (slot >= 0 && slot < 45) {
            List<String> kits = plugin.kits().getKitNames();
            if (slot >= kits.size()) return;
            String kitId = kits.get(slot);

            if (click.isShiftClick() && click.isRightClick()) {
                if (kitId.equalsIgnoreCase(plugin.kits().getForcedKit())) {
                    plugin.kits().clearForcedKit();
                    Msg.send(p, "&eキット指定を解除しました (ランダムに戻ります)");
                } else {
                    plugin.kits().setForcedKit(kitId);
                    Msg.send(p, "&a次回の試合キットを &6[" + kitId + "] &aに指定しました！");
                }
                Sfx.success(p);
                renderList();
            } else if (click.isRightClick()) {
                boolean cur = plugin.kits().isEnabled(kitId);
                plugin.kits().setEnabled(kitId, !cur);
                Msg.send(p, "&e" + kitId + " &7の有効状態: " + (!cur ? "&aON" : "&cOFF"));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                renderList();
            } else {
                openDetail(plugin, p, kitId);
            }
        } else if (slot == 47) {
            p.closeInventory();
            Msg.send(p, "&e/createkit <キット名> &fで現在のインベントリを保存できます");
        } else if (slot == 49) {
            plugin.kits().clearForcedKit();
            Msg.send(p, "&eキット指定を解除しました (ランダムに戻ります)");
            Sfx.success(p);
            renderList();
        } else if (slot == 51) {
            // ★ ゲーム開始
            p.closeInventory();
            if (!plugin.lobby().forceStart(null)) {
                Msg.send(p, "&c開始できません (試合中 または 対象プレイヤーがいません)");
            } else {
                Msg.send(p, "&a試合を開始します！");
            }
        } else if (slot == 53) {
            p.closeInventory();
        }
    }

    private void handleDetailClick(Player p, int slot) {
        String id = selectedKitId;
        switch (slot) {
            case 10 -> {
                plugin.kits().applyKit(p, id);
                Msg.send(p, "&aキット &e" + id + " &aの装備を受け取りました");
                Sfx.gearGive(p);
            }
            case 12 -> {
                plugin.kits().createKit(p, id);
                Msg.send(p, "&a現在のインベントリでキット &e" + id + " &aを上書き保存しました！");
                Sfx.success(p);
                renderDetail();
            }
            case 14 -> openEffects(plugin, p, id); // ポーション設定画面へ
            case 16 -> {
                boolean cur = plugin.kits().isEnabled(id);
                plugin.kits().setEnabled(id, !cur);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                renderDetail();
            }
            case 20 -> {
                if (id.equalsIgnoreCase(plugin.kits().getForcedKit())) {
                    plugin.kits().clearForcedKit();
                    Msg.send(p, "&eキット指定を解除しました");
                } else {
                    plugin.kits().setForcedKit(id);
                    Msg.send(p, "&a次回の試合キットを &6[" + id + "] &aに指定しました！");
                }
                Sfx.success(p);
                renderDetail();
            }
            case 24 -> {
                plugin.kits().deleteKit(id);
                Msg.send(p, "&cキット " + id + " を削除しました");
                Sfx.deny(p);
                openList(plugin, p);
            }
            case 31 -> openList(plugin, p);
        }
    }

    private void handleEffectsClick(Player p, int slot) {
        if (slot >= 0 && slot < AVAILABLE_EFFECTS.size()) {
            PotionEffectType type = AVAILABLE_EFFECTS.get(slot);
            int currentLevel = plugin.kits().getEffectLevel(selectedKitId, type);
            // OFF(0) -> Lv1(1) -> Lv2(2) -> OFF(0)
            int nextLevel = (currentLevel + 1) % 3;

            plugin.kits().setEffectLevel(selectedKitId, type, nextLevel);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            renderEffects();
        } else if (slot == 31) {
            openDetail(plugin, p, selectedKitId);
        }
    }

    private static Material getEffectMaterial(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return Material.SUGAR;
        if (type == PotionEffectType.STRENGTH) return Material.BLAZE_POWDER;
        if (type == PotionEffectType.RESISTANCE) return Material.IRON_CHESTPLATE;
        if (type == PotionEffectType.REGENERATION) return Material.GHAST_TEAR;
        if (type == PotionEffectType.JUMP_BOOST) return Material.RABBIT_FOOT;
        if (type == PotionEffectType.FIRE_RESISTANCE) return Material.MAGMA_CREAM;
        if (type == PotionEffectType.ABSORPTION) return Material.GOLDEN_APPLE;
        if (type == PotionEffectType.NIGHT_VISION) return Material.GOLDEN_CARROT;
        if (type == PotionEffectType.INVISIBILITY) return Material.FERMENTED_SPIDER_EYE;
        if (type == PotionEffectType.GLOWING) return Material.GLOWSTONE_DUST;
        if (type == PotionEffectType.SLOW_FALLING) return Material.FEATHER;
        if (type == PotionEffectType.HASTE) return Material.GOLDEN_PICKAXE;
        return Material.POTION;
    }

    private static String getEffectNameJp(PotionEffectType type) {
        if (type == PotionEffectType.SPEED) return "移動速度上昇 (Speed)";
        if (type == PotionEffectType.STRENGTH) return "攻撃力増加 (Strength)";
        if (type == PotionEffectType.RESISTANCE) return "耐性 (Resistance)";
        if (type == PotionEffectType.REGENERATION) return "再生能力 (Regen)";
        if (type == PotionEffectType.JUMP_BOOST) return "跳躍力上昇 (Jump Boost)";
        if (type == PotionEffectType.FIRE_RESISTANCE) return "耐火 (Fire Resistance)";
        if (type == PotionEffectType.ABSORPTION) return "衝撃吸収 (Absorption)";
        if (type == PotionEffectType.NIGHT_VISION) return "暗視 (Night Vision)";
        if (type == PotionEffectType.INVISIBILITY) return "透明化 (Invisibility)";
        if (type == PotionEffectType.GLOWING) return "発光 (Glowing)";
        if (type == PotionEffectType.SLOW_FALLING) return "低速落下 (Slow Falling)";
        if (type == PotionEffectType.HASTE) return "採掘速度上昇 (Haste)";
        return type.getName();
    }

    private static ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.c(name));
            List<net.kyori.adventure.text.Component> l = new ArrayList<>();
            for (String s : lore) l.add(Msg.c(s));
            meta.lore(l);
            item.setItemMeta(meta);
        }
        return item;
    }
}