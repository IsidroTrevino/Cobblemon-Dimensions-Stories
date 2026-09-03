package com.cobblemondimensions.stories.client.minigame;

import com.cobblemondimensions.stories.minigame.PokedexSource;
import com.cobblemondimensions.stories.minigame.PokewrleHint;
import com.cobblemondimensions.stories.net.PokewrleFeedbackS2C;
import com.cobblemondimensions.stories.net.PokewrleGuessC2S;
import com.cobblemondimensions.stories.net.PokewrleSetupC2S;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Wordle-style species guessing board. Every guess reports back on type 1, type 2,
 * egg group and evolution stage.
 *
 * <p>All coordinates are in the 256x256 design space the art was drawn in. Each layer is
 * just its own sheet blitted at (0,0), so nothing needs aligning by hand. The _right and
 * _wrong sheets are pixel-identical recolours of the plain one, which is what lets a single
 * box be repainted by blitting only that box's rect out of the variant.
 */
public final class PokewrleScreen extends Screen {
    private static final int DESIGN = 256;

    // frame rect inside the sheet, used to centre the board
    private static final int PANEL_W = 252;
    private static final int PANEL_H = 138;
    private static final int PANEL_CENTER_X = 126;
    private static final int PANEL_CENTER_Y = 75;

    // input, tries and gen wells all live on this one row
    private static final int ROW_Y = 27;
    private static final int ROW_H = 8;
    private static final int ROW_CAPTION_Y = 22;

    private static final int INPUT_X = 12;
    private static final int INPUT_W = 96;

    /** Half scale keeps typed names inside the 8px entry well. */
    private static final float ENTRY_SCALE = 0.5F;

    private static final int TRIES_X = 118;
    private static final int TRIES_W = 22;

    private static final int GEN_X = 150;
    private static final int GEN_W = 14;
    private static final int GEN_FRAME_X0 = 147;
    private static final int GEN_FRAME_Y0 = 18;
    private static final int GEN_FRAME_X1 = 167;
    private static final int GEN_FRAME_Y1 = 38;

    private static final int SHELF_X = 11;
    private static final int SHELF_Y = 48;
    private static final int SHELF_W = 117;
    private static final int SHELF_BOTTOM = 57;
    private static final int SHELF_CAPTION_Y = 44;

    // the four hint boxes as x, y, w, h
    private static final int[][] BOX = {
        {12, 77, 29, 19}, {43, 77, 35, 19}, {80, 77, 35, 19}, {117, 77, 29, 19}
    };
    private static final int BOX_CAPTION_Y = 71;

    private static final int PLATFORM_CENTER_X = 208;
    private static final int PLATFORM_TOP = 99;
    private static final int STAGE_CENTER_X = 205;
    private static final int STAGE_FLOOR = 101;
    private static final float QUESTION_PIVOT_Y = 60.0F;

    private static final PokewrleHint.Category[] BOX_CATEGORIES = {
        PokewrleHint.Category.TYPE_1,
        PokewrleHint.Category.TYPE_2,
        PokewrleHint.Category.EGG_GROUP,
        PokewrleHint.Category.STAGE
    };

    private static final ResourceLocation CORNERS = texture("corners.png");
    private static final ResourceLocation BACKGROUND = texture("background.png");
    private static final ResourceLocation INPUT = texture("input.png");
    private static final ResourceLocation TRIES = texture("tries.png");
    private static final ResourceLocation GEN = texture("gen.png");
    private static final ResourceLocation GEN_RIGHT = texture("gen_right.png");
    private static final ResourceLocation GEN_WRONG = texture("gen_wrong.png");
    private static final ResourceLocation SHELF = texture("guessed.png");
    private static final ResourceLocation CRITERIA = texture("criteria.png");
    private static final ResourceLocation CRITERIA_RIGHT = texture("criteria_right.png");
    private static final ResourceLocation CRITERIA_WRONG = texture("criteria_wrong.png");
    private static final ResourceLocation PLATFORM = texture("platform.png");
    private static final ResourceLocation QUESTION = texture("question.png");

    private static final int COLOR_WELL_TEXT = 0xFF241C05;
    private static final int COLOR_STATE_TEXT = 0xFFFFF4D0;
    private static final int COLOR_CAPTION = 0xFFFFE066;
    private static final int COLOR_SHADOW = 0xFF101024;
    private static final int COLOR_GHOST = 0xFF8A7A2C;
    private static final int COLOR_POPUP = 0xE6101B3C;
    private static final int COLOR_POPUP_PICKED = 0xFF2F6AB6;
    private static final int COLOR_GOLD = 0xFFFFCB05;
    // gen well is gold already, hover has to go white to read against it
    private static final int COLOR_HOVER_TEXT = 0xFFFFFFFF;
    private static final int COLOR_GREEN = 0xFF149217;
    private static final int COLOR_RED = 0xFF8F1414;

    private static final long OPEN_NANOS = 340_000_000L;
    private static final long FLIP_NANOS = 240_000_000L;
    private static final long FLIP_STAGGER_NANOS = 130_000_000L;
    private static final long SHAKE_NANOS = 340_000_000L;
    private static final long LAND_NANOS = 320_000_000L;
    private static final long RISE_NANOS = 900_000_000L;
    private static final long DIAL_NANOS = 260_000_000L;

    private static final int SUGGESTION_LIMIT = 5;
    private static final int SUGGESTION_H = 9;
    private static final int NAME_LIMIT = 24;
    private static final int GENERATIONS = 9;

    /** Only one board can be open at a time, so feedback packets find it here. */
    private static PokewrleScreen open;

    private final int best;
    private final int attempts;

    private final StringBuilder typed = new StringBuilder();
    private final List<Guess> guesses = new ArrayList<>();
    private List<PokedexSource.Entry> suggestions = List.of();
    private int picked;

    private int generation;
    private int attemptsLeft;
    private Phase phase = Phase.PLAYING;
    private boolean won;
    private Optional<ResourceLocation> answer = Optional.empty();
    private String answerName = "";

    private long openedNanos;
    private long flippedNanos = Long.MIN_VALUE;
    private long shakenNanos = Long.MIN_VALUE;
    private long dialledNanos = Long.MIN_VALUE;
    private long revealedNanos = Long.MIN_VALUE;

    // hints the boxes are showing: newest guess, unless an older one is hovered
    private List<PokewrleHint> latest = List.of();
    private int reviewing = -1;

    private float panelScale = 1.0F;
    private float originX;
    private float originY;

    public PokewrleScreen(int best, int attempts, int generation) {
        super(Component.translatable("screen.cobblemon_dimensions_stories.pokewrle"));
        this.best = best;
        this.attempts = Math.max(1, attempts);
        this.generation = generation;
        this.attemptsLeft = this.attempts;
    }

    /** Hands a feedback packet to the open board. No-op if the player closed it. */
    public static void accept(PokewrleFeedbackS2C feedback) {
        if (open != null) {
            open.onFeedback(feedback);
        }
    }

    /** Clears the round once the server has re-dealt for a new generation. */
    public void restart(int generation) {
        this.generation = generation;
        attemptsLeft = attempts;
        phase = Phase.PLAYING;
        won = false;
        guesses.clear();
        latest = List.of();
        reviewing = -1;
        answer = Optional.empty();
        answerName = "";
        typed.setLength(0);
        suggestions = List.of();
        picked = 0;
        flippedNanos = Long.MIN_VALUE;
        revealedNanos = Long.MIN_VALUE;
        dialledNanos = System.nanoTime();
        MinigameSounds.dial(generation);
    }

    @Override
    protected void init() {
        open = this;
        openedNanos = System.nanoTime();
        dialledNanos = openedNanos;
    }

    @Override
    public void removed() {
        if (open == this) {
            open = null;
        }
        MinigameSounds.reset();
    }

    // the board runs on real time, keep the game ticking under it
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ----------------------------------------------------------------- render

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        MinigameSounds.tickQueue(now);
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // Biggest integer scale that keeps the frame inside 90% of the width and 80%
        // of the height. Fractional scales give uneven pixel sizes.
        int scale = Mth.clamp(
            Math.min(graphics.guiWidth() * 9 / 10 / PANEL_W, graphics.guiHeight() * 8 / 10 / PANEL_H),
            1, 4);
        panelScale = scale;
        originX = graphics.guiWidth() / 2.0F - scale * PANEL_CENTER_X;
        originY = graphics.guiHeight() / 2.0F - scale * PANEL_CENTER_Y;

        float grow = overshoot(Mth.clamp((now - openedNanos) / (float) OPEN_NANOS, 0.0F, 1.0F));
        float shake = shakeAmount(now);
        int designX = Math.round((mouseX - originX) / scale);
        int designY = Math.round((mouseY - originY) / scale);
        trackHover(designX, designY);

        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2.0F, graphics.guiHeight() / 2.0F, 0.0F);
        graphics.pose().scale(scale * grow, scale * grow, 1.0F);
        graphics.pose().translate(-PANEL_CENTER_X + shake, -PANEL_CENTER_Y, 0.0F);

        sheet(graphics, BACKGROUND, 1.0F);
        drawStageGlow(graphics, now);
        sheet(graphics, CORNERS, 1.0F);

        drawInput(graphics, now);
        drawTries(graphics, now);
        drawDial(graphics, now, designX, designY);
        drawShelf(graphics, now);
        drawBoxes(graphics, now);
        drawStage(graphics, now);
        drawResult(graphics, now);
        drawSuggestions(graphics);

        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Re-blits the background at low alpha to pulse the burst behind the platform. */
    private void drawStageGlow(GuiGraphics graphics, long now) {
        float pulse = 0.12F + 0.10F * (float) Math.sin(now / 5.2E8D);
        if (phase == Phase.REVEAL) {
            pulse += 0.35F * (1.0F - rise(now));
        }
        sheet(graphics, BACKGROUND, pulse);
    }

    private void drawInput(GuiGraphics graphics, long now) {
        sheet(graphics, INPUT, 1.0F);
        caption(graphics, "WHO IS THAT POKEMON?", INPUT_X + INPUT_W / 2, ROW_CAPTION_Y, INPUT_W);

        if (phase == Phase.REVEAL) {
            fitted(graphics, "ROUND OVER", INPUT_X + INPUT_W / 2, ROW_Y + ROW_H / 2,
                INPUT_W - 4, 1.0F, COLOR_GHOST, 1.0F);
            return;
        }

        String text = typed.toString();
        // The well is 8px deep. At full scale the descenders hang out the bottom
        // of the frame, so the entry draws at half scale.
        int textY = Math.round((ROW_Y + ROW_H / 2.0F - font.lineHeight * ENTRY_SCALE / 2.0F)
            / ENTRY_SCALE);
        graphics.pose().pushPose();
        graphics.pose().scale(ENTRY_SCALE, ENTRY_SCALE, 1.0F);
        graphics.drawString(font, text.isEmpty() ? "type a name..." : text,
            Math.round((INPUT_X + 2) / ENTRY_SCALE), textY,
            text.isEmpty() ? COLOR_GHOST : COLOR_WELL_TEXT, false);
        graphics.pose().popPose();

        // caret trails the text, 450ms on 450ms off
        if ((now - openedNanos) / 450_000_000L % 2 == 0) {
            int caretX = INPUT_X + 2 + Math.round(font.width(text) * ENTRY_SCALE);
            graphics.fill(caretX, ROW_Y + 2, caretX + 1, ROW_Y + ROW_H - 2, COLOR_WELL_TEXT);
        }
    }

    private void drawTries(GuiGraphics graphics, long now) {
        sheet(graphics, TRIES, 1.0F);
        caption(graphics, "TRIES", TRIES_X + TRIES_W / 2, ROW_CAPTION_Y, TRIES_W);

        // count reddens as tries run out, and pulses on the last one
        float pressure = 1.0F - attemptsLeft / (float) attempts;
        float beat = attemptsLeft <= 1 && phase == Phase.PLAYING
            ? 1.0F + 0.12F * (float) Math.abs(Math.sin(now / 2.2E8D))
            : 1.0F;

        float centerX = TRIES_X + TRIES_W / 2.0F;
        float centerY = ROW_Y + ROW_H / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(beat, beat, 1.0F);
        graphics.pose().translate(-centerX, -centerY, 0.0F);
        well(graphics, (attempts - attemptsLeft) + "/" + attempts, TRIES_X + TRIES_W / 2,
            TRIES_W - 2, blend(COLOR_WELL_TEXT, COLOR_RED, pressure), 1.0F);
        graphics.pose().popPose();
    }

    private void drawDial(GuiGraphics graphics, long now, int mouseX, int mouseY) {
        Optional<PokewrleHint> gen = hint(PokewrleHint.Category.GENERATION);
        // Generation is only worth showing as a hint on ALL rounds. If the player
        // picked a generation, the answer is obviously from it.
        boolean asHint = generation == 0 && gen.isPresent();
        boolean turned = asHint && flipped(BOX.length, now) >= 0.5F;

        sheet(graphics, GEN, 1.0F);
        if (turned) {
            sheet(graphics, gen.get().state().isCorrect() ? GEN_RIGHT : GEN_WRONG, 1.0F);
        }
        caption(graphics, "GEN", GEN_X + GEN_W / 2, ROW_CAPTION_Y, GEN_W);

        String label = turned ? gen.get().value()
            : generation == 0 ? "ALL" : "G" + generation;
        boolean hovered = phase == Phase.PLAYING
            && inside(mouseX, mouseY, GEN_FRAME_X0, GEN_FRAME_Y0, GEN_FRAME_X1, GEN_FRAME_Y1);

        // label gets thrown up and drops back in when the dial turns
        float roll = Mth.clamp((now - dialledNanos) / (float) DIAL_NANOS, 0.0F, 1.0F);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, -(1.0F - roll) * (1.0F - roll) * 7.0F, 0.0F);
        well(graphics, label, GEN_X + GEN_W / 2, GEN_W,
            hovered ? COLOR_HOVER_TEXT : turned ? COLOR_STATE_TEXT : COLOR_WELL_TEXT, roll);
        graphics.pose().popPose();

        if (hovered) {
            ring(graphics, GEN_FRAME_X0, GEN_FRAME_Y0, GEN_FRAME_X1, GEN_FRAME_Y1,
                fade(COLOR_GOLD, 0.5F + 0.3F * (float) Math.sin(now / 1.6E8D)));
        }
    }

    private void drawShelf(GuiGraphics graphics, long now) {
        sheet(graphics, SHELF, 1.0F);
        caption(graphics, "GUESSED", SHELF_X + 22, SHELF_CAPTION_Y, 46);

        int step = Math.max(SHELF_W / attempts, 1);
        for (int index = 0; index < guesses.size(); index++) {
            Guess guess = guesses.get(index);
            float land = Mth.clamp((now - guess.landedNanos()) / (float) LAND_NANOS, 0.0F, 1.0F);
            // fresh guess slides in from the right of its slot and settles
            float slide = (1.0F - land) * (1.0F - land) * 26.0F;
            float pop = land >= 1.0F ? 1.0F : 0.7F + 0.3F * overshoot(land);
            int centerX = SHELF_X + step * index + step / 2;

            graphics.pose().pushPose();
            graphics.pose().translate(slide, 0.0F, 0.0F);
            if (index == reviewing) {
                ring(graphics, centerX - step / 2 + 1, SHELF_Y - 7,
                    centerX + step / 2 - 1, SHELF_BOTTOM + 1, fade(COLOR_GOLD, 0.9F));
            }
            PokemonPortrait.render(graphics, guess.id(), centerX, SHELF_BOTTOM, 0.9F * pop);
            graphics.pose().popPose();
        }
    }

    private void drawBoxes(GuiGraphics graphics, long now) {
        sheet(graphics, CRITERIA, 1.0F);

        for (int index = 0; index < BOX.length; index++) {
            int[] box = BOX[index];
            caption(graphics, BOX_CATEGORIES[index].label(),
                box[0] + box[2] / 2, BOX_CAPTION_Y, box[2] - 4);

            Optional<PokewrleHint> hint = hint(BOX_CATEGORIES[index]);
            float turn = flipped(index, now);
            if (hint.isEmpty() || turn <= 0.0F) {
                continue;
            }

            // Squash horizontally through zero width. Reads as a card turning over
            // instead of the colour just swapping. The 0.02 floor keeps it off zero scale.
            float squash = Math.max(Math.abs((float) Math.cos(turn * Math.PI)), 0.02F);
            graphics.pose().pushPose();
            graphics.pose().translate(box[0] + box[2] / 2.0F, box[1] + box[3] / 2.0F, 0.0F);
            graphics.pose().scale(squash, 1.0F, 1.0F);
            graphics.pose().translate(-box[0] - box[2] / 2.0F, -box[1] - box[3] / 2.0F, 0.0F);
            if (turn >= 0.5F) {
                ResourceLocation face =
                    hint.get().state().isCorrect() ? CRITERIA_RIGHT : CRITERIA_WRONG;
                graphics.blit(face, box[0], box[1], box[0], box[1], box[2], box[3], DESIGN, DESIGN);
                fitted(graphics, hint.get().value().toUpperCase(Locale.ROOT),
                    box[0] + box[2] / 2, box[1] + box[3] / 2, box[2] - 4, 1.0F,
                    COLOR_STATE_TEXT, 1.0F);
            }
            graphics.pose().popPose();
        }
    }

    private void drawStage(GuiGraphics graphics, long now) {
        sheet(graphics, PLATFORM, 1.0F);

        if (phase != Phase.REVEAL) {
            // idle state: question mark bobs, breathes and rocks a little
            float bob = (float) Math.sin(now / 6.0E8D) * 1.6F;
            float swell = 1.0F + 0.02F * (float) Math.sin(now / 4.0E8D);
            graphics.pose().pushPose();
            graphics.pose().translate(STAGE_CENTER_X, QUESTION_PIVOT_Y + bob, 0.0F);
            graphics.pose().mulPose(Axis.ZP.rotation((float) Math.sin(now / 8.0E8D) * 0.03F));
            graphics.pose().scale(swell, swell, 1.0F);
            graphics.pose().translate(-STAGE_CENTER_X, -QUESTION_PIVOT_Y, 0.0F);
            sheet(graphics, QUESTION, 1.0F);
            graphics.pose().popPose();
            return;
        }

        float rise = rise(now);
        float eased = 1.0F - (1.0F - rise) * (1.0F - rise) * (1.0F - rise);

        // question mark scales out over the first 45% of the rise
        if (rise < 0.45F) {
            float out = 1.0F - rise / 0.45F;
            graphics.pose().pushPose();
            graphics.pose().translate(STAGE_CENTER_X, QUESTION_PIVOT_Y, 0.0F);
            graphics.pose().scale(out, out, 1.0F);
            graphics.pose().translate(-STAGE_CENTER_X, -QUESTION_PIVOT_Y, 0.0F);
            sheet(graphics, QUESTION, out);
            graphics.pose().popPose();
        }

        // three staggered rings expanding off the platform while the species comes up.
        // gold on a win, red on a loss
        if (rise > 0.2F) {
            for (int index = 0; index < 3; index++) {
                float wave = ((rise * 2.2F - index * 0.25F) % 1.0F + 1.0F) % 1.0F;
                int spread = Math.round(6 + wave * 26);
                ring(graphics,
                    PLATFORM_CENTER_X - spread, PLATFORM_TOP - 3 - spread / 3,
                    PLATFORM_CENTER_X + spread, PLATFORM_TOP + 5 + spread / 3,
                    fade(won ? COLOR_GOLD : COLOR_RED, (1.0F - wave) * 0.5F));
            }
        }

        answer.ifPresent(id -> {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, (1.0F - eased) * 22.0F, 0.0F);
            PokemonPortrait.render(graphics, id, STAGE_CENTER_X, STAGE_FLOOR, 3.6F * eased);
            graphics.pose().popPose();
        });

        if (!answerName.isEmpty() && rise > 0.5F) {
            fitted(graphics, answerName.toUpperCase(Locale.ROOT), PLATFORM_CENTER_X, 127,
                70, 1.0F, COLOR_GOLD, (rise - 0.5F) * 2.0F);
        }
    }

    private void drawResult(GuiGraphics graphics, long now) {
        if (phase != Phase.REVEAL) {
            if (best > 0) {
                fitted(graphics, "BEST: " + best + " TRIES TO SPARE", 78, 112, 130, 0.5F,
                    COLOR_CAPTION, 0.75F);
            }
            fitted(graphics, "CLICK GEN TO CHANGE GENERATION", 78, 122, 130, 0.5F,
                COLOR_CAPTION, 0.55F);
            return;
        }

        float age = rise(now);
        float beat = 1.0F + 0.06F * (float) Math.sin(now / 1.8E8D);
        graphics.pose().pushPose();
        graphics.pose().translate(78.0F, 112.0F, 0.0F);
        graphics.pose().scale(beat, beat, 1.0F);
        fitted(graphics, won ? "CAUGHT IT!" : "OUT OF TRIES", 0, 0, 132, 1.2F,
            won ? COLOR_GREEN : COLOR_RED, age);
        graphics.pose().popPose();

        fitted(graphics, "PRESS ENTER TO CLOSE", 78, 126, 130, 0.5F, COLOR_CAPTION, age * 0.8F);
    }

    private void drawSuggestions(GuiGraphics graphics) {
        if (suggestions.isEmpty() || phase != Phase.PLAYING) {
            return;
        }

        int top = ROW_Y + ROW_H + 3;
        int height = suggestions.size() * SUGGESTION_H + 2;
        graphics.fill(INPUT_X - 2, top, INPUT_X + INPUT_W + 2, top + height, COLOR_POPUP);
        ring(graphics, INPUT_X - 2, top, INPUT_X + INPUT_W + 2, top + height, fade(COLOR_GOLD, 0.7F));

        for (int index = 0; index < suggestions.size(); index++) {
            int rowY = top + 1 + index * SUGGESTION_H;
            boolean chosen = index == picked;
            if (chosen) {
                graphics.fill(INPUT_X - 1, rowY, INPUT_X + INPUT_W + 1, rowY + SUGGESTION_H,
                    COLOR_POPUP_PICKED);
            }
            PokedexSource.Entry entry = suggestions.get(index);
            graphics.drawString(font, entry.name(), INPUT_X + 2, rowY + 1,
                chosen ? COLOR_GOLD : COLOR_STATE_TEXT, false);
            fitted(graphics, "G" + entry.generation(), INPUT_X + INPUT_W - 8, rowY + 4,
                14, 0.7F, COLOR_CAPTION, 0.8F);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (phase == Phase.REVEAL) {
            if (enter || keyCode == GLFW.GLFW_KEY_SPACE) {
                onClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (enter) {
            submit();
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!typed.isEmpty()) {
                    typed.deleteCharAt(typed.length() - 1);
                    refreshSuggestions();
                    MinigameSounds.type();
                }
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                return step(1);
            }
            case GLFW.GLFW_KEY_UP -> {
                return step(-1);
            }
            case GLFW.GLFW_KEY_TAB -> {
                complete();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        // take anything printable. Species names have digits, dots and hyphens in them.
        if (phase != Phase.PLAYING || typed.length() >= NAME_LIMIT
            || character < ' ' || character == 127) {
            return false;
        }
        typed.append(character);
        refreshSuggestions();
        MinigameSounds.type();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = Math.round((float) (mouseX - originX) / panelScale);
        int y = Math.round((float) (mouseY - originY) / panelScale);

        if (inside(x, y, GEN_FRAME_X0, GEN_FRAME_Y0, GEN_FRAME_X1, GEN_FRAME_Y1)) {
            cycleGeneration(button == 1 ? -1 : 1);
            return true;
        }

        // popup sits 3px under the well and rows start 1px inside its border, hence the 4
        int row = (y - ROW_Y - ROW_H - 4) / SUGGESTION_H;
        if (!suggestions.isEmpty() && phase == Phase.PLAYING
            && x >= INPUT_X - 2 && x <= INPUT_X + INPUT_W + 2
            && row >= 0 && row < suggestions.size()) {
            picked = row;
            complete();
            submit();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int x = Math.round((float) (mouseX - originX) / panelScale);
        int y = Math.round((float) (mouseY - originY) / panelScale);
        if (inside(x, y, GEN_FRAME_X0, GEN_FRAME_Y0, GEN_FRAME_X1, GEN_FRAME_Y1)) {
            cycleGeneration(scrollY > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ logic

    private boolean step(int direction) {
        if (suggestions.isEmpty()) {
            return true;
        }
        picked = Math.floorMod(picked + direction, suggestions.size());
        MinigameSounds.type();
        return true;
    }

    /** Hovering a portrait on the shelf pulls that guess's hints back into the boxes. */
    private void trackHover(int mouseX, int mouseY) {
        reviewing = -1;
        if (guesses.isEmpty() || mouseY < SHELF_Y - 8 || mouseY > SHELF_BOTTOM + 2
            || mouseX < SHELF_X) {
            return;
        }
        int slot = (mouseX - SHELF_X) / Math.max(SHELF_W / attempts, 1);
        if (slot >= 0 && slot < guesses.size()) {
            reviewing = slot;
        }
    }

    private void cycleGeneration(int direction) {
        if (phase != Phase.PLAYING) {
            return;
        }
        // Server owns the answer. Ask it to re-deal; restart() runs when it replies.
        ClientPlayNetworking.send(
            new PokewrleSetupC2S(Math.floorMod(generation + direction, GENERATIONS + 1)));
        MinigameSounds.select();
    }

    private void complete() {
        if (suggestions.isEmpty()) {
            return;
        }
        typed.setLength(0);
        typed.append(suggestions.get(Math.min(picked, suggestions.size() - 1)).name());
        refreshSuggestions();
        MinigameSounds.select();
    }

    private void submit() {
        String name = typed.toString().trim();
        if (name.isEmpty()) {
            return;
        }
        if (!suggestions.isEmpty()) {
            // enter takes the highlighted suggestion, not the half-typed text
            name = suggestions.get(Math.min(picked, suggestions.size() - 1)).name();
        }
        ClientPlayNetworking.send(new PokewrleGuessC2S(name));
        MinigameSounds.submit();
    }

    private void refreshSuggestions() {
        suggestions = PokedexSource.suggest(typed.toString(), generation, SUGGESTION_LIMIT);
        picked = 0;
    }

    private void onFeedback(PokewrleFeedbackS2C feedback) {
        long now = System.nanoTime();
        if (feedback.outcome() == PokewrleFeedbackS2C.Outcome.REJECTED) {
            shakenNanos = now;
            MinigameSounds.reject();
            return;
        }

        typed.setLength(0);
        suggestions = List.of();
        picked = 0;
        attemptsLeft = feedback.attemptsLeft();
        latest = feedback.hints();
        flippedNanos = now;
        won = feedback.outcome() == PokewrleFeedbackS2C.Outcome.WON;
        PokedexSource.byName(feedback.guess())
            .ifPresent(entry -> guesses.add(new Guess(entry.id(), now, feedback.hints())));

        // one sound per box, staggered to land with each flip
        long stagger = FLIP_STAGGER_NANOS / 1_000_000L;
        for (int index = 0; index < BOX_CATEGORIES.length; index++) {
            PokewrleHint.Category category = BOX_CATEGORIES[index];
            int slot = index;
            feedback.hints().stream()
                .filter(value -> value.category() == category)
                .findFirst()
                .ifPresent(value ->
                    MinigameSounds.flipAfter(slot * stagger, value.state().isCorrect(), slot));
        }

        if (feedback.outcome().isOver()) {
            phase = Phase.REVEAL;
            // hold the reveal until the last box has finished flipping
            revealedNanos = now + FLIP_STAGGER_NANOS * BOX_CATEGORIES.length;
            answer = feedback.answer();
            answerName = feedback.answerName().orElse("");
            MinigameSounds.revealAfter(BOX_CATEGORIES.length * stagger, won);
        } else {
            shakenNanos = now;
        }
    }

    /**
     * Hint for {@code category} out of whichever guess the boxes are showing: the newest
     * one, or an older one while it is hovered on the shelf.
     */
    private Optional<PokewrleHint> hint(PokewrleHint.Category category) {
        List<PokewrleHint> source = reviewing >= 0 && reviewing < guesses.size()
            ? guesses.get(reviewing).hints()
            : latest;
        return source.stream().filter(hint -> hint.category() == category).findFirst();
    }

    /** Flip progress 0..1 for box {@code index}. Stays at 0 until its stagger elapses. */
    private float flipped(int index, long now) {
        if (flippedNanos == Long.MIN_VALUE) {
            return 0.0F;
        }
        long age = now - flippedNanos - index * FLIP_STAGGER_NANOS;
        return age < 0 ? 0.0F : Mth.clamp(age / (float) FLIP_NANOS, 0.0F, 1.0F);
    }

    /** Reveal progress 0..1. */
    private float rise(long now) {
        if (revealedNanos == Long.MIN_VALUE) {
            return 0.0F;
        }
        return Mth.clamp((now - revealedNanos) / (float) RISE_NANOS, 0.0F, 1.0F);
    }

    // ---------------------------------------------------------------- drawing

    /** Little heading on the blue strip above a well. */
    private void caption(GuiGraphics graphics, String text, int centerX, int centerY, int maxWidth) {
        fitted(graphics, text, centerX, centerY, maxWidth, 0.5F, COLOR_CAPTION, 0.95F);
    }

    /**
     * Value text for the shallow wells on the top row.
     *
     * <p>The wells are 8px deep. Text has to land on scale 1.0 or 0.5 with no +1/+1
     * shadow behind it; anything else smears the glyph edges.
     */
    private void well(GuiGraphics graphics, String text, int centerX, int maxWidth,
                      int color, float alpha) {
        if (alpha <= 0.0F) {
            return;
        }
        int width = Math.max(font.width(text), 1);
        float size = width <= maxWidth ? 1.0F : 0.5F;
        graphics.pose().pushPose();
        graphics.pose().scale(size, size, 1.0F);
        int left = Math.round((centerX - width * size / 2.0F) / size);
        int top = Math.round((ROW_Y + ROW_H / 2.0F - font.lineHeight * size / 2.0F + 1.0F) / size);
        graphics.drawString(font, text, left, top, fade(color, alpha), false);
        graphics.pose().popPose();
    }

    /** Centred text with a drop shadow, scaled down until it fits {@code maxWidth}. */
    private void fitted(GuiGraphics graphics, String text, int centerX, int centerY,
                        int maxWidth, float maxSize, int color, float alpha) {
        if (text.isEmpty() || alpha <= 0.0F) {
            return;
        }
        int width = Math.max(font.width(text), 1);
        float size = Math.min(maxSize, maxWidth / (float) width);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(size, size, 1.0F);
        int left = -width / 2;
        int top = -font.lineHeight / 2 + 1;
        graphics.drawString(font, text, left + 1, top + 1, fade(COLOR_SHADOW, alpha * 0.7F), false);
        graphics.drawString(font, text, left, top, fade(color, alpha), false);
        graphics.pose().popPose();
    }

    /** One pixel outline, no fill. */
    private static void ring(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    /** Blits a full 256x256 layer at the origin and puts the tint back afterwards. */
    private static void sheet(GuiGraphics graphics, ResourceLocation texture, float alpha) {
        graphics.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ maths

    /** Decaying horizontal wobble, in design pixels. Fired on a rejected or wrong guess. */
    private float shakeAmount(long now) {
        if (shakenNanos == Long.MIN_VALUE) {
            return 0.0F;
        }
        long age = now - shakenNanos;
        if (age < 0 || age >= SHAKE_NANOS) {
            return 0.0F;
        }
        return (float) Math.sin(age / 1.5E7D) * 2.6F * (1.0F - age / (float) SHAKE_NANOS);
    }

    private static boolean inside(int x, int y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    // damped spring, shoots past 1 and settles back
    private static float overshoot(float progress) {
        if (progress >= 1.0F) {
            return 1.0F;
        }
        return 1.0F - (float) (Math.pow(2.0D, -9.0D * progress) * Math.cos(progress * 14.0D));
    }

    /** Scales the alpha channel of an ARGB colour, leaving RGB alone. */
    private static int fade(int argb, float alpha) {
        int scaled = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    private static int blend(int from, int to, float amount) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        int red = Mth.lerpInt(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int green = Mth.lerpInt(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int blue = Mth.lerpInt(clamped, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath(
            "cobblemon_dimensions_stories", "textures/gui/pokewrle/" + fileName);
    }

    /** A guess already on the shelf, with the hints it came back with. */
    private record Guess(ResourceLocation id, long landedNanos, List<PokewrleHint> hints) {
    }

    private enum Phase {
        PLAYING,
        REVEAL
    }
}
