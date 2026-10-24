package com.cobblemondimensions.stories.ui.art;

/**
 * The interfaces that are drawn through the action bar rather than in a menu.
 *
 * <p>Two things are drawn this way: the walking dialogue box, which has to stay
 * up while the player walks, and the Pattern game, which needs the movement keys
 * and so can't be a menu. A menu is also always drawn at the player's GUI scale,
 * while these should grow with the window the way the old client drew them. Every glyph that belongs to an overlay carries a marker in its corner
 * pixels. The pack's text shader looks for that marker, hides the shadow the
 * action bar adds, and places and scales the glyph for the window size.
 *
 * <p>Each overlay is drawn in the coordinates of its 256 pixel wide art sheets.
 * The action bar centres the text on the screen; the shader takes it from there.
 */
public enum Overlay {
    /** The walking dialogue box above the hotbar. */
    DIALOGUE(1),
    /** The Pattern game, centred on the screen. */
    PATTERN(2);

    /** Every overlay is drawn on a sheet this wide; the action bar centres it. */
    public static final int SHEET_WIDTH = 256;

    /** Marker pixels: this alpha is too faint to draw, and the green says what kind of glyph it is. */
    public static final int MARKER_ALPHA = 7;
    public static final int MARKER_SPRITE = 167;
    public static final int MARKER_TEXT = 168;
    public static final int MARKER_BLUE = 60;

    /**
     * Colours are sent lifted into this range. The action bar draws a shadow copy at a
     * quarter of the brightness, which then always falls below {@link #COLOUR_FLOOR},
     * and that is how the shader tells the two apart.
     */
    public static final int COLOUR_FLOOR = 72;
    public static final int COLOUR_RANGE = 183;

    private final int id;

    Overlay(int id) {
        this.id = id;
    }

    /** The number the shader reads from the marker's red channel. */
    public int id() {
        return id;
    }

    /** ARGB of the marker pixel for a sprite or a text cell of this overlay. */
    public int marker(boolean text) {
        return MARKER_ALPHA << 24 | id << 16 | (text ? MARKER_TEXT : MARKER_SPRITE) << 8 | MARKER_BLUE;
    }

    /** Lifts an RGB colour into the range the shader expects; it undoes this before drawing. */
    public static int encode(int rgb) {
        return lift(rgb >> 16 & 0xFF) << 16 | lift(rgb >> 8 & 0xFF) << 8 | lift(rgb & 0xFF);
    }

    private static int lift(int channel) {
        return COLOUR_FLOOR + Math.round(channel * COLOUR_RANGE / 255.0F);
    }
}
