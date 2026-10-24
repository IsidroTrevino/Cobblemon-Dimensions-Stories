package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.ui.Canvas;
import com.cobblemondimensions.stories.ui.art.Overlay;
import com.cobblemondimensions.stories.ui.art.PatternArt;
import com.cobblemondimensions.stories.ui.art.Sprite;
import java.util.Arrays;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Draws a Pattern round as an overlay. Everything here is a pure function of the
 * round and a few animation timestamps, so the picture can be rebuilt as often as
 * the game wants without keeping any drawing state.
 */
final class PatternView {
    private static final int BOARD_SLOTS = 12;
    private static final int TOP_ROW = 8;

    private static final long BEAT_NANOS = 500_000_000L;
    private static final long WRONG_NANOS = 380_000_000L;
    private static final long POP_NANOS = 240_000_000L;
    private static final long BURST_NANOS = 420_000_000L;
    private static final long BUMP_NANOS = 520_000_000L;
    private static final long REVEAL_STEP_NANOS = 35_000_000L;
    private static final long REVEAL_NANOS = 240_000_000L;

    private static final int CYAN = 0x00F0FF;
    private static final int MAGENTA = 0xEC00F0;
    private static final int GOLD = 0xFFC93C;
    private static final int GREEN = 0x7CFFA8;
    private static final int RED = 0xFF2A4A;
    private static final int PALE = 0xCFC4F2;
    private static final int SHADOW = 0x1F004B;

    private PatternView() {
    }

    /**
     * Timestamps of the little animations a round plays; a time far in the past
     * means none is running. {@code poppedStep} is the tile that was just hit.
     */
    record Effects(long wrongAt, long poppedAt, int poppedStep, long clearedAt, long revealAt, int best) {
    }

    static Component compose(PatternRound round, long now, Effects fx) {
        Canvas canvas = Canvas.overlay(Overlay.PATTERN);
        int shake = shake(now, fx);
        canvas.sprite(round.shapes() ? PatternArt.BASE_SHAPES : PatternArt.BASE, PatternArt.PANEL_X + shake);
        urgency(canvas, round, now, shake);
        board(canvas, round, now, fx, shake);

        clock(canvas, round, now, shake);
        streak(canvas, round, now, fx, shake);
        boolean ended = round.phase() == PatternRound.Phase.CLEARED || round.phase() == PatternRound.Phase.OVER;
        if (ended) {
            canvas.sprite(PatternArt.SCRIM, PatternArt.PANEL_X + 4 + shake);
        }
        headline(canvas, round, now, fx, shake);
        hints(canvas, round, fx);
        return canvas.build();
    }

    // -------------------------------------------------------------- board

    private static void board(Canvas canvas, PatternRound round, long now, Effects fx, int shake) {
        int[] sequence = round.sequence();
        int[] slots = layout(sequence.length);
        int[] stepOf = new int[BOARD_SLOTS];
        Arrays.fill(stepOf, -1);
        for (int step = 0; step < slots.length; step++) {
            stepOf[slots[step]] = step;
        }
        boolean flashing = now - fx.wrongAt() < WRONG_NANOS;
        boolean pulseBright = (now / 240_000_000L) % 2 == 0;

        for (int slot = 0; slot < BOARD_SLOTS; slot++) {
            int row = PatternArt.row(slot);
            int x = PatternArt.tileX(slot) + shake;
            int step = stepOf[slot];
            if (step < 0 || now - fx.revealAt() - step * REVEAL_STEP_NANOS < 0) {
                canvas.sprite(PatternArt.UNUSED[row], x);
                continue;
            }
            canvas.sprite(PatternArt.TILE[slot], x);
            if (flashing) {
                canvas.sprite(PatternArt.WRONG[row], x + 2);
            } else if (step < round.typed()) {
                boolean popping = step == fx.poppedStep() && now - fx.poppedAt() < POP_NANOS;
                canvas.sprite(popping ? PatternArt.POP[row] : PatternArt.DONE[row], x + 2);
            } else if (step == round.typed() && round.phase() == PatternRound.Phase.PLAYING) {
                canvas.sprite(pulseBright ? PatternArt.CURRENT_BRIGHT[row] : PatternArt.CURRENT_DIM[row], x + 2);
            }
            // the sign fades in with the tile; a still-arriving one is left out
            if (now - fx.revealAt() - step * REVEAL_STEP_NANOS >= REVEAL_NANOS / 3) {
                int direction = sequence[step];
                Sprite sign = round.shapes() ? PatternArt.SHAPE[direction][row] : PatternArt.ARROW[direction][row];
                canvas.sprite(sign, x + (16 - sign.width()) / 2);
            }
        }
        bursts(canvas, round, now, fx, slots, shake);
    }

    /** Fills the eight-slot top row first, centred, then the four-slot bottom row. */
    static int[] layout(int length) {
        int[] slots = new int[length];
        int top = Math.min(length, TOP_ROW);
        int topStart = (TOP_ROW - top) / 2;
        for (int index = 0; index < top; index++) {
            slots[index] = topStart + index;
        }
        int bottom = length - top;
        int bottomStart = (BOARD_SLOTS - TOP_ROW - bottom) / 2;
        for (int index = 0; index < bottom; index++) {
            slots[top + index] = TOP_ROW + bottomStart + index;
        }
        return slots;
    }

    /** A ring spreads out of the tile that was just hit, and out of every tile when a round clears. */
    private static void bursts(Canvas canvas, PatternRound round, long now, Effects fx, int[] slots, int shake) {
        long hitAge = now - fx.poppedAt();
        if (fx.poppedStep() >= 0 && fx.poppedStep() < slots.length && hitAge >= 0 && hitAge < BURST_NANOS
            && round.phase() == PatternRound.Phase.PLAYING) {
            int slot = slots[fx.poppedStep()];
            int step = ringStep(hitAge);
            canvas.sprite(PatternArt.RING_CYAN[step][PatternArt.row(slot)],
                PatternArt.ringX(PatternArt.tileX(slot) + 8, step) + shake);
        }
        long clearAge = now - fx.clearedAt();
        if (clearAge >= 0 && clearAge < BURST_NANOS) {
            int step = ringStep(clearAge);
            for (int slot : slots) {
                canvas.sprite(PatternArt.RING_GOLD[step][PatternArt.row(slot)],
                    PatternArt.ringX(PatternArt.tileX(slot) + 8, step) + shake);
            }
        }
    }

    private static int ringStep(long age) {
        return Math.min(PatternArt.rings() - 1, (int) (age * PatternArt.rings() / BURST_NANOS));
    }

    // ---------------------------------------------------- clock and streak

    private static void clock(Canvas canvas, PatternRound round, long now, int shake) {
        canvas.sprite(PatternArt.CLOCK, 110 + shake);
        long remaining = switch (round.phase()) {
            case COUNTDOWN -> round.durationNanos();
            case PLAYING -> Math.max(0L, round.deadlineNanos() - now);
            default -> 0L;
        };
        float share = share(round, remaining);
        int centis = (int) (remaining / 10_000_000L);
        String text = String.format("%02d:%02d", centis / 100, centis % 100);
        // green at full, gold at half, red at empty, with a white flash under a fifth
        int color = share > 0.5F ? blend(GOLD, GREEN, (share - 0.5F) * 2.0F) : blend(RED, GOLD, share * 2.0F);
        boolean urgent = round.phase() == PatternRound.Phase.PLAYING && share < 0.2F;
        if (urgent && (now / 110_000_000L) % 2 == 0) {
            color = 0xFFFFFF;
        }
        // one pixel of jitter while the last fifth runs out
        int jitter = urgent ? (int) (now / 60_000_000L) % 2 : 0;
        canvas.textCentered(PatternArt.CLOCK_TEXT, text, PatternArt.CENTER_X + 1 + jitter + shake, color);
    }

    private static float share(PatternRound round, long remaining) {
        return Mth.clamp(remaining / (float) round.durationNanos(), 0.0F, 1.0F);
    }

    private static void streak(Canvas canvas, PatternRound round, long now, Effects fx, int shake) {
        long age = now - fx.clearedAt();
        if (age >= 0 && age < BUMP_NANOS) {
            int step = Math.min(PatternArt.rings() - 1, (int) (age * PatternArt.rings() / BUMP_NANOS));
            canvas.sprite(PatternArt.RING_GOLD[step][2], PatternArt.ringX(PatternArt.CENTER_X, step) + shake);
        }
        canvas.sprite(PatternArt.STREAK, 118 + shake);
        int streak = round.streak();
        int color;
        if (streak <= 0) {
            color = CYAN;
        } else if (streak < 3) {
            color = GREEN;
        } else if (streak < 6) {
            color = GOLD;
        } else {
            color = Mth.hsvToRgb((float) ((now / 3.0E8D) % 1.0D), 0.85F, 1.0F);
        }
        canvas.textCentered(PatternArt.STREAK_TEXT, String.valueOf(streak), PatternArt.CENTER_X + 1 + shake, color);
    }

    private static int blend(int from, int to, float t) {
        int r = (int) (((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) (((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) ((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return r << 16 | g << 8 | b;
    }

    /** A red frame inside the panel that pulses harder as the last quarter of the clock runs out. */
    private static void urgency(Canvas canvas, PatternRound round, long now, int shake) {
        if (round.phase() != PatternRound.Phase.PLAYING) {
            return;
        }
        float share = share(round, Math.max(0L, round.deadlineNanos() - now));
        if (share > 0.25F) {
            return;
        }
        float strength = (0.25F - share) * 4.0F * (0.55F + (float) Math.sin(now / 1.0E8D) * 0.45F);
        int level = Mth.clamp((int) (strength * 3.0F), 0, 2);
        if (strength < 0.15F) {
            return;
        }
        canvas.sprite(PatternArt.URGENCY_OUTER[level], PatternArt.PANEL_X + 3 + shake);
        canvas.sprite(PatternArt.URGENCY_INNER[level], PatternArt.PANEL_X + 4 + shake);
    }

    /** A decaying sideways shake after a wrong press, in whole pixels. */
    private static int shake(long now, Effects fx) {
        long age = now - fx.wrongAt();
        if (age < 0 || age >= WRONG_NANOS) {
            return 0;
        }
        float decay = 1.0F - age / (float) WRONG_NANOS;
        return Math.round((float) Math.sin(age / 1.6E7D) * 3.0F * decay);
    }

    // ----------------------------------------------------------- headlines

    private static void headline(Canvas canvas, PatternRound round, long now, Effects fx, int shake) {
        int middle = PatternArt.CENTER_X + shake;
        switch (round.phase()) {
            case COUNTDOWN -> {
                int beat = round.countdown(now);
                // each digit starts large and drops a size across its beat
                boolean late = (now - fx.revealAt()) % BEAT_NANOS > BEAT_NANOS / 2;
                String digit = beat == 0 ? "GO!" : String.valueOf(beat);
                canvas.textCentered(late ? PatternArt.COUNTDOWN_SMALL_DROP : PatternArt.COUNTDOWN_DROP, digit, middle + 1, SHADOW);
                canvas.textCentered(late ? PatternArt.COUNTDOWN_SMALL : PatternArt.COUNTDOWN, digit, middle,
                    beat == 0 ? GREEN : CYAN);
            }
            case CLEARED -> {
                boolean shifting = round.level() + 1 == 10;
                String headline = shifting ? "SHAPE MODE" : round.perfect() ? "PERFECT!" : "CLEAR!";
                int color = shifting ? MAGENTA : round.perfect() ? GOLD : GREEN;
                canvas.textCentered(PatternArt.HEADLINE_DROP, headline, middle + 1, SHADOW);
                canvas.textCentered(PatternArt.HEADLINE, headline, middle, color);
                canvas.textCentered(PatternArt.SUBLINE, shifting
                    ? "arrows become shapes, same colours"
                    : "streak " + round.streak() + "  -  next round is faster", middle, CYAN);
            }
            case OVER -> {
                canvas.textCentered(PatternArt.OVER_HEADLINE_DROP, "TIME!", middle + 1, SHADOW);
                canvas.textCentered(PatternArt.OVER_HEADLINE, "TIME!", middle,
                    round.levelsCleared() > 0 ? GOLD : RED);
                canvas.textCentered(PatternArt.OVER_SUBLINE, "cleared " + round.levelsCleared()
                    + "  -  best streak " + round.bestStreak()
                    + "  -  record " + Math.max(fx.best(), round.levelsCleared()), middle, CYAN);
                canvas.textCentered(PatternArt.OVER_HINT, "jump to play again  -  sneak to leave", middle, MAGENTA);
            }
            case PLAYING -> { }
        }
    }

    // ------------------------------------------------------------- hints

    private static void hints(Canvas canvas, PatternRound round, Effects fx) {
        int middle = PatternArt.CENTER_X;
        canvas.textCentered(PatternArt.STATUS, "LEVEL " + round.level() + "   -   RECORD "
            + Math.max(fx.best(), round.levelsCleared()), middle, CYAN);
        if (round.phase() == PatternRound.Phase.OVER) {
            canvas.textCentered(PatternArt.KEYS, "SPACE  PLAY AGAIN", middle, GREEN);
            canvas.textCentered(PatternArt.KEYS_SMALL, "SHIFT  LEAVE", middle, PALE);
        } else {
            canvas.textCentered(PatternArt.KEYS, round.shapes()
                ? "W PINK   S BLUE   A ORANGE   D GREEN"
                : "W UP    S DOWN    A LEFT    D RIGHT", middle, PALE);
            canvas.textCentered(PatternArt.KEYS_SMALL, "SHIFT  LEAVE", middle, PALE);
        }
    }
}
