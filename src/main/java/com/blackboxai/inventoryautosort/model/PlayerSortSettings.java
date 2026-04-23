package com.blackboxai.inventoryautosort.model;

/**
 * Holds all sort-related preferences for a single player.
 * This is the data model that gets saved/loaded from playerdata/<uuid>.yml
 */
public class PlayerSortSettings {

    /**
     * The three available sorting modes.
     *
     * TYPE   — sort alphabetically by Material name (original behaviour)
     * NAME   — sort alphabetically by custom display name, falling back to type
     * RARITY — sort by item rarity (Common → Uncommon → Rare → Epic)
     *          then alphabetically within each rarity tier
     */
    public enum SortMode {
        TYPE, NAME, RARITY;

        /**
         * Case-insensitive parse with a safe fallback to TYPE.
         */
        public static SortMode fromString(String value) {
            if (value == null) return TYPE;
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return TYPE;
            }
        }
    }

    // ---------------------
    // Fields
    // ---------------------

    /** Master toggle — if false, no sorting happens at all for this player. */
    private boolean sortingEnabled;

    /** Whether to sort the player's own backpack (slots 9-35). */
    private boolean sortBackpack;

    /** Whether to sort external containers (chests, barrels, shulkers, etc.). */
    private boolean sortChests;

    /** Which comparator strategy to use when ordering items. */
    private SortMode sortMode;

    // ---------------------
    // Constructor
    // ---------------------

    public PlayerSortSettings(boolean sortingEnabled,
                               boolean sortBackpack,
                               boolean sortChests,
                               SortMode sortMode) {
        this.sortingEnabled = sortingEnabled;
        this.sortBackpack   = sortBackpack;
        this.sortChests     = sortChests;
        this.sortMode       = sortMode;
    }

    // ---------------------
    // Getters & Setters
    // ---------------------

    public boolean isSortingEnabled() { return sortingEnabled; }
    public void setSortingEnabled(boolean sortingEnabled) {
        this.sortingEnabled = sortingEnabled;
    }

    public boolean isSortBackpack() { return sortBackpack; }
    public void setSortBackpack(boolean sortBackpack) {
        this.sortBackpack = sortBackpack;
    }

    public boolean isSortChests() { return sortChests; }
    public void setSortChests(boolean sortChests) {
        this.sortChests = sortChests;
    }

    public SortMode getSortMode() { return sortMode; }
    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode != null ? sortMode : SortMode.TYPE;
    }
}