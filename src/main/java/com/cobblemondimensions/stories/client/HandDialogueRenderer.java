package com.cobblemondimensions.stories.client;

import com.cobblemondimensions.stories.client.StoryDialogueAccess.Option;
import com.cobblemondimensions.stories.client.StoryDialogueAccess.Snapshot;
import com.cobblemondimensions.stories.client.StoryDialogueRenderer.Interaction;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Draws the Hand Assets shop. Hit testing lives here too, because none of these
 * buttons are real widgets.
 */
public final class HandDialogueRenderer {
    public static final long OPENING_DURATION_NANOS = 1_050_000_000L;

    // Art sheets are 256x256 and every layer blits at (0,0). 151 is just the height
    // of the part that's actually drawn; used for centering and the integer scale.
    private static final int DESIGN_WIDTH = 256;
    private static final int DESIGN_HEIGHT = 151;
    private static final int TEXT_X = 36;
    private static final int TEXT_Y = 90;
    private static final int TEXT_WIDTH = 109;
    private static final int BUTTON_X = 148;
    private static final int BUTTON_Y = 90;
    private static final int BUTTON_WIDTH = 71;
    private static final int BUTTON_HEIGHT = 10;
    private static final int BUTTON_STEP = 12;
    private static final int BUTTON_ROWS = 4;
    // Button art is already at its final position in the sheet, so uv == dest.
    private static final int BUTTON_SOURCE_X = 148;
    private static final int BUTTON_SOURCE_Y = 90;
    private static final long CATALOG_REVEAL_STEP_NANOS = 130_000_000L;
    private static final float DIALOGUE_TEXT_MAX_SCALE = 0.68F;
    private static final float DIALOGUE_TEXT_MIN_SCALE = 0.48F;
    private static final float DIALOGUE_TEXT_SCALE_STEP = 0.04F;
    private static final float BUTTON_TEXT_SCALE = 0.58F;
    // junk glyphs cycled per character before the title settles
    private static final char[] DECODE_GLYPHS = {'#', '/', '+', '=', '?', '*', '%'};

    private static final ResourceLocation ILLUSTRATION_BACKGROUND = texture("hand_illustration_background.png");
    private static final ResourceLocation ILLUSTRATION_BACK = texture("hand_illustration_back.png");
    private static final ResourceLocation BOTTOM_BACK = texture("hand_back_of_bottom_part.png");
    private static final ResourceLocation TEXT_BACKGROUND = texture("hand_text_background.png");
    private static final ResourceLocation BUTTON_BACKGROUND = texture("hand_button_background.png");
    private static final ResourceLocation CORNERS = texture("hand_corners.png");
    private static final ResourceLocation BUTTON = texture("hand_button.png");
    private static final ResourceLocation BUTTON_HOVER = texture("hand_button_hover.png");
    private static final ResourceLocation BUTTON_DISABLED = texture("hand_button_disable.png");
    private static final ResourceLocation BASE_DOWN = texture("hand_base_down.png");
    private static final ResourceLocation BASE_UP = texture("hand_base_up.png");
    private static final ResourceLocation SPEAKING_DOWN = texture("hand_speaking_down.png");
    private static final ResourceLocation SPEAKING_UP = texture("hand_speaking_up.png");
    private static final ResourceLocation NO_LEFT = texture("hand_no_left.png");
    private static final ResourceLocation NO_MIDDLE = texture("hand_no_middle.png");
    private static final ResourceLocation NO_RIGHT = texture("hand_no_right.png");
    private static final ResourceLocation YES = texture("hand_yes.png");

    private HandDialogueRenderer() {
    }

    public static void render(
        GuiGraphics graphics,
        Snapshot snapshot,
        int visibleCharacters,
        int screenWidth,
        int screenHeight,
        int mouseX,
        int mouseY,
        long nowNanos,
        long pageStartedNanos,
        long screenStartedNanos,
        boolean inputLocked,
        int catalogSelection
    ) {
        Layout layout = layout(screenWidth, screenHeight);
        int localMouseX = layout.toLocalX(mouseX);
        int localMouseY = layout.toLocalY(mouseY);
        long pageAge = Math.max(0L, nowNanos - pageStartedNanos);
        long screenAge = Math.max(0L, nowNanos - screenStartedNanos);
        boolean complete = visibleCharacters >= snapshot.text().length();

        graphics.fill(0, 0, screenWidth, screenHeight, 0xD6000000);
        graphics.pose().pushPose();
        graphics.pose().translate(layout.originX(), layout.originY(), 0.0F);
        graphics.pose().scale(layout.scale(), layout.scale(), 1.0F);
        applyOpeningTransform(graphics, screenAge);

        blitLayer(graphics, ILLUSTRATION_BACKGROUND);
        blitLayer(graphics, ILLUSTRATION_BACK);
        drawHands(graphics, snapshot.pageId(), complete, nowNanos, pageAge, screenAge);

        // Bottom layers are opaque and go on top of the hands. They hide the offset
        // used by the opening slide, so the hands rise from behind the lower frame.
        blitLayer(graphics, BOTTOM_BACK);
        blitLayer(graphics, TEXT_BACKGROUND);
        blitLayer(graphics, BUTTON_BACKGROUND);
        blitLayer(graphics, CORNERS);

        Font font = Minecraft.getInstance().font;
        drawSceneTitle(graphics, font, titleFor(snapshot.pageId()), pageAge, screenAge, snapshot.pageId());

        if (isCatalog(snapshot.pageId())) {
            drawCatalog(
                graphics,
                font,
                snapshot,
                pageAge,
                localMouseX,
                localMouseY,
                catalogSelection,
                inputLocked
            );
        } else if (isConfirmation(snapshot.pageId())) {
            drawConfirmation(graphics, font, snapshot, visibleCharacters, pageAge);
            drawOptionButtons(
                graphics,
                font,
                snapshot.options(),
                complete,
                localMouseX,
                localMouseY,
                inputLocked
            );
        } else {
            drawDialogueText(graphics, font, snapshot, visibleCharacters, pageAge);
            drawOptionButtons(
                graphics,
                font,
                snapshot.options(),
                complete,
                localMouseX,
                localMouseY,
                inputLocked
            );
            if (complete && snapshot.hasContinueInput() && !inputLocked) {
                drawContinueIndicator(graphics, nowNanos);
            }
        }

        graphics.pose().popPose();
    }

    public static Interaction interactionAt(
        Snapshot snapshot,
        int screenWidth,
        int screenHeight,
        double mouseX,
        double mouseY,
        long nowNanos,
        long pageStartedNanos,
        int catalogSelection
    ) {
        Layout layout = layout(screenWidth, screenHeight);
        int localX = layout.toLocalX(mouseX);
        int localY = layout.toLocalY(mouseY);

        if (isCatalog(snapshot.pageId())) {
            int productCount = productCount(snapshot);
            long pageAge = Math.max(0L, nowNanos - pageStartedNanos);
            if (pageAge < catalogReadyNanos(productCount)) {
                // rows still animating in, swallow the click
                return Interaction.none();
            }

            // Same row geometry as drawCatalog. If you move one, move the other.
            for (int index = 0; index < productCount; index++) {
                int rowY = 93 + index * 10;
                if (localX >= TEXT_X + 1 && localX < TEXT_X + TEXT_WIDTH - 1
                    && localY >= rowY - 1 && localY < rowY + 9
                    && snapshot.options().get(index).selectable()) {
                    return Interaction.selectProduct(index);
                }
            }

            // Row 0 is Buy and commits the highlighted product, row 1 is Back and
            // maps to the trailing option. Rows 2 and 3 are drawn blank.
            int buttonRow = buttonRowAt(localX, localY);
            if (buttonRow == 0 && catalogSelection >= 0 && catalogSelection < productCount) {
                return Interaction.option(catalogSelection);
            }
            if (buttonRow == 1 && snapshot.options().size() > productCount) {
                return Interaction.option(snapshot.options().size() - 1);
            }
            return Interaction.none();
        }

        int buttonRow = buttonRowAt(localX, localY);
        if (buttonRow >= 0
            && buttonRow < snapshot.options().size()
            && snapshot.options().get(buttonRow).selectable()) {
            return Interaction.option(buttonRow);
        }
        return Interaction.none();
    }

    private static void drawHands(
        GuiGraphics graphics,
        String pageId,
        boolean textComplete,
        long nowNanos,
        long pageAge,
        long screenAge
    ) {
        // Opening: slide up 52px on an ease-out cubic, idle flap running underneath.
        if (screenAge < OPENING_DURATION_NANOS) {
            double progress = Math.min(1.0D, screenAge / (double) OPENING_DURATION_NANOS);
            double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
            int offsetY = (int) Math.round((1.0D - eased) * 52.0D);
            ResourceLocation frame = ((screenAge / 120_000_000L) & 1L) == 0L ? BASE_DOWN : BASE_UP;
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, offsetY, 0.0F);
            blitLayer(graphics, frame);
            graphics.pose().popPose();
            return;
        }

        // One-shot head shake, then park on the last frame. Deliberately not looped.
        if (isRefusal(pageId)) {
            ResourceLocation[] sequence = {NO_LEFT, NO_MIDDLE, NO_RIGHT, NO_MIDDLE};
            int frame = Math.min(sequence.length - 1, (int) (pageAge / 145_000_000L));
            blitLayer(graphics, sequence[frame]);
            return;
        }

        if (isSuccess(pageId)) {
            int bob = (int) Math.round(Math.sin(pageAge / 145_000_000.0D) * 2.0D);
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, bob, 0.0F);
            blitLayer(graphics, YES);
            graphics.pose().popPose();
            return;
        }

        // Talking flap while the typewriter runs, then a much slower idle.
        if (!textComplete) {
            ResourceLocation frame = ((nowNanos / 145_000_000L) & 1L) == 0L
                ? SPEAKING_DOWN
                : SPEAKING_UP;
            blitLayer(graphics, frame);
            return;
        }

        ResourceLocation idle = ((nowNanos / 560_000_000L) & 1L) == 0L ? BASE_DOWN : BASE_UP;
        blitLayer(graphics, idle);
    }

    /** Panel unfold: scales from a 14% tall sliver up to full size around the center. */
    private static void applyOpeningTransform(GuiGraphics graphics, long screenAge) {
        if (screenAge >= OPENING_DURATION_NANOS) {
            return;
        }

        double progress = Math.min(1.0D, screenAge / (double) OPENING_DURATION_NANOS);
        double eased = 1.0D - Math.pow(1.0D - progress, 3.0D);
        float scaleX = (float) (0.88D + eased * 0.12D);
        float scaleY = (float) (0.14D + eased * 0.86D);
        graphics.pose().translate(DESIGN_WIDTH / 2.0F, DESIGN_HEIGHT / 2.0F, 0.0F);
        graphics.pose().scale(scaleX, scaleY, 1.0F);
        graphics.pose().translate(-DESIGN_WIDTH / 2.0F, -DESIGN_HEIGHT / 2.0F, 0.0F);
    }

    private static void drawSceneTitle(
        GuiGraphics graphics,
        Font font,
        String title,
        long pageAge,
        long screenAge,
        String pageId
    ) {
        graphics.fill(69, 72, 187, 83, 0xD51D1D1D);
        // First page rides the screen clock with a 330ms delay so the title decodes
        // after the panel has unfolded. Later pages just use their own page age.
        long animationAge = "main".equals(pageId) && screenAge < OPENING_DURATION_NANOS
            ? Math.max(0L, screenAge - 330_000_000L)
            : pageAge;
        drawDecodedTitle(graphics, font, title, 128, 73, animationAge);
    }

    /** Reveals the title left to right, each character churning through junk glyphs first. */
    private static void drawDecodedTitle(
        GuiGraphics graphics,
        Font font,
        String title,
        int centerX,
        int y,
        long animationAge
    ) {
        int cursorX = centerX - font.width(title) / 2;
        long cycle = animationAge / 32_000_000L;
        for (int index = 0; index < title.length(); index++) {
            char actual = title.charAt(index);
            long characterAge = animationAge - index * 42_000_000L;
            if (characterAge < 0L) {
                // not its turn yet, just reserve the space
                cursorX += font.width(String.valueOf(actual));
                continue;
            }

            char shown = actual;
            int color = 0xFFFFFFFF;
            if (actual != ' ' && characterAge < 115_000_000L) {
                shown = DECODE_GLYPHS[(int) ((cycle + index * 3L) % DECODE_GLYPHS.length)];
                color = 0xFFD0D0D0;
            }
            graphics.drawString(font, String.valueOf(shown), cursorX, y, color, true);
            // Advance by the final character's width, not the glyph we just drew,
            // otherwise the whole title jitters while it decodes.
            cursorX += font.width(String.valueOf(actual));
        }
    }

    private static void drawDialogueText(
        GuiGraphics graphics,
        Font font,
        Snapshot snapshot,
        int visibleCharacters,
        long pageAge
    ) {
        String visibleText = prefix(snapshot.text(), visibleCharacters);
        boolean hasName = !snapshot.speakerName().isBlank();
        int textY = hasName ? TEXT_Y + 13 : TEXT_Y + 4; // name header + rule eats 9px

        if (hasName) {
            String name = font.plainSubstrByWidth(snapshot.speakerName(), TEXT_WIDTH - 8);
            graphics.drawString(font, name, TEXT_X + 4, TEXT_Y + 3, 0xFF292929, false);
            graphics.fill(TEXT_X + 3, TEXT_Y + 11, TEXT_X + TEXT_WIDTH - 3, TEXT_Y + 12, 0xFF5C5C5C);
        }

        // Measure against the full text, not the visible prefix. Otherwise the scale
        // keeps changing as the typewriter runs and the text resizes mid-sentence.
        float textScale = fittedDialogueScale(font, snapshot.text(), textY);
        int wrapWidth = Math.max(1, (int) Math.floor((TEXT_WIDTH - 8) / textScale));
        int maxLines = dialogueLineCapacity(font, textY, textScale);
        List<FormattedCharSequence> wrapped = font.split(Component.literal(visibleText), wrapWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(TEXT_X + 4, textY, 0.0F);
        graphics.pose().scale(textScale, textScale, 1.0F);
        for (int index = 0; index < Math.min(wrapped.size(), maxLines); index++) {
            graphics.drawString(font, wrapped.get(index), 0, index * (font.lineHeight + 1), 0xFF202020, false);
        }
        graphics.pose().popPose();

        // Still typing: scanline crawls the left gutter, caret blinks bottom right.
        if (visibleCharacters < snapshot.text().length()) {
            int scanY = TEXT_Y + 2 + (int) ((pageAge / 28_000_000L) % 41L);
            graphics.fill(TEXT_X + 1, scanY, TEXT_X + 3, Math.min(TEXT_Y + 45, scanY + 2), 0xFFE9E9E9);
            if (((pageAge / 130_000_000L) & 1L) == 0L) {
                graphics.fill(TEXT_X + TEXT_WIDTH - 8, TEXT_Y + 39, TEXT_X + TEXT_WIDTH - 3, TEXT_Y + 41, 0xFF333333);
            }
        }
    }

    /** Confirm page layout: line 0 is the prompt, line 1 the item, line 2 the price. */
    private static void drawConfirmation(
        GuiGraphics graphics,
        Font font,
        Snapshot snapshot,
        int visibleCharacters,
        long pageAge
    ) {
        String prompt = visibleLine(snapshot.lines(), 0, visibleCharacters);
        String item = visibleLine(snapshot.lines(), 1, visibleCharacters);
        String price = visibleLine(snapshot.lines(), 2, visibleCharacters);

        List<FormattedCharSequence> promptLines = font.split(Component.literal(prompt), TEXT_WIDTH - 8);
        for (int index = 0; index < Math.min(2, promptLines.size()); index++) {
            graphics.drawString(font, promptLines.get(index), TEXT_X + 4, TEXT_Y + 3 + index * 9, 0xFF292929, false);
        }
        if (!item.isEmpty()) {
            String clipped = font.plainSubstrByWidth(item, TEXT_WIDTH - 8);
            graphics.drawCenteredString(font, clipped, TEXT_X + TEXT_WIDTH / 2, TEXT_Y + 23, 0xFF1D1D1D);
        }
        if (!price.isEmpty()) {
            String clipped = font.plainSubstrByWidth(price, TEXT_WIDTH - 8);
            graphics.drawCenteredString(font, clipped, TEXT_X + TEXT_WIDTH / 2, TEXT_Y + 35, 0xFF383838);
        }
        if (visibleCharacters < snapshot.text().length()) {
            int pulse = (int) ((pageAge / 100_000_000L) & 1L);
            graphics.fill(TEXT_X + 2, TEXT_Y + 43 - pulse, TEXT_X + TEXT_WIDTH - 2, TEXT_Y + 44, 0x55FFFFFF);
        }
    }

    /**
     * Product list with a staggered reveal. Up to four rows, last option is the Back
     * entry and is handled by the buttons instead of a row.
     */
    private static void drawCatalog(
        GuiGraphics graphics,
        Font font,
        Snapshot snapshot,
        long pageAge,
        int mouseX,
        int mouseY,
        int catalogSelection,
        boolean inputLocked
    ) {
        int productCount = productCount(snapshot);
        for (int index = 0; index < productCount; index++) {
            int rowY = 93 + index * 10;
            boolean revealed = pageAge >= index * CATALOG_REVEAL_STEP_NANOS;
            boolean hovered = !inputLocked
                && mouseX >= TEXT_X + 1 && mouseX < TEXT_X + TEXT_WIDTH - 1
                && mouseY >= rowY - 1 && mouseY < rowY + 9;
            boolean selected = index == catalogSelection;
            if (selected) {
                graphics.fill(TEXT_X + 1, rowY - 1, TEXT_X + TEXT_WIDTH - 1, rowY + 9, 0xFF4D4D4D);
            } else if (hovered && revealed) {
                graphics.fill(TEXT_X + 1, rowY - 1, TEXT_X + TEXT_WIDTH - 1, rowY + 9, 0x66808080);
            }

            if (revealed) {
                String label = font.plainSubstrByWidth(snapshot.options().get(index).text().getString(), TEXT_WIDTH - 10);
                int color = selected ? 0xFFFFFFFF : 0xFF222222;
                graphics.drawString(font, (selected ? "> " : "  ") + label, TEXT_X + 3, rowY, color, false);
            } else {
                // placeholder bar, widths staggered so it doesn't look like a table
                int width = 15 + index * 7;
                graphics.fill(TEXT_X + 5, rowY + 3, TEXT_X + 5 + width, rowY + 5, 0xFF5C5C5C);
            }
        }

        boolean ready = pageAge >= catalogReadyNanos(productCount);
        drawButton(
            graphics,
            font,
            0,
            translated("gui.cobblemon_dimensions_stories.buy"),
            ready && catalogSelection >= 0 && catalogSelection < productCount && !inputLocked,
            ready && catalogSelection >= 0 && buttonRowAt(mouseX, mouseY) == 0 && !inputLocked
        );
        drawButton(
            graphics,
            font,
            1,
            translated("gui.cobblemon_dimensions_stories.back"),
            ready && !inputLocked,
            ready && buttonRowAt(mouseX, mouseY) == 1 && !inputLocked
        );
        // rows 2 and 3 stay empty but still get painted, otherwise there are holes
        drawButton(graphics, font, 2, "", false, false);
        drawButton(graphics, font, 3, "", false, false);
    }

    private static void drawOptionButtons(
        GuiGraphics graphics,
        Font font,
        List<Option> options,
        boolean textComplete,
        int mouseX,
        int mouseY,
        boolean inputLocked
    ) {
        // Always paint all four rows. Extra ones render blank and disabled.
        int hoveredRow = buttonRowAt(mouseX, mouseY);
        for (int row = 0; row < BUTTON_ROWS; row++) {
            Option option = row < options.size() ? options.get(row) : null;
            boolean enabled = textComplete && !inputLocked && option != null && option.selectable();
            String label = option == null ? "" : option.text().getString();
            drawButton(graphics, font, row, label, enabled, enabled && hoveredRow == row);
        }
    }

    private static void drawButton(
        GuiGraphics graphics,
        Font font,
        int row,
        String label,
        boolean enabled,
        boolean hovered
    ) {
        int y = BUTTON_Y + row * BUTTON_STEP;
        ResourceLocation state = !enabled ? BUTTON_DISABLED : (hovered ? BUTTON_HOVER : BUTTON);
        graphics.blit(
            state,
            BUTTON_X,
            y,
            (float) BUTTON_SOURCE_X,
            (float) BUTTON_SOURCE_Y,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            256,
            256
        );
        int faceColor;
        if (!enabled) {
            faceColor = 0xFF505050;
        } else if (hovered) {
            faceColor = 0xFFF3F3F3;
        } else {
            faceColor = 0xFFDADADA;
        }
        // The blit supplies the border; this fill repaints the inner face per state.
        graphics.fill(BUTTON_X + 4, y + 2, BUTTON_X + BUTTON_WIDTH - 4, y + BUTTON_HEIGHT - 2, faceColor);
        if (!label.isBlank()) {
            int availableWidth = (int) Math.floor((BUTTON_WIDTH - 10) / BUTTON_TEXT_SCALE);
            String clipped = font.plainSubstrByWidth(label, availableWidth);
            int color = enabled ? 0xFF161616 : 0xFFB8B8B8;
            float labelY = y + (BUTTON_HEIGHT - font.lineHeight * BUTTON_TEXT_SCALE) / 2.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(BUTTON_X + BUTTON_WIDTH / 2.0F, labelY, 0.0F);
            graphics.pose().scale(BUTTON_TEXT_SCALE, BUTTON_TEXT_SCALE, 1.0F);
            graphics.drawString(font, clipped, -font.width(clipped) / 2, 0, color, false);
            graphics.pose().popPose();
        }
    }

    /** Largest scale where the text still fits the box. Gives up and returns the minimum. */
    private static float fittedDialogueScale(Font font, String fullText, int textY) {
        for (float scale = DIALOGUE_TEXT_MAX_SCALE;
             scale >= DIALOGUE_TEXT_MIN_SCALE - 0.001F;
             scale -= DIALOGUE_TEXT_SCALE_STEP) {
            int wrapWidth = Math.max(1, (int) Math.floor((TEXT_WIDTH - 8) / scale));
            int lineCount = font.split(Component.literal(fullText), wrapWidth).size();
            if (lineCount <= dialogueLineCapacity(font, textY, scale)) {
                return scale;
            }
        }
        return DIALOGUE_TEXT_MIN_SCALE;
    }

    private static int dialogueLineCapacity(Font font, int textY, float scale) {
        int availableHeight = TEXT_Y + 44 - textY;
        return Math.max(1, (int) Math.floor(availableHeight / ((font.lineHeight + 1) * scale)));
    }

    /** Blinking triangle at the bottom right of the text box, built from three fills. */
    private static void drawContinueIndicator(GuiGraphics graphics, long nowNanos) {
        int pulse = (int) ((nowNanos / 240_000_000L) & 1L);
        int x = TEXT_X + TEXT_WIDTH - 10;
        int y = TEXT_Y + 39 + pulse;
        graphics.fill(x, y, x + 6, y + 1, 0xFF2A2A2A);
        graphics.fill(x + 1, y + 1, x + 5, y + 2, 0xFF2A2A2A);
        graphics.fill(x + 2, y + 2, x + 4, y + 3, 0xFF2A2A2A);
    }

    /**
     * Slices one line out of the typewriter progress, which counts characters across
     * the joined text rather than per line.
     */
    private static String visibleLine(List<String> lines, int index, int visibleCharacters) {
        if (index < 0 || index >= lines.size()) {
            return "";
        }
        int offset = 0;
        for (int line = 0; line < index; line++) {
            // +2 for the "\n\n" the snapshot joins lines with
            offset += lines.get(line).length() + 2;
        }
        int count = Math.max(0, Math.min(lines.get(index).length(), visibleCharacters - offset));
        return lines.get(index).substring(0, count);
    }

    /** Options minus the trailing Back entry, capped at the four rows we can draw. */
    private static int productCount(Snapshot snapshot) {
        return Math.max(0, Math.min(4, snapshot.options().size() - 1));
    }

    /** When the last row has finished revealing and the catalog will accept clicks. */
    private static long catalogReadyNanos(int productCount) {
        return Math.max(300_000_000L, productCount * CATALOG_REVEAL_STEP_NANOS + 100_000_000L);
    }

    /** Button row under the given local point, or -1. */
    private static int buttonRowAt(int x, int y) {
        if (x < BUTTON_X || x >= BUTTON_X + BUTTON_WIDTH || y < BUTTON_Y) {
            return -1;
        }
        int row = (y - BUTTON_Y) / BUTTON_STEP;
        int rowY = BUTTON_Y + row * BUTTON_STEP;
        // Step is 12 but buttons are 10 tall, so the 2px gutter between rows must
        // miss. Dividing by the step alone would round it into the row above.
        if (row < 0 || row >= BUTTON_ROWS || y >= rowY + BUTTON_HEIGHT) {
            return -1;
        }
        return row;
    }

    private static String titleFor(String pageId) {
        if (isCatalog(pageId)) {
            return "BROWSE THE SHELVES";
        }
        if (isConfirmation(pageId)) {
            return "FINALIZE TRADE";
        }
        if (isSuccess(pageId)) {
            return "A FAIR EXCHANGE";
        }
        if (isRefusal(pageId)) {
            return "NO DEAL";
        }
        if (pageId.startsWith("talk_")) {
            return "A QUIET WORD";
        }
        if ("goodbye".equals(pageId)) {
            return "UNTIL NEXT TIME";
        }
        return "CURIO CLOSET";
    }

    private static boolean isCatalog(String pageId) {
        return "catalog".equals(pageId);
    }

    private static boolean isConfirmation(String pageId) {
        return "confirm".equals(pageId);
    }

    private static boolean isSuccess(String pageId) {
        return "purchase_success".equals(pageId);
    }

    private static boolean isRefusal(String pageId) {
        return "purchase_failure".equals(pageId) || "purchase_cancelled".equals(pageId);
    }

    private static String prefix(String text, int characterCount) {
        if (characterCount >= text.length()) {
            return text;
        }
        if (characterCount <= 0) {
            return "";
        }
        return text.substring(0, characterCount);
    }

    private static String translated(String key) {
        return Component.translatable(key).getString();
    }

    /** Every layer is a full 256x256 sheet drawn at (0,0). No per-layer offsets. */
    private static void blitLayer(GuiGraphics graphics, ResourceLocation texture) {
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/hand/" + fileName);
    }

    private static Layout layout(int screenWidth, int screenHeight) {
        // Integer scale only, 1x to 4x. Fractional scaling smears the pixel art.
        int scale = Math.max(1, Math.min(4, Math.min(screenWidth / DESIGN_WIDTH, screenHeight / DESIGN_HEIGHT)));
        int originX = (screenWidth - DESIGN_WIDTH * scale) / 2;
        int originY = (screenHeight - DESIGN_HEIGHT * scale) / 2;
        return new Layout(originX, originY, scale);
    }

    /** Screen space to the 256x256 design space the hit boxes are authored in. */
    private record Layout(int originX, int originY, int scale) {
        int toLocalX(double screenX) {
            return (int) Math.floor((screenX - originX) / scale);
        }

        int toLocalY(double screenY) {
            return (int) Math.floor((screenY - originY) / scale);
        }
    }
}
