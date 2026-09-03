package com.cobblemondimensions.stories.client.walking;

import com.cobblemondimensions.stories.dialogue.WalkingDialogueOption;
import com.cobblemondimensions.stories.dialogue.WalkingDialoguePage;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Renders the walking dialogue bar as a HUD overlay rather than a Screen, which
 * is what lets the player keep walking during a conversation.
 *
 * <p>Every coordinate below is in the 256x256 design space the art was authored
 * in. Sheets blit at (0,0) and self-align.
 */
public final class WalkingDialogueHud {
    private static final int DESIGN = 256;
    private static final int BAR_BOTTOM = 145;
    private static final int PORTRAIT_X = 36;
    private static final int PORTRAIT_Y = 90;
    private static final int PORTRAIT_W = 52;
    private static final int PORTRAIT_H = 46;
    private static final int PANEL_X = 90;
    private static final int PANEL_Y = 90;
    private static final int PANEL_W = 128;
    private static final int PANEL_H = 46;
    private static final int PADDING = 4;
    // visible bar art spans x 29..226 in the canvas, the rest is empty
    private static final int BAR_W = 197;
    private static final float SPEAKER_SCALE = 0.75F;
    // The choice box floats above the bar and doesn't eat into the panel, which
    // is why drawBody can size against the full panel height and never reflow.
    private static final float CHOICE_SCALE = 0.75F;
    private static final int CHOICE_PAD = 3;
    private static final int CHOICE_GAP = 5;

    private static final int COLOR_TEXT = 0xFF1F1F1F;
    private static final int COLOR_SPEAKER = 0xFF0D0D0D;
    private static final int COLOR_DIVIDER = 0xFF5C5C5C;
    private static final int COLOR_SELECTED_BAR = 0xFF44444A;
    private static final int COLOR_SELECTED_TEXT = 0xFFF2F2F2;
    private static final int COLOR_OPTION = 0xFF2A2A2A;
    private static final int COLOR_CHOICE_EDGE = 0xFFF2F2F2;
    private static final int COLOR_CHOICE_BORDER = 0xFF161616;
    private static final int COLOR_CHOICE_FILL = 0xFFC6C6C6;

    private static final char[] DECODE_GLYPHS = {'#', '/', '+', '=', '?', '*', '%'};

    private static final ResourceLocation FRAME = texture("walking_frame.png");
    private static final ResourceLocation BACKGROUND = texture("walking_background.png");
    private static final ResourceLocation PORTRAIT_BACKGROUND = texture("walking_portrait_background.png");
    private static final ResourceLocation PORTRAIT_1 = texture("walking_portrait_1.png");
    private static final ResourceLocation PORTRAIT_2 = texture("walking_portrait_2.png");
    private static final ResourceLocation TEXT_BACKGROUND = texture("walking_text_background.png");

    private WalkingDialogueHud() {
    }

    public static void render(GuiGraphics graphics) {
        WalkingDialogueSession session = WalkingDialogueSession.active();
        if (session == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        // target roughly half the screen width, capped at 3x
        int scale = Math.max(1, Math.min(3, (int) (screenWidth * 0.5F / BAR_W)));
        int originX = (screenWidth - DESIGN * scale) / 2;
        int originY = screenHeight - 40 - BAR_BOTTOM * scale;

        long now = System.nanoTime();
        float open = ease(session.openProgress(now));
        float close = session.isClosing() ? ease(session.closeProgress(now)) : 0.0F;
        float alpha = Math.min(1.0F, open * 1.6F) * (1.0F - close);
        if (alpha <= 0.01F) {
            return;
        }

        float slideY = (1.0F - open) * 18.0F + close * 14.0F;
        float reveal = Mth.clamp((open - 0.30F) / 0.70F, 0.0F, 1.0F) * (1.0F - close);
        float pop = Mth.clamp((open - 0.10F) / 0.55F, 0.0F, 1.0F);

        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(0.0F, slideY, 0.0F);
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);

        blit(graphics, FRAME);
        blit(graphics, BACKGROUND);
        drawPortrait(graphics, session, now, pop);

        // Panel unrolls left to right. enableScissor takes raw screen pixels, not
        // design space, hence the manual originX + scale maths. Vertical bounds are
        // left wide open because the bar is sliding at the same time.
        int panelLeft = originX + PANEL_X * scale;
        int panelRight = panelLeft + Math.round(PANEL_W * scale * reveal);
        if (panelRight > panelLeft) {
            graphics.enableScissor(panelLeft, 0, panelRight, screenHeight);
            blit(graphics, TEXT_BACKGROUND);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (reveal >= 0.999F && !session.isClosing()) {
                drawPanelContents(graphics, minecraft.font, session, now);
            }
            graphics.disableScissor();
        }

        // Drawn after disableScissor: the box sits above the bar and the unroll
        // clip would cut it off.
        if (reveal >= 0.999F && !session.isClosing() && session.isChoosing(now)) {
            drawChoices(
                graphics,
                minecraft.font,
                session.page().options(),
                session.selection(),
                session.choiceProgress(now)
            );
        }

        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void drawPortrait(
        GuiGraphics graphics,
        WalkingDialogueSession session,
        long now,
        float pop
    ) {
        // squash on one axis and stretch on the other, springs the box open
        float squash = pop >= 1.0F ? 1.0F : 1.0F + (float) Math.sin(pop * Math.PI) * 0.12F;
        float grow = pop;
        float centerX = PORTRAIT_X + PORTRAIT_W / 2.0F;
        float centerY = PORTRAIT_Y + PORTRAIT_H / 2.0F;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(grow * squash, grow / squash, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);
        blit(graphics, PORTRAIT_BACKGROUND);
        blit(graphics, portraitFrame(session, now));
        graphics.pose().popPose();
    }

    /** Two-frame flip. Fast while the line is typing, slow idle blink once it's done. */
    private static ResourceLocation portraitFrame(WalkingDialogueSession session, long now) {
        boolean talking = !session.isTextComplete(now) && !session.isClosing();
        long period = talking ? 120_000_000L : 620_000_000L;
        return ((now / period) & 1L) == 0L ? PORTRAIT_1 : PORTRAIT_2;
    }

    private static void drawPanelContents(
        GuiGraphics graphics,
        Font font,
        WalkingDialogueSession session,
        long now
    ) {
        WalkingDialoguePage page = session.page();
        int innerX = PANEL_X + PADDING;
        int innerWidth = PANEL_W - PADDING * 2;
        int bottom = PANEL_Y + PANEL_H - PADDING;
        int y = PANEL_Y + PADDING;

        String speaker = page.speakerText();
        if (!speaker.isBlank()) {
            long age = now - session.pageStartedNanos();
            drawDecoded(graphics, font, speaker, innerX, y - 1, age);
            graphics.fill(innerX, y + 8, PANEL_X + PANEL_W - PADDING, y + 9, COLOR_DIVIDER);
            y += 11;
        }

        drawBody(graphics, font, session, page, innerX, y, innerWidth, bottom, now);

        if (!page.hasOptions() && session.isTextComplete(now)) {
            drawContinueArrow(graphics, now);
        }
    }

    private static void drawBody(
        GuiGraphics graphics,
        Font font,
        WalkingDialogueSession session,
        WalkingDialoguePage page,
        int x,
        int y,
        int width,
        int bottom,
        long now
    ) {
        int available = Math.max(1, bottom - y);
        float scale = fittedScale(font, page.text(), width, available);
        int wrapWidth = Math.max(1, (int) Math.floor(width / scale));
        int lineStep = Math.max(1, Math.round((font.lineHeight + 1) * scale));
        int maxLines = Math.max(1, available / lineStep);

        String visible = prefix(page.text(), session.visibleCharacters(now));
        List<FormattedCharSequence> lines = font.split(Component.literal(visible), wrapWidth);

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        // fittedScale already sized for the full page, so there is nothing to
        // scroll. firstLine stays 0 and the text is pinned to the top.
        int firstLine = 0;
        int shown = Math.min(lines.size(), maxLines);
        for (int index = 0; index < shown; index++) {
            graphics.drawString(
                font,
                lines.get(firstLine + index),
                0,
                (int) (index * (font.lineHeight + 1)),
                COLOR_TEXT,
                false
            );
        }
        graphics.pose().popPose();

        // caret blinks at the end of the last drawn line while text is still typing
        if (!session.isTextComplete(now) && shown > 0 && ((now / 260_000_000L) & 1L) == 0L) {
            FormattedCharSequence last = lines.get(firstLine + shown - 1);
            int caretX = x + Math.round(font.width(last) * scale);
            int caretY = y + (shown - 1) * lineStep;
            graphics.fill(caretX + 1, caretY, caretX + 2, caretY + lineStep - 1, COLOR_TEXT);
        }
    }

    /** Choice box above the bar: three nested fills for the border, then staggered rows. */
    private static void drawChoices(
        GuiGraphics graphics,
        Font font,
        List<WalkingDialogueOption> options,
        int selection,
        float progress
    ) {
        float eased = ease(progress);
        if (eased <= 0.01F) {
            return;
        }

        int rowHeight = Math.max(1, Math.round((font.lineHeight + 3) * CHOICE_SCALE));
        int boxHeight = options.size() * rowHeight + CHOICE_PAD * 2;
        int boxTop = PANEL_Y - CHOICE_GAP - boxHeight;
        int boxRight = PANEL_X + PANEL_W;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, (1.0F - eased) * 7.0F, 0.0F);

        graphics.fill(PANEL_X - 2, boxTop - 2, boxRight + 2, boxTop + boxHeight + 2, fade(COLOR_CHOICE_EDGE, eased));
        graphics.fill(PANEL_X - 1, boxTop - 1, boxRight + 1, boxTop + boxHeight + 1, fade(COLOR_CHOICE_BORDER, eased));
        graphics.fill(PANEL_X, boxTop, boxRight, boxTop + boxHeight, fade(COLOR_CHOICE_FILL, eased));

        int maxTextWidth = (int) ((PANEL_W - CHOICE_PAD * 2 - 4) / CHOICE_SCALE);
        for (int index = 0; index < options.size(); index++) {
            float rowIn = Mth.clamp((eased - index * 0.16F) / 0.5F, 0.0F, 1.0F);
            if (rowIn <= 0.01F) {
                continue;
            }

            int rowY = boxTop + CHOICE_PAD + index * rowHeight;
            boolean selected = index == Math.floorMod(selection, options.size());
            if (selected) {
                graphics.fill(PANEL_X + 2, rowY, boxRight - 2, rowY + rowHeight - 1, fade(COLOR_SELECTED_BAR, rowIn));
            }

            String label = (index + 1) + ". " + options.get(index).text().getString();
            graphics.pose().pushPose();
            graphics.pose().translate(
                PANEL_X + CHOICE_PAD + 1 + (selected ? 1 : 0) + (1.0F - rowIn) * 4.0F,
                rowY + 1,
                0.0F
            );
            graphics.pose().scale(CHOICE_SCALE, CHOICE_SCALE, 1.0F);
            graphics.drawString(
                font,
                font.plainSubstrByWidth(label, maxTextWidth),
                0,
                0,
                fade(selected ? COLOR_SELECTED_TEXT : COLOR_OPTION, rowIn),
                false
            );
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
    }

    private static int fade(int argb, float alpha) {
        int scaled = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    private static void drawContinueArrow(GuiGraphics graphics, long now) {
        int pulse = (int) ((now / 240_000_000L) & 1L);
        int x = PANEL_X + PANEL_W - 10;
        int y = PANEL_Y + PANEL_H - 8 + pulse;
        graphics.fill(x, y, x + 6, y + 1, COLOR_TEXT);
        graphics.fill(x + 1, y + 1, x + 5, y + 2, COLOR_TEXT);
        graphics.fill(x + 2, y + 2, x + 4, y + 3, COLOR_TEXT);
    }

    /**
     * Speaker name descrambling in. Each character cycles through junk glyphs for
     * ~110ms before settling. cursorX always advances by the real glyph width so
     * the name doesn't jitter while it resolves.
     */
    private static void drawDecoded(GuiGraphics graphics, Font font, String text, int x, int y, long age) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(SPEAKER_SCALE, SPEAKER_SCALE, 1.0F);
        int cursorX = 0;
        long cycle = age / 32_000_000L;
        for (int index = 0; index < text.length(); index++) {
            char actual = text.charAt(index);
            long characterAge = age - index * 38_000_000L;
            if (characterAge < 0L) {
                cursorX += font.width(String.valueOf(actual));
                continue;
            }

            char shown = actual;
            int color = COLOR_SPEAKER;
            if (actual != ' ' && characterAge < 110_000_000L) {
                shown = DECODE_GLYPHS[(int) ((cycle + index * 3L) % DECODE_GLYPHS.length)];
                color = COLOR_DIVIDER;
            }
            graphics.drawString(font, String.valueOf(shown), cursorX, 0, color, false);
            cursorX += font.width(String.valueOf(actual));
        }
        graphics.pose().popPose();
    }

    /** Largest scale in 0.05 steps that fits the whole page. Falls back to 0.40 and clips. */
    private static float fittedScale(Font font, String text, int width, int height) {
        for (float scale = 0.85F; scale >= 0.39F; scale -= 0.05F) {
            int wrapWidth = Math.max(1, (int) Math.floor(width / scale));
            int lineStep = Math.max(1, Math.round((font.lineHeight + 1) * scale));
            int lines = font.split(Component.literal(text), wrapWidth).size();
            if (lines * lineStep <= height) {
                return scale;
            }
        }
        return 0.40F;
    }

    private static float ease(float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        return 1.0F - (float) Math.pow(1.0F - clamped, 3.0D);
    }

    private static String prefix(String text, int count) {
        if (count >= text.length()) {
            return text;
        }
        return count <= 0 ? "" : text.substring(0, count);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture) {
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/walking/" + fileName);
    }
}
