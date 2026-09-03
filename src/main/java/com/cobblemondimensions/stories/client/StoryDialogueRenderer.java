package com.cobblemondimensions.stories.client;

import com.cobblemondimensions.stories.client.StoryDialogueAccess.Option;
import com.cobblemondimensions.stories.client.StoryDialogueAccess.Snapshot;
import com.cobblemondimensions.stories.client.StoryDialogueAccess.Theme;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class StoryDialogueRenderer {
    // Art is authored on 256x256 sheets. Only the top 151 rows are the panel itself.
    private static final int DESIGN_WIDTH = 256;
    private static final int DESIGN_HEIGHT = 151;
    private static final int BUTTON_X = 8;
    private static final int BUTTON_WIDTH = 110;
    private static final int BUTTON_HEIGHT = 14;
    private static final int BUTTON_GAP = 6;

    private static final ResourceLocation SHOP_MAIN = texture("shop_main.png");
    private static final ResourceLocation CHARACTER_BACKGROUND = texture("npc_character_background.png");
    private static final ResourceLocation CHARACTER_CASUAL = texture("npc_character_casual.png");
    private static final ResourceLocation CHARACTER_TALKING = texture("npc_character_talking.png");
    private static final ResourceLocation CHARACTER_SMILING = texture("npc_character_smiling.png");
    private static final ResourceLocation BUTTON_ONE = texture("button_1.png");
    private static final ResourceLocation BUTTON_ONE_HOVER = texture("button_1_hover.png");
    private static final ResourceLocation BUTTON_TWO = texture("button_2.png");
    private static final ResourceLocation BUTTON_TWO_HOVER = texture("button_2_hover.png");

    private StoryDialogueRenderer() {
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
        if (snapshot.theme() == Theme.HAND) {
            HandDialogueRenderer.render(
                graphics,
                snapshot,
                visibleCharacters,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                nowNanos,
                pageStartedNanos,
                screenStartedNanos,
                inputLocked,
                catalogSelection
            );
            return;
        }

        Layout layout = layout(screenWidth, screenHeight, snapshot.options().size());
        int localMouseX = layout.toLocalX(mouseX);
        int localMouseY = layout.toLocalY(mouseY);
        boolean complete = visibleCharacters >= snapshot.text().length();

        graphics.fill(0, 0, screenWidth, screenHeight, 0xB8000000);
        graphics.pose().pushPose();
        graphics.pose().translate(layout.originX(), layout.originY(), 0.0F);
        graphics.pose().scale(layout.scale(), layout.scale(), 1.0F);

        blitDesignTexture(graphics, SHOP_MAIN);
        blitDesignTexture(graphics, CHARACTER_BACKGROUND);
        blitDesignTexture(graphics, characterTexture(complete, nowNanos));

        Font font = Minecraft.getInstance().font;
        drawSpeakerName(graphics, font, snapshot.speakerName());
        drawDialogueText(graphics, font, snapshot, visibleCharacters);

        if (complete && snapshot.hasOptionInput()) {
            drawOptions(graphics, font, snapshot.options(), layout.optionStartY(), localMouseX, localMouseY, inputLocked);
        } else if (complete && snapshot.hasContinueInput() && !inputLocked) {
            drawContinueIndicator(graphics, nowNanos);
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
        if (snapshot.theme() == Theme.HAND) {
            return HandDialogueRenderer.interactionAt(
                snapshot,
                screenWidth,
                screenHeight,
                mouseX,
                mouseY,
                nowNanos,
                pageStartedNanos,
                catalogSelection
            );
        }

        int option = optionAt(snapshot, screenWidth, screenHeight, mouseX, mouseY);
        return option < 0 ? Interaction.none() : Interaction.option(option);
    }

    public static boolean isOpeningComplete(Snapshot snapshot, long nowNanos, long screenStartedNanos) {
        return snapshot.theme() != Theme.HAND
            || nowNanos - screenStartedNanos >= HandDialogueRenderer.OPENING_DURATION_NANOS;
    }

    /** Hold the typewriter until the HAND open animation finishes on the first page. */
    public static long typewriterStartNanos(
        Snapshot snapshot,
        long pageStartedNanos,
        long screenStartedNanos
    ) {
        // The 50ms window is how we tell "opened straight onto this page" from a
        // later navigation back to it, where the animation has long since played.
        if (snapshot.theme() == Theme.HAND
            && "main".equals(snapshot.pageId())
            && pageStartedNanos - screenStartedNanos < 50_000_000L) {
            return screenStartedNanos + HandDialogueRenderer.OPENING_DURATION_NANOS;
        }
        return pageStartedNanos;
    }

    public static int optionAt(
        Snapshot snapshot,
        int screenWidth,
        int screenHeight,
        double mouseX,
        double mouseY
    ) {
        Layout layout = layout(screenWidth, screenHeight, snapshot.options().size());
        int localX = layout.toLocalX(mouseX);
        int localY = layout.toLocalY(mouseY);
        if (localX < BUTTON_X || localX >= BUTTON_X + BUTTON_WIDTH) {
            return -1;
        }

        for (int index = 0; index < snapshot.options().size(); index++) {
            int y = layout.optionStartY() + index * (BUTTON_HEIGHT + BUTTON_GAP);
            if (localY >= y && localY < y + BUTTON_HEIGHT) {
                return index;
            }
        }
        return -1;
    }

    private static void drawSpeakerName(GuiGraphics graphics, Font font, String speakerName) {
        if (speakerName.isBlank()) {
            return;
        }

        graphics.fill(45, 3, 131, 12, 0xFFD0CED6);
        String clipped = font.plainSubstrByWidth(speakerName, 80);
        graphics.drawCenteredString(font, clipped, 88, 3, 0xFF393246);
    }

    private static void drawDialogueText(
        GuiGraphics graphics,
        Font font,
        Snapshot snapshot,
        int visibleCharacters
    ) {
        String visibleText = prefix(snapshot.text(), visibleCharacters);
        // buttons eat most of the panel, so the text only gets 3 lines when options are up
        int maxLines = snapshot.hasOptionInput() ? 3 : 11;
        List<FormattedCharSequence> lines = font.split(Component.literal(visibleText), 106);
        int y = 20;
        for (int index = 0; index < Math.min(lines.size(), maxLines); index++) {
            graphics.drawString(font, lines.get(index), 10, y + index * 10, 0xFFFFFFFF, true);
        }
    }

    private static void drawOptions(
        GuiGraphics graphics,
        Font font,
        List<Option> options,
        int startY,
        int mouseX,
        int mouseY,
        boolean inputLocked
    ) {
        for (int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            int y = startY + index * (BUTTON_HEIGHT + BUTTON_GAP);
            boolean hovered = !inputLocked && option.selectable()
                && mouseX >= BUTTON_X && mouseX < BUTTON_X + BUTTON_WIDTH
                && mouseY >= y && mouseY < y + BUTTON_HEIGHT;

            ResourceLocation texture = buttonTexture(index, hovered);
            // button sheets are 110x14, not 256x256 like the panel layers
            graphics.blit(texture, BUTTON_X, y, 0.0F, 0.0F, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT);

            int faceColor;
            if (!option.selectable() || inputLocked) {
                faceColor = 0xFF4B4754;
            } else {
                faceColor = hovered ? 0xFF393246 : 0xFF6B6773;
            }
            graphics.fill(BUTTON_X + 2, y + 2, BUTTON_X + BUTTON_WIDTH - 2, y + BUTTON_HEIGHT - 2, faceColor);

            String label = font.plainSubstrByWidth(option.text().getString(), BUTTON_WIDTH - 8);
            int textColor = option.selectable() && !inputLocked ? 0xFFFFFFFF : 0xFFAAA6B0;
            graphics.drawCenteredString(font, label, BUTTON_X + BUTTON_WIDTH / 2, y + 3, textColor);
        }
    }

    // 4px down-arrow built from four fills, bobbing 1px on a 250ms beat.
    private static void drawContinueIndicator(GuiGraphics graphics, long nowNanos) {
        int pulse = (int) ((nowNanos / 250_000_000L) & 1L);
        int x = 111;
        int y = 131 + pulse;
        graphics.fill(x, y, x + 7, y + 1, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 6, y + 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + 5, y + 3, 0xFFFFFFFF);
        graphics.fill(x + 3, y + 3, x + 4, y + 4, 0xFFFFFFFF);
    }

    private static ResourceLocation characterTexture(boolean complete, long nowNanos) {
        if (complete) {
            return CHARACTER_SMILING;
        }
        // flip between the two mouth frames every 140ms while the line is still typing
        return ((nowNanos / 140_000_000L) & 1L) == 0L ? CHARACTER_TALKING : CHARACTER_CASUAL;
    }

    private static ResourceLocation buttonTexture(int index, boolean hovered) {
        if ((index & 1) == 0) {
            return hovered ? BUTTON_ONE_HOVER : BUTTON_ONE;
        }
        return hovered ? BUTTON_TWO_HOVER : BUTTON_TWO;
    }

    private static Layout layout(int screenWidth, int screenHeight, int optionCount) {
        // integer scale only, capped at 4x
        int scale = Math.max(1, Math.min(4, Math.min(screenWidth / DESIGN_WIDTH, screenHeight / DESIGN_HEIGHT)));
        int originX = (screenWidth - DESIGN_WIDTH * scale) / 2;
        int originY = (screenHeight - DESIGN_HEIGHT * scale) / 2;
        // more options push the stack higher; 38 is as far up as it goes
        int optionStartY = Math.max(38, 88 - optionCount * 10);
        return new Layout(originX, originY, scale, optionStartY);
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

    // Full sheet at 0,0. The layers are drawn pre-aligned so no per-layer offsets are needed.
    private static void blitDesignTexture(GuiGraphics graphics, ResourceLocation texture) {
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, 256, 256, 256, 256);
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/shop/" + fileName);
    }

    private record Layout(int originX, int originY, int scale, int optionStartY) {
        int toLocalX(double screenX) {
            return (int) Math.floor((screenX - originX) / scale);
        }

        int toLocalY(double screenY) {
            return (int) Math.floor((screenY - originY) / scale);
        }
    }

    public enum InteractionType {
        NONE,
        OPTION,
        SELECT_PRODUCT
    }

    public record Interaction(InteractionType type, int index) {
        public static Interaction none() {
            return new Interaction(InteractionType.NONE, -1);
        }

        public static Interaction option(int index) {
            return new Interaction(InteractionType.OPTION, index);
        }

        public static Interaction selectProduct(int index) {
            return new Interaction(InteractionType.SELECT_PRODUCT, index);
        }
    }
}
