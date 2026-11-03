package me.ripsnortntear.inventoryautosort;

import me.ripsnortntear.inventoryautosort.commands.SortCommand;
import me.ripsnortntear.inventoryautosort.config.ConfigManager;
import me.ripsnortntear.inventoryautosort.listeners.InventorySortListener;
import me.ripsnortntear.inventoryautosort.managers.PlayerPreferenceManager;
import me.ripsnortntear.inventoryautosort.managers.SortCooldownManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class InventoryAutoSort extends JavaPlugin {

    private static InventoryAutoSort instance;

    private ConfigManager configManager;
    private SortCooldownManager cooldownManager;
    private PlayerPreferenceManager preferenceManager;
    private InventorySortListener sortListener;

    @Override
    public void onEnable() {
        instance = this;

        // Ensure config exists
        saveDefaultConfig();

        // Initialize managers
        configManager = new ConfigManager(this);
        preferenceManager = new PlayerPreferenceManager(this);
        cooldownManager = new SortCooldownManager(this);

        // Register listeners
        sortListener = new InventorySortListener(this, cooldownManager);
        getServer().getPluginManager().registerEvents(sortListener, this);

        // Register commands & tab completer
        PluginCommand cmd = getCommand("inventorysort");
        if (cmd != null) {
            SortCommand sortCommand = new SortCommand(this);
            cmd.setExecutor(sortCommand);
            cmd.setTabCompleter(sortCommand);
        }

        Logger log = getLogger();
        log.info("=========================================");
        log.info(" InventoryAutoSort v" + getDescription().getVersion() + " enabled!");
        log.info(" Double right-click any empty slot to sort!");
        log.info(" Hotbar safe: " + configManager.isPreserveHotbar());
        log.info(" Virtual vault protection: " + (!configManager.isSortVirtualInventories() ? "ACTIVE" : "INACTIVE"));
        log.info("=========================================");
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) {
            cooldownManager.cleanup();
        }
        if (preferenceManager != null) {
            preferenceManager.save();
        }
        getLogger().info("InventoryAutoSort disabled. Goodbye!");
    }

    public static InventoryAutoSort getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public SortCooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public PlayerPreferenceManager getPreferenceManager() {
        return preferenceManager;
    }
}
