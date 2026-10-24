package com.cobblemondimensions.stories.ui.art;

/**
 * Pixel widths of the vanilla font, so the server can lay text out and wrap it
 * without ever seeing a font. The tables were read off the game's own glyph
 * sheets; a glyph's advance is its width plus one pixel of spacing.
 */
public final class UiMetrics {
    /** Widths of '!' through '~'. */
    private static final String ASCII = "1355551333515155555555555114545655555555355555555555555555353552555554551542555555535555553136";
    /** Widths of the Latin-1 supplement, U+00A0 through U+00FF. */
    private static final String LATIN1 = "015575153746537545442561034677755555559555553333655555555555555555555595555522335555555555555555";
    /** The accented letters and symbols the pack's text fonts carry beyond plain ASCII. */
    private static final String EXTRA = "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÐÑÒÓÔÕÖØÙÚÛÜÝÞßàáâãäåæçèéêëìíîïðñòóôõöøùúûüýþÿ¡¿×·";

    private UiMetrics() {
    }

    /** True when the pack's text fonts have a glyph for the character. */
    public static boolean supported(char c) {
        return c == ' ' || (c > ' ' && c < 127) || EXTRA.indexOf(c) >= 0;
    }

    /** Replaces anything the fonts cannot draw so the client never shows a missing-glyph box. */
    public static String clean(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (supported(c)) {
                out.append(c);
            } else if (c == '’' || c == '‘') {
                out.append('\'');
            } else if (c == '“' || c == '”') {
                out.append('"');
            } else if (c == '–' || c == '—') {
                out.append('-');
            } else if (c == '…') {
                out.append("...");
            } else if (c == '♀') {
                out.append('F');
            } else if (c == '♂') {
                out.append('M');
            } else if (c == '\n') {
                // kept: wrapping splits paragraphs on it
                out.append('\n');
            } else if (c == '\t') {
                out.append(' ');
            } else {
                out.append('?');
            }
        }
        return out.toString();
    }

    /** Ink width of one glyph, without the trailing pixel of spacing. */
    public static int glyph(char c) {
        if (c > ' ' && c < 127) {
            return ASCII.charAt(c - '!') - '0';
        }
        if (c >= 0xA0 && c <= 0xFF) {
            return LATIN1.charAt(c - 0xA0) - '0';
        }
        return 5;
    }

    /** Cursor advance of the whole string at the given line's size, rounded up to a pixel. */
    public static int width(TextLine line, String text) {
        return (widthEighths(line, text) + 7) / 8;
    }

    /** Cursor advance of the whole string, in eighths of a pixel. */
    public static int widthEighths(TextLine line, String text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += advanceEighths(line, text.charAt(i));
        }
        return total;
    }

    /** Advance of one character, in eighths of a pixel. */
    public static int advanceEighths(TextLine line, char c) {
        if (c == ' ') {
            return line.spaceEighths();
        }
        return line.advanceEighths(line.pixel() ? HudFont.width(c) : glyph(c));
    }

    /** {@link #clean(String)} for whichever font the line uses. */
    public static String clean(TextLine line, String text) {
        String clean = clean(text);
        if (!line.pixel()) {
            return clean;
        }
        StringBuilder out = new StringBuilder(clean.length());
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            out.append(HudFont.supported(c) || c == '\n' ? c : '?');
        }
        return out.toString();
    }

    /** Greedy word wrap on advance widths. Words longer than a line are split. */
    public static java.util.List<String> wrap(TextLine line, String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = current.isEmpty() ? word : current + " " + word;
                if (width(line, candidate) <= maxWidth) {
                    current.setLength(0);
                    current.append(candidate);
                    continue;
                }
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                String rest = word;
                while (width(line, rest) > maxWidth && rest.length() > 1) {
                    int cut = rest.length() - 1;
                    while (cut > 1 && width(line, rest.substring(0, cut)) > maxWidth) {
                        cut--;
                    }
                    lines.add(rest.substring(0, cut));
                    rest = rest.substring(cut);
                }
                current.append(rest);
            }
            lines.add(current.toString());
        }
        return lines;
    }
}
