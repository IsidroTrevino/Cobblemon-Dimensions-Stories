package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.quest.QuestStatus;
import com.cobblemondimensions.stories.ui.art.QuestArt;
import com.cobblemondimensions.stories.ui.art.TextLine;
import com.cobblemondimensions.stories.ui.art.UiMetrics;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Draws the quest journal the way the old client screen did: the panel fading
 * up, the rows cascading in, the chosen quest lit, its description typed out,
 * the active swatch breathing and the "New" button glowing.
 */
public final class JournalView {
    static final long OPEN_NANOS = 260_000_000L;
    static final long ROW_STAGGER_NANOS = 26_000_000L;
    static final double CHARACTERS_PER_SECOND = 90.0D;

    private static final int TEXT_DARK = 0x241C28;
    private static final int TEXT_LIGHT = 0xE9E9E9;
    private static final int TEXT_MUTED = 0x5C5C5C;
    /** Finished quests on the dark list rows: dimmer than the rest, still easy to read. */
    private static final int TEXT_DONE = 0x9C9C9C;
    private static final int ACTIVE = 0xE0B24A;
    private static final int COMPLETED = 0x6FBF5B;
    private static final int AVAILABLE = 0xB9B9C4;
    /** The breathing swatch and glow move in a few steps, so the menu is only redrawn a few times a second. */
    private static final int BREATH_STEPS = 4;

    private JournalView() {
    }

    /** One quest in the list. */
    public record Row(String title, QuestStatus status) {
    }

    /**
     * Everything the journal shows at one moment. {@code page} is the list page on
     * screen; {@code selected} is the index of the chosen quest in {@code rows}, or
     * -1 when the list is empty.
     */
    public record Frame(List<Row> rows, int page, int selected, String description, String hint, String reward,
                        long openedNanos, long selectedNanos) {
        public int pages() {
            return Math.max(1, (rows.size() + QuestArt.ROWS_PER_PAGE - 1) / QuestArt.ROWS_PER_PAGE);
        }

        /** The page, kept on the list if quests went away since it was chosen. */
        public int shownPage() {
            return Math.max(0, Math.min(page, pages() - 1));
        }
    }

    public static Component compose(Frame frame, long now) {
        Canvas canvas = new Canvas();
        long age = Math.max(0L, now - frame.openedNanos());
        // the panel fades up from dark as it opens, as the old screen faded in
        canvas.sprite(QuestArt.PANEL, 0, shade(age / (float) OPEN_NANOS));

        int first = frame.shownPage() * QuestArt.ROWS_PER_PAGE;
        int last = Math.min(frame.rows().size(), first + QuestArt.ROWS_PER_PAGE);
        // one size for the whole page, so the list reads evenly
        boolean large = true;
        for (int index = first; index < last; index++) {
            large &= fits(QuestArt.ROW_TEXT[0], frame.rows().get(index).title(), rowTextWidth());
        }
        for (int slot = 0; slot < QuestArt.ROWS_PER_PAGE && first + slot < frame.rows().size(); slot++) {
            // staggered per slot, so the rows cascade in rather than popping together
            long rowAge = age - slot * ROW_STAGGER_NANOS;
            if (rowAge <= 0) {
                continue;
            }
            float reveal = Math.min(1.0F, rowAge / (float) OPEN_NANOS);
            row(canvas, slot, frame.rows().get(first + slot), first + slot == frame.selected(), reveal, large, now);
        }

        canvas.textCentered(QuestArt.PAGE, "<", QuestArt.PREV_X + QuestArt.PAGE_W / 2,
            frame.shownPage() > 0 ? TEXT_DARK : TEXT_MUTED);
        canvas.textCentered(QuestArt.PAGE, ">", QuestArt.NEXT_X + QuestArt.PAGE_W / 2,
            frame.shownPage() + 1 < frame.pages() ? TEXT_DARK : TEXT_MUTED);

        if (age >= OPEN_NANOS * 6 / 10 && frame.selected() >= 0 && frame.selected() < frame.rows().size()) {
            details(canvas, frame, now);
        }
        return canvas.build();
    }

    private static void row(Canvas canvas, int slot, Row row, boolean chosen, float reveal, boolean large, long now) {
        int fade = shade(reveal);
        canvas.sprite(QuestArt.ROW[slot], QuestArt.ROW_X, fade);
        if (chosen) {
            canvas.sprite(QuestArt.ROW_LIT[slot], QuestArt.ROW_X, fade);
        }

        int color = switch (row.status()) {
            case ACTIVE -> ACTIVE;
            case COMPLETED -> COMPLETED;
            default -> AVAILABLE;
        };
        if (row.status() == QuestStatus.ACTIVE) {
            // the active swatch breathes, as it did on the client
            color = scale(color, 0.65F + 0.35F * breath(now, 1_600_000_000L));
        }
        int iconX = QuestArt.ROW_X + 2;
        if (row.status() == QuestStatus.AVAILABLE) {
            canvas.sprite(QuestArt.ICON_INSET[slot], iconX + 1, scale(color, reveal));
        } else {
            canvas.sprite(QuestArt.ICON[slot], iconX, scale(color, reveal));
        }

        int textColor = chosen ? TEXT_DARK : row.status() == QuestStatus.COMPLETED ? TEXT_DONE : TEXT_LIGHT;
        int textX = iconX + QuestArt.ICON_SIZE + 3;
        int ink = scale(textColor, chosen ? 1.0F : reveal);
        if (large) {
            canvas.text(QuestArt.ROW_TEXT[slot], row.title(), textX, ink);
        } else {
            fitted(canvas, row.title(), textX, rowTextWidth(), ink, null, QuestArt.ROW_TEXT_SMALL[slot],
                QuestArt.ROW_TEXT_UPPER[slot], QuestArt.ROW_TEXT_LOWER[slot]);
        }
    }

    private static int rowTextWidth() {
        return QuestArt.ROW_X + QuestArt.ROW_W - 2 - (QuestArt.ROW_X + 2 + QuestArt.ICON_SIZE + 3);
    }

    private static void details(Canvas canvas, Frame frame, long now) {
        Row row = frame.rows().get(frame.selected());
        int left = QuestArt.DETAIL_X + 4;
        int width = QuestArt.DETAIL_W - 8;
        fitted(canvas, row.title(), left, width, TEXT_DARK,
            QuestArt.TITLE, QuestArt.TITLE_SMALL, QuestArt.TITLE_UPPER, QuestArt.TITLE_LOWER);

        // Wrap the whole text first, so words do not jump between lines as they type out,
        // and pick the size the whole text fits at.
        TextLine[] rows = QuestArt.DESCRIPTION_SMALL;
        List<String> lines = List.of();
        for (TextLine[] size : new TextLine[][] {QuestArt.DESCRIPTION, QuestArt.DESCRIPTION_MEDIUM, QuestArt.DESCRIPTION_SMALL}) {
            rows = size;
            lines = UiMetrics.wrap(rows[0], UiMetrics.clean(rows[0], frame.description()), width);
            if (lines.size() <= rows.length) {
                break;
            }
        }
        double elapsed = Math.max(0L, now - frame.selectedNanos()) / 1_000_000_000.0D;
        int budget = (int) (elapsed * CHARACTERS_PER_SECOND);
        for (int line = 0; line < Math.min(lines.size(), rows.length) && budget > 0; line++) {
            String text = lines.get(line);
            canvas.text(rows[line], text.substring(0, Math.min(text.length(), budget)), left, TEXT_DARK);
            budget -= text.length() + 1;
        }

        if (!frame.hint().isEmpty()) {
            fitted(canvas, frame.hint(), left, width, TEXT_MUTED, QuestArt.HINT, QuestArt.HINT_SMALL,
                QuestArt.HINT_UPPER, QuestArt.HINT_LOWER);
        }
        single(canvas, frame.reward(), left, width, TEXT_DARK, QuestArt.REWARD, QuestArt.REWARD_SMALL);

        QuestStatus status = row.status();
        boolean live = status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE;
        canvas.sprite(live ? QuestArt.BUTTON : QuestArt.BUTTON_OFF, QuestArt.STATUS_X);
        if (status == QuestStatus.AVAILABLE) {
            // a soft glow breathes over the button, inviting the player to go and accept it
            int step = Math.round(breath(now, 2_000_000_000L) * QuestArt.GLOW_STEPS) - 1;
            if (step >= 0) {
                canvas.sprite(QuestArt.BUTTON_GLOW[Math.min(step, QuestArt.GLOW_STEPS - 1)], QuestArt.STATUS_X);
            }
        }
        canvas.textCentered(QuestArt.STATUS, label(status), QuestArt.STATUS_X + QuestArt.STATUS_W / 2,
            live ? TEXT_DARK : TEXT_MUTED);
    }

    private static String label(QuestStatus status) {
        return switch (status) {
            case AVAILABLE -> "New";
            case ACTIVE -> "Active";
            case COMPLETED -> "Done";
            case LOCKED -> "Locked";
        };
    }

    /** Normal size when it fits, then half size, then two half-size lines with the rest cut short. */
    private static void fitted(Canvas canvas, String text, int x, int width, int color,
                               TextLine normal, TextLine small, TextLine upper, TextLine lower) {
        if (normal != null && fits(normal, text, width)) {
            canvas.text(normal, text, x, color);
            return;
        }
        if (fits(small, text, width)) {
            canvas.text(small, text, x, color);
            return;
        }
        List<String> lines = UiMetrics.wrap(upper, UiMetrics.clean(upper, text), width);
        canvas.text(upper, lines.getFirst(), x, color);
        String rest = String.join(" ", lines.subList(1, lines.size()));
        canvas.text(lower, ellipsis(lower, rest, width), x, color);
    }

    /** Normal size when it fits, otherwise half size cut short. */
    private static void single(Canvas canvas, String text, int x, int width, int color, TextLine normal, TextLine small) {
        if (fits(normal, text, width)) {
            canvas.text(normal, text, x, color);
        } else {
            canvas.text(small, ellipsis(small, text, width), x, color);
        }
    }

    private static boolean fits(TextLine line, String text, int width) {
        return UiMetrics.width(line, UiMetrics.clean(line, text)) - 1 <= width;
    }

    private static String ellipsis(TextLine line, String text, int width) {
        String clean = UiMetrics.clean(line, text);
        if (fits(line, clean, width)) {
            return clean;
        }
        String cut = clean;
        while (!cut.isEmpty() && !fits(line, cut + "..", width)) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.stripTrailing() + "..";
    }

    /** A slow 0 to 1 to 0 breath in a few steps. */
    private static float breath(long now, long period) {
        double wave = 0.5D + 0.5D * Math.sin(now * 2.0D * Math.PI / period);
        return Math.round(wave * BREATH_STEPS) / (float) BREATH_STEPS;
    }

    /** Never brighter than the art itself: white is "as drawn". */
    private static int shade(float brightness) {
        int level = Math.round(255 * Math.max(0.25F, Math.min(1.0F, brightness)));
        return level << 16 | level << 8 | level;
    }

    private static int scale(int rgb, float factor) {
        float clamped = Math.max(0.0F, Math.min(1.0F, factor));
        int r = Math.round(((rgb >> 16) & 0xFF) * clamped);
        int g = Math.round(((rgb >> 8) & 0xFF) * clamped);
        int b = Math.round((rgb & 0xFF) * clamped);
        return r << 16 | g << 8 | b;
    }
}
