package me.ripsnortntear.inventoryautosort.managers;

import me.ripsnortntear.inventoryautosort.InventoryAutoSort;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player cooldowns to prevent spam-sorting and handles
 * double-click timing verification.
 */
public class SortCooldownManager {

    private record ClickRecord(long timestamp, boolean isTopInventory) {}

    // Stale-entry cleanup threshold for click records (ms)
    private static final long CLICK_STALE_THRESHOLD_MS = 5000L;

    // Timestamp of each player's last successful sort
    private final Map<UUID, Long> lastSortTime = new ConcurrentHashMap<>();

    // Record of each player's first right-click
    private final Map<UUID, ClickRecord> firstClickMap = new ConcurrentHashMap<>();

    private final InventoryAutoSort plugin;
    private BukkitTask cleanupTask;

    public SortCooldownManager(InventoryAutoSort plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    /**
     * Registers a right-click and checks if it constitutes a valid double-click
     * within the configured window on the same inventory target (top vs bottom).
     *
     * @param uuid The player's UUID
     * @param isTopInventory Whether the click was in the top (container) or bottom (player) inventory
     * @return true if this is the second click within the window on the same inventory target
     */
    public boolean registerClick(UUID uuid, boolean isTopInventory) {
        long now = System.currentTimeMillis();
        long window = plugin.getConfigManager().getDoubleClickWindowMs();
        ClickRecord previous = firstClickMap.get(uuid);

        if (previous == null) {
            firstClickMap.put(uuid, new ClickRecord(now, isTopInventory));
            return false;
        }

        long elapsed = now - previous.timestamp();

        // If too slow or clicked on a different inventory side (e.g. chest vs player backpack)
        if (elapsed > window || previous.isTopInventory() != isTopInventory) {
            firstClickMap.put(uuid, new ClickRecord(now, isTopInventory));
            return false;
        }

        // Valid double-click on the same target!
        firstClickMap.remove(uuid);
        return true;
    }

    /**
     * Resets the first-click timer for a player.
     */
    public void resetClickTimer(UUID uuid) {
        firstClickMap.remove(uuid);
    }

    /**
     * Checks whether a player is currently on sort cooldown.
     */
    public boolean isOnCooldown(UUID uuid) {
        Long lastSort = lastSortTime.get(uuid);
        if (lastSort == null) return false;
        long cooldown = plugin.getConfigManager().getSortCooldownMs();
        return (System.currentTimeMillis() - lastSort) < cooldown;
    }

    /**
     * Returns remaining cooldown in milliseconds, or 0 if not on cooldown.
     */
    public long getRemainingCooldown(UUID uuid) {
        Long lastSort = lastSortTime.get(uuid);
        if (lastSort == null) return 0L;
        long cooldown = plugin.getConfigManager().getSortCooldownMs();
        long remaining = cooldown - (System.currentTimeMillis() - lastSort);
        return Math.max(0L, remaining);
    }

    /**
     * Records a successful sort for a player.
     */
    public void recordSort(UUID uuid) {
        lastSortTime.put(uuid, System.currentTimeMillis());
    }

    /**
     * Cleans up data for a specific player on disconnect.
     */
    public void removePlayer(UUID uuid) {
        lastSortTime.remove(uuid);
        firstClickMap.remove(uuid);
    }

    /**
     * Shuts down cleanup task and clears state on plugin disable.
     */
    public void cleanup() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        lastSortTime.clear();
        firstClickMap.clear();
    }

    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (isCancelled()) return;

                long now = System.currentTimeMillis();
                long cooldown = plugin.getConfigManager().getSortCooldownMs();

                lastSortTime.entrySet().removeIf(entry ->
                        (now - entry.getValue()) > cooldown * 2
                );

                firstClickMap.entrySet().removeIf(entry ->
                        (now - entry.getValue().timestamp()) > CLICK_STALE_THRESHOLD_MS
                );
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes
    }
}
