package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.CobblemonDimensionsStories;
import com.cobblemondimensions.stories.ui.art.HudFont;
import com.cobblemondimensions.stories.ui.art.Overlay;
import com.cobblemondimensions.stories.ui.art.Sprite;
import com.cobblemondimensions.stories.ui.art.TextLine;
import com.cobblemondimensions.stories.ui.art.UiAtlas;
import com.cobblemondimensions.stories.ui.art.UiMetrics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Builds a menu title that the resource pack turns into a picture.
 *
 * <p>The client draws a menu's title as one line of text starting a few pixels in
 * from the corner. Every sprite is a character in a pack font, and negative
 * spacing characters walk the cursor back, so one line can place any number of
 * pictures and words anywhere in the menu. Positions here are pixels from the
 * menu's top-left corner, the same space the slots are laid out in.
 *
 * <p>An overlay canvas ({@link #overlay}) builds action bar text instead, in the
 * coordinates of the overlay's art sheet; see {@link Overlay}.
 */
public final class Canvas {
    /** Where the client starts drawing a title. */
    private static final int ORIGIN_X = 8;
    private static final int WHITE = 0xFFFFFF;
    private static final ResourceLocation SPRITE_FONT = CobblemonDimensionsStories.id(UiAtlas.SPRITE_FONT);

    private final List<Part> parts = new ArrayList<>();
    private final Overlay overlay;
    /** In eighths of a pixel, so overlay letters can sit between whole pixels. */
    private int cursor;
    private MutableComponent tail;
    private boolean finished;

    /** A canvas for a menu title, which the client starts drawing a few pixels in. */
    public Canvas() {
        this(ORIGIN_X);
    }

    /**
     * A canvas whose text starts at {@code origin}. The action bar centres its
     * text, so a canvas drawn there starts at 0 and finishes with {@link #end}.
     */
    public Canvas(int origin) {
        this(origin, null);
    }

    private Canvas(int origin, Overlay overlay) {
        this.cursor = origin * 8;
        this.overlay = overlay;
    }

    /** A canvas for one of the overlays, drawn in the coordinates of its art sheet. */
    public static Canvas overlay(Overlay overlay) {
        return new Canvas(0, overlay);
    }

    /**
     * Leaves the cursor at {@code x}. Centred text such as the action bar is centred
     * on the cursor's final position, so ending on the art's right edge centres the art.
     */
    public Canvas end(int x) {
        moveTo(x * 8);
        return this;
    }

    /** Draws a sprite with its left edge {@code x} pixels from the menu's left edge. */
    public Canvas sprite(Sprite sprite, int x) {
        return sprite(sprite, x, WHITE);
    }

    /**
     * Same, tinted. White leaves the art untouched; any other colour multiplies
     * with it, which is how the pack fades and dims pictures.
     */
    public Canvas sprite(Sprite sprite, int x, int tint) {
        if (sprite.overlay() != overlay) {
            throw new IllegalArgumentException(sprite.name() + " does not belong on this canvas");
        }
        moveTo((x - sprite.border()) * 8);
        append(SPRITE_FONT, colour(tint), String.valueOf(sprite.glyph()));
        cursor += sprite.advance() * 8;
        return this;
    }

    /** Draws text with the left edge of the first letter at {@code x}. */
    public Canvas text(TextLine line, String text, int x, int rgb) {
        return textAt(line, text, x * 8, rgb);
    }

    /**
     * Draws text starting {@code eighths} eighths of a pixel from the left edge.
     * The game adds a whole pixel after every letter whatever its size, so on an
     * overlay each letter is nudged back to where scaled text would put it.
     */
    public Canvas textAt(TextLine line, String text, int eighths, int rgb) {
        if (line.overlay() != overlay) {
            throw new IllegalArgumentException(line.fontName() + " does not belong on this canvas");
        }
        String clean = UiMetrics.clean(line, text).replace('\n', ' ');
        if (clean.isEmpty()) {
            return this;
        }
        moveTo(eighths);
        String drawn = clean;
        if (line.pixel()) {
            StringBuilder spaced = new StringBuilder();
            for (int i = 0; i < clean.length(); i++) {
                char c = clean.charAt(i);
                spaced.append(c);
                if (c != ' ') {
                    int ink = HudFont.width(c);
                    spaced.append(UiAtlas.shift(line.advanceEighths(ink) - line.advance(ink) * 8));
                }
            }
            drawn = spaced.toString();
        }
        append(CobblemonDimensionsStories.id(line.fontName()), colour(rgb), drawn);
        cursor += UiMetrics.widthEighths(line, clean);
        return this;
    }

    public Canvas textCentered(TextLine line, String text, int centerX, int rgb) {
        String clean = UiMetrics.clean(line, text);
        return textAt(line, clean, centerX * 8 - inkEighths(line, clean) / 2, rgb);
    }

    public Canvas textRight(TextLine line, String text, int rightX, int rgb) {
        String clean = UiMetrics.clean(line, text);
        return textAt(line, clean, rightX * 8 - inkEighths(line, clean), rgb);
    }

    /** The width of the letters without the gap that follows the last one. */
    private static int inkEighths(TextLine line, String clean) {
        int gap = line.pixel() ? line.eighths() : 8;
        return Math.max(0, UiMetrics.widthEighths(line, clean) - gap);
    }

    /**
     * Text the player's client translates itself, such as item names. Its width is
     * only known to the client, so it has to be the last thing drawn.
     */
    public Canvas trailing(TextLine line, Component text, int x, int rgb) {
        if (overlay != null) {
            throw new IllegalStateException("Overlay text is measured by the server, so it cannot end in client text");
        }
        if (finished) {
            throw new IllegalStateException("Only one trailing text fits at the end of a title");
        }
        moveTo(x * 8);
        tail = text.copy().withStyle(Style.EMPTY
            .withFont(CobblemonDimensionsStories.id(line.fontName())).withColor(rgb & 0xFFFFFF));
        finished = true;
        return this;
    }

    public Component build() {
        if (overlay != null) {
            // the action bar centres on the total width, which must be the sheet's
            moveTo(Overlay.SHEET_WIDTH * 8);
        }
        MutableComponent root = Component.empty();
        for (Part part : parts) {
            root.append(Component.literal(part.text.toString())
                .withStyle(Style.EMPTY.withFont(part.font).withColor(part.rgb)));
        }
        if (tail != null) {
            root.append(tail);
        }
        return root;
    }

    /** Moves the cursor to {@code eighths} eighths of a pixel from the left edge. */
    private void moveTo(int eighths) {
        if (finished) {
            throw new IllegalStateException("Nothing can follow trailing text");
        }
        int delta = eighths - cursor;
        if (delta != 0) {
            append(SPRITE_FONT, WHITE, UiAtlas.shift(delta));
            cursor = eighths;
        }
    }

    private int colour(int rgb) {
        return overlay == null ? rgb & 0xFFFFFF : Overlay.encode(rgb & 0xFFFFFF);
    }

    private void append(ResourceLocation font, int rgb, String text) {
        if (!parts.isEmpty()) {
            Part last = parts.getLast();
            if (last.font.equals(font) && last.rgb == rgb) {
                last.text.append(text);
                return;
            }
        }
        parts.add(new Part(font, rgb, new StringBuilder(text)));
    }

    private record Part(ResourceLocation font, int rgb, StringBuilder text) {
    }
}
