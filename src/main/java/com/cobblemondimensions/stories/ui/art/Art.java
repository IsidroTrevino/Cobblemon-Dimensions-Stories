package com.cobblemondimensions.stories.ui.art;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a sprite's pixels come from. The pack builder turns each of these into a
 * PNG; the server only ever reads the size.
 */
public sealed interface Art {
    int width();

    int height();

    /** A rectangle of a source sheet, drawn at {@code (dx, dy)} on the sprite. */
    record Piece(String sheet, int sx, int sy, int sw, int sh, int dx, int dy) {
    }

    /**
     * Pieces of the source sheets stacked in order. {@code scale} repeats every
     * pixel, so pixel art stays crisp when it has to be bigger.
     */
    record Composite(int w, int h, int scale, List<Piece> pieces) implements Art {
        public Composite {
            pieces = List.copyOf(pieces);
        }

        @Override
        public int width() {
            return w * scale;
        }

        @Override
        public int height() {
            return h * scale;
        }

        /** The same picture with every pixel repeated {@code factor} times. */
        public Composite scaled(int factor) {
            return new Composite(w, h, scale * factor, pieces);
        }
    }

    /** A flat colour (ARGB), optionally with a one pixel border. Used for backing plates and glows. */
    record Plate(int w, int h, int fill, int border) implements Art {
        @Override
        public int width() {
            return w;
        }

        @Override
        public int height() {
            return h;
        }
    }

    /**
     * Flat rectangles painted in order, each {@code {x, y, w, h, argb}}. Used for
     * the boxes and small marks the original screens drew with plain fills.
     */
    record Rects(int w, int h, List<int[]> rects) implements Art {
        public Rects {
            rects = rects.stream().map(int[]::clone).toList();
        }

        @Override
        public int width() {
            return w;
        }

        @Override
        public int height() {
            return h;
        }
    }

    /** Another picture with every pixel's opacity multiplied by {@code alpha} (0 to 255). */
    record Faded(Art base, int alpha) implements Art {
        @Override
        public int width() {
            return base.width();
        }

        @Override
        public int height() {
            return base.height();
        }
    }

    /**
     * The rectangle {@code (x, y, w, h)} cut from each sheet and stacked at the
     * origin. The art sheets are all 256x256 with every layer aligned, so this is
     * how a finished screen is put back together.
     */
    static Composite crop(int x, int y, int w, int h, String... sheets) {
        List<Piece> pieces = new ArrayList<>();
        for (String sheet : sheets) {
            pieces.add(new Piece(sheet, x, y, w, h, 0, 0));
        }
        return new Composite(w, h, 1, pieces);
    }

    /**
     * Like {@link #crop}, but taller: row {@code atRow} is repeated {@code extra}
     * more times. Only used on rows that are flat from edge to edge, so the art
     * looks the same, just with more room inside.
     */
    static Composite stretch(int x, int y, int w, int h, int atRow, int extra, String... sheets) {
        List<Piece> pieces = new ArrayList<>();
        int above = atRow - y;
        for (String sheet : sheets) {
            pieces.add(new Piece(sheet, x, y, w, above, 0, 0));
            for (int copy = 0; copy < extra; copy++) {
                pieces.add(new Piece(sheet, x, atRow, w, 1, 0, above + copy));
            }
            pieces.add(new Piece(sheet, x, atRow, w, h - above, 0, above + extra));
        }
        return new Composite(w, h + extra, 1, pieces);
    }

    /** Starts a free-form composite {@code w} by {@code h} pixels. */
    static Builder canvas(int w, int h) {
        return new Builder(w, h);
    }

    final class Builder {
        private final int w;
        private final int h;
        private final List<Piece> pieces = new ArrayList<>();

        private Builder(int w, int h) {
            this.w = w;
            this.h = h;
        }

        /** Draws the rectangle {@code (sx, sy, sw, sh)} of {@code sheet} at {@code (dx, dy)}. */
        public Builder piece(String sheet, int sx, int sy, int sw, int sh, int dx, int dy) {
            pieces.add(new Piece(sheet, sx, sy, sw, sh, dx, dy));
            return this;
        }

        public Composite build() {
            return new Composite(w, h, 1, pieces);
        }
    }
}
