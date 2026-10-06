package net.gate88.wars.kit;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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

    // 権限保持者マップ: Map<UUID, プレイヤー名>
    private final Map<UUID, String> createPerms = new HashMap<>();
    private final Map<UUID, String> creativePerms = new HashMap<>();
    // ★ クリエイティブ化禁止プレイヤー: Map<UUID, プレイヤー名>
    private final Map<UUID, String> blockedCreative = new HashMap<>();

    // ★ /kit suggest start で現在クリエイティブ化している一般プレイヤー (移動監視対象)
    private final Set<UUID> activeSuggestors = new HashSet<>();

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

        if (!config.contains("categories") || config.getStringList("categories").isEmpty()) {
            config.set("categories", List.of("近接", "遠距離", "魔法", "サポート", "その他"));
            save();
        }

        loadArea();
        loadPermissions();
        removeDuplicateKits();
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

    // ------------------------------------------------ 提案クリエイティブ監視 & 押し出し防止
    public void startSuggesting(Player p) {
        activeSuggestors.add(p.getUniqueId());
        // ★ 他者による押し出しを防ぐため衝突判定を一時無効化
        p.setCollidable(false);
    }

    public void stopSuggesting(Player p) {
        activeSuggestors.remove(p.getUniqueId());
        p.setCollidable(true);
    }

    public boolean isSuggesting(Player p) {
        return activeSuggestors.contains(p.getUniqueId());
    }

    // ------------------------------------------------ クリエイティブ禁止権限管理
    public boolean isCreativeBlocked(UUID uuid) {
        return blockedCreative.containsKey(uuid);
    }

    public void setCreativeBlocked(UUID uuid, String name, boolean blocked) {
        if (blocked) blockedCreative.put(uuid, name != null ? name : "不明");
        else blockedCreative.remove(uuid);
        savePermissions();
    }

    public Map<UUID, String> getBlockedCreativeHolders() {
        return new HashMap<>(blockedCreative);
    }

    // ------------------------------------------------ 提案システム (Suggestions)
    public boolean isOpOnline() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isOp()) return true;
        }
        return false;
    }

    public int getPlayerSuggestionCount(UUID uuid) {
        ConfigurationSection sec = config.getConfigurationSection("suggestions");
        if (sec == null) return 0;
        int count = 0;
        for (String id : sec.getKeys(false)) {
            if (uuid.toString().equals(config.getString("suggestions." + id + ".creator_uuid"))) {
                count++;
            }
        }
        return count;
    }

    public boolean isSuggested(String kitName) {
        return config.contains("suggestions." + kitName.toLowerCase());
    }

    public boolean suggestKit(Player player, String kitName, String category) {
        PlayerInventory inv = player.getInventory();
        String path = "suggestions." + kitName.toLowerCase();

        config.set(path + ".name", kitName);
        config.set(path + ".creator", player.getName());
        config.set(path + ".creator_uuid", player.getUniqueId().toString());
        config.set(path + ".category", category != null ? category : "その他");
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
        return true;
    }

    public List<String> getSuggestionIds() {
        ConfigurationSection sec = config.getConfigurationSection("suggestions");
        if (sec == null) return List.of();
        return new ArrayList<>(sec.getKeys(false));
    }

    public boolean approveSuggestion(String id) {
        String sPath = "suggestions." + id.toLowerCase();
        if (!config.contains(sPath)) return false;

        String kPath = "kits." + id.toLowerCase();
        config.set(kPath, config.get(sPath));
        config.set(kPath + ".enabled", true);
        config.set(kPath + ".play_count", 0);
        config.set(sPath, null);
        save();
        return true;
    }

    public boolean rejectSuggestion(String id) {
        String sPath = "suggestions." + id.toLowerCase();
        if (!config.contains(sPath)) return false;
        config.set(sPath, null);
        save();
        return true;
    }

    // ------------------------------------------------ カテゴリ管理
    public List<String> getCategories() {
        List<String> list = config.getStringList("categories");
        if (list.isEmpty()) return List.of("近接", "遠距離", "魔法", "サポート", "その他");
        return list;
    }

    public void addCategory(String category) {
        List<String> list = new ArrayList<>(getCategories());
        if (!list.contains(category)) {
            list.add(category);
            config.set("categories", list);
            save();
        }
    }

    public void removeCategory(String category) {
        List<String> list = new ArrayList<>(getCategories());
        if (list.remove(category)) {
            config.set("categories", list);
            save();
        }
    }

    public String getKitCategory(String kitId) {
        return config.getString("kits." + kitId.toLowerCase() + ".category", "近接");
    }

    public void setKitCategory(String kitId, String category) {
        config.set("kits." + kitId.toLowerCase() + ".category", category);
        save();
    }

    // ------------------------------------------------ プレイヤー個人設定
    public boolean isFavorite(UUID uuid, String kitId) {
        return config.getStringList("player-settings." + uuid + ".favorites").contains(kitId.toLowerCase());
    }

    public void toggleFavorite(UUID uuid, String kitId) {
        String path = "player-settings." + uuid + ".favorites";
        List<String> favs = new ArrayList<>(config.getStringList(path));
        String idLower = kitId.toLowerCase();
        if (favs.contains(idLower)) favs.remove(idLower);
        else favs.add(idLower);
        config.set(path, favs);
        save();
    }

    public boolean isFavoriteOnly(UUID uuid) {
        return config.getBoolean("player-settings." + uuid + ".favorite_only", false);
    }

    public void setFavoriteOnly(UUID uuid, boolean val) {
        config.set("player-settings." + uuid + ".favorite_only", val);
        save();
    }

    public boolean isKeepKit(UUID uuid) {
        return config.getBoolean("player-settings." + uuid + ".keep_kit", false);
    }

    public void setKeepKit(UUID uuid, boolean val) {
        config.set("player-settings." + uuid + ".keep_kit", val);
        save();
    }

    public int getPlayCount(String kitId) {
        return config.getInt("kits." + kitId.toLowerCase() + ".play_count", 0);
    }

    public void incrementPlayCount(String kitId) {
        String path = "kits." + kitId.toLowerCase() + ".play_count";
        config.set(path, config.getInt(path, 0) + 1);
        save();
    }

    public void setCustomIcon(String kitId, Material mat) {
        if (mat != null) {
            config.set("kits." + kitId.toLowerCase() + ".custom_icon", mat.name());
            save();
        }
    }

    public ItemStack getKitIcon(String kitId) {
        String path = "kits." + kitId.toLowerCase();
        String customMatName = config.getString(path + ".custom_icon");
        if (customMatName != null) {
            Material m = Material.getMaterial(customMatName.toUpperCase());
            if (m != null && !m.isAir()) return new ItemStack(m);
        }

        for (String armor : List.of("chestplate", "helmet", "boots", "leggings")) {
            ItemStack piece = config.getItemStack(path + ".armor." + armor);
            if (piece != null && !piece.getType().isAir()) return piece.clone();
        }

        ConfigurationSection slots = config.getConfigurationSection(path + ".slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                ItemStack item = slots.getItemStack(key);
                if (item != null && !item.getType().isAir()) return item.clone();
            }
        }
        return new ItemStack(Material.CHEST);
    }

    public List<ItemStack> getKitContents(String kitId) {
        List<ItemStack> list = new ArrayList<>();
        String path = "kits." + kitId.toLowerCase();

        for (String armor : List.of("helmet", "chestplate", "leggings", "boots")) {
            ItemStack it = config.getItemStack(path + ".armor." + armor);
            if (it != null && !it.getType().isAir()) list.add(it.clone());
        }
        ItemStack off = config.getItemStack(path + ".offhand");
        if (off != null && !off.getType().isAir()) list.add(off.clone());

        ConfigurationSection slots = config.getConfigurationSection(path + ".slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                ItemStack it = slots.getItemStack(key);
                if (it != null && !it.getType().isAir()) list.add(it.clone());
            }
        }
        return list;
    }

    public String pickMatchKitForPlayer(Player p) {
        if (forcedKit != null && exists(forcedKit)) {
            return forcedKit;
        }

        UUID uuid = p.getUniqueId();
        if (isFavoriteOnly(uuid)) {
            List<String> favs = config.getStringList("player-settings." + uuid + ".favorites");
            List<String> validFavs = new ArrayList<>();
            for (String id : favs) {
                if (exists(id) && isEnabled(id)) validFavs.add(id);
            }
            if (!validFavs.isEmpty()) {
                String picked = validFavs.get(ThreadLocalRandom.current().nextInt(validFavs.size()));
                incrementPlayCount(picked);
                return picked;
            }
        }
        return pickMatchKit();
    }

    public String pickMatchKit() {
        if (forcedKit != null && exists(forcedKit)) {
            incrementPlayCount(forcedKit);
            return forcedKit;
        }
        List<String> pool = getEnabledKitNames();
        if (pool.isEmpty()) pool = getKitNames();
        if (pool.isEmpty()) return null;
        String picked = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        incrementPlayCount(picked);
        return picked;
    }

    // ------------------------------------------------ 重複Kitの削除＆番号付け
    private void removeDuplicateKits() {
        ConfigurationSection kitsSec = config.getConfigurationSection("kits");
        if (kitsSec == null) return;

        List<String> kitKeys = new ArrayList<>(kitsSec.getKeys(false));
        Set<String> seenSignatures = new HashSet<>();
        List<String> toDelete = new ArrayList<>();

        for (String id : kitKeys) {
            String path = "kits." + id;
            String signature = buildKitSignature(path);
            if (!seenSignatures.add(signature)) {
                toDelete.add(id);
            }
        }
        for (String id : toDelete) {
            config.set("kits." + id, null);
            kitKeys.remove(id);
            plugin.getLogger().info("[KitManager] 内容が完全重複しているキットを削除しました: " + id);
        }

        Map<String, List<String>> nameGroups = new LinkedHashMap<>();
        for (String id : kitKeys) {
            String path = "kits." + id;
            String rawName = config.getString(path + ".name", id).trim();
            String baseName = rawName.replaceAll(" \\d+$", "").trim();
            nameGroups.computeIfAbsent(baseName.toLowerCase(), k -> new ArrayList<>()).add(id);
        }

        boolean changed = !toDelete.isEmpty();
        for (Map.Entry<String, List<String>> entry : nameGroups.entrySet()) {
            List<String> ids = entry.getValue();
            if (ids.size() > 1) {
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.get(i);
                    String baseName = config.getString("kits." + id + ".name", id).replaceAll(" \\d+$", "").trim();
                    String numberedName = baseName + " " + (i + 1);
                    config.set("kits." + id + ".name", numberedName);
                }
                changed = true;
            }
        }
        if (changed) save();
    }

    private String buildKitSignature(String path) {
        StringBuilder sb = new StringBuilder();
        for (String armor : List.of("helmet", "chestplate", "leggings", "boots")) {
            ItemStack it = config.getItemStack(path + ".armor." + armor);
            sb.append(armor).append("=").append(itemSig(it)).append(";");
        }
        sb.append("offhand=").append(itemSig(config.getItemStack(path + ".offhand"))).append(";");

        ConfigurationSection slots = config.getConfigurationSection(path + ".slots");
        if (slots != null) {
            for (String key : slots.getKeys(false)) {
                sb.append("s").append(key).append("=").append(itemSig(slots.getItemStack(key))).append(";");
            }
        }
        return sb.toString();
    }

    private String itemSig(ItemStack it) {
        if (it == null || it.getType().isAir()) return "AIR";
        return it.getType().name() + "x" + it.getAmount() + ":" + Objects.hashCode(it.getEnchantments());
    }

    // ------------------------------------------------ 権限管理
    private void loadPermissions() {
        createPerms.clear();
        creativePerms.clear();
        blockedCreative.clear();

        ConfigurationSection crSec = config.getConfigurationSection("permissions.create");
        if (crSec != null) {
            for (String key : crSec.getKeys(false)) {
                try { createPerms.put(UUID.fromString(key), crSec.getString(key, "不明")); } catch (Exception ignored) {}
            }
        }
        ConfigurationSection cvSec = config.getConfigurationSection("permissions.creative");
        if (cvSec != null) {
            for (String key : cvSec.getKeys(false)) {
                try { creativePerms.put(UUID.fromString(key), cvSec.getString(key, "不明")); } catch (Exception ignored) {}
            }
        }
        ConfigurationSection blSec = config.getConfigurationSection("permissions.blocked_creative");
        if (blSec != null) {
            for (String key : blSec.getKeys(false)) {
                try { blockedCreative.put(UUID.fromString(key), blSec.getString(key, "不明")); } catch (Exception ignored) {}
            }
        }
    }

    private void savePermissions() {
        config.set("permissions", null);
        for (Map.Entry<UUID, String> e : createPerms.entrySet()) {
            config.set("permissions.create." + e.getKey().toString(), e.getValue());
        }
        for (Map.Entry<UUID, String> e : creativePerms.entrySet()) {
            config.set("permissions.creative." + e.getKey().toString(), e.getValue());
        }
        for (Map.Entry<UUID, String> e : blockedCreative.entrySet()) {
            config.set("permissions.blocked_creative." + e.getKey().toString(), e.getValue());
        }
        save();
    }

    public boolean canCreateKit(Player p) {
        return p.isOp() || p.hasPermission("wars.admin") || p.hasPermission("kit.create") || createPerms.containsKey(p.getUniqueId());
    }

    /** ★ クリエイティブになれるか (OP不在時は一般プレイヤー拒否、禁止リスト者は完全拒否) */
    public boolean canAutoCreative(Player p) {
        if (p.isOp()) return true;
        // OPが1人もオンラインでない場合、一般プレイヤーは自動クリエイティブ不可
        if (!isOpOnline()) return false;
        // クリエイティブ化禁止リストに入っているプレイヤーは不可
        if (isCreativeBlocked(p.getUniqueId())) return false;

        return p.hasPermission("wars.admin") || p.hasPermission("kit.creative") || creativePerms.containsKey(p.getUniqueId());
    }

    public boolean hasCreatePerm(UUID uuid) { return createPerms.containsKey(uuid); }
    public boolean hasCreativePerm(UUID uuid) { return creativePerms.containsKey(uuid); }

    public void setCreatePerm(UUID uuid, String name, boolean allow) {
        if (allow) createPerms.put(uuid, name != null ? name : "不明");
        else createPerms.remove(uuid);
        savePermissions();
    }

    public void setCreativePerm(UUID uuid, String name, boolean allow) {
        if (allow) creativePerms.put(uuid, name != null ? name : "不明");
        else creativePerms.remove(uuid);
        savePermissions();
    }

    public void removeAllPerms(UUID uuid) {
        createPerms.remove(uuid);
        creativePerms.remove(uuid);
        blockedCreative.remove(uuid);
        savePermissions();
    }

    public Map<UUID, String> getAllPermHolders() {
        Map<UUID, String> map = new HashMap<>(createPerms);
        for (Map.Entry<UUID, String> e : creativePerms.entrySet()) map.putIfAbsent(e.getKey(), e.getValue());
        for (Map.Entry<UUID, String> e : blockedCreative.entrySet()) map.putIfAbsent(e.getKey(), e.getValue());
        return map;
    }

    // ------------------------------------------------ エリア管理
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

    public boolean hasArea() { return hasArea; }

    public boolean isInKitArea(Location loc) {
        if (!hasArea || loc == null || loc.getWorld() == null) return false;
        if (!loc.getWorld().getName().equals(areaWorld)) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    // ------------------------------------------------ Kitデータ管理
    private void registerDefaults() {
        saveDefaultKit("iron_warrior", "鉄フルアーマー + 石の剣", "近接",
                Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, null,
                List.of(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.GOLDEN_APPLE, 2)));

        saveDefaultKit("chain_archer", "チェーンメイル + 鉄の剣 + 弓", "遠距離",
                Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS, null,
                List.of(new ItemStack(Material.IRON_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 16), new ItemStack(Material.GOLDEN_APPLE, 1)));

        saveDefaultKit("diamond_tank", "ダイヤ胸当て + 木の剣 + 盾", "サポート",
                null, Material.DIAMOND_CHESTPLATE, null, null, new ItemStack(Material.SHIELD),
                List.of(new ItemStack(Material.WOODEN_SWORD), new ItemStack(Material.GOLDEN_APPLE, 2)));

        saveDefaultKit("sniper", "弓兵セット (革 + パワー弓)", "遠距離",
                Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS, null,
                List.of(new ItemStack(Material.STONE_SWORD), new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32), new ItemStack(Material.GOLDEN_APPLE, 1)));

        save();
    }

    private void saveDefaultKit(String id, String displayName, String category, Material h, Material c, Material l, Material b, ItemStack offhand, List<ItemStack> items) {
        String path = "kits." + id.toLowerCase();
        config.set(path + ".name", displayName);
        config.set(path + ".category", category);
        config.set(path + ".creator", "System");
        config.set(path + ".enabled", true);
        config.set(path + ".play_count", 1);
        config.set(path + ".armor.helmet", h != null ? new ItemStack(h) : null);
        config.set(path + ".armor.chestplate", c != null ? new ItemStack(c) : null);
        config.set(path + ".armor.leggings", l != null ? new ItemStack(l) : null);
        config.set(path + ".armor.boots", b != null ? new ItemStack(b) : null);
        config.set(path + ".offhand", offhand);

        for (int i = 0; i < items.size(); i++) {
            config.set(path + ".slots." + i, items.get(i));
        }
    }

    public boolean exists(String kitName) {
        return config.contains("kits." + kitName.toLowerCase());
    }

    public void createKit(Player player, String kitName) {
        PlayerInventory inv = player.getInventory();
        String path = "kits." + kitName.toLowerCase();

        config.set(path + ".name", kitName);
        config.set(path + ".creator", player.getName());
        config.set(path + ".creator_uuid", player.getUniqueId().toString());
        config.set(path + ".play_count", 0);
        if (!config.contains(path + ".category")) {
            config.set(path + ".category", "近接");
        }
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

    public String getForcedKit() { return forcedKit; }
    public void setForcedKit(String kitId) { this.forcedKit = kitId; }
    public void clearForcedKit() { this.forcedKit = null; }

    public String getKitDisplayName(String kitId) {
        return config.getString("kits." + kitId.toLowerCase() + ".name", kitId);
    }

    public String getKitCreator(String kitId) {
        return config.getString("kits." + kitId.toLowerCase() + ".creator", "不明");
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

    // ------------------------------------------------ ポーション
    public int getDefaultVanillaDuration(PotionEffectType type, int level) {
        if (level >= 2) {
            if (type == PotionEffectType.REGENERATION) return 22;
            if (type == PotionEffectType.SLOW_FALLING) return 90;
            if (type == PotionEffectType.GLOWING) return 10;
            return 90;
        }
        if (type == PotionEffectType.SPEED || type == PotionEffectType.STRENGTH
                || type == PotionEffectType.JUMP_BOOST || type == PotionEffectType.RESISTANCE
                || type == PotionEffectType.HASTE || type == PotionEffectType.FIRE_RESISTANCE
                || type == PotionEffectType.NIGHT_VISION || type == PotionEffectType.INVISIBILITY) {
            return 480;
        }
        if (type == PotionEffectType.SLOW_FALLING) return 240;
        if (type == PotionEffectType.REGENERATION) return 90;
        if (type == PotionEffectType.ABSORPTION) return 120;
        if (type == PotionEffectType.GLOWING) return 30;
        return 480;
    }

    public List<PotionEffect> getKitEffects(String kitId) {
        List<PotionEffect> list = new ArrayList<>();
        String path = "kits." + kitId.toLowerCase() + ".effects";
        ConfigurationSection sec = config.getConfigurationSection(path);
        if (sec == null) return list;

        for (String key : sec.getKeys(false)) {
            PotionEffectType type = PotionEffectType.getByName(key.toUpperCase());
            if (type != null) {
                int level = sec.isConfigurationSection(key) ? sec.getInt(key + ".level", 0) : sec.getInt(key, 0);
                int durationSec = sec.isConfigurationSection(key) ? sec.getInt(key + ".duration", getDefaultVanillaDuration(type, level)) : getDefaultVanillaDuration(type, level);
                if (level > 0 && durationSec > 0) {
                    list.add(new PotionEffect(type, durationSec * 20, level - 1));
                }
            }
        }
        return list;
    }

    public int getEffectLevel(String kitId, PotionEffectType type) {
        String path = "kits." + kitId.toLowerCase() + ".effects." + type.getName().toLowerCase();
        return config.isConfigurationSection(path) ? config.getInt(path + ".level", 0) : config.getInt(path, 0);
    }

    public int getEffectDuration(String kitId, PotionEffectType type) {
        String path = "kits." + kitId.toLowerCase() + ".effects." + type.getName().toLowerCase();
        int level = getEffectLevel(kitId, type);
        int defaultSec = getDefaultVanillaDuration(type, Math.max(1, level));
        return config.isConfigurationSection(path) ? config.getInt(path + ".duration", defaultSec) : defaultSec;
    }

    public void setEffect(String kitId, PotionEffectType type, int level, int durationSeconds) {
        String path = "kits." + kitId.toLowerCase() + ".effects." + type.getName().toLowerCase();
        if (level <= 0) config.set(path, null);
        else {
            config.set(path + ".level", level);
            config.set(path + ".duration", Math.max(5, durationSeconds));
        }
        save();
    }
}