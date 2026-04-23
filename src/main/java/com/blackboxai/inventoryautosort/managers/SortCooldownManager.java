package com.blackboxai.inventoryautosort.managers;

import com.blackboxai.inventoryautosort.InventoryAutoSort;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player cooldowns to prevent spam-sorting.
 *
 * Sort cooldown  : 2000 ms (2 seconds) between successful sorts.
 * Double-click window : 500 ms between first and second right-click.
 *
 * Uses ConcurrentHashMap for thread safety since the cleanup task
 * runs asynchronously.
 */
public class SortCooldownManager {

    // How long (ms) a player must wait between sorts
    private static final long SORT_COOLDOWN_MS = 2000L;

    // How long (ms) the double-click detection window is
    public static final long DOUBLE_CLICK_WINDOW_MS = 500L;

    // Stale-entry cleanup threshold for firstClickTime (ms)
    private static final long CLICK_STALE_THRESHOLD_MS = 5000L;

    // Timestamp of each player's last SUCCESSFUL sort
    private final Map<UUID, Long> lastSortTime = new ConcurrentHashMap<>();

    // Timestamp of each player's FIRST right-click (double-click detection)
    private final Map<UUID, Long> firstClickTime = new ConcurrentHashMap<>();

    private final InventoryAutoSort plugin;

    // Reference kept so we can cancel the task cleanly on plugin disable
    private BukkitTask cleanupTask;

    public SortCooldownManager(InventoryAutoSort plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    // -------------------------------------------------------------------------
    // Double-Click Detection
    // -------------------------------------------------------------------------

    /**
     * Records a right-click and determines whether it is a valid double-click.
     *
     * Logic:
     *  • No previous click recorded  → record as first click, return false.
     *  • Previous click > 500 ms ago → reset, record as new first click, return false.
     *  • Previous click ≤ 500 ms ago → valid double-click! clear record, return true.
     *
     * @param uuid The clicking player's UUID.
     * @return {@code true} if this is the second click within the 500 ms window.
     */
    public boolean registerClick(UUID uuid) {
        long now        = System.currentTimeMillis();
        Long firstClick = firstClickTime.get(uuid);

        if (firstClick == null) {
            // No previous click — record this as the first
            firstClickTime.put(uuid, now);
            return false;
        }

        long elapsed = now - firstClick;

        if (elapsed > DOUBLE_CLICK_WINDOW_MS) {
            // Clicked too slowly — reset window, treat this as a new first click
            firstClickTime.put(uuid, now);
            return false;
        }

        // Valid double-click within the window
        firstClickTime.remove(uuid);
        return true;
    }

    /**
     * Clears the first-click timer for a player.
     * Should be called when the player closes their inventory so a stale
     * first-click cannot accidentally trigger a sort on the next open.
     *
     * @param uuid The player's UUID.
     */
    public void resetClickTimer(UUID uuid) {
        firstClickTime.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Sort Cooldown
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the player must wait before sorting again.
     *
     * @param uuid The player's UUID.
     */
    public boolean isOnCooldown(UUID uuid) {
        Long lastSort = lastSortTime.get(uuid);
        if (lastSort == null) return false;
        return (System.currentTimeMillis() - lastSort) < SORT_COOLDOWN_MS;
    }

    /**
     * Returns the remaining cooldown time in milliseconds, or 0 if none.
     *
     * @param uuid The player's UUID.
     */
    public long getRemainingCooldown(UUID uuid) {
        Long lastSort = lastSortTime.get(uuid);
        if (lastSort == null) return 0L;
        long remaining = SORT_COOLDOWN_MS - (System.currentTimeMillis() - lastSort);
        return Math.max(0L, remaining);
    }

    /**
     * Records that a player just performed a successful sort,
     * starting their cooldown timer.
     *
     * @param uuid The player's UUID.
     */
    public void recordSort(UUID uuid) {
        lastSortTime.put(uuid, System.currentTimeMillis());
    }

    // -------------------------------------------------------------------------
    // Player Cleanup
    // -------------------------------------------------------------------------

    /**
     * Removes all stored data for a specific player.
     * Call this when the player leaves the server.
     *
     * @param uuid The player's UUID.
     */
    public void removePlayer(UUID uuid) {
        lastSortTime.remove(uuid);
        firstClickTime.remove(uuid);
    }

    /**
     * Clears ALL stored data and cancels the background cleanup task.
     * Called by {@link com.blackboxai.inventoryautosort.InventoryAutoSort#onDisable()}.
     */
    public void cleanup() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }
        lastSortTime.clear();
        firstClickTime.clear();
    }

    // -------------------------------------------------------------------------
    // Background Cleanup Task
    // -------------------------------------------------------------------------

    /**
     * Starts a repeating async task that removes stale map entries every
     * 5 minutes, preventing memory leaks from players who disconnected
     * without triggering {@link #removePlayer(UUID)}.
     */
    private void startCleanupTask() {
        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Guard: stop silently if the task was cancelled during shutdown
                if (isCancelled()) return;

                long now = System.currentTimeMillis();

                // Remove sort-cooldown entries that have long since expired
                lastSortTime.entrySet().removeIf(entry ->
                        (now - entry.getValue()) > SORT_COOLDOWN_MS * 2
                );

                // Remove stale first-click entries (older than 5 seconds)
                firstClickTime.entrySet().removeIf(entry ->
                        (now - entry.getValue()) > CLICK_STALE_THRESHOLD_MS
                );
            }
        // 6000 ticks = 5 minutes at 20 ticks/sec
        }.runTaskTimerAsynchronously(plugin, 6000L, 6000L);
    }
}package com.blackboxai.inventoryautosort.managers;

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
