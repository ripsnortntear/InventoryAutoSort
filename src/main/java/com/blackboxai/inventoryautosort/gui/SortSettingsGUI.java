package com.blackboxai.inventoryautosort.gui;

import com.blackboxai.inventoryautosort.InventoryAutoSort;
import com.blackboxai.inventoryautosort.managers.PlayerSettingsManager;
import com.blackboxai.inventoryautosort.model.PlayerSortSettings;
import com.blackboxai.inventoryautosort.model.PlayerSortSettings.SortMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SortSettingsGUI implements Listener {

    // -------------------
    // Slot indices
    // -------------------
    private static final int SLOT_BANNER       = 4;
    private static final int SLOT_TOGGLE_ALL   = 10;
    private static final int SLOT_TOGGLE_BACK  = 12;
    private static final int SLOT_TOGGLE_CHEST = 14;
    private static final int SLOT_MODE_TYPE    = 20;
    private static final int SLOT_MODE_NAME    = 22;
    private static final int SLOT_MODE_RARITY  = 24;

    /** Slots that are purely decorative and should never trigger actions. */
    private static final Set<Integer> DECORATIVE_SLOTS = new HashSet<>(Arrays.asList(
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 11, 13, 15, 16, 17,
            18, 19, 21, 23, 25, 26
    ));

    // -------------------
    // GUI title
    // -------------------
    private static final Component GUI_TITLE = Component.text("AutoSort Settings")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.BOLD, true)
            .decoration(TextDecoration.ITALIC, false);

    // -------------------
    // Dependencies
    // -------------------
    private final InventoryAutoSort plugin;
    private final PlayerSettingsManager settingsManager;

    /** Tracks which players currently have our GUI open. */
    private final Set<UUID> openGUIs = new HashSet<>();

    public SortSettingsGUI(InventoryAutoSort plugin, PlayerSettingsManager settingsManager) {
        this.plugin         = plugin;
        this.settingsManager = settingsManager;
    }

    // -------------------
    // Open GUI
    // -------------------

    public void open(Player player) {
        PlayerSortSettings settings = settingsManager.getSettings(player.getUniqueId());

        Inventory gui = Bukkit.createInventory(null, 27, GUI_TITLE);

        fillBorders(gui);

        gui.setItem(SLOT_BANNER,       makeBanner());
        gui.setItem(SLOT_TOGGLE_ALL,   makeToggleAll(settings.isSortingEnabled()));
        gui.setItem(SLOT_TOGGLE_BACK,  makeToggleBackpack(settings.isSortBackpack()));
        gui.setItem(SLOT_TOGGLE_CHEST, makeToggleChests(settings.isSortChests()));
        gui.setItem(SLOT_MODE_TYPE,    makeModeItem(SortMode.TYPE,   settings.getSortMode()));
        gui.setItem(SLOT_MODE_NAME,    makeModeItem(SortMode.NAME,   settings.getSortMode()));
        gui.setItem(SLOT_MODE_RARITY,  makeModeItem(SortMode.RARITY, settings.getSortMode()));

        openGUIs.add(player.getUniqueId());
        player.openInventory(gui);
    }

    // -------------------
    // Event: Click inside GUI
    // -------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Primary guard
        if (!openGUIs.contains(player.getUniqueId())) return;

        // Cancel ALL clicks while our GUI is open
        event.setCancelled(true);

        // Ignore clicks outside the top inventory
        if (event.getClickedInventory() == null) return;
        if (!isOurGUI(event.getClickedInventory())) return;

        int slot = event.getSlot();
        if (DECORATIVE_SLOTS.contains(slot)) return;

        UUID uuid     = player.getUniqueId();
        PlayerSortSettings settings = settingsManager.getSettings(uuid);
        Inventory gui = event.getView().getTopInventory();

        switch (slot) {

            case SLOT_TOGGLE_ALL -> {
                boolean newValue = !settings.isSortingEnabled();
                settings.setSortingEnabled(newValue);
                settingsManager.saveSettings(uuid, settings);
                refreshGUI(gui, settings);
                playToggleSound(player, newValue);
                player.sendActionBar(newValue
                        ? Component.text("Sorting enabled!").color(NamedTextColor.GREEN)
                        : Component.text("Sorting disabled.").color(NamedTextColor.RED));
            }

            case SLOT_TOGGLE_BACK -> {
                boolean newValue = !settings.isSortBackpack();
                settings.setSortBackpack(newValue);
                settingsManager.saveSettings(uuid, settings);
                gui.setItem(SLOT_TOGGLE_BACK, makeToggleBackpack(newValue));
                playToggleSound(player, newValue);
                player.sendActionBar(newValue
                        ? Component.text("Backpack sorting enabled!").color(NamedTextColor.GREEN)
                        : Component.text("Backpack sorting disabled.").color(NamedTextColor.RED));
            }

            case SLOT_TOGGLE_CHEST -> {
                boolean newValue = !settings.isSortChests();
                settings.setSortChests(newValue);
                settingsManager.saveSettings(uuid, settings);
                gui.setItem(SLOT_TOGGLE_CHEST, makeToggleChests(newValue));
                playToggleSound(player, newValue);
                player.sendActionBar(newValue
                        ? Component.text("Chest sorting enabled!").color(NamedTextColor.GREEN)
                        : Component.text("Chest sorting disabled.").color(NamedTextColor.RED));
            }

            case SLOT_MODE_TYPE -> {
                settings.setSortMode(SortMode.TYPE);
                settingsManager.saveSettings(uuid, settings);
                refreshModeButtons(gui, SortMode.TYPE);
                playSelectSound(player);
                player.sendActionBar(
                        Component.text("Sort mode: ").color(NamedTextColor.GRAY)
                                .append(Component.text("Type").color(NamedTextColor.AQUA)));
            }

            case SLOT_MODE_NAME -> {
                settings.setSortMode(SortMode.NAME);
                settingsManager.saveSettings(uuid, settings);
                refreshModeButtons(gui, SortMode.NAME);
                playSelectSound(player);
                player.sendActionBar(
                        Component.text("Sort mode: ").color(NamedTextColor.GRAY)
                                .append(Component.text("Name").color(NamedTextColor.YELLOW)));
            }

            case SLOT_MODE_RARITY -> {
                settings.setSortMode(SortMode.RARITY);
                settingsManager.saveSettings(uuid, settings);
                refreshModeButtons(gui, SortMode.RARITY);
                playSelectSound(player);
                player.sendActionBar(
                        Component.text("Sort mode: ").color(NamedTextColor.GRAY)
                                .append(Component.text("Rarity").color(NamedTextColor.LIGHT_PURPLE)));
            }

            default -> { /* ignore */ }
        }
    }

    // -------------------
    // Event: GUI Closed
    // -------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        openGUIs.remove(player.getUniqueId());
    }

    // -------------------
    // GUI Refresh Helpers
    // -------------------

    private void refreshGUI(Inventory gui, PlayerSortSettings settings) {
        gui.setItem(SLOT_TOGGLE_ALL,   makeToggleAll(settings.isSortingEnabled()));
        gui.setItem(SLOT_TOGGLE_BACK,  makeToggleBackpack(settings.isSortBackpack()));
        gui.setItem(SLOT_TOGGLE_CHEST, makeToggleChests(settings.isSortChests()));
        gui.setItem(SLOT_MODE_TYPE,    makeModeItem(SortMode.TYPE,   settings.getSortMode()));
        gui.setItem(SLOT_MODE_NAME,    makeModeItem(SortMode.NAME,   settings.getSortMode()));
        gui.setItem(SLOT_MODE_RARITY,  makeModeItem(SortMode.RARITY, settings.getSortMode()));
    }

    private void refreshModeButtons(Inventory gui, SortMode selected) {
        gui.setItem(SLOT_MODE_TYPE,   makeModeItem(SortMode.TYPE,   selected));
        gui.setItem(SLOT_MODE_NAME,   makeModeItem(SortMode.NAME,   selected));
        gui.setItem(SLOT_MODE_RARITY, makeModeItem(SortMode.RARITY, selected));
    }

    // -------------------
    // Sound Helpers
    // -------------------

    private void playToggleSound(Player player, boolean enabled) {
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_LOOM_SELECT_PATTERN,
                0.6f, enabled ? 1.3f : 0.8f);
    }

    private void playSelectSound(Player player) {
        player.playSound(player.getLocation(),
                org.bukkit.Sound.UI_LOOM_SELECT_PATTERN,
                0.6f, 1.0f);
    }

    // -------------------
    // Item Builders
    // -------------------

    private ItemStack makeBanner() {
        return buildItem(
                Material.BOOKSHELF,
                Component.text("AutoSort Settings")
                        .color(NamedTextColor.GOLD)
                        .decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Configure your sorting preferences.")
                                .color(NamedTextColor.GRAY)
                )
        );
    }

    private ItemStack makeToggleAll(boolean enabled) {
        return buildItem(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Inventory Sorting")
                        .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Master toggle for all sorting.")
                                .color(NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Status: ").color(NamedTextColor.GRAY)
                                .append(enabled
                                        ? Component.text("ENABLED").color(NamedTextColor.GREEN)
                                        : Component.text("DISABLED").color(NamedTextColor.RED)),
                        Component.empty(),
                        Component.text("Click to toggle.").color(NamedTextColor.YELLOW)
                )
        );
    }

    private ItemStack makeToggleBackpack(boolean enabled) {
        return buildItem(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Sort Backpack")
                        .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Sort your personal inventory")
                                .color(NamedTextColor.GRAY),
                        Component.text("(slots 9-35, hotbar preserved).")
                                .color(NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Status: ").color(NamedTextColor.GRAY)
                                .append(enabled
                                        ? Component.text("ENABLED").color(NamedTextColor.GREEN)
                                        : Component.text("DISABLED").color(NamedTextColor.RED)),
                        Component.empty(),
                        Component.text("Click to toggle.").color(NamedTextColor.YELLOW)
                )
        );
    }

    private ItemStack makeToggleChests(boolean enabled) {
        return buildItem(
                enabled ? Material.LIME_DYE : Material.GRAY_DYE,
                Component.text("Sort Chests & Containers")
                        .color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                        .decoration(TextDecoration.BOLD, true),
                List.of(
                        Component.text("Sort chests, barrels, shulker boxes,")
                                .color(NamedTextColor.GRAY),
                        Component.text("and other containers.")
                                .color(NamedTextColor.GRAY),
                        Component.empty(),
                        Component.text("Status: ").color(NamedTextColor.GRAY)
                                .append(enabled
                                        ? Component.text("ENABLED").color(NamedTextColor.GREEN)
                                        : Component.text("DISABLED").color(NamedTextColor.RED)),
                        Component.empty(),
                        Component.text("Click to toggle.").color(NamedTextColor.YELLOW)
                )
        );
    }

    private ItemStack makeModeItem(SortMode mode, SortMode currentMode) {
        boolean isSelected = mode == currentMode;

        Material material;
        Component name;
        List<Component> lore;

        switch (mode) {
            case TYPE -> {
                material = isSelected
                        ? Material.CYAN_STAINED_GLASS_PANE
                        : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                name = Component.text("Sort by: Type")
                        .color(isSelected ? NamedTextColor.AQUA : NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, isSelected);
                lore = List.of(
                        Component.text("Groups items by their material type.")
                                .color(NamedTextColor.GRAY),
                        Component.text("e.g. all swords together, all logs together.")
                                .color(NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        isSelected
                                ? Component.text("Currently selected.").color(NamedTextColor.GREEN)
                                : Component.text("Click to select.").color(NamedTextColor.YELLOW)
                );
            }
            case NAME -> {
                material = isSelected
                        ? Material.YELLOW_STAINED_GLASS_PANE
                        : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                name = Component.text("Sort by: Name")
                        .color(isSelected ? NamedTextColor.YELLOW : NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, isSelected);
                lore = List.of(
                        Component.text("Sorts items alphabetically by their")
                                .color(NamedTextColor.GRAY),
                        Component.text("display name (or type name if unnamed).")
                                .color(NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        isSelected
                                ? Component.text("Currently selected.").color(NamedTextColor.GREEN)
                                : Component.text("Click to select.").color(NamedTextColor.YELLOW)
                );
            }
            case RARITY -> {
                material = isSelected
                        ? Material.PURPLE_STAINED_GLASS_PANE
                        : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
                name = Component.text("Sort by: Rarity")
                        .color(isSelected ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GRAY)
                        .decoration(TextDecoration.BOLD, isSelected);
                lore = List.of(
                        Component.text("Sorts items by rarity tier:")
                                .color(NamedTextColor.GRAY),
                        Component.text("Common > Uncommon > Rare > Epic")
                                .color(NamedTextColor.DARK_GRAY),
                        Component.text("then alphabetically within each tier.")
                                .color(NamedTextColor.DARK_GRAY),
                        Component.empty(),
                        isSelected
                                ? Component.text("Currently selected.").color(NamedTextColor.GREEN)
                                : Component.text("Click to select.").color(NamedTextColor.YELLOW)
                );
            }
            default -> {
                material = Material.BARRIER;
                name     = Component.text("Unknown Mode").color(NamedTextColor.RED);
                lore     = List.of();
            }
        }

        return buildItem(material, name, lore);
    }

    private void fillBorders(Inventory gui) {
        ItemStack pane = buildItem(
                Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" "),
                List.of()
        );
        for (int slot : DECORATIVE_SLOTS) {
            gui.setItem(slot, pane);
        }
    }

    /**
     * Builds an ItemStack with a display name and lore.
     * Italic is explicitly disabled — Paper 1.21.4 applies italic by
     * default to all custom item names and lore lines in GUIs.
     */
    private ItemStack buildItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();

        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));

            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                        .map(line -> line.decoration(TextDecoration.ITALIC, false))
                        .toList());
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Checks whether a given inventory is our settings GUI.
     * The openGUIs set is the PRIMARY guard.
     * This is a secondary size + type check to filter out other inventories.
     */
    private boolean isOurGUI(Inventory inventory) {
        if (inventory == null) return false;
        if (inventory.getSize() != 27) return false;
        return inventory.getType() == InventoryType.CHEST;
    }
}