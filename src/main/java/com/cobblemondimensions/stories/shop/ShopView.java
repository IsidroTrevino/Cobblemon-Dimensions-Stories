package com.cobblemondimensions.stories.shop;

import com.cobblemondimensions.stories.ui.Canvas;
import com.cobblemondimensions.stories.ui.art.ShopArt;
import com.cobblemondimensions.stories.ui.art.Sprite;
import com.cobblemondimensions.stories.ui.art.TextLine;
import com.cobblemondimensions.stories.ui.art.UiMetrics;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Draws a shopkeeper conversation the way the old client screens did: the
 * Curator's hands rising out of the dark and talking while the text types, or
 * Mara's portrait mouthing along on her blue counter.
 */
public final class ShopView {
    /** How long the closet takes to open before the Curator starts talking. */
    static final long HAND_OPENING_NANOS = 1_050_000_000L;
    static final double CHARACTERS_PER_SECOND = 34.0D;
    /** Catalog rows appear one after another. */
    static final long CATALOG_STEP_NANOS = 130_000_000L;
    /** How long a clicked button stays pressed in. */
    static final long PRESS_NANOS = 180_000_000L;

    private static final char[] SCRAMBLE = {'#', '/', '+', '=', '?', '*', '%'};
    private static final int WHITE = 0xFFFFFF;
    private static final int SHADOW = 0x3F3F3F;

    private ShopView() {
    }

    /**
     * Everything the picture depends on at one moment. {@code pressed} is the
     * button row that was just clicked, or -1, and {@code pressedNanos} when.
     */
    public record Frame(ShopDialogue.Theme theme, ShopDialogue.Page page, String speaker, List<String> lines,
                        int visible, boolean complete, int picked, int pressed, long pressedNanos,
                        long openedNanos, long pageNanos, boolean firstPage) {
        String text() {
            return String.join("\n\n", lines);
        }

        boolean pressed(int row, long now) {
            return row == pressed && now - pressedNanos < PRESS_NANOS;
        }
    }

    public static Component compose(Frame frame, long now) {
        return frame.theme() == ShopDialogue.Theme.HAND ? hand(frame, now) : classic(frame, now);
    }

    // ------------------------------------------------------------- closet

    private static Component hand(Frame frame, long now) {
        Canvas canvas = new Canvas();
        long screenAge = Math.max(0L, now - frame.openedNanos());
        // the first page starts talking only once the closet is open, so its age starts at zero then
        long pageAge = Math.max(0L, now - frame.pageNanos());
        canvas.sprite(ShopArt.HAND_SCENE, ShopArt.handX(34));
        canvas.sprite(hands(frame, now, screenAge, pageAge), ShopArt.handX(34));
        // the lower half goes over the hands, so they rise from behind it
        canvas.sprite(ShopArt.HAND_LOWER, ShopArt.handX(34));
        canvas.sprite(ShopArt.HAND_FRAME, ShopArt.handX(29));

        canvas.sprite(ShopArt.HAND_TITLE_BAR, ShopArt.handX(69));
        // On the first page the title waits for the closet to finish unfolding, and keeps that clock.
        long titleAge = frame.firstPage() ? screenAge - 330_000_000L : pageAge;
        title(canvas, frame.page().title().orElse("CURIO CLOSET"), titleAge);

        ShopDialogue.Page page = frame.page();
        if (page.isCatalog()) {
            catalog(canvas, frame, pageAge, now);
        } else if (page.options().stream().anyMatch(ShopDialogue.Option::buy)) {
            confirmation(canvas, frame);
            buttons(canvas, frame, now);
        } else {
            handText(canvas, frame, pageAge);
            buttons(canvas, frame, now);
            if (frame.complete() && page.options().isEmpty()) {
                canvas.sprite(ShopArt.HAND_CONTINUE[(int) ((now / 480_000_000L) & 1L)],
                    ShopArt.HAND_TEXT_X + ShopArt.HAND_TEXT_W - 10);
            }
        }
        return canvas.build();
    }

    private static Sprite hands(Frame frame, long now, long screenAge, long pageAge) {
        if (frame.firstPage() && screenAge < HAND_OPENING_NANOS) {
            // rising out of the dark on an ease-out, with the idle flap running underneath
            double progress = screenAge / (double) HAND_OPENING_NANOS;
            int drop = (int) Math.round(Math.pow(1.0D - progress, 3.0D) * 52.0D);
            int flap = (int) ((screenAge / 120_000_000L) & 1L);
            for (int rise = 0; rise < ShopArt.RISE.length; rise++) {
                if (ShopArt.RISE[rise] <= drop) {
                    return ShopArt.HAND_RISING[rise][flap];
                }
            }
            return flap == 0 ? ShopArt.HAND_BASE_DOWN : ShopArt.HAND_BASE_UP;
        }
        switch (frame.page().mood()) {
            case NO -> {
                // one head shake, then hold the last frame
                Sprite[] shake = {ShopArt.HAND_NO[0], ShopArt.HAND_NO[1], ShopArt.HAND_NO[2], ShopArt.HAND_NO[1]};
                return shake[(int) Math.min(shake.length - 1, pageAge / 145_000_000L)];
            }
            case YES -> {
                long bob = Math.round(Math.sin(pageAge / 145_000_000.0D) * 2.0D);
                return ShopArt.HAND_YES[(int) bob + 2];
            }
            default -> {
                if (!frame.complete()) {
                    return ((now / 145_000_000L) & 1L) == 0L ? ShopArt.HAND_SPEAKING_DOWN : ShopArt.HAND_SPEAKING_UP;
                }
                return ((now / 560_000_000L) & 1L) == 0L ? ShopArt.HAND_BASE_DOWN : ShopArt.HAND_BASE_UP;
            }
        }
    }

    /** The title churns through junk symbols, one letter after another, before it settles. */
    private static void title(Canvas canvas, String title, long age) {
        if (age < 0) {
            return;
        }
        String clean = UiMetrics.clean(ShopArt.HAND_TITLE, title);
        int cursor = ShopArt.HAND_TITLE_CENTER * 8 - UiMetrics.widthEighths(ShopArt.HAND_TITLE, clean) / 2;
        long cycle = age / 32_000_000L;
        for (int index = 0; index < clean.length(); index++) {
            char actual = clean.charAt(index);
            long charAge = age - index * 42_000_000L;
            if (charAge >= 0 && actual != ' ') {
                boolean settled = charAge >= 115_000_000L;
                String shown = String.valueOf(settled ? actual
                    : SCRAMBLE[(int) Math.floorMod(cycle + index * 3L, (long) SCRAMBLE.length)]);
                canvas.textAt(ShopArt.HAND_TITLE_SHADOW, shown, cursor + 8, SHADOW);
                canvas.textAt(ShopArt.HAND_TITLE, shown, cursor, settled ? WHITE : 0xD0D0D0);
            }
            // step by the real letter, so the title does not jitter while it decodes
            cursor += UiMetrics.advanceEighths(ShopArt.HAND_TITLE, actual);
        }
    }

    private static void handText(Canvas canvas, Frame frame, long pageAge) {
        int x = ShopArt.HAND_TEXT_X + 4;
        int width = ShopArt.HAND_TEXT_W - 8;
        if (!frame.speaker().isBlank()) {
            canvas.text(ShopArt.HAND_NAME, fit(ShopArt.HAND_NAME, frame.speaker(), width), x, 0x292929);
            canvas.sprite(ShopArt.HAND_RULE, ShopArt.HAND_TEXT_X + 3);
        }
        TextLine[] rows = ShopArt.HAND_BODY;
        List<String> wrapped = UiMetrics.wrap(rows[0], UiMetrics.clean(rows[0], frame.text()), width);
        if (wrapped.size() > rows.length) {
            rows = ShopArt.HAND_BODY_SMALL;
            wrapped = UiMetrics.wrap(rows[0], UiMetrics.clean(rows[0], frame.text()), width);
        }
        typed(canvas, rows, wrapped, frame.visible(), x, 0x202020);
        if (!frame.complete() && ((pageAge / 130_000_000L) & 1L) == 0L) {
            canvas.sprite(ShopArt.HAND_CARET, ShopArt.HAND_TEXT_X + ShopArt.HAND_TEXT_W - 8);
        }
    }

    /** The confirm page: the question on top, then the product and its price centred. */
    private static void confirmation(Canvas canvas, Frame frame) {
        int x = ShopArt.HAND_TEXT_X + 4;
        int width = ShopArt.HAND_TEXT_W - 8;
        int center = ShopArt.HAND_TEXT_X + ShopArt.HAND_TEXT_W / 2;
        List<String> lines = frame.lines();
        String prompt = lines.isEmpty() ? "" : lines.getFirst();
        List<String> wrapped = UiMetrics.wrap(ShopArt.HAND_PROMPT[0], UiMetrics.clean(ShopArt.HAND_PROMPT[0], prompt), width);
        typed(canvas, ShopArt.HAND_PROMPT, wrapped, Math.min(frame.visible(), prompt.length()), x, 0x292929);
        int budget = frame.visible() - prompt.length() - 2;
        if (lines.size() > 1 && budget > 0) {
            String item = lines.get(1).substring(0, Math.min(lines.get(1).length(), budget));
            canvas.textCentered(ShopArt.HAND_ITEM, fit(ShopArt.HAND_ITEM, item, width), center, 0x1D1D1D);
            budget -= lines.get(1).length() + 2;
        }
        if (lines.size() > 2 && budget > 0) {
            String price = lines.get(2).substring(0, Math.min(lines.get(2).length(), budget));
            canvas.textCentered(ShopArt.HAND_PRICE, fit(ShopArt.HAND_PRICE, price, width), center, 0x383838);
        }
    }

    /** Products in the text box, Buy and Back on the buttons. */
    private static void catalog(Canvas canvas, Frame frame, long pageAge, long now) {
        List<ShopDialogue.Product> products = frame.page().catalog();
        int count = catalogCount(frame.page());
        for (int index = 0; index < count; index++) {
            boolean revealed = pageAge >= index * CATALOG_STEP_NANOS;
            boolean picked = index == frame.picked();
            if (!revealed) {
                // placeholder bar, widths staggered so it does not look like a table
                canvas.sprite(ShopArt.HAND_ROW_PENDING[index], ShopArt.HAND_TEXT_X + 5);
                continue;
            }
            if (picked) {
                canvas.sprite(ShopArt.HAND_ROW_PICKED[index], ShopArt.HAND_TEXT_X + 1);
            }
            ShopDialogue.Product product = products.get(index);
            String label = (picked ? "> " : "  ") + product.name() + "  ·  " + product.price() + "E";
            canvas.text(ShopArt.HAND_ROW_TEXT[index], fit(ShopArt.HAND_ROW_TEXT[index], label, ShopArt.HAND_TEXT_W - 6),
                ShopArt.HAND_TEXT_X + 3, picked ? WHITE : 0x222222);
        }
        boolean ready = pageAge >= catalogReady(count);
        boolean canBuy = ready && frame.picked() >= 0 && frame.picked() < count;
        button(canvas, 0, "Buy", canBuy, frame.pressed(0, now));
        button(canvas, 1, "Back", ready, frame.pressed(1, now));
        // rows 2 and 3 stay empty but are still painted, otherwise there are holes
        button(canvas, 2, "", false, false);
        button(canvas, 3, "", false, false);
    }

    static int catalogCount(ShopDialogue.Page page) {
        return Math.min(page.catalog().size(), ShopArt.BUTTON_ROWS.length);
    }

    static long catalogReady(int products) {
        return Math.max(300_000_000L, products * CATALOG_STEP_NANOS + 100_000_000L);
    }

    /** All four rows are always painted; extra ones stay blank and disabled. */
    private static void buttons(Canvas canvas, Frame frame, long now) {
        List<ShopDialogue.Option> options = frame.page().options();
        for (int row = 0; row < ShopArt.BUTTON_ROWS.length; row++) {
            boolean present = row < options.size();
            button(canvas, row, present ? options.get(row).text() : "", frame.complete() && present,
                frame.pressed(row, now));
        }
    }

    private static void button(Canvas canvas, int row, String label, boolean enabled, boolean pressed) {
        Sprite plate = !enabled ? ShopArt.HAND_BUTTON_OFF[row] : pressed ? ShopArt.HAND_BUTTON_PRESSED[row] : ShopArt.HAND_BUTTON[row];
        Sprite face = !enabled ? ShopArt.HAND_FACE_OFF[row] : pressed ? ShopArt.HAND_FACE_PRESSED[row] : ShopArt.HAND_FACE[row];
        canvas.sprite(plate, ShopArt.HAND_BUTTON_X);
        canvas.sprite(face, ShopArt.HAND_BUTTON_X + 4);
        if (!label.isBlank()) {
            TextLine line = ShopArt.HAND_ROW_TEXT[row];
            canvas.textCentered(line, fit(line, label, ShopArt.HAND_LABEL_W),
                ShopArt.HAND_BUTTON_X + ShopArt.HAND_BUTTON_W / 2, enabled ? 0x161616 : 0xB8B8B8);
        }
    }

    // ------------------------------------------------------------ counter

    private static Component classic(Frame frame, long now) {
        Canvas canvas = new Canvas();
        canvas.sprite(ShopArt.CLASSIC_CORNER, ShopArt.CLASSIC_X + 176);
        canvas.sprite(ShopArt.CLASSIC_BASE, ShopArt.CLASSIC_X);
        Sprite face = frame.complete() ? ShopArt.CLASSIC_SMILING
            : ((now / 140_000_000L) & 1L) == 0L ? ShopArt.CLASSIC_TALKING : ShopArt.CLASSIC_CASUAL;
        canvas.sprite(face, ShopArt.CLASSIC_X + 139);

        if (!frame.speaker().isBlank()) {
            canvas.sprite(ShopArt.CLASSIC_NAME_PLATE, ShopArt.CLASSIC_X + 45);
            String name = fit(ShopArt.CLASSIC_NAME, frame.speaker(), 80);
            canvas.textCentered(ShopArt.CLASSIC_NAME_SHADOW, name, ShopArt.CLASSIC_X + 89, 0x0E0C11);
            canvas.textCentered(ShopArt.CLASSIC_NAME, name, ShopArt.CLASSIC_X + 88, 0x393246);
        }

        ShopDialogue.Page page = frame.page();
        boolean options = !page.options().isEmpty();
        int maxLines = options ? ShopArt.CLASSIC_OPTION_LINES : ShopArt.CLASSIC_FULL_LINES;
        List<String> wrapped = UiMetrics.wrap(ShopArt.CLASSIC_BODY[0],
            UiMetrics.clean(ShopArt.CLASSIC_BODY[0], frame.text()), ShopArt.CLASSIC_TEXT_W);
        int budget = frame.visible();
        for (int line = 0; line < Math.min(wrapped.size(), maxLines) && budget > 0; line++) {
            String row = wrapped.get(line);
            String shown = row.substring(0, Math.min(row.length(), budget));
            // the old counter drew its text with a drop shadow
            canvas.text(ShopArt.CLASSIC_BODY_SHADOW[line], shown, ShopArt.CLASSIC_TEXT_X + 1, SHADOW);
            canvas.text(ShopArt.CLASSIC_BODY[line], shown, ShopArt.CLASSIC_TEXT_X, WHITE);
            budget -= row.length() + 1;
        }

        if (frame.complete() && options) {
            int count = Math.min(page.options().size(), ShopArt.BUTTON_ROWS.length);
            for (int row = 0; row < count; row++) {
                boolean pressed = frame.pressed(row, now);
                Sprite[] plates = pressed ? ShopArt.CLASSIC_BUTTON_PRESSED[row] : ShopArt.CLASSIC_BUTTON[row];
                canvas.sprite(plates[row & 1], ShopArt.CLASSIC_BUTTON_X);
                canvas.sprite(pressed ? ShopArt.CLASSIC_FACE_PRESSED[row] : ShopArt.CLASSIC_FACE[row],
                    ShopArt.CLASSIC_BUTTON_X + 2);
                String label = fit(ShopArt.CLASSIC_LABEL[row], page.options().get(row).text(), ShopArt.CLASSIC_BUTTON_W - 8);
                int center = ShopArt.CLASSIC_BUTTON_X + ShopArt.CLASSIC_BUTTON_W / 2;
                canvas.textCentered(ShopArt.CLASSIC_LABEL_SHADOW[row], label, center + 1, SHADOW);
                canvas.textCentered(ShopArt.CLASSIC_LABEL[row], label, center, WHITE);
            }
        } else if (frame.complete()) {
            canvas.sprite(ShopArt.CLASSIC_CONTINUE[(int) ((now / 500_000_000L) & 1L)], ShopArt.CLASSIC_X + 111);
        }
        return canvas.build();
    }

    // ------------------------------------------------------------- shared

    /** Wrapped lines typed out character by character across the rows. */
    private static void typed(Canvas canvas, TextLine[] rows, List<String> lines, int visible, int x, int color) {
        int budget = visible;
        for (int line = 0; line < Math.min(lines.size(), rows.length) && budget > 0; line++) {
            String row = lines.get(line);
            canvas.text(rows[line], row.substring(0, Math.min(row.length(), budget)), x, color);
            budget -= row.length() + 1;
        }
    }

    private static String fit(TextLine line, String text, int width) {
        String clean = UiMetrics.clean(line, text);
        if (UiMetrics.width(line, clean) - 1 <= width) {
            return clean;
        }
        while (clean.length() > 1 && UiMetrics.width(line, clean + "..") - 1 > width) {
            clean = clean.substring(0, clean.length() - 1);
        }
        return clean.stripTrailing() + "..";
    }
}
