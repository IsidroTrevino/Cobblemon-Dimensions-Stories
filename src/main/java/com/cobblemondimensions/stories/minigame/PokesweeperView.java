package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.ui.Canvas;
import com.cobblemondimensions.stories.ui.art.Grid;
import com.cobblemondimensions.stories.ui.art.PokesweeperArt;
import net.minecraft.network.chat.Component;

/**
 * Draws a Pokesweeper board as a menu title: the tiles and counts here, the TNT
 * and flags as ordinary items in the slots above them.
 */
final class PokesweeperView {
    static final int ROWS = PokesweeperArt.MENU_ROWS;
    /** Control row: view movement, restart and the three sizes. */
    static final int CONTROL_ROW = 5;
    static final int PREVIOUS_COLUMNS = 0;
    static final int NEXT_COLUMNS = 1;
    static final int PREVIOUS_ROWS = 2;
    static final int NEXT_ROWS = 3;
    static final int RESTART = 4;
    static final int FIRST_SIZE = 6;

    private static final int[] COUNT_COLORS = {
        0x000000, 0x6FA8FF, 0x63D471, 0xFF6B6B, 0xFFD166, 0xC98BFF, 0x5FE3D8, 0xFFFFFF, 0xB9AEC4,
    };
    private static final int LED = 0xFF4136;
    private static final int LED_GHOST = 0x3D1512;
    private static final int LABEL = 0x2A1C2E;
    private static final int LABEL_HOT = 0x7A2018;
    private static final int LABEL_WIN = 0x14763A;
    private static final int PALE = 0xCFC4D8;
    private static final int GOLD = 0xFFD166;
    private static final int DISABLED = 0x7A7080;

    private PokesweeperView() {
    }

    static Component compose(PokesweeperMode mode, PokesweeperBoard board, PokesweeperViewport view,
                             int seconds, int bestSeconds) {
        Canvas canvas = new Canvas();
        canvas.sprite(PokesweeperArt.PLATE[mode.ordinal()], 0);
        canvas.sprite(PokesweeperArt.BOARD_BACK, PokesweeperArt.BOARD_X);
        canvas.text(PokesweeperArt.TITLE, "POKESWEEPER - " + mode.name(), 8, LED);
        if (bestSeconds > 0) {
            canvas.textRight(PokesweeperArt.TITLE, "BEST " + bestSeconds + "S", Grid.WIDTH - 8, GOLD);
        }

        for (int slot = 0; slot < PokesweeperArt.VIEW_COLUMNS * PokesweeperArt.VIEW_ROWS; slot++) {
            int cell = view.cellAt(slot);
            if (cell < 0) {
                continue;
            }
            int column = slot % PokesweeperArt.VIEW_COLUMNS;
            int row = slot / PokesweeperArt.VIEW_COLUMNS;
            tile(canvas, board, cell, column, row);
        }
        controls(canvas, mode, view, board);
        readouts(canvas, board, seconds);
        help(canvas, view, board, bestSeconds);
        return canvas.build();
    }

    private static void tile(Canvas canvas, PokesweeperBoard board, int cell, int column, int row) {
        PokesweeperBoard.Cell state = board.state(cell);
        int x = PokesweeperArt.tileX(column);
        switch (state) {
            case HIDDEN -> canvas.sprite(PokesweeperArt.CLOSED[row], x);
            case FLAGGED -> canvas.sprite(PokesweeperArt.CLOSED[row], x,
                board.over() && board.wrongFlag(cell) ? WRONG_TINT : 0xFFFFFF);
            case REVEALED -> {
                boolean mine = board.mine(cell);
                canvas.sprite(PokesweeperArt.OPENED[row], x, mine ? BOOM_TINT : 0xFFFFFF);
                int count = board.count(cell);
                if (!mine && count > 0) {
                    canvas.textCentered(PokesweeperArt.DIGIT[row], String.valueOf(count),
                        PokesweeperArt.faceCenter(column), COUNT_COLORS[Math.min(count, 8)]);
                }
            }
        }
    }

    private static final int WRONG_TINT = 0xFF9A9A;
    private static final int BOOM_TINT = 0xFF8A8A;

    private static void controls(Canvas canvas, PokesweeperMode mode, PokesweeperViewport view,
                                 PokesweeperBoard board) {
        String[] labels = {"<", ">", "^", "v", "R"};
        boolean[] enabled = {
            view.firstCol() > 0, view.lastCol() < board.cols(),
            view.firstRow() > 0, view.lastRow() < board.rows(), true,
        };
        for (int column = 0; column <= RESTART; column++) {
            canvas.sprite(PokesweeperArt.BUTTON, PokesweeperArt.tileX(column));
            canvas.textCentered(PokesweeperArt.BUTTON_LABEL, labels[column],
                PokesweeperArt.faceCenter(column), enabled[column] ? LABEL : DISABLED);
        }
        String[] sizes = {"E", "M", "H"};
        for (int size = 0; size < sizes.length; size++) {
            int column = FIRST_SIZE + size;
            canvas.sprite(PokesweeperArt.BUTTON, PokesweeperArt.tileX(column));
            canvas.textCentered(PokesweeperArt.BUTTON_LABEL, sizes[size], PokesweeperArt.faceCenter(column),
                size == mode.ordinal() ? LABEL_WIN : LABEL);
        }
    }

    private static void readouts(Canvas canvas, PokesweeperBoard board, int seconds) {
        canvas.sprite(PokesweeperArt.MINES_WELL, 8);
        canvas.sprite(PokesweeperArt.TIMER_WELL, Grid.WIDTH - 8 - 30);
        led(canvas, digits(board.minesLeft()), 8 + 14);
        led(canvas, digits(seconds), Grid.WIDTH - 8 - 15);

        canvas.sprite(PokesweeperArt.STATUS_BAR, 40);
        String label;
        int color = LABEL;
        if (board.lost()) {
            label = "BOOM - PRESS R";
            color = LABEL_HOT;
        } else if (board.won()) {
            label = "SWEPT CLEAN";
            color = LABEL_WIN;
        } else if (!board.started()) {
            label = "DIG ANYWHERE";
        } else {
            label = board.uncovered() + "/" + board.safeCells();
        }
        canvas.textCentered(PokesweeperArt.LED, label, 88, color);
    }

    /** A three digit red readout over a dim "888", so the unlit segments still show. */
    private static void led(Canvas canvas, String value, int centerX) {
        canvas.textCentered(PokesweeperArt.LED, "888", centerX, LED_GHOST);
        canvas.textCentered(PokesweeperArt.LED, value, centerX, LED);
    }

    private static String digits(int value) {
        return String.format("%03d", Math.max(0, Math.min(999, value)));
    }

    private static void help(Canvas canvas, PokesweeperViewport view, PokesweeperBoard board, int bestSeconds) {
        String[] lines = {
            "Columns " + (view.firstCol() + 1) + "-" + view.lastCol() + " of " + board.cols()
                + "   Rows " + (view.firstRow() + 1) + "-" + view.lastRow() + " of " + board.rows(),
            "Left click digs, or chords on a number.",
            "Right click plants or lifts a flag.",
            "The first dig and its neighbours are safe.",
            bestSeconds > 0 ? "Best clear: " + bestSeconds + " seconds" : "No clear on record yet.",
        };
        for (int line = 0; line < lines.length; line++) {
            canvas.text(PokesweeperArt.LOWER[line], lines[line], 8, PALE);
        }
    }
}
