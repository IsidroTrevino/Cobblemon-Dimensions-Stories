package com.cobblemondimensions.stories.ui.art;

/**
 * A row of text at a fixed height. A font cannot move text up or down on its
 * own, so the pack carries one tiny font for each row the interfaces use.
 *
 * <p>Size is counted in eighths: 8 is the game's normal text, 4 is half size and
 * 16 double. There are three kinds of row:
 * <ul>
 *   <li>menu title rows in the game's own letters, where {@code y} is the top of
 *       the glyph cell counted from the top of the menu;</li>
 *   <li>menu title rows in the pack's {@link HudFont} ({@code pixel}), where
 *       {@code y} is the top of a capital letter counted from the top of the menu;</li>
 *   <li>{@link Overlay} rows, also in {@link HudFont}, where {@code y} is the top of
 *       a capital letter on the overlay's art sheet.</li>
 * </ul>
 * Rows in {@link HudFont} are spaced as if the whole line were scaled, gaps
 * included, so small text reads like the old screens' scaled text.
 */
public record TextLine(int y, int eighths, Overlay overlay, boolean pixel) {
    /** A menu title row in the game's own font; {@code halves} is 1, 2 or 4. */
    public static TextLine title(int y, int halves) {
        return new TextLine(y, halves * 4, null, false);
    }

    /** A menu title row in the pack's pixel font. */
    public static TextLine menu(int y, int eighths) {
        return new TextLine(y, eighths, null, true);
    }

    /** A row on an overlay's sheet. */
    public static TextLine overlay(Overlay overlay, int y, int eighths) {
        return new TextLine(y, eighths, overlay, true);
    }

    public boolean isOverlay() {
        return overlay != null;
    }

    /** Path of the font inside the pack's namespace. */
    public String fontName() {
        if (isOverlay()) {
            return "o" + overlay.id() + "_e" + eighths + "_y" + y;
        }
        return pixel ? "m_e" + eighths + "_y" + y : "text_h" + halves() + "_y" + y;
    }

    /** Size of a row in the game's own font, in halves of the normal size. */
    public int halves() {
        return eighths / 4;
    }

    /** Height of a game-font row's glyph cell in pixels. */
    public int cell() {
        return eighths;
    }

    public float scale() {
        return eighths / 8.0F;
    }

    /** Advance of a glyph whose ink is {@code pixels} wide, rounded the way the client does. */
    public int advance(int pixels) {
        return (int) (0.5F + pixels * scale()) + 1;
    }

    /**
     * Where the next letter starts, in eighths of a pixel. Pixel-font rows are
     * spaced as if the whole line were scaled, gap included;
     * {@link com.cobblemondimensions.stories.ui.Canvas} nudges each letter to match.
     */
    public int advanceEighths(int pixels) {
        return pixel ? (pixels + 1) * eighths : advance(pixels) * 8;
    }

    /** The game's space is four pixels at normal size. */
    public float spaceAdvance() {
        return eighths / 2.0F;
    }

    public int spaceEighths() {
        return eighths * 4;
    }
}
