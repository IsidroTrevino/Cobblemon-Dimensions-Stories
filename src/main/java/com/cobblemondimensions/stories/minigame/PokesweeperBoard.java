package com.cobblemondimensions.stories.minigame;

import java.util.Arrays;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Server-owned minesweeper state. Mines are laid only on the first successful dig. */
final class PokesweeperBoard {
    private static final int MINE = -1;

    enum Cell {
        HIDDEN, FLAGGED, REVEALED
    }

    private final RandomGenerator random;
    private final int cols;
    private final int rows;
    private final int mines;
    private final int[] counts;
    private final Cell[] board;
    private boolean laid;
    private boolean lost;
    private int uncovered;
    private int flags;

    PokesweeperBoard(RandomGenerator random, int cols, int rows, int mines) {
        if (cols < 1 || rows < 1 || mines < 0
            || (long) cols * rows - mines < (long) Math.min(3, cols) * Math.min(3, rows)) {
            throw new IllegalArgumentException("Board cannot guarantee a safe first neighbourhood");
        }
        this.random = Objects.requireNonNull(random);
        this.cols = cols;
        this.rows = rows;
        this.mines = mines;
        this.counts = new int[Math.multiplyExact(cols, rows)];
        this.board = new Cell[counts.length];
        Arrays.fill(board, Cell.HIDDEN);
    }

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    int cells() {
        return board.length;
    }

    int safeCells() {
        return cells() - mines;
    }

    int index(int col, int row) {
        return row * cols + col;
    }

    boolean onBoard(int col, int row) {
        return col >= 0 && col < cols && row >= 0 && row < rows;
    }

    Cell state(int cell) {
        return board[cell];
    }

    boolean mine(int cell) {
        return counts[cell] == MINE;
    }

    int count(int cell) {
        return Math.max(counts[cell], 0);
    }

    boolean wrongFlag(int cell) {
        return board[cell] == Cell.FLAGGED && !mine(cell);
    }

    boolean started() {
        return laid;
    }

    boolean lost() {
        return lost;
    }

    boolean won() {
        return !lost && uncovered == safeCells();
    }

    boolean over() {
        return lost || won();
    }

    int uncovered() {
        return uncovered;
    }

    int minesLeft() {
        return mines - flags;
    }

    /** Reveals a hidden safe cell and floods its empty pocket without disturbing flags. */
    int[] dig(int cell) {
        if (cell < 0 || cell >= cells() || over() || board[cell] != Cell.HIDDEN) {
            return new int[0];
        }
        if (!laid) {
            lay(cell);
        }
        if (mine(cell)) {
            detonate();
            return new int[0];
        }
        return flood(cell);
    }

    /** Matching adjacent flags allow a revealed number to open its other neighbours. */
    int[] chord(int cell) {
        if (cell < 0 || cell >= cells() || over()
            || board[cell] != Cell.REVEALED || count(cell) == 0) {
            return new int[0];
        }
        int[] neighbours = neighbours(cell);
        int flagged = 0;
        for (int neighbour : neighbours) {
            if (board[neighbour] == Cell.FLAGGED) {
                flagged++;
            }
        }
        if (flagged != count(cell)) {
            return new int[0];
        }
        int[] opened = new int[0];
        for (int neighbour : neighbours) {
            if (board[neighbour] != Cell.HIDDEN) {
                continue;
            }
            if (mine(neighbour)) {
                detonate();
                return new int[0];
            }
            int[] extra = flood(neighbour);
            int previous = opened.length;
            opened = Arrays.copyOf(opened, previous + extra.length);
            System.arraycopy(extra, 0, opened, previous, extra.length);
        }
        return opened;
    }

    boolean toggleFlag(int cell) {
        if (cell < 0 || cell >= cells() || over() || board[cell] == Cell.REVEALED) {
            return false;
        }
        if (board[cell] == Cell.FLAGGED) {
            board[cell] = Cell.HIDDEN;
            flags--;
            return false;
        }
        board[cell] = Cell.FLAGGED;
        flags++;
        return true;
    }

    private void lay(int safe) {
        boolean[] blocked = new boolean[cells()];
        blocked[safe] = true;
        for (int neighbour : neighbours(safe)) {
            blocked[neighbour] = true;
        }
        int[] candidates = new int[cells()];
        int available = 0;
        for (int cell = 0; cell < cells(); cell++) {
            if (!blocked[cell]) {
                candidates[available++] = cell;
            }
        }
        // Partial Fisher-Yates avoids an unbounded retry loop while preserving a uniform field.
        for (int placed = 0; placed < mines; placed++) {
            int choice = random.nextInt(available);
            counts[candidates[choice]] = MINE;
            candidates[choice] = candidates[--available];
        }
        for (int cell = 0; cell < cells(); cell++) {
            if (mine(cell)) {
                continue;
            }
            for (int neighbour : neighbours(cell)) {
                if (mine(neighbour)) {
                    counts[cell]++;
                }
            }
        }
        laid = true;
    }

    private int[] flood(int start) {
        int[] queue = new int[cells()];
        int head = 0;
        int tail = 0;
        board[start] = Cell.REVEALED;
        uncovered++;
        queue[tail++] = start;
        while (head < tail) {
            int cell = queue[head++];
            if (count(cell) != 0) {
                continue;
            }
            for (int neighbour : neighbours(cell)) {
                if (board[neighbour] != Cell.HIDDEN || mine(neighbour)) {
                    continue;
                }
                board[neighbour] = Cell.REVEALED;
                uncovered++;
                queue[tail++] = neighbour;
            }
        }
        return Arrays.copyOf(queue, tail);
    }

    private void detonate() {
        lost = true;
        for (int cell = 0; cell < cells(); cell++) {
            if (mine(cell)) {
                board[cell] = Cell.REVEALED;
            }
        }
    }

    private int[] neighbours(int cell) {
        int col = cell % cols;
        int row = cell / cols;
        int[] found = new int[8];
        int size = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if ((dx != 0 || dy != 0) && onBoard(col + dx, row + dy)) {
                    found[size++] = index(col + dx, row + dy);
                }
            }
        }
        return Arrays.copyOf(found, size);
    }
}
