package com.cobblemondimensions.stories.client.quest;

import com.cobblemondimensions.stories.client.ClientQuestStore;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestObjective;
import com.cobblemondimensions.stories.quest.QuestProgress;
import com.cobblemondimensions.stories.quest.QuestReward;
import com.cobblemondimensions.stories.quest.QuestStatus;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Quest journal screen.
 *
 * <p>The art is authored on a 256x256 canvas. Everything below draws in that
 * design space and gets scaled by an integer factor, so nothing lands on a
 * half pixel.
 */
public final class QuestScreen extends Screen {
    private static final int DESIGN = 256;

    private static final int FRAME_CENTER_X = 128;
    private static final int FRAME_CENTER_Y = 76;

    // Visible frame bounds inside the canvas. The art doesn't fill all 256x256,
    // so scale() and origin*() work off these numbers, not the canvas size.
    private static final int CONTENT_X = 40;
    private static final int CONTENT_Y = 7;
    private static final int CONTENT_W = 176;
    private static final int CONTENT_H = 137;
    private static final int SCREEN_MARGIN = 40;

    private static final int ROW_X = 51;
    private static final int ROW_Y = 18;
    private static final int ROW_W = 72;
    private static final int ROW_H = 11;
    private static final int ROW_PITCH = 13;
    private static final int ROWS_PER_PAGE = 8;

    private static final int ICON_DX = 2;
    private static final int ICON_DY = 2;
    private static final int ICON_SIZE = 7;

    private static final int PAGE_Y = 125;
    private static final int PAGE_W = 33;
    private static final int PAGE_H = 8;
    private static final int PREV_X = 52;
    private static final int NEXT_X = 89;

    private static final int TITLE_X = 131;
    private static final int TITLE_Y = 16;
    private static final int TITLE_W = 76;
    private static final int TITLE_H = 12;

    private static final int DESC_X = 131;
    private static final int DESC_Y = 31;
    private static final int DESC_W = 76;
    private static final int DESC_H = 80;

    private static final int REWARD_X = 131;
    private static final int REWARD_Y = 115;
    private static final int REWARD_W = 76;
    private static final int REWARD_H = 8;

    private static final int STATUS_X = 174;
    private static final int STATUS_Y = 127;
    private static final int STATUS_W = 33;

    // reserved strip at the bottom of the description box for the giver hint
    private static final int HINT_H = 10;

    private static final int TEXT_DARK = 0xFF241C28;
    private static final int TEXT_LIGHT = 0xFFE9E9E9;
    private static final int TEXT_MUTED = 0xFF5C5C5C;

    private static final long OPEN_NANOS = 260_000_000L;
    private static final long ROW_STAGGER_NANOS = 26_000_000L;
    private static final double DESCRIPTION_CHARACTERS_PER_SECOND = 90.0D;

    private static final ResourceLocation FRAME = texture("quest_frame.png");
    private static final ResourceLocation BACKGROUND = texture("quest_background.png");
    private static final ResourceLocation LIST_BACKGROUND = texture("quest_list_background.png");
    private static final ResourceLocation ROWS = texture("quest_row_disabled.png");
    private static final ResourceLocation ROW = texture("quest_row.png");
    private static final ResourceLocation PAGE_BUTTONS = texture("quest_page_buttons.png");
    private static final ResourceLocation TITLE_BACKGROUND = texture("quest_title_background.png");
    private static final ResourceLocation DESCRIPTION_BACKGROUND = texture("quest_description_background.png");
    private static final ResourceLocation REWARD_BACKGROUND = texture("quest_reward_background.png");
    private static final ResourceLocation BUTTON = texture("quest_button.png");
    private static final ResourceLocation BUTTON_ACTIVE = texture("quest_button_active.png");
    private static final ResourceLocation BUTTON_DISABLED = texture("quest_button_disabled.png");

    private final long openedNanos = System.nanoTime();

    private int page;
    private int selected;
    private long selectionChangedNanos = System.nanoTime();
    private long lastSyncSeenNanos = ClientQuestStore.changedNanos();

    public QuestScreen() {
        super(Component.translatable("screen.cobblemon_dimensions_stories.quests"));
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/quest/" + fileName);
    }

    /** Everything except LOCKED quests. Those stay hidden until unlocked. */
    private List<Quest> visibleQuests() {
        return ClientQuestStore.quests().stream()
            .filter(quest -> ClientQuestStore.statusOf(quest.id()) != QuestStatus.LOCKED)
            .toList();
    }

    private Quest selectedQuest() {
        List<Quest> quests = visibleQuests();
        return quests.isEmpty() ? null : quests.get(Math.min(selected, quests.size() - 1));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();

        // A sync can shrink the list while the screen is open. Re-clamp the
        // selection and page before anything indexes into it.
        if (ClientQuestStore.changedNanos() != lastSyncSeenNanos) {
            lastSyncSeenNanos = ClientQuestStore.changedNanos();
            clampSelection();
        }

        graphics.fill(0, 0, width, height, 0xA0000000);

        float open = openProgress(now);
        int scale = scale();
        int originX = originX(scale);
        int originY = originY(scale);

        graphics.pose().pushPose();
        graphics.pose().translate(originX, originY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);

        // translate to the frame centre, scale, translate back. Scaling straight
        // off the canvas origin makes the panel grow out of the top-left corner.
        float pop = 0.90F + 0.10F * overshoot(open);
        graphics.pose().translate(FRAME_CENTER_X, FRAME_CENTER_Y, 0.0F);
        graphics.pose().scale(pop, pop, 1.0F);
        graphics.pose().translate(-FRAME_CENTER_X, -FRAME_CENTER_Y, 0.0F);

        // every sheet is 256x256 blitted at 0,0, so order here is the layer order
        graphics.setColor(1.0F, 1.0F, 1.0F, open);
        blit(graphics, FRAME);
        blit(graphics, BACKGROUND);
        blit(graphics, LIST_BACKGROUND);
        blit(graphics, ROWS);
        blit(graphics, TITLE_BACKGROUND);
        blit(graphics, DESCRIPTION_BACKGROUND);
        blit(graphics, REWARD_BACKGROUND);
        blit(graphics, PAGE_BUTTONS);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        int localX = (int) Math.floor((mouseX - originX) / (double) scale);
        int localY = (int) Math.floor((mouseY - originY) / (double) scale);

        drawRows(graphics, now, open, localX, localY);
        drawDetails(graphics, now, open);
        drawPageLabels(graphics, open);

        graphics.pose().popPose();
    }

    private void drawRows(GuiGraphics graphics, long now, float open, int localX, int localY) {
        List<Quest> quests = visibleQuests();
        int first = page * ROWS_PER_PAGE;

        for (int slot = 0; slot < ROWS_PER_PAGE; slot++) {
            int index = first + slot;
            if (index >= quests.size()) {
                break;
            }
            // staggered per slot, rows cascade in rather than popping together
            float reveal = revealProgress(now, slot);
            if (reveal <= 0.0F) {
                continue;
            }

            Quest quest = quests.get(index);
            QuestStatus status = ClientQuestStore.statusOf(quest.id());
            int y = ROW_Y + slot * ROW_PITCH;
            boolean hovered = inside(localX, localY, ROW_X, y, ROW_W, ROW_H);
            boolean chosen = index == selected;

            float slide = (1.0F - reveal) * 6.0F;
            graphics.pose().pushPose();
            graphics.pose().translate(-slide, 0.0F, 0.0F);

            if (chosen || hovered) {
                float glow = chosen ? 1.0F : 0.72F;
                graphics.setColor(glow, glow, glow, open * reveal);
                // ROW.png only has the highlight drawn at the first slot's y.
                // Translate down by the slot offset to reuse it for every row.
                graphics.pose().pushPose();
                graphics.pose().translate(0.0F, (float) (y - ROW_Y), 0.0F);
                blit(graphics, ROW);
                graphics.pose().popPose();
                graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            }

            drawStatusIcon(graphics, ROW_X + ICON_DX, y + ICON_DY, status, open * reveal, now);

            int textColor = chosen || hovered ? TEXT_DARK : TEXT_LIGHT;
            if (status == QuestStatus.COMPLETED && !chosen && !hovered) {
                textColor = TEXT_MUTED;
            }
            drawFitted(graphics, quest.title().getString(),
                ROW_X + ICON_DX + ICON_SIZE + 3, y + 2, ROW_W - ICON_SIZE - 8, textColor);

            graphics.pose().popPose();
            ClientQuestStore.markSeen(quest.id());
        }
    }

    /** 7x7 colour swatch in the row's icon slot. ACTIVE pulses, AVAILABLE draws inset by 1. */
    private void drawStatusIcon(GuiGraphics graphics, int x, int y, QuestStatus status, float alpha, long now) {
        int color = switch (status) {
            case ACTIVE -> 0xFFE0B24A;
            case COMPLETED -> 0xFF6FBF5B;
            default -> 0xFFB9B9C4;
        };
        int inset = status == QuestStatus.AVAILABLE ? 1 : 0;
        if (status == QuestStatus.ACTIVE) {
            float pulse = 0.65F + 0.35F * (float) Math.sin(now / 260_000_000.0D);
            alpha *= pulse;
        }
        int shaded = (color & 0x00FFFFFF) | (Math.round(255 * Math.min(1.0F, alpha)) << 24);
        graphics.fill(x + inset, y + inset, x + ICON_SIZE - inset, y + ICON_SIZE - inset, shaded);
    }

    private void drawDetails(GuiGraphics graphics, long now, float open) {
        Quest quest = selectedQuest();
        if (quest == null || open < 0.6F) {
            return;
        }

        drawFitted(graphics, quest.title().getString(), TITLE_X + 4, TITLE_Y + 3, TITLE_W - 8, TEXT_DARK);

        QuestStatus status = ClientQuestStore.statusOf(quest.id());

        // typewriter runs off selectionChangedNanos, so picking a new quest restarts it
        String description = quest.descriptionText();
        String step = stepLine(quest, status);
        if (!step.isEmpty()) {
            description = description + "\n\n" + step;
        }
        double elapsed = Math.max(0L, now - selectionChangedNanos) / 1_000_000_000.0D;
        int visible = Math.min(description.length(), (int) (elapsed * DESCRIPTION_CHARACTERS_PER_SECOND));
        drawWrapped(graphics, description.substring(0, visible),
            DESC_X + 4, DESC_Y + 4, DESC_W - 8, DESC_H - 8 - HINT_H);

        String hint = giverHint(quest, status);
        if (!hint.isEmpty()) {
            drawFitted(graphics, hint, DESC_X + 4, DESC_Y + DESC_H - HINT_H, DESC_W - 8, TEXT_MUTED);
        }

        drawFitted(graphics, rewardLine(quest), REWARD_X + 4, REWARD_Y + 2, REWARD_W - 8, TEXT_DARK);

        // The plate is display only. Accepting and turning in both happen through
        // NPC dialogue, there is nothing to click here.
        boolean live = status == QuestStatus.AVAILABLE || status == QuestStatus.ACTIVE;
        blit(graphics, live ? BUTTON : BUTTON_DISABLED);
        if (status == QuestStatus.AVAILABLE) {
            float flash = 0.25F + 0.25F * (float) Math.sin(now / 320_000_000.0D);
            graphics.setColor(1.0F, 1.0F, 1.0F, flash);
            blit(graphics, BUTTON_ACTIVE);
            graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        drawFittedCentered(graphics, statusLabel(status), STATUS_X, STATUS_Y + 2, STATUS_W,
            live ? TEXT_DARK : TEXT_MUTED);
    }

    private void drawPageLabels(GuiGraphics graphics, float open) {
        if (open < 0.6F) {
            return;
        }
        int pages = pageCount();
        drawFittedCentered(graphics, "<", PREV_X, PAGE_Y + 2, PAGE_W, page > 0 ? TEXT_DARK : TEXT_MUTED);
        drawFittedCentered(graphics, ">", NEXT_X, PAGE_Y + 2, PAGE_W, page < pages - 1 ? TEXT_DARK : TEXT_MUTED);
    }

    private String rewardLine(Quest quest) {
        if (quest.rewards().isEmpty()) {
            return "No rewards";
        }
        return String.join(", ", quest.rewards().stream().map(QuestReward::displayName)
            .map(Component::getString).toList());
    }

    private static String statusLabel(QuestStatus status) {
        return switch (status) {
            case AVAILABLE -> "New";
            case ACTIVE -> "Active";
            case COMPLETED -> "Done";
            case LOCKED -> "Locked";
        };
    }

    /**
     * Current stage line for an ACTIVE quest, e.g. "Step 2/3: Find the shard (1/5)".
     * Empty for any other status.
     */
    private static String stepLine(Quest quest, QuestStatus status) {
        if (status != QuestStatus.ACTIVE) {
            return "";
        }
        QuestProgress progress = ClientQuestStore.progressOf(quest.id());
        QuestObjective objective = quest.stage(progress.stage());

        StringBuilder line = new StringBuilder();
        if (quest.stageCount() > 1) {
            line.append("Step ").append(progress.stage() + 1).append('/').append(quest.stageCount());
        }
        objective.hint().map(Component::getString).ifPresent(hint -> {
            if (!line.isEmpty()) {
                line.append(": ");
            }
            line.append(hint);
        });
        if (objective.tracksCounter()) {
            line.append(line.isEmpty() ? "" : " ")
                .append('(').append(progress.count()).append('/').append(objective.required()).append(')');
        }
        return line.toString();
    }

    /** One-line pointer to the giver NPC. Empty once the quest is done or still locked. */
    private static String giverHint(Quest quest, QuestStatus status) {
        if (status == QuestStatus.COMPLETED || status == QuestStatus.LOCKED) {
            return "";
        }
        String giver = ClientQuestStore.giverOf(quest.id()).map(Component::getString).orElse("");
        if (giver.isEmpty()) {
            return status == QuestStatus.AVAILABLE ? "Find the quest giver" : "";
        }
        if (status == QuestStatus.AVAILABLE) {
            return "Talk to " + giver + " to start";
        }
        return quest.stage(ClientQuestStore.progressOf(quest.id()).stage()).isTurnIn()
            ? "Bring it back to " + giver
            : "Return to " + giver;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int scale = scale();
        int localX = (int) Math.floor((mouseX - originX(scale)) / (double) scale);
        int localY = (int) Math.floor((mouseY - originY(scale)) / (double) scale);

        List<Quest> quests = visibleQuests();
        for (int slot = 0; slot < ROWS_PER_PAGE; slot++) {
            int index = page * ROWS_PER_PAGE + slot;
            if (index >= quests.size()) {
                break;
            }
            if (inside(localX, localY, ROW_X, ROW_Y + slot * ROW_PITCH, ROW_W, ROW_H)) {
                select(index);
                return true;
            }
        }

        if (inside(localX, localY, PREV_X, PAGE_Y, PAGE_W, PAGE_H) && page > 0) {
            page--;
            playClick();
            return true;
        }
        if (inside(localX, localY, NEXT_X, PAGE_Y, PAGE_W, PAGE_H) && page < pageCount() - 1) {
            page++;
            playClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<Quest> quests = visibleQuests();
        if (quests.isEmpty()) {
            return false;
        }
        select(Math.floorMod(selected + (scrollY > 0 ? -1 : 1), quests.size()));
        page = selected / ROWS_PER_PAGE;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        List<Quest> quests = visibleQuests();
        if (!quests.isEmpty() && (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            select(Math.floorMod(selected + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1), quests.size()));
            page = selected / ROWS_PER_PAGE;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void select(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        selectionChangedNanos = System.nanoTime();
        playClick();
    }

    private void clampSelection() {
        int count = visibleQuests().size();
        selected = count == 0 ? 0 : Math.min(selected, count - 1);
        page = Math.min(page, Math.max(0, pageCount() - 1));
    }

    private int pageCount() {
        return Math.max(1, (visibleQuests().size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    private void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager()
                .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private float openProgress(long now) {
        long age = now - openedNanos;
        return age >= OPEN_NANOS ? 1.0F : Math.max(0.0F, age / (float) OPEN_NANOS);
    }

    private float revealProgress(long now, int slot) {
        long age = now - openedNanos - slot * ROW_STAGGER_NANOS;
        return age <= 0 ? 0.0F : Math.min(1.0F, age / (float) OPEN_NANOS);
    }

    // ease-out that goes slightly past 1 before settling, gives the panel a bounce
    private static float overshoot(float t) {
        float inverted = 1.0F - t;
        return 1.0F + 2.2F * inverted * inverted * inverted - 1.9F * inverted * inverted;
    }

    private int scale() {
        return Math.max(1, Math.min(5,
            Math.min((width - SCREEN_MARGIN) / CONTENT_W, (height - SCREEN_MARGIN) / CONTENT_H)));
    }

    private int originX(int scale) {
        return (width - CONTENT_W * scale) / 2 - CONTENT_X * scale;
    }

    private int originY(int scale) {
        return (height - CONTENT_H * scale) / 2 - CONTENT_Y * scale;
    }

    private static boolean inside(int x, int y, int left, int top, int w, int h) {
        return x >= left && x < left + w && y >= top && y < top + h;
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture) {
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);
    }

    private void drawFitted(GuiGraphics graphics, String text, int x, int y, int maxWidth, int color) {
        Font font = this.font;
        // shrink until it fits, bottoming out at 0.34 rather than truncating
        float textScale = 0.6F;
        while (textScale > 0.34F && font.width(text) * textScale > maxWidth) {
            textScale -= 0.02F;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(textScale, textScale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawFittedCentered(GuiGraphics graphics, String text, int x, int y, int boxWidth, int color) {
        float textScale = 0.6F;
        while (textScale > 0.34F && font.width(text) * textScale > boxWidth - 4) {
            textScale -= 0.02F;
        }
        int left = x + Math.round((boxWidth - font.width(text) * textScale) / 2.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(left, y, 0.0F);
        graphics.pose().scale(textScale, textScale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int maxHeight) {
        if (text.isEmpty()) {
            return;
        }
        float textScale = 0.55F;
        int lineHeight = Math.max(4, Math.round(font.lineHeight * textScale));
        List<net.minecraft.util.FormattedCharSequence> lines =
            font.split(Component.literal(text), Math.round(maxWidth / textScale));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(textScale, textScale, 1.0F);
        for (int index = 0; index < lines.size() && (index + 1) * lineHeight <= maxHeight; index++) {
            graphics.drawString(font, lines.get(index), 0, Math.round(index * font.lineHeight), TEXT_DARK, false);
        }
        graphics.pose().popPose();
    }
}
