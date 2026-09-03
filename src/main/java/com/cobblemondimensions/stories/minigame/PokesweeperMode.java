package com.cobblemondimensions.stories.minigame;

/**
 * Board sizes for Pokesweeper. The client deals the field from these; the server
 * only needs {@link #safeCells()} to tell a full sweep from a partial one.
 */
public enum PokesweeperMode {
    EASY(Minigame.POKESWEEPER_EASY, 9, 9, 10),
    MEDIUM(Minigame.POKESWEEPER_MEDIUM, 12, 12, 20),
    HARD(Minigame.POKESWEEPER, 16, 16, 40);

    private final Minigame minigame;
    private final int cols;
    private final int rows;
    private final int mines;

    PokesweeperMode(Minigame minigame, int cols, int rows, int mines) {
        this.minigame = minigame;
        this.cols = cols;
        this.rows = rows;
        this.mines = mines;
    }

    public static PokesweeperMode of(Minigame minigame) {
        for (PokesweeperMode mode : values()) {
            if (mode.minigame == minigame) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Not a Pokesweeper minigame: " + minigame);
    }

    public Minigame minigame() {
        return minigame;
    }

    public int cols() {
        return cols;
    }

    public int rows() {
        return rows;
    }

    public int mines() {
        return mines;
    }

    public int cells() {
        return cols * rows;
    }

    public int safeCells() {
        return cells() - mines;
    }
}
