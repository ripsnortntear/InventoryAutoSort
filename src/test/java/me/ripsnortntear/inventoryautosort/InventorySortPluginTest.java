package me.ripsnortntear.inventoryautosort;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class InventorySortPluginTest {

    private ServerMock server;
    private InventoryAutoSort plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(InventoryAutoSort.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testPluginLoadsAndEnables() {
        assertNotNull(plugin);
        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getConfigManager());
        assertNotNull(plugin.getCooldownManager());
        assertNotNull(plugin.getPreferenceManager());
    }

    @Test
    void testCommandToggleAndReload() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "inventoryautosort.use", true);
        player.addAttachment(plugin, "inventoryautosort.reload", true);

        // Test toggle off
        player.performCommand("inventorysort toggle");
        assertTrue(plugin.getPreferenceManager().isDisabled(player.getUniqueId()));

        // Test toggle on
        player.performCommand("inventorysort toggle");
        assertFalse(plugin.getPreferenceManager().isDisabled(player.getUniqueId()));

        // Test explicit off
        player.performCommand("inventorysort off");
        assertTrue(plugin.getPreferenceManager().isDisabled(player.getUniqueId()));

        // Test explicit on
        player.performCommand("inventorysort on");
        assertFalse(plugin.getPreferenceManager().isDisabled(player.getUniqueId()));

        // Test reload
        assertTrue(player.performCommand("inventorysort reload"));
    }

    @Test
    void testVirtualVaultIsProtectedFromSorting() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "inventoryautosort.use", true);

        // Create a custom 54-slot virtual inventory representing PlayerVaultZ
        Inventory vault = server.createInventory(null, 54, Component.text("Vault #1"));
        vault.setItem(0, new ItemStack(Material.DIAMOND, 10));
        vault.setItem(1, new ItemStack(Material.COBBLESTONE, 5));
        // Slot 49 is a navigation item (e.g., arrow for next page)
        vault.setItem(49, new ItemStack(Material.ARROW, 1));

        InventoryView view = player.openInventory(vault);
        assertNotNull(view);

        // Simulate double right-click on an empty slot in the vault (e.g. slot 10)
        InventoryClickEvent click1 = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                10,
                ClickType.RIGHT,
                InventoryAction.NOTHING
        );
        server.getPluginManager().callEvent(click1);

        InventoryClickEvent click2 = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                10,
                ClickType.RIGHT,
                InventoryAction.NOTHING
        );
        server.getPluginManager().callEvent(click2);

        // Verify the vault was NOT sorted: navigation arrow remains at slot 49!
        assertNotNull(vault.getItem(49));
        assertEquals(Material.ARROW, vault.getItem(49).getType());
        assertEquals(Material.DIAMOND, vault.getItem(0).getType());
        assertEquals(Material.COBBLESTONE, vault.getItem(1).getType());
    }

    @Test
    void testCursorWithItemDoesNotTriggerSort() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(plugin, "inventoryautosort.use", true);

        Inventory chest = server.createInventory(null, 27, Component.text("Chest"));
        InventoryView view = player.openInventory(chest);
        assertNotNull(view);

        // Simulate holding an item on cursor and right-clicking an empty slot
        InventoryClickEvent click = new InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                12,
                ClickType.RIGHT,
                InventoryAction.PLACE_ONE
        );
        click.setCursor(new ItemStack(Material.DIRT, 64));

        server.getPluginManager().callEvent(click);

        // Click event must NOT be cancelled because the player is placing items
        assertFalse(click.isCancelled());
    }
}
