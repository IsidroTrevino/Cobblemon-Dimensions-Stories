package com.cobblemondimensions.stories.ui.art;

import java.util.ArrayList;
import java.util.List;

/**
 * The two shopkeeper screens: The Curator's closet with its gloved hands, and
 * Mara's counter with her portrait.
 *
 * <p>Both are drawn in a one-row chest menu. Its clickable rows are the chest row
 * plus the lower block, so the buttons sit on {@link #BUTTON_ROWS}; the art covers
 * the whole menu, so nothing of the chest shows around it.
 */
public final class ShopArt {
    public static final int MENU_ROWS = 1;
    /** Top edges of the four button rows, which are the lower block's rows. */
    public static final int[] BUTTON_ROWS = {
        Grid.lowerY(MENU_ROWS, 0), Grid.lowerY(MENU_ROWS, 1), Grid.lowerY(MENU_ROWS, 2), Grid.lowerY(MENU_ROWS, 3),
    };

    // ------------------------------------------------------------ the closet

    /**
     * The hand sheets are placed 39 left and 45 up of their sheet coordinates, and
     * the lower half is {@link #HAND_STRETCH} pixels taller than drawn so its
     * buttons can sit one slot apart.
     */
    public static final int HAND_STRETCH = 36;
    private static final int HAND_SPLIT = 105;
    private static final String HAND = "hand/";

    public static final int HAND_TEXT_X = handX(36);
    public static final int HAND_TEXT_W = 109;
    public static final int HAND_TEXT_TOP = handY(90);
    public static final int HAND_TEXT_BOTTOM = handY(136);
    public static final int HAND_BUTTON_X = handX(148);
    public static final int HAND_BUTTON_W = 71;
    public static final int HAND_BUTTON_H = 16;
    public static final int HAND_TITLE_CENTER = handX(128);
    /** Room for a label inside a button's border. */
    public static final int HAND_LABEL_W = HAND_BUTTON_W - 6;
    public static final int HAND_BODY_LINES = 7;
    public static final int HAND_BODY_SMALL_LINES = 12;

    /** How far the hands sit below their resting place as they rise out of the dark. */
    public static final int[] RISE = {52, 45, 38, 33, 28, 23, 19, 16, 13, 10, 8, 6, 4, 3, 2, 1};

    public static final Sprite HAND_SCENE = UiAtlas.sprite("hand_scene", handY(10),
        Art.crop(34, 10, 187, 74, HAND + "hand_illustration_background.png", HAND + "hand_illustration_back.png"));
    public static final Sprite HAND_LOWER = UiAtlas.sprite("hand_lower", handY(87),
        Art.stretch(34, 87, 187, 53, HAND_SPLIT, HAND_STRETCH, HAND + "hand_back_of_bottom_part.png",
            HAND + "hand_text_background.png", HAND + "hand_button_background.png"));
    public static final Sprite HAND_FRAME = UiAtlas.sprite("hand_frame", handY(5),
        Art.stretch(29, 5, 197, 139, HAND_SPLIT, HAND_STRETCH, HAND + "hand_corners.png"));
    public static final Sprite HAND_TITLE_BAR = UiAtlas.sprite("hand_title_bar", handY(72),
        new Art.Plate(118, 11, 0xD51D1D1D, 0));
    public static final Sprite HAND_RULE = UiAtlas.sprite("hand_rule", HAND_TEXT_TOP + 11,
        new Art.Plate(HAND_TEXT_W - 6, 1, 0xFF5C5C5C, 0));
    public static final Sprite HAND_CARET = UiAtlas.sprite("hand_caret", HAND_TEXT_BOTTOM - 8,
        new Art.Plate(5, 2, 0xFF333333, 0));
    public static final Sprite[] HAND_CONTINUE = {
        UiAtlas.sprite("hand_continue_0", HAND_TEXT_BOTTOM - 8, triangle(6, 0xFF2A2A2A)),
        UiAtlas.sprite("hand_continue_1", HAND_TEXT_BOTTOM - 7, triangle(6, 0xFF2A2A2A)),
    };

    /** The hands at rest, the two flap frames of each pose, the head shake and the nod. */
    public static final Sprite HAND_BASE_DOWN = hands("hand_base_down", 0);
    public static final Sprite HAND_BASE_UP = hands("hand_base_up", 0);
    public static final Sprite HAND_SPEAKING_DOWN = hands("hand_speaking_down", 0);
    public static final Sprite HAND_SPEAKING_UP = hands("hand_speaking_up", 0);
    public static final Sprite[] HAND_NO = {
        hands("hand_no_left", 0), hands("hand_no_middle", 0), hands("hand_no_right", 0),
    };
    /** The nod, bobbing from two pixels up to two down. */
    public static final Sprite[] HAND_YES = {
        hands("hand_yes", -2), hands("hand_yes", -1), hands("hand_yes", 0), hands("hand_yes", 1), hands("hand_yes", 2),
    };
    /** Rising out of the dark when the closet opens, by [rise][flap]. */
    public static final Sprite[][] HAND_RISING = new Sprite[RISE.length][2];

    /** Buttons by [row]: resting, just pressed, and greyed out, with the face painted over each. */
    public static final Sprite[] HAND_BUTTON = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] HAND_BUTTON_PRESSED = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] HAND_BUTTON_OFF = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] HAND_FACE = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] HAND_FACE_PRESSED = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] HAND_FACE_OFF = new Sprite[BUTTON_ROWS.length];
    /** Catalog row highlight and the placeholder bars rows show while they are still revealing. */
    public static final Sprite[] HAND_ROW_PICKED = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] HAND_ROW_PENDING = new Sprite[BUTTON_ROWS.length];

    public static final TextLine HAND_TITLE = UiAtlas.menuLine(handY(73), 8);
    public static final TextLine HAND_TITLE_SHADOW = UiAtlas.menuLine(handY(73) + 1, 8);
    public static final TextLine HAND_NAME = UiAtlas.menuLine(HAND_TEXT_TOP + 3, 8);
    public static final TextLine[] HAND_BODY = new TextLine[HAND_BODY_LINES];
    public static final TextLine[] HAND_BODY_SMALL = new TextLine[HAND_BODY_SMALL_LINES];
    /** Button labels and catalog rows share these, one per row. */
    public static final TextLine[] HAND_ROW_TEXT = new TextLine[BUTTON_ROWS.length];
    public static final TextLine[] HAND_PROMPT = {
        UiAtlas.menuLine(HAND_TEXT_TOP + 3, 8), UiAtlas.menuLine(HAND_TEXT_TOP + 12, 8),
    };
    public static final TextLine HAND_ITEM = UiAtlas.menuLine(HAND_TEXT_TOP + 26, 8);
    public static final TextLine HAND_PRICE = UiAtlas.menuLine(HAND_TEXT_TOP + 40, 8);

    // ------------------------------------------------------------ the counter

    private static final String SHOP = "shop/";
    /**
     * The counter art is 236 wide; shifting it 30 left centres it on the menu, as
     * the old screen centred it on the window.
     */
    public static final int CLASSIC_X = -30;
    public static final int CLASSIC_BUTTON_X = CLASSIC_X + 8;
    public static final int CLASSIC_BUTTON_W = 110;
    public static final int CLASSIC_TEXT_X = CLASSIC_X + 10;
    public static final int CLASSIC_TEXT_W = 106;
    public static final int CLASSIC_FULL_LINES = 11;
    public static final int CLASSIC_OPTION_LINES = 3;

    public static final Sprite CLASSIC_BASE = UiAtlas.sprite("classic_base", 0,
        Art.crop(0, 0, 236, 148, SHOP + "shop_main.png", SHOP + "npc_character_background.png"));
    /** Mara's name plate, painted over the placeholder lettering on the panel. */
    public static final Sprite CLASSIC_NAME_PLATE = UiAtlas.sprite("classic_name_plate", 3,
        new Art.Plate(86, 9, 0xFFD0CED6, 0));
    public static final Sprite CLASSIC_CASUAL = UiAtlas.sprite("classic_casual", 13,
        Art.crop(139, 13, 84, 121, SHOP + "npc_character_casual.png"));
    public static final Sprite CLASSIC_TALKING = UiAtlas.sprite("classic_talking", 13,
        Art.crop(139, 13, 84, 121, SHOP + "npc_character_talking.png"));
    public static final Sprite CLASSIC_SMILING = UiAtlas.sprite("classic_smiling", 13,
        Art.crop(139, 13, 84, 121, SHOP + "npc_character_smiling.png"));
    /** The corner of the menu that the shifted art leaves bare, darkened to match the backdrop. */
    public static final Sprite CLASSIC_CORNER = UiAtlas.sprite("classic_corner", 0,
        new Art.Plate(Grid.WIDTH - (CLASSIC_X + 176), 5, 0xFF101010, 0));
    public static final Sprite[] CLASSIC_CONTINUE = {
        UiAtlas.sprite("classic_continue_0", 131, triangle(7, 0xFFFFFFFF)),
        UiAtlas.sprite("classic_continue_1", 132, triangle(7, 0xFFFFFFFF)),
    };
    /** Buttons by [row][sheet], resting and just pressed; buttons alternate between the two sheets. */
    public static final Sprite[][] CLASSIC_BUTTON = new Sprite[BUTTON_ROWS.length][2];
    public static final Sprite[][] CLASSIC_BUTTON_PRESSED = new Sprite[BUTTON_ROWS.length][2];
    public static final Sprite[] CLASSIC_FACE = new Sprite[BUTTON_ROWS.length];
    public static final Sprite[] CLASSIC_FACE_PRESSED = new Sprite[BUTTON_ROWS.length];

    public static final TextLine CLASSIC_NAME = UiAtlas.menuLine(3, 8);
    public static final TextLine CLASSIC_NAME_SHADOW = UiAtlas.menuLine(4, 8);
    public static final TextLine[] CLASSIC_BODY = new TextLine[CLASSIC_FULL_LINES];
    public static final TextLine[] CLASSIC_BODY_SHADOW = new TextLine[CLASSIC_FULL_LINES];
    public static final TextLine[] CLASSIC_LABEL = new TextLine[BUTTON_ROWS.length];
    public static final TextLine[] CLASSIC_LABEL_SHADOW = new TextLine[BUTTON_ROWS.length];

    static {
        for (int rise = 0; rise < RISE.length; rise++) {
            HAND_RISING[rise][0] = hands("hand_base_down", RISE[rise]);
            HAND_RISING[rise][1] = hands("hand_base_up", RISE[rise]);
        }
        for (int line = 0; line < HAND_BODY_LINES; line++) {
            HAND_BODY[line] = UiAtlas.menuLine(HAND_TEXT_TOP + 13 + line * 9, 8);
        }
        for (int line = 0; line < HAND_BODY_SMALL_LINES; line++) {
            HAND_BODY_SMALL[line] = UiAtlas.menuLine(HAND_TEXT_TOP + 13 + line * 5, 4);
        }
        for (int row = 0; row < BUTTON_ROWS.length; row++) {
            int y = BUTTON_ROWS[row];
            HAND_BUTTON[row] = handButton("hand_button", row, y);
            HAND_BUTTON_PRESSED[row] = handButton("hand_button_hover", row, y);
            HAND_BUTTON_OFF[row] = handButton("hand_button_disable", row, y);
            HAND_FACE[row] = handFace("hand_face_" + row, y, 0xFFDADADA);
            HAND_FACE_PRESSED[row] = handFace("hand_face_pressed_" + row, y, 0xFFF3F3F3);
            HAND_FACE_OFF[row] = handFace("hand_face_off_" + row, y, 0xFF505050);
            HAND_ROW_PICKED[row] = UiAtlas.sprite("hand_row_picked_" + row, y,
                new Art.Plate(HAND_TEXT_W - 2, 16, 0xFF4D4D4D, 0));
            HAND_ROW_PENDING[row] = UiAtlas.sprite("hand_row_pending_" + row, y + 7,
                new Art.Plate(15 + row * 7, 2, 0xFF5C5C5C, 0));
            HAND_ROW_TEXT[row] = UiAtlas.menuLine(y + 4, 8);

            int buttonY = y + 1;
            for (int sheet = 0; sheet < 2; sheet++) {
                String file = SHOP + "button_" + (sheet + 1);
                CLASSIC_BUTTON[row][sheet] = UiAtlas.sprite("classic_button_" + (sheet + 1) + "_" + row, buttonY,
                    Art.crop(0, 0, CLASSIC_BUTTON_W, 14, file + ".png"));
                CLASSIC_BUTTON_PRESSED[row][sheet] = UiAtlas.sprite("classic_button_" + (sheet + 1) + "_pressed_" + row,
                    buttonY, Art.crop(0, 0, CLASSIC_BUTTON_W, 14, file + "_hover.png"));
            }
            CLASSIC_FACE[row] = UiAtlas.sprite("classic_face_" + row, buttonY + 2,
                new Art.Plate(CLASSIC_BUTTON_W - 4, 10, 0xFF6B6773, 0));
            CLASSIC_FACE_PRESSED[row] = UiAtlas.sprite("classic_face_pressed_" + row, buttonY + 2,
                new Art.Plate(CLASSIC_BUTTON_W - 4, 10, 0xFF393246, 0));
            CLASSIC_LABEL[row] = UiAtlas.menuLine(buttonY + 3, 8);
            CLASSIC_LABEL_SHADOW[row] = UiAtlas.menuLine(buttonY + 4, 8);
        }
        for (int line = 0; line < CLASSIC_FULL_LINES; line++) {
            CLASSIC_BODY[line] = UiAtlas.menuLine(20 + line * 10, 8);
            CLASSIC_BODY_SHADOW[line] = UiAtlas.menuLine(21 + line * 10, 8);
        }
    }

    private ShopArt() {
    }

    public static int handX(int sheetX) {
        return sheetX - 39;
    }

    public static int handY(int sheetY) {
        return sheetY - 45 + (sheetY >= HAND_SPLIT ? HAND_STRETCH : 0);
    }

    /** One pose of the hands, cut from its sheet and lowered by {@code drop} pixels. */
    private static Sprite hands(String sheet, int drop) {
        String name = sheet + (drop == 0 ? "" : drop > 0 ? "_down" + drop : "_up" + -drop);
        return UiAtlas.sprite(name, handY(10) + drop, Art.crop(34, 10, 187, 76, HAND + sheet + ".png"));
    }

    /** A button sheet stretched to a slot's height by repeating a flat row of its middle. */
    private static Sprite handButton(String sheet, int row, int y) {
        String file = HAND + sheet + ".png";
        return UiAtlas.sprite(sheet + "_" + row, y, Art.canvas(HAND_BUTTON_W, HAND_BUTTON_H)
            .piece(file, 148, 90, HAND_BUTTON_W, 5, 0, 0)
            .piece(file, 148, 95, HAND_BUTTON_W, 1, 0, 5)
            .piece(file, 148, 95, HAND_BUTTON_W, 1, 0, 6)
            .piece(file, 148, 95, HAND_BUTTON_W, 1, 0, 7)
            .piece(file, 148, 95, HAND_BUTTON_W, 1, 0, 8)
            .piece(file, 148, 95, HAND_BUTTON_W, 1, 0, 9)
            .piece(file, 148, 95, HAND_BUTTON_W, 1, 0, 10)
            .piece(file, 148, 95, HAND_BUTTON_W, 5, 0, 11)
            .build());
    }

    /** The button art supplies the border; the face is repainted per state. */
    private static Sprite handFace(String name, int y, int color) {
        return UiAtlas.sprite(name, y + 2, new Art.Plate(HAND_BUTTON_W - 8, HAND_BUTTON_H - 4, color, 0));
    }

    /** A small downward triangle, the old screens' "click to continue" mark. */
    private static Art triangle(int width, int color) {
        int rows = (width + 1) / 2;
        List<int[]> rects = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            rects.add(new int[] {row, row, width - row * 2, 1, color});
        }
        return new Art.Rects(width, rows, List.copyOf(rects));
    }

    public static void init() {
        // Touching the class runs the registrations above.
    }
}
