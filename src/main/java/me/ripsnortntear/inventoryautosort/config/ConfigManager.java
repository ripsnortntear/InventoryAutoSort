package me.ripsnortntear.inventoryautosort.config;

import me.ripsnortntear.inventoryautosort.InventoryAutoSort;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.inventory.InventoryType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ConfigManager {

    private final InventoryAutoSort plugin;

    private long doubleClickWindowMs;
    private long sortCooldownMs;
    private boolean showActionBar;
    private String actionBarMessage;
    private String cooldownMessage;
    private boolean playSounds;
    private boolean preserveHotbar;

    private boolean containersEnabled;
    private boolean sortVirtualInventories;
    private boolean sortEnderChests;
    private Set<InventoryType> allowedContainerTypes;
    private List<String> blacklistedTitles;
    private List<String> blacklistedHolders;

    private boolean debug;

    public ConfigManager(InventoryAutoSort plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        doubleClickWindowMs = config.getLong("double-click-window", 500L);
        sortCooldownMs = config.getLong("sort-cooldown", 2000L);
        showActionBar = config.getBoolean("show-action-bar", true);
        actionBarMessage = config.getString("action-bar-message", "&a✔ Inventory sorted & stacked! Hotbar safe ✓");
        cooldownMessage = config.getString("cooldown-message", "&cPlease wait &e{time}s &cbefore sorting again!");
        playSounds = config.getBoolean("play-sounds", true);
        preserveHotbar = config.getBoolean("preserve-hotbar", true);

        containersEnabled = config.getBoolean("containers.enabled", true);
        sortVirtualInventories = config.getBoolean("containers.sort-virtual-inventories", false);
        sortEnderChests = config.getBoolean("containers.sort-ender-chests", true);

        List<String> typeStrings = config.getStringList("containers.allowed-types");
        if (typeStrings.isEmpty()) {
            allowedContainerTypes = Set.of(
                    InventoryType.CHEST,
                    InventoryType.BARREL,
                    InventoryType.SHULKER_BOX,
                    InventoryType.ENDER_CHEST,
                    InventoryType.DISPENSER,
                    InventoryType.DROPPER,
                    InventoryType.HOPPER
            );
        } else {
            Set<InventoryType> parsedTypes = new HashSet<>();
            for (String str : typeStrings) {
                try {
                    parsedTypes.add(InventoryType.valueOf(str.trim().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Unknown InventoryType in config: " + str);
                }
            }
            allowedContainerTypes = Collections.unmodifiableSet(parsedTypes);
        }

        blacklistedTitles = config.getStringList("containers.blacklisted-titles").stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        blacklistedHolders = config.getStringList("containers.blacklisted-holders").stream()
                .map(String::toLowerCase)
                .collect(Collectors.toList());

        debug = config.getBoolean("debug", false);
    }

    public long getDoubleClickWindowMs() {
        return doubleClickWindowMs;
    }

    public long getSortCooldownMs() {
        return sortCooldownMs;
    }

    public boolean isShowActionBar() {
        return showActionBar;
    }

    public String getActionBarMessage() {
        return actionBarMessage;
    }

    public String getCooldownMessage() {
        return cooldownMessage;
    }

    public boolean isPlaySounds() {
        return playSounds;
    }

    public boolean isPreserveHotbar() {
        return preserveHotbar;
    }

    public boolean isContainersEnabled() {
        return containersEnabled;
    }

    public boolean isSortVirtualInventories() {
        return sortVirtualInventories;
    }

    public boolean isSortEnderChests() {
        return sortEnderChests;
    }

    public Set<InventoryType> getAllowedContainerTypes() {
        return allowedContainerTypes;
    }

    public List<String> getBlacklistedTitles() {
        return blacklistedTitles;
    }

    public List<String> getBlacklistedHolders() {
        return blacklistedHolders;
    }

    public boolean isDebug() {
        return debug;
    }
}
