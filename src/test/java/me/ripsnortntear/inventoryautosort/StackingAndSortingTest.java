package me.ripsnortntear.inventoryautosort;

import me.ripsnortntear.inventoryautosort.listeners.InventorySortListener;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class StackingAndSortingTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void testStackItemsMergesDuplicates() {
        ItemStack[] items = new ItemStack[]{
                new ItemStack(Material.COBBLESTONE, 30),
                new ItemStack(Material.DIRT, 10),
                new ItemStack(Material.COBBLESTONE, 40)
        };

        ItemStack[] stacked = InventorySortListener.stackItems(items);

        // 30 + 40 cobblestone should produce 1 full stack of 64 and 1 leftover stack of 6
        int totalCobble = 0;
        int cobbleStacks = 0;
        for (ItemStack item : stacked) {
            if (item.getType() == Material.COBBLESTONE) {
                totalCobble += item.getAmount();
                cobbleStacks++;
            }
        }

        assertEquals(70, totalCobble);
        assertEquals(2, cobbleStacks);
        assertEquals(64, stacked[0].getAmount());
    }

    @Test
    void testStackItemsDoesNotMergeMaps() {
        ItemStack[] items = new ItemStack[]{
                new ItemStack(Material.MAP, 1),
                new ItemStack(Material.MAP, 1)
        };

        ItemStack[] stacked = InventorySortListener.stackItems(items);
        assertEquals(2, stacked.length);
        assertEquals(1, stacked[0].getAmount());
        assertEquals(1, stacked[1].getAmount());
    }

    @Test
    void testStackItemsDoesNotMergeSwords() {
        ItemStack[] items = new ItemStack[]{
                new ItemStack(Material.DIAMOND_SWORD, 1),
                new ItemStack(Material.DIAMOND_SWORD, 1)
        };

        ItemStack[] stacked = InventorySortListener.stackItems(items);
        assertEquals(2, stacked.length);
        assertEquals(1, stacked[0].getAmount());
        assertEquals(1, stacked[1].getAmount());
    }

    @Test
    void testComparatorAlphabeticalOrder() {
        ItemStack[] items = new ItemStack[]{
                new ItemStack(Material.STONE, 10),
                new ItemStack(Material.APPLE, 5),
                new ItemStack(Material.DIRT, 20)
        };

        Arrays.sort(items, InventorySortListener.buildItemComparator());

        assertEquals(Material.APPLE, items[0].getType());
        assertEquals(Material.DIRT, items[1].getType());
        assertEquals(Material.STONE, items[2].getType());
    }
}
