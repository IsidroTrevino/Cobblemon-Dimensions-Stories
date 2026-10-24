package com.cobblemondimensions.stories.ui.art;

import java.util.ArrayList;
import java.util.List;

/**
 * The walking dialogue box above the hotbar.
 *
 * <p>Everything is placed by its own coordinates on the walking art sheets. The
 * pack's shader scales the box to about half the window's width, as the old
 * client did, with its bottom edge resting 40 pixels above the bottom of the
 * screen.
 */
public final class DialogueArt {
    private static final Overlay OVERLAY = Overlay.DIALOGUE;
    private static final String DIR = "walking/";

    public static final int BAR_X = 29;
    public static final int BAR_Y = 74;
    public static final int PORTRAIT_X = 38;
    public static final int PANEL_X = 90;
    public static final int PANEL_Y = 90;
    public static final int PANEL_W = 128;
    public static final int PANEL_H = 46;
    public static final int PADDING = 4;
    /** Where the body starts with a speaker's name above it, and without one. */
    public static final int BODY_TOP_NAMED = PANEL_Y + PADDING + 11;
    public static final int BODY_TOP = PANEL_Y + PADDING;
    public static final int BODY_BOTTOM = PANEL_Y + PANEL_H - PADDING;

    /** Opening slide: the bar rises into place over these offsets. */
    private static final int[] SLIDE = {14, 8, 4, 1};
    public static final int MAX_CHOICES = 4;
    public static final int CHOICE_ROW = 9;
    public static final int CHOICE_PAD = 3;
    private static final int CHOICE_GAP = 5;
    /** The body shrinks through these sizes, in eighths, until the whole page fits. */
    public static final int[] BODY_SIZES = {7, 6, 5, 4};

    public static final Sprite BAR = UiAtlas.overlaySprite(OVERLAY, "dialogue_bar", BAR_Y, bar());
    public static final Sprite[] BAR_RISING = new Sprite[SLIDE.length];
    public static final Sprite[] PORTRAIT = {
        UiAtlas.overlaySprite(OVERLAY, "dialogue_portrait_1", 95, Art.crop(38, 95, 48, 41, DIR + "walking_portrait_1.png")),
        UiAtlas.overlaySprite(OVERLAY, "dialogue_portrait_2", 95, Art.crop(38, 95, 48, 41, DIR + "walking_portrait_2.png")),
    };
    /** The text panel unrolling from the left, in quarters. */
    public static final Sprite[] PANEL = new Sprite[4];
    public static final Sprite DIVIDER = UiAtlas.overlaySprite(OVERLAY, "dialogue_divider", PANEL_Y + PADDING + 8,
        new Art.Plate(PANEL_W - PADDING * 2, 1, 0xFF5C5C5C, 0));
    /** The little "click to continue" arrow, resting and bobbed down a pixel. */
    public static final Sprite[] CONTINUE = {
        UiAtlas.overlaySprite(OVERLAY, "dialogue_continue_0", PANEL_Y + PANEL_H - 8, arrow()),
        UiAtlas.overlaySprite(OVERLAY, "dialogue_continue_1", PANEL_Y + PANEL_H - 7, arrow()),
    };
    /** Choice boxes, by number of options; and the highlight bar, by [options - 1][index]. */
    public static final Sprite[] CHOICES = new Sprite[MAX_CHOICES];
    public static final Sprite[][] CHOICE_HIGHLIGHT = new Sprite[MAX_CHOICES][];

    public static final TextLine SPEAKER = UiAtlas.overlayLine(OVERLAY, PANEL_Y + PADDING - 1, 6);
    /** Body rows by [named ? 0 : 1][size][line], and the typing caret beside each row. */
    public static final TextLine[][][] BODY = new TextLine[2][BODY_SIZES.length][];
    public static final Sprite[][][] CARET = new Sprite[2][BODY_SIZES.length][];
    public static final TextLine[][] CHOICE_TEXT = new TextLine[MAX_CHOICES][];

    static {
        for (int frame = 0; frame < SLIDE.length; frame++) {
            BAR_RISING[frame] = UiAtlas.overlaySprite(OVERLAY, "dialogue_bar_rising_" + frame, BAR_Y + SLIDE[frame], bar());
        }
        for (int quarter = 0; quarter < 4; quarter++) {
            PANEL[quarter] = UiAtlas.overlaySprite(OVERLAY, "dialogue_panel_" + quarter, PANEL_Y,
                Art.crop(PANEL_X, PANEL_Y, PANEL_W * (quarter + 1) / 4, PANEL_H, DIR + "walking_text_background.png"));
        }
        for (int named = 0; named < 2; named++) {
            int top = named == 0 ? BODY_TOP_NAMED : BODY_TOP;
            for (int size = 0; size < BODY_SIZES.length; size++) {
                int eighths = BODY_SIZES[size];
                int lines = bodyLines(top, eighths);
                BODY[named][size] = new TextLine[lines];
                CARET[named][size] = new Sprite[lines];
                for (int line = 0; line < lines; line++) {
                    int y = bodyRow(top, eighths, line);
                    BODY[named][size][line] = UiAtlas.overlayLine(OVERLAY, y, eighths);
                    CARET[named][size][line] = UiAtlas.overlaySprite(OVERLAY,
                        "dialogue_caret_" + named + "_" + eighths + "_" + line, y,
                        new Art.Plate(1, bodyStep(eighths) - 1, 0xFF1F1F1F, 0));
                }
            }
        }
        for (int count = 1; count <= MAX_CHOICES; count++) {
            int height = count * CHOICE_ROW + CHOICE_PAD * 2;
            int top = choicesTop(count);
            List<int[]> box = new ArrayList<>();
            box.add(new int[] {0, 0, PANEL_W + 4, height + 4, 0xFFF2F2F2});
            box.add(new int[] {1, 1, PANEL_W + 2, height + 2, 0xFF161616});
            box.add(new int[] {2, 2, PANEL_W, height, 0xFFC6C6C6});
            CHOICES[count - 1] = UiAtlas.overlaySprite(OVERLAY, "dialogue_choices_" + count, top - 2,
                new Art.Rects(PANEL_W + 4, height + 4, box));
            CHOICE_HIGHLIGHT[count - 1] = new Sprite[count];
            CHOICE_TEXT[count - 1] = new TextLine[count];
            for (int index = 0; index < count; index++) {
                int rowTop = top + CHOICE_PAD + index * CHOICE_ROW;
                CHOICE_HIGHLIGHT[count - 1][index] = UiAtlas.overlaySprite(OVERLAY,
                    "dialogue_choice_" + count + "_" + index, rowTop,
                    new Art.Plate(PANEL_W - 4, CHOICE_ROW - 1, 0xFF44444A, 0));
                CHOICE_TEXT[count - 1][index] = UiAtlas.overlayLine(OVERLAY, rowTop + 1, 6);
            }
        }
    }

    private DialogueArt() {
    }

    /** Line pitch for a body size: the game's line of text and a pixel, scaled. */
    public static int bodyStep(int eighths) {
        return Math.max(1, Math.round(10 * eighths / 8.0F));
    }

    /** How many body lines fit below {@code top} at a size. */
    public static int bodyLines(int top, int eighths) {
        return Math.max(1, (BODY_BOTTOM - top) / bodyStep(eighths));
    }

    private static int bodyRow(int top, int eighths, int line) {
        // a whole-pixel pitch keeps the gaps between lines even
        return top + line * bodyStep(eighths);
    }

    /** Top of the choice box for a number of options; it floats above the panel. */
    public static int choicesTop(int count) {
        return PANEL_Y - CHOICE_GAP - (count * CHOICE_ROW + CHOICE_PAD * 2);
    }

    private static Art bar() {
        return Art.crop(BAR_X, BAR_Y, 197, 70,
            DIR + "walking_frame.png", DIR + "walking_background.png", DIR + "walking_portrait_background.png");
    }

    private static Art arrow() {
        int ink = 0xFF1F1F1F;
        return new Art.Rects(6, 3, List.of(
            new int[] {0, 0, 6, 1, ink}, new int[] {1, 1, 4, 1, ink}, new int[] {2, 2, 2, 1, ink}));
    }

    public static void init() {
        // Touching the class runs the registrations above.
    }
}
