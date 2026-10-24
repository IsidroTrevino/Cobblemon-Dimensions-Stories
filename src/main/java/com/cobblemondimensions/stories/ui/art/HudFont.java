package com.cobblemondimensions.stories.ui.art;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small pixel font of our own for the overlays (see {@link Overlay}).
 *
 * <p>Overlay text has to carry the overlay's marker pixels like every other
 * overlay glyph, which the game's own font sheets cannot. The letters follow the
 * game's proportions so the text reads the same; widths match the game's own
 * where it matters.
 *
 * <p>Each glyph is written as rows of {@code #} and {@code .}, starting at the
 * top of a capital letter. Row 6 sits on the baseline and row 7 holds
 * descenders. Accented letters are built from a base letter and a mark.
 */
public final class HudFont {
    /** Glyph rows from cap height to the last descender row. */
    public static final int ROWS = 8;
    /** Two clear rows above capitals hold accents on capital letters. */
    public static final int ACCENT_ROWS = 2;
    public static final int CELL_ROWS = ACCENT_ROWS + ROWS;

    private static final String SOURCE = """
        !:#|#|#|#|#|.|#
        ":#.#|#.#
        #:.#.#.|.#.#.|#####|.#.#.|#####|.#.#.|.#.#.
        $:..#..|.####|#....|.###.|....#|####.|..#..
        %:##..#|##.#.|...#.|..#..|.#...|.#.##|#..##
        &:..#..|.#.#.|..#..|.##.#|#..#.|#..#.|.##.#
        ':#|#
        (:..#|.#.|#..|#..|#..|.#.|..#
        ):#..|.#.|..#|..#|..#|.#.|#..
        *:...|#.#|.#.|#.#
        +:.....|..#..|..#..|#####|..#..|..#..
        ,:.|.|.|.|.|#|#|#
        -:.....|.....|.....|#####
        .:.|.|.|.|.|#|#
        /:....#|...#.|...#.|..#..|.#...|.#...|#....
        0:.###.|#...#|#..##|#.#.#|##..#|#...#|.###.
        1:..#..|.##..|..#..|..#..|..#..|..#..|#####
        2:.###.|#...#|....#|..##.|.#...|#....|#####
        3:.###.|#...#|....#|..##.|....#|#...#|.###.
        4:...##|..#.#|.#..#|#...#|#####|....#|....#
        5:#####|#....|####.|....#|....#|#...#|.###.
        6:..##.|.#...|#....|####.|#...#|#...#|.###.
        7:#####|#...#|....#|...#.|..#..|..#..|..#..
        8:.###.|#...#|#...#|.###.|#...#|#...#|.###.
        9:.###.|#...#|#...#|.####|....#|...#.|.##..
        ::.|#|#|.|.|#|#
        ;:.|#|#|.|.|#|#|#
        <:...#|..#.|.#..|#...|.#..|..#.|...#
        =:.....|.....|#####|.....|.....|#####
        >:#...|.#..|..#.|...#|..#.|.#..|#...
        ?:.###.|#...#|....#|...#.|..#..|.....|..#..
        @:.####.|#....#|#.##.#|#.#.##|#.###.|#.....|.####.
        A:.###.|#...#|#####|#...#|#...#|#...#|#...#
        B:####.|#...#|####.|#...#|#...#|#...#|####.
        C:.###.|#...#|#....|#....|#....|#...#|.###.
        D:####.|#...#|#...#|#...#|#...#|#...#|####.
        E:#####|#....|###..|#....|#....|#....|#####
        F:#####|#....|###..|#....|#....|#....|#....
        G:.####|#....|#..##|#...#|#...#|#...#|.###.
        H:#...#|#...#|#####|#...#|#...#|#...#|#...#
        I:###|.#.|.#.|.#.|.#.|.#.|###
        J:....#|....#|....#|....#|....#|#...#|.###.
        K:#...#|#..#.|###..|#..#.|#...#|#...#|#...#
        L:#....|#....|#....|#....|#....|#....|#####
        M:#...#|##.##|#.#.#|#...#|#...#|#...#|#...#
        N:#...#|##..#|#.#.#|#..##|#...#|#...#|#...#
        O:.###.|#...#|#...#|#...#|#...#|#...#|.###.
        P:####.|#...#|####.|#....|#....|#....|#....
        Q:.###.|#...#|#...#|#...#|#...#|#..#.|.##.#
        R:####.|#...#|####.|#...#|#...#|#...#|#...#
        S:.####|#....|.###.|....#|....#|#...#|.###.
        T:#####|..#..|..#..|..#..|..#..|..#..|..#..
        U:#...#|#...#|#...#|#...#|#...#|#...#|.###.
        V:#...#|#...#|#...#|#...#|.#.#.|.#.#.|..#..
        W:#...#|#...#|#...#|#...#|#.#.#|##.##|#...#
        X:#...#|.#.#.|..#..|.#.#.|#...#|#...#|#...#
        Y:#...#|.#.#.|..#..|..#..|..#..|..#..|..#..
        Z:#####|....#|...#.|..#..|.#...|#....|#####
        [:###|#..|#..|#..|#..|#..|###
        \\:#....|.#...|.#...|..#..|...#.|...#.|....#
        ]:###|..#|..#|..#|..#|..#|###
        ^:..#..|.#.#.|#...#
        _:.....|.....|.....|.....|.....|.....|.....|#####
        `:#.|.#
        a:.....|.....|.###.|....#|.####|#...#|.####
        b:#....|#....|#.##.|##..#|#...#|#...#|####.
        c:.....|.....|.###.|#...#|#....|#...#|.###.
        d:....#|....#|.##.#|#..##|#...#|#...#|.####
        e:.....|.....|.###.|#...#|#####|#....|.####
        f:..##|.#..|####|.#..|.#..|.#..|.#..
        g:.....|.....|.####|#...#|#...#|.####|....#|####.
        h:#....|#....|#.##.|##..#|#...#|#...#|#...#
        i:#|.|#|#|#|#|#
        j:....#|.....|....#|....#|....#|#...#|#...#|.###.
        k:#...|#...|#..#|#.#.|##..|#.#.|#..#
        l:#.|#.|#.|#.|#.|#.|.#
        m:.....|.....|##.#.|#.#.#|#.#.#|#...#|#...#
        n:.....|.....|####.|#...#|#...#|#...#|#...#
        o:.....|.....|.###.|#...#|#...#|#...#|.###.
        p:.....|.....|#.##.|##..#|#...#|####.|#....|#....
        q:.....|.....|.##.#|#..##|#...#|.####|....#|....#
        r:.....|.....|#.##.|##..#|#....|#....|#....
        s:.....|.....|.####|#....|.###.|....#|####.
        t:.#.|.#.|###|.#.|.#.|.#.|..#
        u:.....|.....|#...#|#...#|#...#|#...#|.####
        v:.....|.....|#...#|#...#|#...#|.#.#.|..#..
        w:.....|.....|#...#|#...#|#.#.#|#.#.#|.####
        x:.....|.....|#...#|.#.#.|..#..|.#.#.|#...#
        y:.....|.....|#...#|#...#|#...#|.####|....#|####.
        z:.....|.....|#####|...#.|..#..|.#...|#####
        {:..#|.#.|.#.|#..|.#.|.#.|..#
        |:#|#|#|#|#|#|#|#
        }:#..|.#.|.#.|..#|.#.|.#.|#..
        ~:......|.##..#|#..##.
        ¡:#|.|#|#|#|#|#
        ¿:..#..|.....|..#..|.#...|#....|#...#|.###.
        ß:.###.|#...#|#..#.|#.##.|#...#|#...#|#.##.
        ı:.|.|#|#|#|#|#
        ·:.|.|.|#
        """;

    /** Accent marks, two rows each, five pixels wide; centred over the base letter. */
    private static final Map<Character, String[]> MARKS = Map.of(
        '́', new String[] {"...#.", "..#.."},
        '̀', new String[] {".#...", "..#.."},
        '̂', new String[] {"..#..", ".#.#."},
        '̈', new String[] {".....", ".#.#."},
        '̃', new String[] {".##.#", "#..#."});

    /** Accented letters as base letter plus mark. Dotless i takes marks on the small letters. */
    private static final String COMPOSED = "ÀÀ ÁÁ ÂÂ ÄÄ ÃÃ ÈÈ ÉÉ ÊÊ ËË "
        + "ÌÌ ÍÍ ÎÎ ÏÏ ÒÒ ÓÓ ÔÔ ÖÖ ÕÕ ÙÙ ÚÚ ÛÛ "
        + "ÜÜ ÑÑ àà áá ââ ää ãã èè éé êê ëë "
        + "ìı̀ íı́ îı̂ ïı̈ òò óó ôô öö õõ ùù úú "
        + "ûû üü ññ";

    private static final Map<Character, List<String>> GLYPHS = load();

    private HudFont() {
    }

    /** Every character the font can draw, in a stable order for the pack's glyph sheet. */
    public static List<Character> characters() {
        return List.copyOf(GLYPHS.keySet());
    }

    /**
     * The glyph as {@link #CELL_ROWS} rows: two accent rows, then the cap-height
     * rows down to the descender. Empty rows are padded with dots.
     */
    public static List<String> cell(char c) {
        return GLYPHS.get(c);
    }

    public static boolean supported(char c) {
        return c == ' ' || GLYPHS.containsKey(c);
    }

    /**
     * A cell on the pack's glyph sheet: eight clear rows above the capitals (the top
     * two hold accents) and eight below the descender. The markers sit in the clear
     * corners on the left edge, so they never touch a letter or change its width,
     * and a size in eighths always puts the capitals on a whole pixel.
     */
    public static final int SHEET_CELL_WIDTH = 8;
    public static final int SHEET_CELL_HEIGHT = 24;
    /** Rows of the sheet cell above the {@link #cell} rows. */
    public static final int SHEET_TOP_ROWS = 8 - ACCENT_ROWS;

    /** Ink width in pixels: up to the last column with a lit pixel, as the client measures it. */
    public static int width(char c) {
        List<String> rows = GLYPHS.get(c);
        if (rows == null) {
            return 5;
        }
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.lastIndexOf('#') + 1);
        }
        return width;
    }

    private static Map<Character, List<String>> load() {
        Map<Character, List<String>> glyphs = new LinkedHashMap<>();
        for (String line : SOURCE.strip().split("\n")) {
            char c = line.charAt(0);
            String[] rows = line.substring(2).split("\\|");
            glyphs.put(c, pad(rows, null));
        }
        for (String entry : COMPOSED.split(" ")) {
            char letter = entry.charAt(0);
            List<String> base = glyphs.get(entry.charAt(1));
            String[] mark = MARKS.get(entry.charAt(2));
            glyphs.put(letter, withMark(base, mark, Character.isUpperCase(letter)));
        }
        return glyphs;
    }

    private static List<String> pad(String[] rows, String[] accent) {
        int width = 0;
        for (String row : rows) {
            width = Math.max(width, row.length());
        }
        String blank = ".".repeat(width);
        String[] cell = new String[CELL_ROWS];
        for (int i = 0; i < CELL_ROWS; i++) {
            int row = i - ACCENT_ROWS;
            cell[i] = row >= 0 && row < rows.length ? (rows[row] + blank).substring(0, width) : blank;
        }
        return List.of(cell);
    }

    /** Puts a mark above a letter: in the accent rows for capitals, above the x-height otherwise. */
    private static List<String> withMark(List<String> base, String[] mark, boolean capital) {
        int width = Math.max(base.getFirst().length(), 3);
        String[] cell = new String[CELL_ROWS];
        int inset = (width - base.getFirst().length()) / 2;
        for (int i = 0; i < CELL_ROWS; i++) {
            String row = ".".repeat(inset) + base.get(i);
            cell[i] = (row + ".".repeat(width)).substring(0, width);
        }
        int top = capital ? 0 : ACCENT_ROWS;
        for (int r = 0; r < 2; r++) {
            String row = trimMark(mark[r], width);
            StringBuilder merged = new StringBuilder(cell[top + r]);
            for (int x = 0; x < width; x++) {
                if (row.charAt(x) == '#') {
                    merged.setCharAt(x, '#');
                }
            }
            cell[top + r] = merged.toString();
        }
        return List.of(cell);
    }

    /** Centres a five pixel mark over a narrower letter. */
    private static String trimMark(String mark, int width) {
        int cut = (mark.length() - width) / 2;
        return cut <= 0 ? mark.substring(0, Math.min(mark.length(), width)) : mark.substring(cut, cut + width);
    }
}
