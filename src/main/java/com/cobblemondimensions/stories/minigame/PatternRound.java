package com.cobblemondimensions.stories.minigame;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Direction sequence, progress and deadlines are owned entirely by the server. */
final class PatternRound {
    private static final long SECOND = 1_000_000_000L;
    private static final long COUNTDOWN_NANOS = 1_900_000_000L;
    private static final long CLEARED_NANOS = 1_100_000_000L;
    private static final long SHIFT_NANOS = 2_000_000_000L;

    enum Phase {
        COUNTDOWN, PLAYING, CLEARED, OVER
    }

    enum Input {
        IGNORED, CORRECT, WRONG, CLEARED
    }

    private final RandomGenerator random;
    private Phase phase = Phase.COUNTDOWN;
    private long phaseStartedNanos;
    private long deadlineNanos;
    private int level = 1;
    private int levelsCleared;
    private int streak;
    private int bestStreak;
    private int[] sequence;
    private int typed;
    private boolean perfect;

    PatternRound(RandomGenerator random, long now) {
        this.random = Objects.requireNonNull(random);
        phaseStartedNanos = now;
        rollPattern();
    }

    /** Advance before processing input so a click at or after a deadline cannot score. */
    Input press(int direction, long now) {
        advance(now);
        if (phase != Phase.PLAYING || direction < 0 || direction > 3) {
            return Input.IGNORED;
        }
        if (sequence[typed] != direction) {
            typed = 0;
            streak = 0;
            return Input.WRONG;
        }
        typed++;
        if (typed < sequence.length) {
            return Input.CORRECT;
        }
        levelsCleared++;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        perfect = (deadlineNanos - now) / (double) durationNanos() >= 0.55D;
        phase = Phase.CLEARED;
        phaseStartedNanos = now;
        return Input.CLEARED;
    }

    void advance(long now) {
        switch (phase) {
            case COUNTDOWN -> {
                if (now - phaseStartedNanos >= COUNTDOWN_NANOS) {
                    phase = Phase.PLAYING;
                    phaseStartedNanos = now;
                    typed = 0;
                    perfect = false;
                    deadlineNanos = now + durationNanos();
                }
            }
            case PLAYING -> {
                if (now >= deadlineNanos) {
                    finish(now);
                }
            }
            case CLEARED -> {
                long hold = level == 9 ? SHIFT_NANOS : CLEARED_NANOS;
                if (now - phaseStartedNanos >= hold) {
                    level++;
                    rollPattern();
                    phase = Phase.COUNTDOWN;
                    phaseStartedNanos = now;
                }
            }
            case OVER -> { }
        }
    }

    void finish(long now) {
        if (phase != Phase.OVER) {
            phase = Phase.OVER;
            phaseStartedNanos = now;
        }
    }

    private void rollPattern() {
        sequence = new int[Math.min(12, 4 + (level - 1) / 2 * 2)];
        for (int step = 0; step < sequence.length; step++) {
            sequence[step] = random.nextInt(4);
        }
        typed = 0;
    }

    long durationNanos() {
        return Math.max(5, 16 - level) * SECOND;
    }

    int[] sequence() {
        return sequence.clone();
    }

    int typed() {
        return typed;
    }

    int streak() {
        return streak;
    }

    int bestStreak() {
        return bestStreak;
    }

    int level() {
        return level;
    }

    int levelsCleared() {
        return levelsCleared;
    }

    boolean shapes() {
        return level >= 10;
    }

    boolean perfect() {
        return perfect;
    }

    long deadlineNanos() {
        return deadlineNanos;
    }

    Phase phase() {
        return phase;
    }

    int remainingSeconds(long now) {
        return phase == Phase.PLAYING
            ? (int) Math.max(0, Math.ceil((deadlineNanos - now) / 1.0E9D)) : 0;
    }

    int countdown(long now) {
        return (int) Math.max(0, 3 - Math.max(0, now - phaseStartedNanos) / 500_000_000L);
    }
}
