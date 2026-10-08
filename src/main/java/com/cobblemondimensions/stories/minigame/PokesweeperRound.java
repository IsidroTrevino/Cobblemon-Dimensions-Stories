package com.cobblemondimensions.stories.minigame;

import java.util.random.RandomGenerator;

/** Measures only server time after the first dig, frozen when the board ends or closes. */
final class PokesweeperRound {
    static final int RECORD_CAP = 3600;
    private final PokesweeperBoard board;
    private boolean started;
    private boolean ended;
    private long startedNanos;
    private long endedNanos;

    PokesweeperRound(RandomGenerator random, int cols, int rows, int mines) {
        board = new PokesweeperBoard(random, cols, rows, mines);
    }

    PokesweeperBoard board() {
        return board;
    }

    void toggleFlag(int cell) {
        if (!ended) {
            board.toggleFlag(cell);
        }
    }

    int[] dig(int cell, long now) {
        if (ended) {
            return new int[0];
        }
        int[] revealed = board.dig(cell);
        afterInput(now);
        return revealed;
    }

    int[] chord(int cell, long now) {
        if (ended) {
            return new int[0];
        }
        int[] revealed = board.chord(cell);
        afterInput(now);
        return revealed;
    }

    private void afterInput(long now) {
        if (!started && board.started()) {
            started = true;
            startedNanos = now;
        }
        if (board.over()) {
            finish(now);
        }
    }

    void finish(long now) {
        if (!ended) {
            ended = true;
            endedNanos = now;
        }
    }

    int seconds(long now) {
        return (int) Math.clamp(Math.round(elapsedNanos(now) / 1.0E9D), 1, RECORD_CAP);
    }

    int displaySeconds(long now) {
        return (int) Math.min(RECORD_CAP, elapsedNanos(now) / 1_000_000_000L);
    }

    private long elapsedNanos(long now) {
        return started ? Math.max(0, (ended ? endedNanos : now) - startedNanos) : 0;
    }
}
