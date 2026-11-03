package me.ripsnortntear.inventoryautosort.managers;

import me.ripsnortntear.inventoryautosort.InventoryAutoSort;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages and persists player auto-sort toggle preferences.
 */
public class PlayerPreferenceManager {

    private final InventoryAutoSort plugin;
    private final File file;
    private final Set<UUID> disabledPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public PlayerPreferenceManager(InventoryAutoSort plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "disabled-players.yml");
        load();
    }

    public void load() {
        disabledPlayers.clear();
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        List<String> uuids = config.getStringList("disabled");
        for (String raw : uuids) {
            try {
                disabledPlayers.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        List<String> list = disabledPlayers.stream().map(UUID::toString).toList();
        config.set("disabled", list);
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save disabled-players.yml: " + e.getMessage());
        }
    }

    public void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public boolean isDisabled(UUID uuid) {
        return disabledPlayers.contains(uuid);
    }

    public boolean toggle(UUID uuid) {
        boolean nowDisabled;
        if (disabledPlayers.contains(uuid)) {
            disabledPlayers.remove(uuid);
            nowDisabled = false;
        } else {
            disabledPlayers.add(uuid);
            nowDisabled = true;
        }
        saveAsync();
        return nowDisabled;
    }

    public void setDisabled(UUID uuid, boolean disabled) {
        if (disabled) {
            disabledPlayers.add(uuid);
        } else {
            disabledPlayers.remove(uuid);
        }
        saveAsync();
    }
}
