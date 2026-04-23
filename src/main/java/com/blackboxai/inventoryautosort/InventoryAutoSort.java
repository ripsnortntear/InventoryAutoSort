package com.blackboxai.inventoryautosort;

import com.blackboxai.inventoryautosort.gui.SortSettingsGUI;
import com.blackboxai.inventoryautosort.listeners.InventorySortListener;
import com.blackboxai.inventoryautosort.managers.PlayerSettingsManager;
import com.blackboxai.inventoryautosort.managers.SortCooldownManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public final class InventoryAutoSort extends JavaPlugin implements CommandExecutor {

    private static InventoryAutoSort instance;
    private SortCooldownManager cooldownManager;
    private PlayerSettingsManager settingsManager;
    private SortSettingsGUI settingsGUI;

    @Override
    public void onEnable() {
        instance = this;

        // Generate default config.yml if it doesn't exist
        saveDefaultConfig();

        // Initialize managers
        cooldownManager  = new SortCooldownManager(this);
        settingsManager  = new PlayerSettingsManager(this);

        // Initialize GUI (also a Listener)
        settingsGUI = new SortSettingsGUI(this, settingsManager);

        // Register listeners
        InventorySortListener sortListener =
                new InventorySortListener(this, cooldownManager, settingsManager);
        getServer().getPluginManager().registerEvents(sortListener, this);
        getServer().getPluginManager().registerEvents(settingsGUI, this);

        // Register commands
        if (getCommand("autosort") != null) {
            getCommand("autosort").setExecutor(this);
        }

        Logger log = getLogger();
        log.info("=================================");
        log.info(" InventoryAutoSort v" + getDescription().getVersion() + " enabled!");
        log.info(" Double right-click ANY empty slot to sort!");
        log.info(" Use /autosort to open the settings menu.");
        log.info("=================================");
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) cooldownManager.cleanup();
        if (settingsManager  != null) settingsManager.cleanup();
        getLogger().info("InventoryAutoSort disabled. Goodbye!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        // Open the settings GUI — no permission required
        settingsGUI.open(player);
        return true;
    }

    // -------------------
    // Public API
    // -------------------

    public static InventoryAutoSort getInstance() {
        return instance;
    }

    public SortCooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public PlayerSettingsManager getSettingsManager() {
        return settingsManager;
    }
}