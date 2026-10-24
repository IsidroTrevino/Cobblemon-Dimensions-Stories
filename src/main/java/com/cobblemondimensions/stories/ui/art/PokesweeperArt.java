package com.cobblemondimensions.stories.ui.art;

/**
 * Pokesweeper's sprites.
 *
 * <p>The board is drawn at twice the size of the supplied cell art so that one
 * cell is exactly one chest slot (18 pixels). The supplied cells are nine pixels
 * wide and eight tall; one flat middle row is repeated before doubling so the
 * result is a square 18x18 tile that still lines up with its neighbours.
 */
public final class PokesweeperArt {
    public static final int MENU_ROWS = 6;
    public static final int VIEW_COLUMNS = 9;
    public static final int VIEW_ROWS = 5;
    /**
     * A tile is a two pixel border plus a 16 pixel face, so the first tile starts
     * two pixels up and left of the slot's item area and its face lines up with it.
     */
    public static final int BOARD_X = Grid.x(0) - 2;
    public static final int BOARD_Y = Grid.y(0) - 2;
    public static final int TILE = 18;
    /** Where the lower panel (readouts, labels and help text) starts. */
    public static final int LOWER_Y = 132;

    private static final String CELL = "pokesweeper/cell.png";
    private static final String OPEN = "pokesweeper/hard/grid_open.png";
    private static final String SCREENS = "pokesweeper/hard/screens.png";
    private static final String TOPBAR = "pokesweeper/hard/topbar.png";

    /** Border colours that tell the three sizes apart: easy, medium, hard. */
    private static final int[] ACCENTS = {0xFF4BE07A, 0xFFFFD166, 0xFFFF6B6B};

    public static final Sprite[] PLATE = new Sprite[3];
    public static final Sprite BOARD_BACK = UiAtlas.sprite("sweep_board_back", BOARD_Y,
        new Art.Plate(VIEW_COLUMNS * TILE + 2, VIEW_ROWS * TILE + 2, 0xFF1D121E, 0));
    public static final Sprite[] CLOSED = new Sprite[VIEW_ROWS];
    public static final Sprite[] OPENED = new Sprite[VIEW_ROWS];
    /** A closed tile in the control row, used as a button face. */
    public static final Sprite BUTTON = UiAtlas.sprite("sweep_button", Grid.y(5) - 2, tile(CELL));
    public static final Sprite MINES_WELL = UiAtlas.sprite("sweep_mines_well", LOWER_Y,
        Art.crop(59, 21, 28, 19, SCREENS));
    public static final Sprite TIMER_WELL = UiAtlas.sprite("sweep_timer_well", LOWER_Y,
        Art.crop(168, 21, 30, 19, SCREENS));
    public static final Sprite STATUS_BAR = UiAtlas.sprite("sweep_status_bar", LOWER_Y,
        Art.crop(80, 21, 96, 19, TOPBAR));

    /** Double-size digit rows, one per viewport row, so counts sit centred in their tiles. */
    public static final TextLine[] DIGIT = new TextLine[VIEW_ROWS];
    public static final TextLine TITLE = UiAtlas.line(5, 1);
    public static final TextLine BUTTON_LABEL = UiAtlas.line(Grid.y(5) + 4, 1);
    public static final TextLine LED = UiAtlas.line(LOWER_Y + 6, 1);
    public static final TextLine[] LOWER = {
        UiAtlas.line(LOWER_Y + 26, 1), UiAtlas.line(LOWER_Y + 37, 1), UiAtlas.line(LOWER_Y + 48, 1),
        UiAtlas.line(LOWER_Y + 59, 1), UiAtlas.line(LOWER_Y + 70, 1),
    };

    static {
        for (int mode = 0; mode < PLATE.length; mode++) {
            PLATE[mode] = UiAtlas.sprite("sweep_plate_" + mode, 0,
                new Art.Plate(Grid.WIDTH, Grid.height(MENU_ROWS), 0xFF2A1C2E, ACCENTS[mode]));
        }
        for (int row = 0; row < VIEW_ROWS; row++) {
            CLOSED[row] = UiAtlas.sprite("sweep_closed_" + row, BOARD_Y + row * TILE, tile(CELL));
            OPENED[row] = UiAtlas.sprite("sweep_open_" + row, BOARD_Y + row * TILE, tile(OPEN));
            // a digit cell is 16 tall at double size, exactly the tile's inner area
            DIGIT[row] = UiAtlas.line(BOARD_Y + 2 + row * TILE, 2);
        }
    }

    private PokesweeperArt() {
    }

    /** One 18x18 tile: the 9x8 cell with a flat row repeated, doubled. */
    private static Art tile(String sheet) {
        Art.Composite tile = Art.canvas(9, 9)
            .piece(sheet, 56, 47, 9, 5, 0, 0)
            .piece(sheet, 56, 51, 9, 1, 0, 5)
            .piece(sheet, 56, 52, 9, 3, 0, 6)
            .build();
        return tile.scaled(2);
    }

    /** Left edge of the tile for a viewport column. */
    public static int tileX(int column) {
        return BOARD_X + column * TILE;
    }

    /** Horizontal centre of a tile's face, where its digit is centred. */
    public static int faceCenter(int column) {
        return tileX(column) + 2 + 8;
    }

    public static void init() {
        // Touching the class runs the registrations above.
    }
}
