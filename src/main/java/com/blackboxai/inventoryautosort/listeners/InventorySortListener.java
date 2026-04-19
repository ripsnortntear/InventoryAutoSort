package com.blackboxai.inventoryautosort.listeners;

import com.blackboxai.inventoryautosort.InventoryAutoSort;
import com.blackboxai.inventoryautosort.managers.SortCooldownManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class InventorySortListener implements Listener {

    private final InventoryAutoSort plugin;
    private final SortCooldownManager cooldownManager;

    public InventorySortListener(InventoryAutoSort plugin, SortCooldownManager cooldownManager) {
        this.plugin = plugin;
        this.cooldownManager = cooldownManager;
    }

    private boolean isEmptySlot(ItemStack item) {
        return item == null || item.getType().isAir();
    }

    /**
     * Returns true if the item on the player's cursor is a bundle.
     * When holding a bundle, right-clicking an empty slot pulls an item
     * out of the bundle — we must NOT cancel that click.
     */
    private boolean isCursorBundle(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        return cursor != null && cursor.getType() == Material.BUNDLE;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        // Only care about right-clicks
        if (event.getClick() != ClickType.RIGHT) return;

        // Only care about empty slots
        if (!isEmptySlot(event.getCurrentItem())) return;

        // If the player is holding a bundle on their cursor, let Minecraft
        // handle it normally (extracts item from bundle into the slot).
        if (isCursorBundle(event)) return;

        Player player = (Player) event.getWhoClicked();

        if (!player.hasPermission("inventoryautosort.use")) return;
        if (plugin.isDisabled(player.getUniqueId())) return;
        if (player.getOpenInventory() == null) return;

        // Cancel the click so nothing weird happens while we track double-clicks
        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        boolean isDoubleClick = cooldownManager.registerClick(uuid);

        if (!isDoubleClick) {
            // First click — play a subtle tick sound as feedback
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.3f, 1.0f);
            return;
        }

        // Double-click detected — check sort cooldown before sorting
        if (cooldownManager.isOnCooldown(uuid)) {
            long remaining = cooldownManager.getRemainingCooldown(uuid);
            double seconds = remaining / 1000.0;
            player.sendActionBar(Component.text(String.format(
                    "§cPlease wait §e%.1fs §cbefore sorting!", seconds)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 1.2f);
            return;
        }

        performSort(player);
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

    // ========== STACKING + SORTING ==========

    private void performSort(Player player) {
        InventoryView view = player.getOpenInventory();

        if (isPlayerInventoryView(view)) {
            sortPlayerBackpackOnly(player);
        } else {
            sortContainerInventory(view.getTopInventory());
        }

        player.updateInventory();

        cooldownManager.recordSort(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        showSortSuccessActionBar(player);
    }

    private boolean isPlayerInventoryView(InventoryView view) {
        return view.getType().name().contains("CRAFTING") ||
               view.getTopInventory().getType().toString().contains("PLAYER") ||
               view.getTopInventory().getHolder() == view.getPlayer();
    }

    /**
     * Merges duplicate item stacks into as few slots as possible.
     * Respects each item type's max stack size.
     * Items with unique meta (custom names, enchants, etc.) are only
     * merged with identical meta items.
     */
    private ItemStack[] stackItems(ItemStack[] items) {
        // Work on clones so we don't mutate originals mid-process
        List<ItemStack> stacked = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;

            boolean merged = false;

            for (ItemStack existing : stacked) {
                // isSimilar checks type + meta but NOT amount
                if (existing.isSimilar(item) && existing.getAmount() < existing.getMaxStackSize()) {
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    int toAdd = Math.min(space, item.getAmount());
                    existing.setAmount(existing.getAmount() + toAdd);
                    item = item.getAmount() - toAdd > 0
                            ? item.asQuantity(item.getAmount() - toAdd)
                            : null;
                    if (item == null) {
                        merged = true;
                        break;
                    }
                }
            }

            // If there's a remainder (or nothing merged), add what's left
            if (!merged && item != null) {
                stacked.add(item.clone());
            }
        }

        return stacked.toArray(new ItemStack[0]);
    }

    /** Sorts BACKPACK ONLY (slots 9-35). HOTBAR (0-8) preserved! */
    private void sortPlayerBackpackOnly(Player player) {
        ItemStack[] allContents = player.getInventory().getStorageContents();

        // Extract BACKPACK only (slots 9-35 = 27 slots)
        ItemStack[] backpackItems = new ItemStack[27];
        System.arraycopy(allContents, 9, backpackItems, 0, 27);

        // Stack first, then sort
        ItemStack[] stackedBackpack = stackItems(backpackItems);
        ItemStack[] sortedBackpack = Arrays.stream(stackedBackpack)
                .filter(item -> item != null && !item.getType().isAir())
                .sorted(buildItemComparator())
                .toArray(ItemStack[]::new);

        // Rebuild: ORIGINAL hotbar (0-8) + stacked+sorted backpack (9-35)
        ItemStack[] finalContents = new ItemStack[36];

        // Hotbar preserved (slots 0-8)
        System.arraycopy(allContents, 0, finalContents, 0, 9);

        // Sorted backpack fills from slot 9 onward, rest stays null (empty)
        System.arraycopy(sortedBackpack, 0, finalContents, 9, sortedBackpack.length);

        player.getInventory().setStorageContents(finalContents);
    }

    private void sortContainerInventory(Inventory container) {
        ItemStack[] contents = container.getContents();

        // Stack first, then sort
        ItemStack[] stacked = stackItems(contents);
        ItemStack[] sorted = Arrays.stream(stacked)
                .filter(item -> item != null && !item.getType().isAir())
                .sorted(buildItemComparator())
                .toArray(ItemStack[]::new);

        ItemStack[] sortedContents = new ItemStack[contents.length];
        System.arraycopy(sorted, 0, sortedContents, 0, sorted.length);
        container.setContents(sortedContents);
    }

    private Comparator<ItemStack> buildItemComparator() {
        return Comparator.comparing((ItemStack item) -> item.getType().name())
                .thenComparing(item -> {
                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                .serialize(item.getItemMeta().displayName());
                    }
                    return "";
                })
                .thenComparingInt(item -> {
                    if (item.getItemMeta() instanceof Damageable damageable) {
                        return damageable.getDamage();
                    }
                    return 0;
                })
                .thenComparingInt(item -> -item.getAmount());
    }

    private void showSortSuccessActionBar(Player player) {
        Component message = Component.text("✔ Inventory sorted & stacked! Hotbar safe ✓").color(NamedTextColor.GREEN);
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