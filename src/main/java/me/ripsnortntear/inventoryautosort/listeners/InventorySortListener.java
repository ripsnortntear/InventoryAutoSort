package me.ripsnortntear.inventoryautosort.listeners;

import me.ripsnortntear.inventoryautosort.InventoryAutoSort;
import me.ripsnortntear.inventoryautosort.config.ConfigManager;
import me.ripsnortntear.inventoryautosort.managers.SortCooldownManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class InventorySortListener implements Listener {

    private final InventoryAutoSort plugin;
    private final SortCooldownManager cooldownManager;

    private static final Sound SOUND_FIRST_CLICK  = Sound.UI_LOOM_SELECT_PATTERN;
    private static final Sound SOUND_SORT_SUCCESS = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound SOUND_ON_COOLDOWN  = Sound.ENTITY_VILLAGER_NO;

    public InventorySortListener(InventoryAutoSort plugin, SortCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
    }

    private boolean isEmptySlot(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        // Only trigger on right clicks
        if (event.getClick() != ClickType.RIGHT) return;

        // Must be a player
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check permission and player toggle preference
        if (!player.hasPermission("inventoryautosort.use")) return;
        if (plugin.getPreferenceManager().isDisabled(player.getUniqueId())) return;

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        // If the player holds an item on their cursor, right click places an item — NEVER intercept!
        if (!isEmptySlot(event.getCursor())) return;

        // Must be clicking on an empty slot
        if (!isEmptySlot(event.getCurrentItem())) return;

        // Ignore special slot types (armor, result slots, crafting slots, etc.)
        InventoryType.SlotType slotType = event.getSlotType();
        if (slotType == InventoryType.SlotType.ARMOR
                || slotType == InventoryType.SlotType.RESULT
                || slotType == InventoryType.SlotType.CRAFTING) {
            return;
        }

        InventoryView view = event.getView();
        boolean isTopInventory = clickedInventory.equals(view.getTopInventory());
        boolean isBottomInventory = clickedInventory.equals(view.getBottomInventory());

        if (!isTopInventory && !isBottomInventory) return;

        // If clicking in the top container, verify if it is allowed to be sorted
        if (isTopInventory && !isSortableContainer(view, clickedInventory, player)) {
            return;
        }

        // If clicking in the player inventory (bottom), only allow main storage slots (0-35)
        if (isBottomInventory) {
            int slot = event.getSlot();
            if (slot < 0 || slot > 35) return;
        }

        // Cancel the vanilla click now that we verified this is an intentional sort click
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        boolean isDoubleClick = cooldownManager.registerClick(uuid, isTopInventory);

        if (!isDoubleClick) {
            if (plugin.getConfigManager().isPlaySounds()) {
                player.playSound(player.getLocation(), SOUND_FIRST_CLICK, 0.3f, 1.0f);
            }
            return;
        }

        // Double click confirmed — check cooldown
        if (cooldownManager.isOnCooldown(uuid)) {
            handleCooldownFeedback(player, uuid);
            return;
        }

        // Perform sorting on the clicked target
        if (isTopInventory) {
            sortContainerInventory(clickedInventory);
        } else {
            sortPlayerBackpackOnly(player);
        }

        player.updateInventory();
        cooldownManager.recordSort(uuid);
        handleSuccessFeedback(player);
    }

    private boolean isSortableContainer(InventoryView view, Inventory topInventory, Player player) {
        ConfigManager config = plugin.getConfigManager();

        if (!config.isContainersEnabled()) {
            return false;
        }

        // Validate inventory type
        if (!config.getAllowedContainerTypes().contains(topInventory.getType())) {
            return false;
        }

        // Handle Ender Chests
        if (topInventory.getType() == InventoryType.ENDER_CHEST) {
            return config.isSortEnderChests();
        }

        InventoryHolder holder = topInventory.getHolder();

        // Check whether it is a physical container block in the world
        boolean isBlockContainer = (holder instanceof Container || holder instanceof DoubleChest);

        // If not a block container and virtual inventory sorting is disabled, reject
        if (!isBlockContainer && !config.isSortVirtualInventories()) {
            if (config.isDebug()) {
                plugin.getLogger().info("[Debug] Ignored virtual container with holder: "
                        + (holder != null ? holder.getClass().getName() : "null"));
            }
            return false;
        }

        // Check blacklisted titles
        String plainTitle = PlainTextComponentSerializer.plainText().serialize(view.title()).toLowerCase();
        for (String blacklisted : config.getBlacklistedTitles()) {
            if (plainTitle.contains(blacklisted)) {
                if (config.isDebug()) {
                    plugin.getLogger().info("[Debug] Ignored container with blacklisted title '"
                            + plainTitle + "' matching '" + blacklisted + "'");
                }
                return false;
            }
        }

        // Check blacklisted holders
        if (holder != null) {
            String holderClass = holder.getClass().getName().toLowerCase();
            for (String blacklistedHolder : config.getBlacklistedHolders()) {
                if (holderClass.contains(blacklistedHolder)) {
                    if (config.isDebug()) {
                        plugin.getLogger().info("[Debug] Ignored container with blacklisted holder: " + holderClass);
                    }
                    return false;
                }
            }
        }

        return true;
    }

    private void sortPlayerBackpackOnly(Player player) {
        ItemStack[] allContents = player.getInventory().getStorageContents();

        if (!plugin.getConfigManager().isPreserveHotbar()) {
            ItemStack[] stacked = stackItems(allContents);
            ItemStack[] sorted = Arrays.stream(stacked)
                    .filter(i -> i != null && !i.getType().isAir())
                    .sorted(buildItemComparator())
                    .toArray(ItemStack[]::new);

            ItemStack[] result = new ItemStack[allContents.length];
            System.arraycopy(sorted, 0, result, 0, sorted.length);
            player.getInventory().setStorageContents(result);
            return;
        }

        if (allContents.length < 36) return;

        // Extract backpack slots (9-35)
        ItemStack[] backpackItems = new ItemStack[27];
        System.arraycopy(allContents, 9, backpackItems, 0, 27);

        ItemStack[] stacked = stackItems(backpackItems);
        ItemStack[] sorted = Arrays.stream(stacked)
                .filter(i -> i != null && !i.getType().isAir())
                .sorted(buildItemComparator())
                .toArray(ItemStack[]::new);

        ItemStack[] finalContents = new ItemStack[36];
        // Preserve hotbar (0-8)
        System.arraycopy(allContents, 0, finalContents, 0, 9);
        // Place sorted backpack items starting at slot 9
        System.arraycopy(sorted, 0, finalContents, 9, sorted.length);

        player.getInventory().setStorageContents(finalContents);
    }

    private void sortContainerInventory(Inventory container) {
        ItemStack[] contents = container.getContents();
        ItemStack[] stacked = stackItems(contents);
        ItemStack[] sorted = Arrays.stream(stacked)
                .filter(i -> i != null && !i.getType().isAir())
                .sorted(buildItemComparator())
                .toArray(ItemStack[]::new);

        ItemStack[] result = new ItemStack[contents.length];
        System.arraycopy(sorted, 0, result, 0, sorted.length);
        container.setContents(result);
    }

    public static ItemStack[] stackItems(ItemStack[] items) {
        List<ItemStack> stacked = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            // Maps carry unique ID data — do not merge
            if (item.getType() == Material.FILLED_MAP || item.getType() == Material.MAP) {
                stacked.add(item.clone());
                continue;
            }

            // Unstackable items
            if (item.getMaxStackSize() <= 1) {
                stacked.add(item.clone());
                continue;
            }

            boolean fullyMerged = false;

            for (ItemStack existing : stacked) {
                if (existing.getAmount() >= existing.getMaxStackSize()) continue;
                if (!existing.isSimilar(item)) continue;

                int space = existing.getMaxStackSize() - existing.getAmount();
                if (space <= 0) continue;

                int toAdd = Math.min(space, item.getAmount());
                existing.setAmount(existing.getAmount() + toAdd);

                int leftover = item.getAmount() - toAdd;
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

    public static Comparator<ItemStack> buildItemComparator() {
        return Comparator
                .comparing((ItemStack item) -> item.getType().name())
                .thenComparing(item -> {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null && meta.hasDisplayName()) {
                        Component displayName = meta.displayName();
                        if (displayName != null) {
                            return PlainTextComponentSerializer.plainText().serialize(displayName);
                        }
                    }
                    return "";
                })
                .thenComparingInt(item -> {
                    ItemMeta meta = item.getItemMeta();
                    if (meta instanceof Damageable damageable) {
                        return damageable.getDamage();
                    }
                    return 0;
                })
                .thenComparingInt(item -> -item.getAmount());
    }

    private void handleCooldownFeedback(Player player, UUID uuid) {
        ConfigManager config = plugin.getConfigManager();
        if (config.isShowActionBar()) {
            long remaining = cooldownManager.getRemainingCooldown(uuid);
            double seconds = remaining / 1000.0;
            String msg = config.getCooldownMessage().replace("{time}", String.format(Locale.US, "%.1f", seconds));
            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(msg);
            player.sendActionBar(component);
        }
        if (config.isPlaySounds()) {
            player.playSound(player.getLocation(), SOUND_ON_COOLDOWN, 0.5f, 1.2f);
        }
    }

    private void handleSuccessFeedback(Player player) {
        ConfigManager config = plugin.getConfigManager();
        if (config.isPlaySounds()) {
            player.playSound(player.getLocation(), SOUND_SORT_SUCCESS, 0.7f, 1.2f);
        }
        if (config.isShowActionBar()) {
            Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(config.getActionBarMessage());
            player.sendActionBar(component);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendActionBar(Component.empty());
                }
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            cooldownManager.resetClickTimer(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        cooldownManager.removePlayer(event.getPlayer().getUniqueId());
    }
}
