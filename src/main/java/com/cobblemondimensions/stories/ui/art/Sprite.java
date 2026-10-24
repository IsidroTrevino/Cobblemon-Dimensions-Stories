package com.cobblemondimensions.stories.ui.art;

/**
 * One picture the pack draws through a font glyph.
 *
 * <p>In a menu title {@code y} is where the top edge lands, counted from the top
 * of the menu; a glyph can only be shifted up by as much as it is tall, so the
 * pack builder pads short pictures with clear rows. In an {@link Overlay} it is
 * the row on the overlay's art sheet, and the pack frames the picture with one
 * clear pixel that holds the overlay's corner markers.
 */
public record Sprite(String name, char glyph, int y, Art art, Overlay overlay) {
    public int width() {
        return art.width();
    }

    public int height() {
        return art.height();
    }

    /** The clear frame around an overlay sprite, on each side. */
    public int border() {
        return overlay == null ? 0 : 1;
    }

    /** How far the cursor moves after this glyph is drawn. */
    public int advance() {
        return width() + 2 * border() + 1;
    }
}
