package com.blackboxai.inventoryautosort;

import com.blackboxai.inventoryautosort.listeners.InventorySortListener;
import com.blackboxai.inventoryautosort.managers.SortCooldownManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class InventoryAutoSort extends JavaPlugin implements CommandExecutor {

    private static InventoryAutoSort instance;
    private SortCooldownManager cooldownManager;
    private InventorySortListener sortListener;

    // Players who have disabled the feature via /inventorysort toggle
    private final Set<UUID> disabledPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;

        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Initialize managers
        cooldownManager = new SortCooldownManager(this);

        // Register listeners
        sortListener = new InventorySortListener(this, cooldownManager);
        getServer().getPluginManager().registerEvents(sortListener, this);

        // Register commands
        if (getCommand("inventorysort") != null) {
            getCommand("inventorysort").setExecutor(this);
        }

        Logger log = getLogger();
        log.info("=================================");
        log.info(" InventoryAutoSort v" + getDescription().getVersion() + " enabled!");
        log.info(" Double right-click ANY empty slot to sort!");
        log.info(" Supports: Chest, Barrel, Shulker, Player Inv, & more!");
        log.info("=================================");
    }

    @Override
    public void onDisable() {
        if (cooldownManager != null) {
            cooldownManager.cleanup();
        }
        disabledPlayers.clear();
        getLogger().info("InventoryAutoSort disabled. Goodbye!");
    }

    // -------------------------------------------------------------------------
    // Command Handler - /inventorysort (toggle on/off per player)
    // -------------------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this command.");
            return true;
        }

        if (!player.hasPermission("inventoryautosort.use")) {
            player.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            player.sendMessage("§aInventoryAutoSort §fenabled! "
                    + "§7Double right-click ANY empty slot to sort!");
        } else {
            disabledPlayers.add(uuid);
            player.sendMessage("§cInventoryAutoSort §fdisabled. "
                    + "§7Use §e/inventorysort §7to re-enable.");
        }

        return true;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the singleton instance of this plugin.
     */
    public static InventoryAutoSort getInstance() {
        return instance;
    }

    /**
     * Checks whether a player has disabled the sort feature.
     *
     * @param uuid The player's UUID
     * @return true if the player has disabled the feature
     */
    public boolean isDisabled(UUID uuid) {
        return disabledPlayers.contains(uuid);
    }

    /**
     * Returns the cooldown manager instance.
     */
    public SortCooldownManager getCooldownManager() {
        return cooldownManager;
    }
}