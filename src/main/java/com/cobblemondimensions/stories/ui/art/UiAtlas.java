package com.cobblemondimensions.stories.ui.art;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single list of everything the resource pack has to draw.
 *
 * <p>The server and the pack builder both read this class, so a glyph can never
 * exist on one side only. Characters are handed out in registration order, which
 * is stable as long as the art classes load in a fixed order; deploy the pack and
 * the mod from the same build.
 */
public final class UiAtlas {
    /** Font holding every sprite and the spacing characters. */
    public static final String SPRITE_FONT = "ui";

    /** Characters that move the cursor right by 1, 2, 4 ... 256 pixels. */
    private static final char FORWARD = '';
    /** Characters that move the cursor left by 1, 2, 4 ... 256 pixels. */
    private static final char BACK = '';
    private static final int BITS = 9;
    /** Characters that move the cursor right, then left, by an eighth, a quarter and half a pixel. */
    private static final char FORWARD_FRACTION = '';
    private static final char BACK_FRACTION = '';
    private static final int FRACTION_BITS = 3;
    private static final char FIRST_SPRITE = '';

    private static final Map<String, Sprite> SPRITES = new LinkedHashMap<>();
    private static final Map<String, TextLine> LINES = new LinkedHashMap<>();
    private static boolean loaded;

    private UiAtlas() {
    }

    /** Loads every art class so the pack builder sees the whole catalogue. */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        PatternArt.init();
        PokesweeperArt.init();
        QuestArt.init();
        DialogueArt.init();
        ShopArt.init();
    }

    public static synchronized Sprite sprite(String name, int y, Art art) {
        return register(name, y, art, null);
    }

    /** A picture drawn in an {@link Overlay}, at row {@code y} of its art sheet. */
    public static synchronized Sprite overlaySprite(Overlay overlay, String name, int y, Art art) {
        return register(name, y, art, overlay);
    }

    private static Sprite register(String name, int y, Art art, Overlay overlay) {
        if (SPRITES.containsKey(name)) {
            throw new IllegalStateException("Sprite registered twice: " + name);
        }
        // the font atlas pages are 256 pixels square; anything bigger is never drawn
        int frame = overlay == null ? 0 : 2;
        if (art.width() + frame > 256 || art.height() + frame > 256) {
            throw new IllegalArgumentException("Sprite too large for a font page: " + name);
        }
        char glyph = (char) (FIRST_SPRITE + SPRITES.size());
        if (glyph >= '') {
            throw new IllegalStateException("Out of private-use glyphs at " + name);
        }
        Sprite sprite = new Sprite(name, glyph, y, art, overlay);
        SPRITES.put(name, sprite);
        return sprite;
    }

    /** Normal ({@code scale} 1) or double ({@code scale} 2) menu title text with its cell top at {@code y}. */
    public static synchronized TextLine line(int y, int scale) {
        if (scale != 1 && scale != 2) {
            throw new IllegalArgumentException("Text scale must be 1 or 2: " + scale);
        }
        return register(TextLine.title(y, scale * 2));
    }

    /** Half-size menu title text. */
    public static synchronized TextLine smallLine(int y) {
        return register(TextLine.title(y, 1));
    }

    /**
     * Menu title text in the pack's pixel font, {@code eighths} of the normal size,
     * with the top of its capitals {@code y} pixels below the top of the menu.
     */
    public static synchronized TextLine menuLine(int y, int eighths) {
        checkSize(eighths);
        TextLine line = TextLine.menu(y, eighths);
        // the glyph cell has eight rows above the capitals, and it can only be lifted by its height
        if (13 - y + eighths > 3 * eighths) {
            throw new IllegalArgumentException("Text row too high: " + line);
        }
        return LINES.computeIfAbsent(line.fontName(), name -> line);
    }

    /**
     * Overlay text in the pack's pixel font, {@code eighths} of the normal size, with
     * the top of its capitals at row {@code y} of the overlay's sheet.
     */
    public static synchronized TextLine overlayLine(Overlay overlay, int y, int eighths) {
        checkSize(eighths, MAX_OVERLAY_EIGHTHS);
        TextLine line = TextLine.overlay(overlay, y, eighths);
        return LINES.computeIfAbsent(line.fontName(), name -> line);
    }

    /** Menus stay close to normal text; an overlay is scaled up with the window, so its headlines can be big. */
    private static final int MAX_MENU_EIGHTHS = 16;
    private static final int MAX_OVERLAY_EIGHTHS = 32;

    private static void checkSize(int eighths) {
        checkSize(eighths, MAX_MENU_EIGHTHS);
    }

    private static void checkSize(int eighths, int max) {
        if (eighths < 3 || eighths > max) {
            throw new IllegalArgumentException("Text size out of range: " + eighths);
        }
    }

    private static TextLine register(TextLine line) {
        // A glyph may only be lifted by as much as it is tall, and the title sits
        // six pixels down, so small text cannot reach the very top of a menu.
        if (line.y() < 13 - line.cell()) {
            throw new IllegalArgumentException("Text row too high: " + line);
        }
        return LINES.computeIfAbsent(line.fontName(), name -> line);
    }

    public static synchronized List<Sprite> sprites() {
        load();
        return Collections.unmodifiableList(new ArrayList<>(SPRITES.values()));
    }

    public static synchronized List<TextLine> lines() {
        load();
        return Collections.unmodifiableList(new ArrayList<>(LINES.values()));
    }

    /**
     * The spacing characters that move the cursor by {@code eighths} of a pixel,
     * forwards or backwards. Menus only ever move by whole pixels; overlay text
     * uses the fractions to space its letters the way scaled text is spaced.
     */
    public static String shift(int eighths) {
        if (eighths == 0) {
            return "";
        }
        int remaining = Math.abs(eighths);
        StringBuilder out = new StringBuilder();
        char whole = eighths > 0 ? FORWARD : BACK;
        for (int bit = BITS - 1; bit >= 0; bit--) {
            int step = 8 << bit;
            while (remaining >= step) {
                out.append((char) (whole + bit));
                remaining -= step;
            }
        }
        char fraction = eighths > 0 ? FORWARD_FRACTION : BACK_FRACTION;
        for (int bit = FRACTION_BITS - 1; bit >= 0; bit--) {
            if (remaining >= 1 << bit) {
                out.append((char) (fraction + bit));
                remaining -= 1 << bit;
            }
        }
        return out.toString();
    }

    /** The advance each spacing character carries, in pixels, for the pack's space providers. */
    public static Map<Character, Float> spacing() {
        Map<Character, Float> advances = new LinkedHashMap<>();
        for (int bit = 0; bit < BITS; bit++) {
            advances.put((char) (FORWARD + bit), (float) (1 << bit));
            advances.put((char) (BACK + bit), (float) -(1 << bit));
        }
        for (int bit = 0; bit < FRACTION_BITS; bit++) {
            advances.put((char) (FORWARD_FRACTION + bit), (1 << bit) / 8.0F);
            advances.put((char) (BACK_FRACTION + bit), -(1 << bit) / 8.0F);
        }
        return advances;
    }
}
