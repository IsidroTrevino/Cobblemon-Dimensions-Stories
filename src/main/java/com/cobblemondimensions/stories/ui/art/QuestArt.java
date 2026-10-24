package com.cobblemondimensions.stories.ui.art;

/**
 * The quest journal's sprites.
 *
 * <p>The journal is the 176x137 panel that starts at (40, 7) on the sheets, which
 * is exactly as wide as a chest menu. A six-row chest is taller than that, so the
 * panel is stretched through a row that is flat from edge to edge: the list and
 * the description simply get taller, and the panel covers the whole menu with
 * nothing of the chest showing around it. Quest rows sit on the six chest rows;
 * the page buttons and the status plate sit on the bottom strip of slots.
 *
 * <p>Text is drawn at normal size wherever it fits, and smaller otherwise.
 */
public final class QuestArt {
    public static final int MENU_ROWS = 6;
    private static final int ORIGIN_X = 40;
    private static final int ORIGIN_Y = 7;
    /** Sheet row that is repeated, and how many extra times. */
    private static final int SPLIT = 70;
    private static final int STRETCH = Grid.height(MENU_ROWS) - 137;
    private static final String DIR = "quest/";

    public static final int ROWS_PER_PAGE = MENU_ROWS;
    public static final int ROW_X = 51 - ORIGIN_X;
    public static final int ROW_W = 72;
    public static final int ICON_SIZE = 7;
    /** Slot row of the page buttons and the status plate: the bottom strip. */
    public static final int BUTTON_ROW = 3;

    public static final int DETAIL_X = 131 - ORIGIN_X;
    public static final int DETAIL_W = 76;
    public static final int STATUS_X = 174 - ORIGIN_X;
    public static final int STATUS_W = 33;
    public static final int PREV_X = 52 - ORIGIN_X;
    public static final int NEXT_X = 89 - ORIGIN_X;
    public static final int PAGE_W = 33;
    /** How bright the "New" glow gets, in steps, as it breathes. */
    public static final int GLOW_STEPS = 4;

    public static final Sprite PANEL = UiAtlas.sprite("quest_panel", 0,
        Art.stretch(ORIGIN_X, ORIGIN_Y, 176, 137, SPLIT, STRETCH,
            DIR + "quest_frame.png", DIR + "quest_background.png", DIR + "quest_list_background.png",
            DIR + "quest_title_background.png", DIR + "quest_description_background.png",
            DIR + "quest_reward_background.png", DIR + "quest_page_buttons.png"));
    public static final Sprite BUTTON = UiAtlas.sprite("quest_status_button", y(127),
        Art.crop(174, 127, STATUS_W, 8, DIR + "quest_button.png"));
    public static final Sprite BUTTON_OFF = UiAtlas.sprite("quest_status_off", y(127),
        Art.crop(174, 127, STATUS_W, 8, DIR + "quest_button_disabled.png"));
    /** The "New" glow over the status button at a few strengths, faintest first. */
    public static final Sprite[] BUTTON_GLOW = new Sprite[GLOW_STEPS];

    /** Idle and highlighted row plates, one per list row. */
    public static final Sprite[] ROW = new Sprite[ROWS_PER_PAGE];
    public static final Sprite[] ROW_LIT = new Sprite[ROWS_PER_PAGE];
    /** The status swatch, full and inset by a pixel. Plain white, tinted when drawn. */
    public static final Sprite[] ICON = new Sprite[ROWS_PER_PAGE];
    public static final Sprite[] ICON_INSET = new Sprite[ROWS_PER_PAGE];
    /** Row titles: one line at normal or half size, or two stacked half-size lines. */
    public static final TextLine[] ROW_TEXT = new TextLine[ROWS_PER_PAGE];
    public static final TextLine[] ROW_TEXT_SMALL = new TextLine[ROWS_PER_PAGE];
    public static final TextLine[] ROW_TEXT_UPPER = new TextLine[ROWS_PER_PAGE];
    public static final TextLine[] ROW_TEXT_LOWER = new TextLine[ROWS_PER_PAGE];

    public static final TextLine TITLE = UiAtlas.menuLine(y(16) + 3, 8);
    public static final TextLine TITLE_SMALL = UiAtlas.menuLine(y(16) + 4, 4);
    public static final TextLine TITLE_UPPER = UiAtlas.menuLine(y(16) + 1, 4);
    public static final TextLine TITLE_LOWER = UiAtlas.menuLine(y(16) + 6, 4);
    /** The description at normal size when the whole text fits, otherwise at half size. */
    private static final int DESCRIPTION_TOP = y(31) + 4;
    private static final int DESCRIPTION_BOTTOM = y(101) - 2;
    public static final TextLine[] DESCRIPTION = lines(DESCRIPTION_TOP, DESCRIPTION_BOTTOM, 8, 9);
    public static final TextLine[] DESCRIPTION_MEDIUM = lines(DESCRIPTION_TOP, DESCRIPTION_BOTTOM, 6, 7);
    public static final TextLine[] DESCRIPTION_SMALL = lines(DESCRIPTION_TOP, DESCRIPTION_BOTTOM, 4, 5);
    public static final TextLine HINT = UiAtlas.menuLine(y(101) + 1, 8);
    public static final TextLine HINT_SMALL = UiAtlas.menuLine(y(101) + 3, 4);
    public static final TextLine HINT_UPPER = UiAtlas.menuLine(y(101), 4);
    public static final TextLine HINT_LOWER = UiAtlas.menuLine(y(101) + 5, 4);
    public static final TextLine REWARD = UiAtlas.menuLine(y(115) + 1, 8);
    public static final TextLine REWARD_SMALL = UiAtlas.menuLine(y(115) + 2, 4);
    /** The status plate and page buttons are only four pixels deep inside, so their labels are small. */
    public static final TextLine STATUS = UiAtlas.menuLine(y(127) + 2, 4);
    public static final TextLine PAGE = UiAtlas.menuLine(y(125) + 2, 4);

    static {
        for (int step = 0; step < GLOW_STEPS; step++) {
            BUTTON_GLOW[step] = UiAtlas.sprite("quest_status_glow_" + step, y(127),
                new Art.Faded(Art.crop(174, 127, STATUS_W, 8, DIR + "quest_button_active.png"),
                    Math.round(255 * 0.5F * (step + 1) / GLOW_STEPS)));
        }
        for (int row = 0; row < ROWS_PER_PAGE; row++) {
            int top = rowY(row);
            ROW[row] = UiAtlas.sprite("quest_row_" + row, top, Art.crop(51, 18, ROW_W, 11, DIR + "quest_row_disabled.png"));
            ROW_LIT[row] = UiAtlas.sprite("quest_row_lit_" + row, top, Art.crop(51, 18, ROW_W, 11, DIR + "quest_row.png"));
            ICON[row] = UiAtlas.sprite("quest_icon_" + row, top + 2, new Art.Plate(ICON_SIZE, ICON_SIZE, 0xFFFFFFFF, 0));
            ICON_INSET[row] = UiAtlas.sprite("quest_icon_inset_" + row, top + 3,
                new Art.Plate(ICON_SIZE - 2, ICON_SIZE - 2, 0xFFFFFFFF, 0));
            ROW_TEXT[row] = UiAtlas.menuLine(top + 2, 8);
            ROW_TEXT_SMALL[row] = UiAtlas.menuLine(top + 4, 4);
            ROW_TEXT_UPPER[row] = UiAtlas.menuLine(top + 1, 4);
            ROW_TEXT_LOWER[row] = UiAtlas.menuLine(top + 6, 4);
        }
    }

    private QuestArt() {
    }

    /** Where a sheet row lands in the menu, allowing for the stretch. */
    public static int y(int sheetY) {
        return sheetY - ORIGIN_Y + (sheetY >= SPLIT ? STRETCH : 0);
    }

    /** Top of a list row: centred in its chest row. */
    public static int rowY(int row) {
        return Grid.y(row) + 2;
    }

    private static TextLine[] lines(int top, int bottom, int eighths, int pitch) {
        TextLine[] lines = new TextLine[(bottom - top) / pitch];
        for (int line = 0; line < lines.length; line++) {
            lines[line] = UiAtlas.menuLine(top + line * pitch, eighths);
        }
        return lines;
    }

    public static void init() {
        // Touching the class runs the registrations above.
    }
}
