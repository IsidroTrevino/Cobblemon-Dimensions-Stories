package com.cobblemondimensions.stories.client.minigame;

import com.cobblemondimensions.stories.minigame.PokesweeperMode;
import java.util.Arrays;
import net.minecraft.util.RandomSource;

/**
 * Minesweeper grid for Pokesweeper. Size and mine count come from {@link PokesweeperMode}.
 *
 * <p>Pure logic apart from openAtNanos, which the screen reads back to stagger
 * a cascade outward from whichever cell was clicked.
 *
 * <p>Mines are laid on the first dig, not at construction. The dug cell and its
 * eight neighbours are kept clear, which guarantees the opening click breaks a
 * pocket open instead of landing on a lone number.
 */
final class PokesweeperBoard {
    /** counts[] sentinel for TNT. */
    private static final int MINE = -1;

    /** Delay between rings of a cascade. */
    private static final long RIPPLE_NANOS = 22_000_000L;

    /** Delay between rings of the chain reaction after TNT is hit. */
    private static final long CHAIN_NANOS = 70_000_000L;

    enum Cell {
        HIDDEN,
        FLAGGED,
        REVEALED
    }

    private final RandomSource random;
    private final int cols;
    private final int rows;
    private final int mines;
    private final int cells;
    private final int safeCells;
    private final int[] counts;
    private final Cell[] board;
    private final long[] openAtNanos;

    private boolean laid;
    private boolean lost;
    private int uncovered;
    private int flags;
    private int epicentre = -1;

    PokesweeperBoard(RandomSource random, PokesweeperMode mode) {
        this.random = random;
        this.cols = mode.cols();
        this.rows = mode.rows();
        this.mines = mode.mines();
        this.cells = mode.cells();
        this.safeCells = mode.safeCells();
        this.counts = new int[cells];
        this.board = new Cell[cells];
        this.openAtNanos = new long[cells];
        Arrays.fill(board, Cell.HIDDEN);
    }

    // --------------------------------------------------------------- queries

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    int cells() {
        return cells;
    }

    int safeCells() {
        return safeCells;
    }

    int index(int col, int row) {
        return row * cols + col;
    }

    boolean onBoard(int col, int row) {
        return col >= 0 && col < cols && row >= 0 && row < rows;
    }

    Cell state(int index) {
        return board[index];
    }

    boolean mine(int index) {
        return counts[index] == MINE;
    }

    /** Neighbouring mine count. Zero for a cell that is itself TNT. */
    int count(int index) {
        return Math.max(counts[index], 0);
    }

    /** Timestamp for when this cell's open animation should start. */
    long openAt(int index) {
        return openAtNanos[index];
    }

    /** Flag sitting on a cell with no mine. Only worth drawing after the board ends. */
    boolean wrongFlag(int index) {
        return board[index] == Cell.FLAGGED && !mine(index);
    }

    boolean started() {
        return laid;
    }

    boolean lost() {
        return lost;
    }

    boolean won() {
        return !lost && uncovered >= safeCells;
    }

    boolean over() {
        return lost || won();
    }

    int uncovered() {
        return uncovered;
    }

    /** Mines minus flags. Goes negative if the player overflags. */
    int minesLeft() {
        return mines - flags;
    }

    int epicentre() {
        return epicentre;
    }

    // ---------------------------------------------------------------- digging

    /**
     * Uncovers a cell and floods through any empty pocket behind it.
     *
     * @return cells uncovered in ripple order, empty when the click did nothing
     */
    int[] dig(int index, long now) {
        if (over() || board[index] != Cell.HIDDEN) {
            return new int[0];
        }
        if (!laid) {
            lay(index);
        }
        if (mine(index)) {
            detonate(index, now);
            return new int[0];
        }
        return flood(index, now);
    }

    /**
     * Chord. Clicking an open number whose surrounding flags already match its
     * count opens every other cell touching it. Nobody finishes a board at
     * speed without this.
     */
    int[] chord(int index, long now) {
        if (over() || board[index] != Cell.REVEALED || count(index) == 0) {
            return new int[0];
        }

        int flagged = 0;
        for (int neighbour : neighbours(index)) {
            if (board[neighbour] == Cell.FLAGGED) {
                flagged++;
            }
        }
        if (flagged != count(index)) {
            return new int[0];
        }

        int[] opened = new int[0];
        for (int neighbour : neighbours(index)) {
            if (board[neighbour] != Cell.HIDDEN) {
                continue;
            }
            if (mine(neighbour)) {
                // flags were in the wrong place, the chord walks onto TNT
                detonate(neighbour, now);
                return new int[0];
            }
            opened = concat(opened, flood(neighbour, now));
        }
        return opened;
    }

    /** Toggles a flag. True means one was planted, false that it came off. */
    boolean toggleFlag(int index) {
        if (over() || board[index] == Cell.REVEALED) {
            return false;
        }
        if (board[index] == Cell.FLAGGED) {
            board[index] = Cell.HIDDEN;
            flags--;
            return false;
        }
        board[index] = Cell.FLAGGED;
        flags++;
        return true;
    }

    // ---------------------------------------------------------------- internals

    /**
     * Scatters the mines, skipping {@code safe} and everything touching it,
     * then fills counts[] for every remaining cell.
     */
    private void lay(int safe) {
        boolean[] blocked = new boolean[cells];
        blocked[safe] = true;
        for (int neighbour : neighbours(safe)) {
            blocked[neighbour] = true;
        }

        int placed = 0;
        while (placed < mines) {
            int spot = random.nextInt(cells);
            if (blocked[spot] || counts[spot] == MINE) {
                continue;
            }
            counts[spot] = MINE;
            placed++;
        }

        for (int index = 0; index < cells; index++) {
            if (counts[index] == MINE) {
                continue;
            }
            int around = 0;
            for (int neighbour : neighbours(index)) {
                if (counts[neighbour] == MINE) {
                    around++;
                }
            }
            counts[index] = around;
        }
        laid = true;
    }

    /**
     * BFS out from {@code start}, expanding only through zero-count cells.
     * Each ring is timestamped one RIPPLE_NANOS behind the last so the screen
     * can play the cascade outwards instead of in one frame.
     */
    private int[] flood(int start, long now) {
        int[] queue = new int[cells];
        int[] depth = new int[cells];
        int head = 0;
        int tail = 0;

        board[start] = Cell.REVEALED;
        openAtNanos[start] = now;
        uncovered++;
        queue[tail++] = start;

        while (head < tail) {
            int current = queue[head++];
            if (count(current) != 0) {
                continue;
            }
            for (int neighbour : neighbours(current)) {
                // non-HIDDEN covers flags too, a cascade never tears one off
                if (board[neighbour] != Cell.HIDDEN || mine(neighbour)) {
                    continue;
                }
                board[neighbour] = Cell.REVEALED;
                depth[neighbour] = depth[current] + 1;
                openAtNanos[neighbour] = now + depth[neighbour] * RIPPLE_NANOS;
                uncovered++;
                queue[tail++] = neighbour;
            }
        }

        int[] opened = new int[tail];
        System.arraycopy(queue, 0, opened, 0, tail);
        return opened;
    }

    /**
     * Loses the board and reveals every mine. Timestamps are keyed off
     * Chebyshev distance from the hit, giving a chain that spreads outward
     * instead of one flat flash.
     */
    private void detonate(int index, long now) {
        lost = true;
        epicentre = index;
        int hitCol = index % cols;
        int hitRow = index / cols;

        for (int cell = 0; cell < cells; cell++) {
            if (!mine(cell)) {
                continue;
            }
            int distance = Math.max(
                Math.abs(cell % cols - hitCol),
                Math.abs(cell / cols - hitRow));
            board[cell] = Cell.REVEALED;
            openAtNanos[cell] = now + distance * CHAIN_NANOS;
        }
        openAtNanos[index] = now;
    }

    /** Up to eight cells touching {@code index}, clipped at the board edges. */
    private int[] neighbours(int index) {
        int col = index % cols;
        int row = index / cols;
        int[] found = new int[8];
        int count = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0 || !onBoard(col + dx, row + dy)) {
                    continue;
                }
                found[count++] = index(col + dx, row + dy);
            }
        }
        int[] trimmed = new int[count];
        System.arraycopy(found, 0, trimmed, 0, count);
        return trimmed;
    }

    private static int[] concat(int[] first, int[] second) {
        if (second.length == 0) {
            return first;
        }
        int[] joined = new int[first.length + second.length];
        System.arraycopy(first, 0, joined, 0, first.length);
        System.arraycopy(second, 0, joined, first.length, second.length);
        return joined;
    }
}
