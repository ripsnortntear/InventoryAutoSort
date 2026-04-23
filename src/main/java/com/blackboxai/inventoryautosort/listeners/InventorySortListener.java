package com.blackboxai.inventoryautosort.listeners;

import com.blackboxai.inventoryautosort.InventoryAutoSort;
import com.blackboxai.inventoryautosort.managers.PlayerSettingsManager;
import com.blackboxai.inventoryautosort.managers.SortCooldownManager;
import com.blackboxai.inventoryautosort.model.PlayerSortSettings;
import com.blackboxai.inventoryautosort.model.PlayerSortSettings.SortMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InventorySortListener implements Listener {

    private final InventoryAutoSort plugin;
    private final SortCooldownManager cooldownManager;
    private final PlayerSettingsManager settingsManager;

    private static final Sound SOUND_FIRST_CLICK  = Sound.UI_LOOM_SELECT_PATTERN;
    private static final Sound SOUND_SORT_SUCCESS = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound SOUND_ON_COOLDOWN  = Sound.ENTITY_VILLAGER_NO;

    public InventorySortListener(InventoryAutoSort plugin,
                                  SortCooldownManager cooldownManager,
                                  PlayerSettingsManager settingsManager) {
        this.plugin          = plugin;
        this.cooldownManager = cooldownManager;
        this.settingsManager = settingsManager;
    }

    // -------------------
    // Helpers
    // -------------------

    private boolean isEmptySlot(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    private boolean isCursorBundle(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        return cursor != null && cursor.getType() == Material.BUNDLE;
    }

    private boolean isPlayerInventoryView(InventoryView view) {
        InventoryType topType = view.getTopInventory().getType();
        return topType == InventoryType.CRAFTING
            || topType == InventoryType.PLAYER;
    }

    // -------------------
    // Event: Inventory Click
    // -------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT) return;
        if (!isEmptySlot(event.getCurrentItem())) return;
        if (isCursorBundle(event)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!player.hasPermission("inventoryautosort.use")) return;

        // Load player settings
        PlayerSortSettings settings = settingsManager.getSettings(player.getUniqueId());

        // Master toggle check
        if (!settings.isSortingEnabled()) return;

        // Check whether this is a container or backpack view
        // and whether the player has that specific sort type enabled
        InventoryView view = player.getOpenInventory();
        boolean isPlayerInv = isPlayerInventoryView(view);

        if (isPlayerInv && !settings.isSortBackpack()) return;
        if (!isPlayerInv && !settings.isSortChests()) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        boolean isDoubleClick = cooldownManager.registerClick(uuid);

        if (!isDoubleClick) {
            player.playSound(player.getLocation(), SOUND_FIRST_CLICK, 0.3f, 1.0f);
            return;
        }

        if (cooldownManager.isOnCooldown(uuid)) {
            long remaining = cooldownManager.getRemainingCooldown(uuid);
            double seconds  = remaining / 1000.0;
            player.sendActionBar(Component.text(
                    String.format("§cPlease wait §e%.1fs §cbefore sorting again!", seconds)));
            player.playSound(player.getLocation(), SOUND_ON_COOLDOWN, 0.5f, 1.2f);
            return;
        }

        performSort(player, settings);
    }

    // -------------------
    // Event: Inventory Close
    // -------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cooldownManager.resetClickTimer(player.getUniqueId());
        }
    }

    // -------------------
    // Event: Player Quit
    // -------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cooldownManager.removePlayer(uuid);
        settingsManager.unloadPlayer(uuid);
    }

    // -------------------
    // Core Sort
    // -------------------

    private void performSort(Player player, PlayerSortSettings settings) {
        InventoryView view = player.getOpenInventory();

        if (isPlayerInventoryView(view)) {
            sortPlayerBackpackOnly(player, settings.getSortMode());
        } else {
            sortContainerInventory(view.getTopInventory(), settings.getSortMode());
        }

        player.updateInventory();
        cooldownManager.recordSort(player.getUniqueId());
        player.playSound(player.getLocation(), SOUND_SORT_SUCCESS, 0.7f, 1.2f);
        showSortSuccessActionBar(player);
    }

    // -------------------
    // Stacking
    // -------------------

    private ItemStack[] stackItems(ItemStack[] items) {
        List<ItemStack> stacked = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (item.getType() == Material.FILLED_MAP
                    || item.getType() == Material.MAP) {
                stacked.add(item.clone());
                continue;
            }

            boolean fullyMerged = false;

            for (ItemStack existing : stacked) {
                if (!existing.isSimilar(item)) continue;
                if (existing.getAmount() >= existing.getMaxStackSize()) continue;

                int space    = existing.getMaxStackSize() - existing.getAmount();
                int toAdd    = Math.min(space, item.getAmount());
                int leftover = item.getAmount() - toAdd;
                existing.setAmount(existing.getAmount() + toAdd);

                if (leftover <= 0) {
				    fullyMerged = true;
                    break;
                }
                item = item.asQuantity(leftover);
            }

            if (!fullyMerged) {
                stacked.add(item.clone());
            }
        }

        return stacked.toArray(new ItemStack[0]);
    }

    // -------------------
    // Player Backpack Sort
    // -------------------

    private void sortPlayerBackpackOnly(Player player, SortMode mode) {
        ItemStack[] allContents = player.getInventory().getStorageContents();

        // Extract backpack slots only (9-35)
        ItemStack[] backpackItems = new ItemStack[27];
        System.arraycopy(allContents, 9, backpackItems, 0, 27);

        ItemStack[] stacked = stackItems(backpackItems);
        ItemStack[] sorted  = Arrays.stream(stacked)
                .filter(i -> i != null && !i.getType().isAir())
                .sorted(buildComparator(mode))
                .toArray(ItemStack[]::new);

        // Rebuild: original hotbar (0-8) + sorted backpack (9-35)
        ItemStack[] finalContents = new ItemStack[36];
        System.arraycopy(allContents, 0, finalContents, 0, 9);
        System.arraycopy(sorted,      0, finalContents, 9, sorted.length);

        player.getInventory().setStorageContents(finalContents);
    }

    // -------------------
    // Container Sort
    // -------------------

    private void sortContainerInventory(Inventory container, SortMode mode) {
        ItemStack[] contents = container.getContents();

        ItemStack[] stacked = stackItems(contents);
        ItemStack[] sorted  = Arrays.stream(stacked)
                .filter(i -> i != null && !i.getType().isAir())
                .sorted(buildComparator(mode))
                .toArray(ItemStack[]::new);

        ItemStack[] result = new ItemStack[contents.length];
        System.arraycopy(sorted, 0, result, 0, sorted.length);
        container.setContents(result);
    }

    // -------------------
    // Comparators — one per SortMode
    // --------------

    /**
     * Returns the correct comparator for the given sort mode.
     *
     * TYPE   — alphabetical by Material name, then stack size descending
     * NAME   — alphabetical by display name (falls back to material name),
     *           then stack size descending
     * RARITY — by rarity tier (Common → Uncommon → Rare → Epic),
     *           then alphabetical by material name within each tier,
     *           then stack size descending
     */
    private Comparator<ItemStack> buildComparator(SortMode mode) {
        return switch (mode) {
            case NAME   -> buildNameComparator();
            case RARITY -> buildRarityComparator();
            default     -> buildTypeComparator();   // TYPE is the default
        };
    }

    /** Sort alphabetically by Material name, fuller stacks first. */
    private Comparator<ItemStack> buildTypeComparator() {
        return Comparator
                .comparing((ItemStack item) -> item.getType().name())
                .thenComparingInt(item -> {
                    ItemMeta meta = item.getItemMeta();
                    return (meta instanceof Damageable d) ? d.getDamage() : 0;
                })
                .thenComparingInt(item -> -item.getAmount());
    }

    /**
     * Sort alphabetically by display name.
     * Items without a custom name fall back to their Material name
     * so they sort cleanly alongside named items.
     */
    private Comparator<ItemStack> buildNameComparator() {
        return Comparator
                .comparing((ItemStack item) -> {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        Component displayName = meta.displayName();
                        if (displayName != null) {
                            return PlainTextComponentSerializer.plainText()
                                    .serialize(displayName);
                        }
                    }
                    // Fallback: use the material name in Title Case
                    return toTitleCase(item.getType().name());
                })
                .thenComparing(item -> item.getType().name())
                .thenComparingInt(item -> -item.getAmount());
    }

    /**
     * Sort by Minecraft item rarity tier, then alphabetically within
     * each tier, then fuller stacks first.
     *
     * Rarity tiers (low → high):
     *   0 = COMMON    (white)  — most items
     *   1 = UNCOMMON  (yellow) — golden items, some tools/armour
     *   2 = RARE      (aqua)   — enchanted books, some special items
     *   3 = EPIC      (purple) — nether star, elytra, dragon egg, etc.
     */
    private Comparator<ItemStack> buildRarityComparator() {
        return Comparator
                .comparingInt((ItemStack item) -> getRarityTier(item.getType()))
                .thenComparing(item -> item.getType().name())
                .thenComparingInt(item -> -item.getAmount());
    }

    /**
     * Maps a Material to a numeric rarity tier.
     * Pottery sherds were renamed from POTTERY_SHARD_* to *_POTTERY_SHERD
     * in Minecraft 1.20+. All names here are verified against Paper 1.21.4.
     */
    private int getRarityTier(Material material) {
        return switch (material) {

            // ── EPIC (3) ─────────────
            case NETHER_STAR,
                 DRAGON_EGG,
                 DRAGON_HEAD,
                 ELYTRA,
                 BEACON,
                 HEART_OF_THE_SEA,
                 TRIDENT,
                 TOTEM_OF_UNDYING,
                 END_CRYSTAL,
                 ENCHANTED_GOLDEN_APPLE -> 3;

            // ── RARE (2) ─────────────
            case ENCHANTED_BOOK,
                 GOLDEN_APPLE,
                 MUSIC_DISC_13,
                 MUSIC_DISC_CAT,
                 MUSIC_DISC_BLOCKS,
                 MUSIC_DISC_CHIRP,
                 MUSIC_DISC_FAR,
                 MUSIC_DISC_MALL,
                 MUSIC_DISC_MELLOHI,
                 MUSIC_DISC_STAL,
                 MUSIC_DISC_STRAD,
                 MUSIC_DISC_WARD,
                 MUSIC_DISC_11,
                 MUSIC_DISC_WAIT,
                 MUSIC_DISC_OTHERSIDE,
                 MUSIC_DISC_5,
                 MUSIC_DISC_PIGSTEP,
                 MUSIC_DISC_RELIC,
                 EXPERIENCE_BOTTLE,
                 NETHERITE_INGOT,
                 NETHERITE_SWORD,
                 NETHERITE_PICKAXE,
                 NETHERITE_AXE,
                 NETHERITE_SHOVEL,
                 NETHERITE_HOE,
                 NETHERITE_HELMET,
                 NETHERITE_CHESTPLATE,
                 NETHERITE_LEGGINGS,
                 NETHERITE_BOOTS,
                 NETHERITE_SCRAP,
                 ANCIENT_DEBRIS,
                 CONDUIT,
                 SHULKER_SHELL,
                 NAUTILUS_SHELL,
                 TURTLE_HELMET,
                 SPYGLASS,
                 GOAT_HORN,
                 ECHO_SHARD,
                 DISC_FRAGMENT_5,
                 // Pottery sherds — correct 1.21.4 names
                 ARCHER_POTTERY_SHERD,
                 PRIZE_POTTERY_SHERD,
                 ARMS_UP_POTTERY_SHERD,
                 SKULL_POTTERY_SHERD,
                 ANGLER_POTTERY_SHERD,
                 BLADE_POTTERY_SHERD,
                 BREWER_POTTERY_SHERD,
                 BURN_POTTERY_SHERD,
                 DANGER_POTTERY_SHERD,
                 EXPLORER_POTTERY_SHERD,
                 FLOW_POTTERY_SHERD,
                 FRIEND_POTTERY_SHERD,
                 GUSTER_POTTERY_SHERD,
                 HEART_POTTERY_SHERD,
                 HEARTBREAK_POTTERY_SHERD,
                 HOWL_POTTERY_SHERD,
                 MINER_POTTERY_SHERD,
                 MOURNER_POTTERY_SHERD,
                 PLENTY_POTTERY_SHERD,
                 SCRAPE_POTTERY_SHERD,
                 SHEAF_POTTERY_SHERD,
                 SHELTER_POTTERY_SHERD,
                 SNORT_POTTERY_SHERD -> 2;

            // ── UNCOMMON (1) ───────────
            case GOLDEN_SWORD,
                 GOLDEN_PICKAXE,
                 GOLDEN_AXE,
                 GOLDEN_SHOVEL,
                 GOLDEN_HOE,
                 GOLDEN_HELMET,
                 GOLDEN_CHESTPLATE,
                 GOLDEN_LEGGINGS,
                 GOLDEN_BOOTS,
                 IRON_SWORD,
                 IRON_PICKAXE,
                 IRON_AXE,
                 IRON_SHOVEL,
                 IRON_HOE,
                 IRON_HELMET,
                 IRON_CHESTPLATE,
                 IRON_LEGGINGS,
                 IRON_BOOTS,
                 DIAMOND_SWORD,
                 DIAMOND_PICKAXE,
                 DIAMOND_AXE,
                 DIAMOND_SHOVEL,
                 DIAMOND_HOE,
                 DIAMOND_HELMET,
                 DIAMOND_CHESTPLATE,
                 DIAMOND_LEGGINGS,
                 DIAMOND_BOOTS,
                 BOW,
                 CROSSBOW,
                 FISHING_ROD,
                 FLINT_AND_STEEL,
                 SHEARS,
                 SHIELD,
                 SADDLE,
                 NAME_TAG,
                 LEAD,
                 DIAMOND,
                 EMERALD,
                 ENDER_PEARL,
                 ENDER_EYE,
                 BLAZE_ROD,
                 GHAST_TEAR,
                 NETHER_WART,
                 WITHER_SKELETON_SKULL,
                 CREEPER_HEAD,
                 ZOMBIE_HEAD,
                 SKELETON_SKULL,
                 PLAYER_HEAD,
                 PIGLIN_HEAD,
                 FILLED_MAP,
                 RECOVERY_COMPASS,
                 BUNDLE -> 1;

            // ── COMMON (0) — everything else ─────────
            default -> 0;
        };
    }

    /**
     * Converts a SNAKE_CASE material name to Title Case for display.
     * e.g. "DIAMOND_SWORD" → "Diamond Sword"
     */
    private String toTitleCase(String snakeCase) {
        String[] words = snakeCase.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1))
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ------------------------
    // Action Bar Feedback
    // -----------------------

    private void showSortSuccessActionBar(Player player) {
        Component message = Component.text("✔ Inventory sorted & stacked! Hotbar safe ✓")
                .color(NamedTextColor.GREEN);
        player.sendActionBar(message);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.sendActionBar(Component.empty());
                }
            }
        }.runTaskLater(plugin, 40L);
    }
}