package com.cobblemondimensions.stories.client.quest;

import com.cobblemondimensions.stories.client.ClientQuestStore;
import com.cobblemondimensions.stories.client.walking.WalkingDialogueSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Top-left journal badge. Pulses when there are unseen or active quests. */
public final class QuestHudIcon {
    private static final int SIZE = 16;
    private static final int MARGIN = 6;

    private static final ResourceLocation ICON =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/quest/quest_icon.png");
    private static final ResourceLocation ICON_BRIGHT =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/quest/quest_icon_bright.png");

    private QuestHudIcon() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || ClientQuestStore.isEmpty()) {
            return;
        }
        if (WalkingDialogueSession.isActive() || minecraft.getDebugOverlay().showDebugScreen()) {
            return;
        }

        graphics.blit(ICON, MARGIN, MARGIN, 0.0F, 0.0F, SIZE, SIZE, 256, 256);

        boolean unseen = ClientQuestStore.hasUnseenQuests();
        if (!unseen && !ClientQuestStore.hasActiveQuests()) {
            return;
        }
        // unseen = fast and full brightness, active = slow and dim
        float speed = unseen ? 320_000_000.0F : 900_000_000.0F;
        float peak = unseen ? 1.0F : 0.45F;
        float pulse = peak * (0.5F + 0.5F * (float) Math.sin(System.nanoTime() / speed));
        graphics.setColor(1.0F, 1.0F, 1.0F, pulse);
        graphics.blit(ICON_BRIGHT, MARGIN, MARGIN, 0.0F, 0.0F, SIZE, SIZE, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
