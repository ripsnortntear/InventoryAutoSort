package com.blackboxai.inventoryautosort.managers;

import com.blackboxai.inventoryautosort.InventoryAutoSort;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player cooldowns to prevent spam-sorting.
 *
 * Cooldown period: 2000ms (2 seconds) between successful sorts.
 * Uses ConcurrentHashMap for thread safety.
 */
public class SortCooldownManager {

    // How long (ms) a player must wait between sorts
    private static final long SORT_COOLDOWN_MS = 2000L;

    // How long (ms) the double-click window is
    public static final long DOUBLE_CLICK_WINDOW_MS = 500L;

    // Stores the timestamp of the player's LAST SUCCESSFUL SORT
    private final Map<UUID, Long> lastSortTime = new ConcurrentHashMap<>();

    // Stores the timestamp of the player's FIRST right-click (for double-click detection)
    private final Map<UUID, Long> firstClickTime = new ConcurrentHashMap<>();

    private final InventoryAutoSort plugin;

    public SortCooldownManager(InventoryAutoSort plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    // -------------------------------------------------------------------------
    // Double-Click Detection
    // -------------------------------------------------------------------------

    /**
     * Records a right-click and determines if it's a valid double-click.
     *
     * Logic:
     *  - If no previous click recorded → record first click, return false
     *  - If previous click exists but > 500ms ago → reset, record new first click, return false
     *  - If previous click exists and ≤ 500ms ago → valid double-click! clear record, return true
     *
     * @param uuid The player's UUID
     * @return true if this click is the second click within the 500ms window
     */
    public boolean registerClick(UUID uuid) {
        long now = System.currentTimeMillis();
        Long firstClick = firstClickTime.get(uuid);

        if (firstClick == null) {
            // No previous click - this is the first click
            firstClickTime.put(uuid, now);
            return false;
        }

        long elapsed = now - firstClick;

        if (elapsed > DOUBLE_CLICK_WINDOW_MS) {
            // Too slow - reset and treat this as a new first click
            firstClickTime.put(uuid, now);
            return false;
        }

        // Valid double-click within window!
        firstClickTime.remove(uuid);
        return true;
    }

    /**
     * Resets the first-click timer for a player.
     * Called when the player closes their inventory.
     *
     * @param uuid The player's UUID
     */
    public void resetClickTimer(UUID uuid) {
        firstClickTime.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Sort Cooldown
    // -------------------------------------------------------------------------

    /**
     * Checks whether a player is currently on sort cooldown.
     *
     * @param uuid The player's UUID
     * @return true if the player must wait before sorting again
     */
    public boolean isOnCooldown(UUID uuid) {
        Long lastSort = lastSortTime.get(uuid);
        if (lastSort == null) return false;
        return (System.currentTimeMillis() - lastSort) < SORT_COOLDOWN_MS;
    }

    /**
     * Returns the remaining cooldown time in milliseconds.
     *
     * @param uuid The player's UUID
     * @return Remaining cooldown in ms, or 0 if not on cooldown
     */
    public long getRemainingCooldown(UUID uuid) {
        Long lastSort = lastSortTime.get(uuid);
        if (lastSort == null) return 0L;
        long remaining = SORT_COOLDOWN_MS - (System.currentTimeMillis() - lastSort);
        return Math.max(0L, remaining);
    }

    /**
     * Records that a player just performed a successful sort.
     * Starts their cooldown timer.
     *
     * @param uuid The player's UUID
     */
    public void recordSort(UUID uuid) {
        lastSortTime.put(uuid, System.currentTimeMillis());
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Removes all data for a specific player.
     * Called when a player leaves the server.
     *
     * @param uuid The player's UUID
     */
    public void removePlayer(UUID uuid) {
        lastSortTime.remove(uuid);
        firstClickTime.remove(uuid);
    }

    /**
     * Clears all stored data. Called on plugin disable.
     */
    public void cleanup() {
        lastSortTime.clear();
        firstClickTime.clear();
    }

    /**
     * Periodic cleanup task to remove stale entries from offline players.
     * Runs every 5 minutes to prevent memory leaks.
     */
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();

                // Remove expired sort cooldowns (older than cooldown period)
                lastSortTime.entrySet().removeIf(entry ->
                        (now - entry.getValue()) > SORT_COOLDOWN_MS * 2
                );

                // Remove stale first-click entries (older than 5 seconds)
                firstClickTime.entrySet().removeIf(entry ->
                        (now - entry.getValue()) > 5000L
                );
            }
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L); // Every 5 minutes
    }
}