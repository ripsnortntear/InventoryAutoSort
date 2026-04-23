package com.blackboxai.inventoryautosort.managers;

import com.blackboxai.inventoryautosort.InventoryAutoSort;
import com.blackboxai.inventoryautosort.model.PlayerSortSettings;
import com.blackboxai.inventoryautosort.model.PlayerSortSettings.SortMode;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Loads, caches, and saves per-player sort settings.
 *
 * Storage layout:
 *   plugins/InventoryAutoSort/playerdata/<uuid>.yml
 *
 * Each file contains:
 *   SortInventory: true/false
 *   SortBackpack:  true/false
 *   SortChests:    true/false
 *   SortType:      Type | Name | Rarity
 *
 * Server defaults come from config.yml and are applied only when a
 * player's data file does not yet exist (i.e. first join).
 */
public class PlayerSettingsManager {

    private final InventoryAutoSort plugin;

    /** In-memory cache — avoids disk I/O on every sort event. */
    private final Map<UUID, PlayerSortSettings> cache = new ConcurrentHashMap<>();

    /** The playerdata/ sub-folder inside the plugin's data folder. */
    private final File playerDataFolder;

    // config.yml keys
    private static final String CFG_SORT_INVENTORY = "SortInventory";
    private static final String CFG_SORT_BACKPACK   = "SortBackpack";
    private static final String CFG_SORT_CHESTS     = "SortChests";
    private static final String CFG_SORT_TYPE       = "SortType";

    public PlayerSettingsManager(InventoryAutoSort plugin) {
        this.plugin = plugin;
        this.playerDataFolder = new File(plugin.getDataFolder(), "playerdata");

        // Create the playerdata directory if it doesn't exist yet
        if (!playerDataFolder.exists()) {
            playerDataFolder.mkdirs();
        }
    }

    // ---------------------
    // Public API
    // ---------------------

    /**
     * Returns the settings for a player, loading from disk if not cached.
     * If no file exists yet, server defaults from config.yml are used and
     * immediately saved so the file is created on first join.
     *
     * @param uuid The player's UUID.
     * @return The player's current {@link PlayerSortSettings}.
     */
    public PlayerSortSettings getSettings(UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadOrCreate);
    }

    /**
     * Persists a player's settings to disk asynchronously.
     *
     * @param uuid     The player's UUID.
     * @param settings The settings to save.
     */
    public void saveSettings(UUID uuid, PlayerSortSettings settings) {
        cache.put(uuid, settings);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            writeSettingsToDisk(uuid, settings);
        });
    }

    /**
     * Removes a player's settings from the in-memory cache.
     * Their data file on disk is preserved for the next login.
     * Call this when a player disconnects.
     *
     * @param uuid The player's UUID.
     */
    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    /**
     * Clears the entire in-memory cache.
     * Called on plugin disable — disk files are already up to date
     * because we save asynchronously on every change.
     */
    public void cleanup() {
        cache.clear();
    }

    // ---------------------
    // Internal — Load / Save
    // ---------------------

    /**
     * Attempts to load settings from disk.
     * Falls back to server defaults (config.yml) if no file exists.
     */
    private PlayerSortSettings loadOrCreate(UUID uuid) {
        File file = getPlayerFile(uuid);

        if (!file.exists()) {
            // First time this player has used the plugin on this server
            PlayerSortSettings defaults = buildFromServerDefaults();
            writeSettingsToDisk(uuid, defaults);
            return defaults;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        boolean sortInventory = yaml.getBoolean(CFG_SORT_INVENTORY, true);
        boolean sortBackpack  = yaml.getBoolean(CFG_SORT_BACKPACK,  true);
        boolean sortChests    = yaml.getBoolean(CFG_SORT_CHESTS,    true);
        SortMode sortMode     = SortMode.fromString(yaml.getString(CFG_SORT_TYPE, "Type"));

        return new PlayerSortSettings(sortInventory, sortBackpack, sortChests, sortMode);
    }

    /**
     * Writes a player's settings to their YAML file on disk.
     * Safe to call from an async thread.
     */
    private void writeSettingsToDisk(UUID uuid, PlayerSortSettings settings) {
        File file = getPlayerFile(uuid);
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set(CFG_SORT_INVENTORY, settings.isSortingEnabled());
        yaml.set(CFG_SORT_BACKPACK,  settings.isSortBackpack());
        yaml.set(CFG_SORT_CHESTS,    settings.isSortChests());
        yaml.set(CFG_SORT_TYPE,      settings.getSortMode().name());

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save settings for player " + uuid, e);
        }
    }

    /**
     * Reads the server-wide defaults from config.yml.
     * These are only applied to players who have no existing data file.
     */
    private PlayerSortSettings buildFromServerDefaults() {
        boolean sortInventory = plugin.getConfig().getBoolean(CFG_SORT_INVENTORY, true);
        boolean sortBackpack  = plugin.getConfig().getBoolean(CFG_SORT_BACKPACK,  true);
        boolean sortChests    = plugin.getConfig().getBoolean(CFG_SORT_CHESTS,    true);
        SortMode sortMode     = SortMode.fromString(
                plugin.getConfig().getString(CFG_SORT_TYPE, "Type"));

        return new PlayerSortSettings(sortInventory, sortBackpack, sortChests, sortMode);
    }

    private File getPlayerFile(UUID uuid) {
        return new File(playerDataFolder, uuid.toString() + ".yml");
    }
}