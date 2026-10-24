package com.cobblemondimensions.stories.ui.art;

/**
 * Pattern's sprites, cut from the supplied sheets and drawn as an {@link Overlay}.
 *
 * <p>Everything keeps the coordinates it has on the 256 pixel sheets. The finished
 * board is the 176x66 rectangle at (40, 7); the pack's shader centres it on the
 * screen and scales it with the window. The hints under the board sit on the same
 * sheet, below its bottom edge.
 */
public final class PatternArt {
    private static final Overlay OVERLAY = Overlay.PATTERN;
    private static final String DIR = "pattern/";

    public static final int PANEL_X = 40;
    public static final int PANEL_Y = 7;
    public static final int PANEL_W = 176;
    public static final int PANEL_H = 66;
    public static final int CENTER_X = 128;
    public static final int CENTER_Y = 40;

    private static final int CLOCK_Y = 14;
    private static final int STREAK_Y = 44;
    public static final int STREAK_CENTER_Y = 53;

    /** Left and top edges of the twelve board tiles. */
    public static final int[][] TILES = {
        {50, 26}, {70, 26}, {90, 26}, {110, 26}, {130, 26}, {150, 26}, {170, 26}, {190, 26},
        {60, 43}, {100, 43}, {140, 43}, {180, 43},
    };

    /** Source rectangles on the arrow and shape sheets, in direction order: up, down, left, right. */
    private static final int[][] ARROWS = {{93, 31, 10, 5}, {113, 32, 10, 5}, {135, 29, 5, 10}, {156, 29, 5, 10}};
    private static final int[][] SHAPES = {{94, 30, 8, 8}, {114, 30, 8, 8}, {134, 30, 8, 8}, {154, 30, 8, 8}};

    /** Edge lengths and opacities of the ring that spreads out from a tile or the streak dial. */
    private static final int[] RING_SIZES = {18, 22, 26, 30};
    private static final int[] RING_ALPHAS = {0xFF, 0xC0, 0x80, 0x40};
    /** Where the rings are centred: the two tile rows, then the streak dial. */
    private static final int[] RING_CENTERS = {34, 51, STREAK_CENTER_Y};
    private static final int CYAN = 0x00F0FF;
    private static final int GOLD = 0xFFC93C;
    private static final int RED = 0xFF2A4A;
    private static final int[] URGENCY_ALPHAS = {0x50, 0x90, 0xE0};

    public static final Sprite BASE = UiAtlas.overlaySprite(OVERLAY, "pattern_base", PANEL_Y,
        Art.crop(PANEL_X, PANEL_Y, PANEL_W, PANEL_H,
            DIR + "pattern_background.png", DIR + "pattern_icons.png", DIR + "pattern_corners.png"));
    public static final Sprite BASE_SHAPES = UiAtlas.overlaySprite(OVERLAY, "pattern_base_shapes", PANEL_Y,
        Art.crop(PANEL_X, PANEL_Y, PANEL_W, PANEL_H,
            DIR + "pattern_background_x10.png", DIR + "pattern_icons_x10.png", DIR + "pattern_corners.png"));
    public static final Sprite SCRIM = UiAtlas.overlaySprite(OVERLAY, "pattern_scrim", PANEL_Y + 4,
        new Art.Plate(168, 58, 0xC01F004B, 0));
    public static final Sprite CLOCK = UiAtlas.overlaySprite(OVERLAY, "pattern_clock", CLOCK_Y,
        Art.crop(110, CLOCK_Y, 36, 11, DIR + "pattern_timer.png"));
    public static final Sprite STREAK = UiAtlas.overlaySprite(OVERLAY, "pattern_streak", STREAK_Y,
        Art.crop(118, STREAK_Y, 20, 19, DIR + "pattern_streak.png"));

    /** Board tile pictures, one per slot because each has its own border colours. */
    public static final Sprite[] TILE = new Sprite[TILES.length];
    /** Dimmed tile for slots the current pattern does not use, per row. */
    public static final Sprite[] UNUSED = new Sprite[2];
    /** Inner fill of a tile: progress, flashing wrong, the hit pop, and the two pulse levels. */
    public static final Sprite[] DONE = new Sprite[2];
    public static final Sprite[] WRONG = new Sprite[2];
    public static final Sprite[] POP = new Sprite[2];
    public static final Sprite[] CURRENT_DIM = new Sprite[2];
    public static final Sprite[] CURRENT_BRIGHT = new Sprite[2];
    /** Arrow and shape in a board tile, indexed [direction][row]. */
    public static final Sprite[][] ARROW = new Sprite[4][2];
    public static final Sprite[][] SHAPE = new Sprite[4][2];
    /** Rings by [step][centre], cyan for a tile that was hit and gold for a cleared round. */
    public static final Sprite[][] RING_CYAN = new Sprite[RING_SIZES.length][2];
    public static final Sprite[][] RING_GOLD = new Sprite[RING_SIZES.length][RING_CENTERS.length];
    /** The red frame that pulses in the last quarter of the clock, two lines thick, by strength. */
    public static final Sprite[] URGENCY_OUTER = new Sprite[URGENCY_ALPHAS.length];
    public static final Sprite[] URGENCY_INNER = new Sprite[URGENCY_ALPHAS.length];

    public static final TextLine CLOCK_TEXT = UiAtlas.overlayLine(OVERLAY, CLOCK_Y + 2, 8);
    public static final TextLine STREAK_TEXT = UiAtlas.overlayLine(OVERLAY, STREAK_CENTER_Y - 3, 7);
    /** The big countdown digit, and the same one a step smaller as the beat runs out. */
    public static final TextLine COUNTDOWN = UiAtlas.overlayLine(OVERLAY, 30, 24);
    public static final TextLine COUNTDOWN_SMALL = UiAtlas.overlayLine(OVERLAY, 32, 18);
    public static final TextLine HEADLINE = UiAtlas.overlayLine(OVERLAY, 21, 20);
    public static final TextLine SUBLINE = UiAtlas.overlayLine(OVERLAY, 46, 6);
    public static final TextLine OVER_HEADLINE = UiAtlas.overlayLine(OVERLAY, 22, 24);
    public static final TextLine OVER_SUBLINE = UiAtlas.overlayLine(OVERLAY, 46, 6);
    public static final TextLine OVER_HINT = UiAtlas.overlayLine(OVERLAY, 57, 6);
    /** The same rows a pixel lower, drawn first in a dark colour as their drop shadow. */
    public static final TextLine COUNTDOWN_DROP = UiAtlas.overlayLine(OVERLAY, 31, 24);
    public static final TextLine COUNTDOWN_SMALL_DROP = UiAtlas.overlayLine(OVERLAY, 33, 18);
    public static final TextLine HEADLINE_DROP = UiAtlas.overlayLine(OVERLAY, 22, 20);
    public static final TextLine OVER_HEADLINE_DROP = UiAtlas.overlayLine(OVERLAY, 23, 24);
    /** Under the board: level and record, the movement keys, and the keys for the end screen. */
    public static final TextLine STATUS = UiAtlas.overlayLine(OVERLAY, 78, 8);
    public static final TextLine KEYS = UiAtlas.overlayLine(OVERLAY, 90, 8);
    public static final TextLine KEYS_SMALL = UiAtlas.overlayLine(OVERLAY, 101, 6);

    static {
        for (int slot = 0; slot < TILES.length; slot++) {
            TILE[slot] = UiAtlas.overlaySprite(OVERLAY, "pattern_tile_" + slot, TILES[slot][1],
                Art.crop(TILES[slot][0], TILES[slot][1], 16, 16, DIR + "pattern_slots.png"));
        }
        for (int row = 0; row < 2; row++) {
            int tileY = TILES[row == 0 ? 0 : 8][1];
            UNUSED[row] = UiAtlas.overlaySprite(OVERLAY, "pattern_unused_" + row, tileY,
                Art.crop(50, 26, 16, 16, DIR + "pattern_slot_disabled.png"));
            DONE[row] = UiAtlas.overlaySprite(OVERLAY, "pattern_done_" + row, tileY + 2,
                new Art.Plate(12, 12, 0x7A38F27A, 0));
            WRONG[row] = UiAtlas.overlaySprite(OVERLAY, "pattern_wrong_" + row, tileY + 2,
                new Art.Plate(12, 12, 0xAAFF2A4A, 0));
            POP[row] = UiAtlas.overlaySprite(OVERLAY, "pattern_pop_" + row, tileY + 2,
                new Art.Plate(12, 12, 0xB0FFFFFF, 0));
            CURRENT_DIM[row] = UiAtlas.overlaySprite(OVERLAY, "pattern_current_dim_" + row, tileY + 2,
                new Art.Plate(12, 12, 0x5000F0FF, 0));
            CURRENT_BRIGHT[row] = UiAtlas.overlaySprite(OVERLAY, "pattern_current_bright_" + row, tileY + 2,
                new Art.Plate(12, 12, 0xA000F0FF, 0));
            for (int direction = 0; direction < 4; direction++) {
                int[] arrow = ARROWS[direction];
                int[] shape = SHAPES[direction];
                ARROW[direction][row] = UiAtlas.overlaySprite(OVERLAY, "pattern_arrow_" + direction + "_" + row,
                    tileY + (16 - arrow[3]) / 2,
                    Art.crop(arrow[0], arrow[1], arrow[2], arrow[3], DIR + "pattern_arrows.png"));
                SHAPE[direction][row] = UiAtlas.overlaySprite(OVERLAY, "pattern_shape_" + direction + "_" + row,
                    tileY + (16 - shape[3]) / 2,
                    Art.crop(shape[0], shape[1], shape[2], shape[3], DIR + "pattern_shapes.png"));
            }
        }
        for (int step = 0; step < RING_SIZES.length; step++) {
            int size = RING_SIZES[step];
            for (int center = 0; center < RING_CENTERS.length; center++) {
                int y = RING_CENTERS[center] - size / 2;
                if (center < 2) {
                    RING_CYAN[step][center] = UiAtlas.overlaySprite(OVERLAY, "pattern_ring_cyan_" + step + "_" + center, y,
                        new Art.Plate(size, size, 0, ring(CYAN, RING_ALPHAS[step])));
                }
                RING_GOLD[step][center] = UiAtlas.overlaySprite(OVERLAY, "pattern_ring_gold_" + step + "_" + center, y,
                    new Art.Plate(size, size, 0, ring(GOLD, RING_ALPHAS[step])));
            }
        }
        for (int strength = 0; strength < URGENCY_ALPHAS.length; strength++) {
            int border = ring(RED, URGENCY_ALPHAS[strength]);
            URGENCY_OUTER[strength] = UiAtlas.overlaySprite(OVERLAY, "pattern_urgency_outer_" + strength,
                PANEL_Y + 3, new Art.Plate(PANEL_W - 6, PANEL_H - 6, 0, border));
            URGENCY_INNER[strength] = UiAtlas.overlaySprite(OVERLAY, "pattern_urgency_inner_" + strength,
                PANEL_Y + 4, new Art.Plate(PANEL_W - 8, PANEL_H - 8, 0, border));
        }
    }

    private PatternArt() {
    }

    private static int ring(int rgb, int alpha) {
        return alpha << 24 | rgb;
    }

    /** Row of the two-row board a slot sits in. */
    public static int row(int slot) {
        return slot < 8 ? 0 : 1;
    }

    /** Left edge of a board tile. */
    public static int tileX(int slot) {
        return TILES[slot][0];
    }

    /** Where the ring around a tile or the dial is drawn: its left edge is half a ring left of the centre. */
    public static int ringX(int centerX, int step) {
        return centerX - RING_SIZES[step] / 2;
    }

    public static int rings() {
        return RING_SIZES.length;
    }

    public static void init() {
        // Touching the class runs the registrations above.
    }
}
