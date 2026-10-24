package com.cobblemondimensions.stories.dialogue;

import com.cobblemondimensions.stories.ui.Canvas;
import com.cobblemondimensions.stories.ui.art.DialogueArt;
import com.cobblemondimensions.stories.ui.art.Overlay;
import com.cobblemondimensions.stories.ui.art.TextLine;
import com.cobblemondimensions.stories.ui.art.UiMetrics;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Draws the walking dialogue box: the bar rising into place, the panel unrolling,
 * the speaker's name unscrambling, the line typing out with the portrait talking,
 * then either the continue arrow or the choice box.
 */
public final class DialogueView {
    static final long OPEN_NANOS = 420_000_000L;
    static final long UNROLL_NANOS = 120_000_000L;
    static final long CHOICE_NANOS = 190_000_000L;
    static final double CHARACTERS_PER_SECOND = 38.0D;

    private static final int TEXT = 0x1F1F1F;
    private static final int SPEAKER = 0x0D0D0D;
    private static final int SCRAMBLED = 0x5C5C5C;
    private static final int OPTION = 0x2A2A2A;
    private static final int OPTION_SELECTED = 0xF2F2F2;
    private static final char[] SCRAMBLE = {'#', '/', '+', '=', '?', '*', '%'};

    private DialogueView() {
    }

    /** What the box needs to know about the conversation at this moment. */
    public record Frame(String speaker, String text, int visible, boolean complete, boolean hasOptions,
                        List<String> options, int selection, long openedNanos, long pageNanos, long choicesNanos) {
    }

    public static Component compose(Frame frame, long now) {
        Canvas canvas = Canvas.overlay(Overlay.DIALOGUE);
        long age = Math.max(0L, now - frame.openedNanos());
        if (age < OPEN_NANOS) {
            // the bar slides up and brightens as it arrives
            int step = (int) Math.min(DialogueArt.BAR_RISING.length - 1, age * DialogueArt.BAR_RISING.length / OPEN_NANOS);
            canvas.sprite(DialogueArt.BAR_RISING[step], DialogueArt.BAR_X, shade(0.4F + 0.6F * age / (float) OPEN_NANOS));
            return canvas.build();
        }
        canvas.sprite(DialogueArt.BAR, DialogueArt.BAR_X);

        // two-frame flip: quick while the line types, a slow blink once it is done
        long period = frame.complete() ? 620_000_000L : 120_000_000L;
        canvas.sprite(DialogueArt.PORTRAIT[(int) ((now / period) & 1L)], DialogueArt.PORTRAIT_X);

        long unrolled = age - OPEN_NANOS;
        if (unrolled < UNROLL_NANOS) {
            int quarter = (int) Math.min(3, unrolled * 4 / UNROLL_NANOS);
            canvas.sprite(DialogueArt.PANEL[quarter], DialogueArt.PANEL_X);
            return canvas.build();
        }
        canvas.sprite(DialogueArt.PANEL[3], DialogueArt.PANEL_X);

        int left = DialogueArt.PANEL_X + DialogueArt.PADDING;
        boolean named = !frame.speaker().isBlank();
        if (named) {
            speaker(canvas, frame.speaker(), left, now - frame.pageNanos());
            canvas.sprite(DialogueArt.DIVIDER, left);
        }
        body(canvas, frame, named, left, now);

        if (!frame.hasOptions() && frame.complete()) {
            canvas.sprite(DialogueArt.CONTINUE[(int) ((now / 240_000_000L) & 1L)],
                DialogueArt.PANEL_X + DialogueArt.PANEL_W - 10);
        }
        if (frame.hasOptions() && frame.complete() && frame.choicesNanos() != 0L) {
            choices(canvas, frame.options(), frame.selection(), now - frame.choicesNanos());
        }
        return canvas.build();
    }

    /** The speaker's name settles letter by letter out of scrambled symbols. */
    private static void speaker(Canvas canvas, String name, int x, long age) {
        TextLine line = DialogueArt.SPEAKER;
        String clean = UiMetrics.clean(line, name);
        long cycle = age / 32_000_000L;
        int cursor = x * 8;
        for (int index = 0; index < clean.length(); index++) {
            char actual = clean.charAt(index);
            long charAge = age - index * 38_000_000L;
            if (charAge >= 0 && actual != ' ') {
                boolean settled = charAge >= 110_000_000L;
                char shown = settled ? actual : SCRAMBLE[(int) Math.floorMod(cycle + index * 3L, (long) SCRAMBLE.length)];
                canvas.textAt(line, String.valueOf(shown), cursor, settled ? SPEAKER : SCRAMBLED);
            }
            // always step by the real letter, so the name does not jitter as it settles
            cursor += UiMetrics.advanceEighths(line, actual);
        }
    }

    /**
     * The largest size at which the whole page fits, measured on the full text so
     * the size never changes while it types. A caret blinks after the last letter.
     */
    private static void body(Canvas canvas, Frame frame, boolean named, int x, long now) {
        int width = DialogueArt.PANEL_W - DialogueArt.PADDING * 2;
        int set = named ? 0 : 1;
        TextLine[] rows = null;
        int size = 0;
        List<String> lines = List.of();
        for (; size < DialogueArt.BODY_SIZES.length; size++) {
            rows = DialogueArt.BODY[set][size];
            lines = UiMetrics.wrap(rows[0], UiMetrics.clean(rows[0], frame.text()), width);
            if (lines.size() <= rows.length) {
                break;
            }
        }
        size = Math.min(size, DialogueArt.BODY_SIZES.length - 1);
        int budget = frame.visible();
        int lastLine = -1;
        int caretX = x;
        for (int line = 0; line < Math.min(lines.size(), rows.length) && budget > 0; line++) {
            String row = lines.get(line);
            String shown = row.substring(0, Math.min(row.length(), budget));
            canvas.text(rows[line], shown, x, TEXT);
            budget -= row.length() + 1;
            lastLine = line;
            caretX = x + UiMetrics.widthEighths(rows[line], UiMetrics.clean(rows[line], shown)) / 8;
        }
        if (!frame.complete() && lastLine >= 0 && ((now / 260_000_000L) & 1L) == 0L) {
            canvas.sprite(DialogueArt.CARET[set][size][lastLine], caretX);
        }
    }

    private static void choices(Canvas canvas, List<String> options, int selection, long age) {
        int count = Math.min(options.size(), DialogueArt.MAX_CHOICES);
        canvas.sprite(DialogueArt.CHOICES[count - 1], DialogueArt.PANEL_X - 2);
        int chosen = Math.floorMod(selection, count);
        int width = DialogueArt.PANEL_W - DialogueArt.CHOICE_PAD * 2 - 4;
        for (int index = 0; index < count; index++) {
            // rows drop in one after another as the box opens
            long rowAge = age - index * CHOICE_NANOS / 4;
            if (rowAge < 0) {
                continue;
            }
            boolean selected = index == chosen;
            if (selected) {
                canvas.sprite(DialogueArt.CHOICE_HIGHLIGHT[count - 1][index], DialogueArt.PANEL_X + 2);
            }
            TextLine line = DialogueArt.CHOICE_TEXT[count - 1][index];
            String label = fit(line, (index + 1) + ". " + options.get(index), width);
            // each row slides in from a few pixels to the right
            int slide = (int) Math.max(0L, 4L - rowAge * 4L / CHOICE_NANOS);
            canvas.text(line, label, DialogueArt.PANEL_X + DialogueArt.CHOICE_PAD + 1 + (selected ? 1 : 0) + slide,
                selected ? OPTION_SELECTED : OPTION);
        }
    }

    private static String fit(TextLine line, String text, int width) {
        String clean = UiMetrics.clean(line, text);
        while (clean.length() > 1 && UiMetrics.width(line, clean) - 1 > width) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean;
    }

    /** A grey level for tinting: white leaves the art as drawn. */
    private static int shade(float brightness) {
        int level = Math.round(255 * Math.max(0.0F, Math.min(1.0F, brightness)));
        return level << 16 | level << 8 | level;
    }
}
