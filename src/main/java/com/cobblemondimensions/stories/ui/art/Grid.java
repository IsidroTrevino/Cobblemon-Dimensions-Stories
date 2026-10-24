package com.cobblemondimensions.stories.ui.art;

/**
 * The fixed geometry of a vanilla chest menu, in pixels from its top-left corner.
 * Artwork is lined up against these numbers so a button's picture sits exactly
 * over the slot that catches its click.
 */
public final class Grid {
    public static final int WIDTH = 176;
    public static final int COLUMNS = 9;
    public static final int SLOT_PITCH = 18;
    /** Size of the item drawn in a slot. */
    public static final int ITEM = 16;
    private static final int FIRST_X = 8;
    private static final int FIRST_Y = 18;
    /** The title strip and the inventory block that surround the slot rows. */
    private static final int CHROME = 114;

    private Grid() {
    }

    /** Left edge of a slot column's item area. */
    public static int x(int column) {
        return FIRST_X + column * SLOT_PITCH;
    }

    /** Top edge of a slot row's item area. */
    public static int y(int row) {
        return FIRST_Y + row * SLOT_PITCH;
    }

    /**
     * Top edge of a row in the lower block (the player's inventory in a normal
     * chest): rows 0 to 2, then 3 for the bottom strip.
     */
    public static int lowerY(int chestRows, int row) {
        int shift = (chestRows - 4) * SLOT_PITCH;
        return row < 3 ? 103 + shift + row * SLOT_PITCH : 161 + shift;
    }

    /** Total height of a menu with the given number of chest rows. */
    public static int height(int rows) {
        return CHROME + rows * SLOT_PITCH;
    }
}
