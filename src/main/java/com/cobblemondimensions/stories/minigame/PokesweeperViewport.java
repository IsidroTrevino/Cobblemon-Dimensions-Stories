package com.cobblemondimensions.stories.minigame;

/** Nine columns by five rows, leaving the sixth chest row for controls. */
final class PokesweeperViewport {
    private final int cols;
    private final int rows;
    private int columnPage;
    private int rowPage;

    PokesweeperViewport(int cols, int rows) {
        if (cols < 1 || rows < 1) {
            throw new IllegalArgumentException("Empty viewport");
        }
        this.cols = cols;
        this.rows = rows;
    }

    int cellAt(int slot) {
        if (slot < 0 || slot >= 45) {
            return -1;
        }
        int col = firstCol() + slot % 9;
        int row = firstRow() + slot / 9;
        return col < cols && row < rows ? row * cols + col : -1;
    }

    void move(int horizontal, int vertical) {
        columnPage = (int) Math.clamp((long) columnPage + horizontal, 0, (cols - 1) / 9);
        rowPage = (int) Math.clamp((long) rowPage + vertical, 0, (rows - 1) / 5);
    }

    int firstCol() {
        return columnPage * 9;
    }

    int firstRow() {
        return rowPage * 5;
    }

    int lastCol() {
        return Math.min(cols, firstCol() + 9);
    }

    int lastRow() {
        return Math.min(rows, firstRow() + 5);
    }
}
