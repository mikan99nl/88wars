package net.gate88.wars.kit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.gate88.wars.WarsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class KitManager {
    private final WarsPlugin plugin;
    private File file;
    private YamlConfiguration config;
    private String forcedKit = null;

    private String areaWorld;
    private int minX, minY, minZ;
    private int maxX, maxY, maxZ;
    private boolean hasArea = false;

    public KitManager(WarsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            boolean ignored = plugin.getDataFolder().mkdirs();
        }
        file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            try {
                boolean ignored = file.createNewFile();
            } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection kitsSec = config.getConfigurationSection("kits");
        if (kitsSec == null || kitsSec.getKeys(false).isEmpty()) {
            registerDefaults();
        }

        loadArea();
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("kits.yml の保存に失敗しました: " + e.getMessage());
        }
    }

    public void reload() {
        load();
    }

    // ------------------------------------------------ Kit制作エリア管理
    private void loadArea() {
        ConfigurationSection sec = config.getConfigurationSection("kit-area");
        if (sec != null && sec.getBoolean("enabled", false)) {
            areaWorld = sec.getString("world");
            minX = sec.getInt("minX");
            minY = sec.getInt("minY");
            minZ = sec.getInt("minZ");
            maxX = sec.getInt("maxX");
            maxY = sec.getInt("maxY");
            maxZ = sec.getInt("maxZ");
            hasArea = areaWorld != null && Bukkit.getWorld(areaWorld) != null;
        } else {
            hasArea = false;
        }
    }

    public void setArea(Location p1, Location p2) {
        if (!p1.getWorld().equals(p2.getWorld())) return;
        areaWorld = p1.getWorld().getName();
        minX = Math.min(p1.getBlockX(), p2.getBlockX());
        minY = Math.min(p1.getBlockY(), p2.getBlockY());
        minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
        hasArea = true;

        ConfigurationSection sec = config.createSection("kit-area");
        sec.set("enabled", true);
        sec.set("world", areaWorld);
        sec.set("minX", minX);
        sec.set("minY", minY);
        sec.set("minZ", minZ);
        sec.set("maxX", maxX);
        sec.set("maxY", maxY);
        sec.set("maxZ", maxZ);
        save();
    }

    public void clearArea() {
        hasArea = false;
        config.set("kit-area", null);
        save();
    }

    public boolean hasArea() {
        return hasArea;
    }

    public boolean isInKitArea(Location loc) {
        if (!hasArea || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(areaWorld)) return false;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    // ------------------------------------------------ Kitデータ管理
    private void registerDefaults() {
        saveDefaultKit("iron_warrior", "鉄フルアーマー + 石の剣",
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, null,
                List.of(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.GOLDEN_APPLE, 2)));

        saveDefaultKit("chain_archer", "チェーンメイル + 鉄の剣 + 弓",
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, null,
                List.of(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16), new ItemStack(Material.GOLDEN_APPLE, 1)));

        saveDefaultKit("diamond_tank", "ダイヤ胸当て + 木の剣 + 盾",
                null, Material.DIAMOND_CHESTPLATE, null, null, new ItemStack(Material.SHIELD),
                List.of(new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.GOLDEN_APPLE, 2)));

        saveDefaultKit("leather_diamond", "革フル + ダイヤの剣",
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, null,
                List.of(new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.GOLDEN_APPLE, 1)));

        saveDefaultKit("gold_berserker", "金フル + 鉄のオノ + 金リンゴ",
                Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS, null,
                List.of(new ItemStack(Material.IRON_AXE), new ItemStack(Material.GOLDEN_APPLE, 4)));

        ItemStack powerBow = new ItemStack(Material.BOW);
        powerBow.addUnsafeEnchantment(Enchantment.POWER, 1);
        saveDefaultKit("sniper", "弓兵セット (革 + パワー弓)",
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, null,
                List.of(new ItemStack(Material.STONE_SWORD), powerBow, new ItemStack(Material.ARROW, 32), new ItemStack(Material.GOLDEN_APPLE, 1)));

        saveDefaultKit("pearl_ninja", "ノーアーマー + 鉄の剣 + パール",
                null, null, null, null, null,
                List.of(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.ENDER_PEARL, 2), new ItemStack(Material.GOLDEN_APPLE, 5)));

        saveDefaultKit("crossbow_soldier", "鉄上半身 + クロスボウ",
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, null, null, null,
                List.of(new ItemStack(Material.STONE_AXE), new ItemStack(Material.CROSSBOW), new ItemStack(Material.ARROW, 20), new ItemStack(Material.GOLDEN_APPLE, 2)));

        saveDefaultKit("trident_fighter", "チェーン + トライデント + 盾",
                null, Material.CHAINMAIL_CHESTPLATE, null, Material.CHAINMAIL_BOOTS, new ItemStack(Material.SHIELD),
                List.of(new ItemStack(Material.TRIDENT), new ItemStack(Material.GOLDEN_APPLE, 1)));

        saveDefaultKit("diamond_parts", "ダイヤ兜&ブーツ + 鉄の剣",
                Material.DIAMOND_HELMET, null, null, Material.DIAMOND_BOOTS, null,
                List.of(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.GOLDEN_APPLE, 2)));

        save();
    }

    private void saveDefaultKit(String id, String displayName, Material h, Material c, Material l, Material b, ItemStack offhand, List<ItemStack> items) {
        String path = "kits." + id.toLowerCase();
        config.set(path + ".name", displayName);
        config.set(path + ".enabled", true);
        config.set(path + ".armor.helmet", h != null ? new ItemStack(h) : null);
        config.set(path + ".armor.chestplate", c != null ? new ItemStack(c) : null);
        config.set(path + ".armor.leggings", l != null ? new ItemStack(l) : null);
        config.set(path + ".armor.boots", b != null ? new ItemStack(b) : null);
        config.set(path + ".offhand", offhand);

        for (int i = 0; i < items.size(); i++) {
            config.set(path + ".slots." + i, items.get(i));
        }
    }

    public void createKit(Player player, String kitName) {
        PlayerInventory inv = player.getInventory();
        String path = "kits." + kitName.toLowerCase();

        config.set(path + ".name", kitName);
        if (!config.contains(path + ".enabled")) {
            config.set(path + ".enabled", true);
        }
        config.set(path + ".armor.helmet", inv.getHelmet());
        config.set(path + ".armor.chestplate", inv.getChestplate());
        config.set(path + ".armor.leggings", inv.getLeggings());
        config.set(path + ".armor.boots", inv.getBoots());
        config.set(path + ".offhand", inv.getItemInOffHand());

        config.set(path + ".slots", null);
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack item = storage[i];
            if (item != null && !item.getType().isAir()) {
                config.set(path + ".slots." + i, item);
            }
        }

        save();
    }

    /** プレイヤーにキットを適用（ポーション効果も付与） */
    public void applyKit(Player player, String kitId) {
        String path = "kits." + (kitId != null ? kitId.toLowerCase() : "");
        if (!config.contains(path)) {
            List<String> kits = getEnabledKitNames();
            if (kits.isEmpty()) kits = getKitNames();
            if (kits.isEmpty()) return;
            path = "kits." + kits.getFirst().toLowerCase();
        }

        PlayerInventory inv = player.getInventory();
        inv.clear();

        inv.setHelmet(config.getItemStack(path + ".armor.helmet"));
        inv.setChestplate(config.getItemStack(path + ".armor.chestplate"));
        inv.setLeggings(config.getItemStack(path + ".armor.leggings"));
        inv.setBoots(config.getItemStack(path + ".armor.boots"));

        ItemStack offhand = config.getItemStack(path + ".offhand");
        if (offhand != null) inv.setItemInOffHand(offhand);

        ConfigurationSection slotsSection = config.getConfigurationSection(path + ".slots");
        if (slotsSection != null) {
            for (String key : slotsSection.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    ItemStack item = slotsSection.getItemStack(key);
                    if (item != null && slot >= 0 && slot < inv.getSize()) {
                        inv.setItem(slot, item);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // ★ 設定されたポーション効果をプレイヤーに付与（試合中持続: 600秒）
        List<PotionEffect> effects = getKitEffects(kitId);
        for (PotionEffect effect : effects) {
            player.addPotionEffect(effect);
        }

        player.updateInventory();
    }

    public void deleteKit(String kitId) {
        config.set("kits." + kitId.toLowerCase(), null);
        if (forcedKit != null && forcedKit.equalsIgnoreCase(kitId)) {
            forcedKit = null;
        }
        save();
    }

    public boolean isEnabled(String kitId) {
        return config.getBoolean("kits." + kitId.toLowerCase() + ".enabled", true);
    }

    public void setEnabled(String kitId, boolean enabled) {
        config.set("kits." + kitId.toLowerCase() + ".enabled", enabled);
        save();
    }

    public String getForcedKit() {
        return forcedKit;
    }

    public void setForcedKit(String kitId) {
        this.forcedKit = kitId;
    }

    public void clearForcedKit() {
        this.forcedKit = null;
    }

    public String pickMatchKit() {
        if (forcedKit != null && config.contains("kits." + forcedKit.toLowerCase())) {
            return forcedKit;
        }
        List<String> pool = getEnabledKitNames();
        if (pool.isEmpty()) pool = getKitNames();
        if (pool.isEmpty()) return null;
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    public String getKitDisplayName(String kitId) {
        String name = config.getString("kits." + kitId.toLowerCase() + ".name");
        return name != null ? name : kitId;
    }

    public List<String> getKitNames() {
        ConfigurationSection sec = config.getConfigurationSection("kits");
        if (sec == null) return List.of();
        return new ArrayList<>(sec.getKeys(false));
    }

    public List<String> getEnabledKitNames() {
        List<String> list = new ArrayList<>();
        for (String id : getKitNames()) {
            if (isEnabled(id)) list.add(id);
        }
        return list;
    }

    public ItemStack getKitIcon(String kitId) {
        String path = "kits." + kitId.toLowerCase();

        for (String armor : List.of("chestplate", "helmet", "boots", "leggings")) {
            ItemStack piece = config.getItemStack(path + ".armor." + armor);
            if (piece != null && !piece.getType().isAir()) {
                return piece.clone();
            }
        }

        ConfigurationSection slots = config.getConfigurationSection(path + ".slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                ItemStack item = slots.getItemStack(key);
                if (item != null && !item.getType().isAir()) {
                    return item.clone();
                }
            }
        }

        return new ItemStack(Material.CHEST);
    }

    // ------------------------------------------------ ポーション効果の管理
    /** Kitに設定されているポーション効果一覧を取得 */
    public List<PotionEffect> getKitEffects(String kitId) {
        List<PotionEffect> list = new ArrayList<>();
        String path = "kits." + kitId.toLowerCase() + ".effects";
        ConfigurationSection sec = config.getConfigurationSection(path);
        if (sec == null) return list;

        for (String key : sec.getKeys(false)) {
            PotionEffectType type = PotionEffectType.getByName(key.toUpperCase());
            if (type != null) {
                int level = sec.getInt(key); // 1 = Lv1 (amp 0), 2 = Lv2 (amp 1)
                if (level > 0) {
                    list.add(new PotionEffect(type, 20 * 600, level - 1)); // 10分間 (試合中持続)
                }
            }
        }
        return list;
    }

    /** Kitの特定ポーション効果のレベルを取得 (0 = なし, 1 = Lv1, 2 = Lv2) */
    public int getEffectLevel(String kitId, PotionEffectType type) {
        String path = "kits." + kitId.toLowerCase() + ".effects." + type.getName().toLowerCase();
        return config.getInt(path, 0);
    }

    /** ポーション効果のレベルを設定 (0 = 解除, 1 = Lv1, 2 = Lv2) */
    public void setEffectLevel(String kitId, PotionEffectType type, int level) {
        String path = "kits." + kitId.toLowerCase() + ".effects." + type.getName().toLowerCase();
        if (level <= 0) {
            config.set(path, null);
        } else {
            config.set(path, level);
        }
        save();
    }
}