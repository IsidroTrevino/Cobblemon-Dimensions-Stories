package com.cobblemondimensions.stories.client.minigame;

import com.cobblemondimensions.stories.net.PatternResultC2S;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.lwjgl.glfw.GLFW;

/**
 * Arrow pattern minigame. A random run of directions is shown and has to be
 * typed back on the arrow keys before the timer runs out. From level 10 the
 * arrows are replaced by shapes using the same colour-to-direction mapping.
 *
 * <p>Every coordinate below is in the 256x256 design space of the art sheets,
 * same as the walking dialogue bar. Each sheet is blitted whole at (0,0) so the
 * layers line up without per-layer offsets.
 */
public final class PatternGameScreen extends Screen {
    private static final int DESIGN = 256;

    /** Frame bounds inside the sheet. Used for centring and for the scrim rect. */
    private static final int PANEL_X0 = 40;
    private static final int PANEL_Y0 = 7;
    private static final int PANEL_X1 = 216;
    private static final int PANEL_Y1 = 73;
    private static final int PANEL_CENTER_X = 128;
    private static final int PANEL_CENTER_Y = 40;

    private static final int SLOT = 16;
    private static final int SLOT_INSET = 2;
    private static final int SLOT_INNER = 12;
    private static final int TOP_ROW_Y = 26;
    private static final int BOTTOM_ROW_Y = 43;
    private static final int[] TOP_ROW_X = {50, 70, 90, 110, 130, 150, 170, 190};
    private static final int[] BOTTOM_ROW_X = {60, 100, 140, 180};
    private static final int BOARD_SLOTS = TOP_ROW_X.length + BOTTOM_ROW_X.length;

    /** Top-left and width of the dark well the timer digits are drawn into. */
    private static final int CLOCK_X = 113;
    private static final int CLOCK_Y = 15;
    private static final int CLOCK_W = 30;

    private static final int STREAK_X = 128;
    private static final int STREAK_Y = 53;

    /** Source rects on the sheets, indexed up, down, left, right. Both arrays match. */
    private static final int[][] ARROW_RECTS = {
        {93, 31, 10, 5}, {113, 32, 10, 5}, {135, 29, 5, 10}, {156, 29, 5, 10}
    };
    private static final int[][] SHAPE_RECTS = {
        {94, 30, 8, 8}, {114, 30, 8, 8}, {134, 30, 8, 8}, {154, 30, 8, 8}
    };

    private static final long BEAT_NANOS = 500_000_000L;
    private static final long COUNTDOWN_NANOS = BEAT_NANOS * 3 + 400_000_000L;
    private static final long CLEARED_NANOS = 1_100_000_000L;
    private static final long SHIFT_NANOS = 2_000_000_000L;
    private static final long WRONG_NANOS = 380_000_000L;
    private static final long POP_NANOS = 240_000_000L;
    private static final long BURST_NANOS = 420_000_000L;
    private static final long STREAK_BUMP_NANOS = 520_000_000L;
    private static final long REVEAL_STEP_NANOS = 35_000_000L;
    private static final long FADE_NANOS = 700_000_000L;
    /** 60s back-dating. Any animation stamped this far back is already over. */
    private static final long IDLE_NANOS = 60_000_000_000L;

    /** Arrows switch to shapes at this level. */
    private static final int SHAPE_LEVEL = 10;
    /** Fraction of the timer that must remain for a clear to count as perfect. */
    private static final float PERFECT_SHARE = 0.55F;

    private static final float MIN_SECONDS = 5.0F;
    private static final float START_SECONDS = 15.0F;

    private static final int COLOR_DONE = 0x7A38F27A;
    private static final int COLOR_CURRENT = 0x8000F0FF;
    private static final int COLOR_WRONG = 0xAAFF2A4A;
    private static final int COLOR_CYAN = 0xFF00F0FF;
    private static final int COLOR_MAGENTA = 0xFFEC00F0;
    private static final int COLOR_GOLD = 0xFFFFC93C;
    private static final int COLOR_GREEN = 0xFF7CFFA8;
    private static final int COLOR_RED = 0xFFFF2A4A;
    private static final int COLOR_SHADOW = 0xFF1F004B;
    private static final int COLOR_SCRIM = 0xC01F004B;

    private static final ResourceLocation CORNERS = texture("pattern_corners.png");
    private static final ResourceLocation BACKGROUND = texture("pattern_background.png");
    private static final ResourceLocation BACKGROUND_X10 = texture("pattern_background_x10.png");
    private static final ResourceLocation ICONS = texture("pattern_icons.png");
    private static final ResourceLocation ICONS_X10 = texture("pattern_icons_x10.png");
    private static final ResourceLocation SLOTS = texture("pattern_slots.png");
    private static final ResourceLocation SLOT_DISABLED = texture("pattern_slot_disabled.png");
    private static final ResourceLocation ARROWS = texture("pattern_arrows.png");
    private static final ResourceLocation SHAPES = texture("pattern_shapes.png");
    private static final ResourceLocation CLOCK = texture("pattern_timer.png");
    private static final ResourceLocation STREAK = texture("pattern_streak.png");

    private final RandomSource random = RandomSource.create();
    private final List<Burst> bursts = new ArrayList<>();
    private final int best;

    private Phase phase = Phase.COUNTDOWN;
    private long phaseStartedNanos = System.nanoTime();
    private long openedNanos = System.nanoTime();
    private long deadlineNanos;

    private int level = 1;
    private int levelsCleared;
    private int streak;
    private int bestStreak;

    private int[] pattern = new int[0];
    /** Board slot index per pattern step. See layoutFor. */
    private int[] steps = new int[0];
    private int typed;

    private long revealStartedNanos;
    // back-dated so none of these animations fire on the first frame
    private long wrongAtNanos = openedNanos - IDLE_NANOS;
    private long popAtNanos = openedNanos - IDLE_NANOS;
    private int popStep = -1;
    private long streakBumpAtNanos = openedNanos - IDLE_NANOS;
    private long shapeShiftAtNanos = openedNanos - IDLE_NANOS;
    private boolean shapeShifted;
    private int countdownBeat = -1;
    private int lastTickSecond = -1;
    private boolean perfect;
    private boolean reported;

    public PatternGameScreen(int best) {
        super(Component.translatable("screen.cobblemon_dimensions_stories.pattern"));
        this.best = best;
    }

    @Override
    protected void init() {
        // init also runs on resize, so only seed the run when there is no pattern yet
        if (pattern.length == 0) {
            long now = System.nanoTime();
            openedNanos = now;
            phaseStartedNanos = now;
            MinigameSounds.reset();
            rollPattern(now);
        }
    }

    /** False so the world keeps ticking. The round timer is real time. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.nanoTime();
        MinigameSounds.tickQueue(now);
        advance(now);

        renderBackground(graphics, mouseX, mouseY, partialTick);

        // integer scale only, no half pixels: panel takes ~72% of width, 2x to 5x
        int scale = Mth.clamp(Math.min((int) (graphics.guiWidth() * 0.72F / (PANEL_X1 - PANEL_X0)),
            graphics.guiHeight() / 96), 2, 5);

        // open animation over 340ms, plus horizontal shake on a wrong key
        float grow = overshoot(Mth.clamp((now - openedNanos) / 3.4E8F, 0.0F, 1.0F));
        float shake = shakeAmount(now);

        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2.0F, graphics.guiHeight() / 2.0F, 0.0F);
        graphics.pose().scale(scale * grow, scale * grow, 1.0F);
        graphics.pose().translate(-PANEL_CENTER_X + shake, -PANEL_CENTER_Y, 0.0F);

        drawBackdrop(graphics, now);
        graphics.blit(CORNERS, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);
        drawSlots(graphics, now);
        // Scrim before the clock and streak: those two stay at full brightness
        // over the dimmed board on a cleared level.
        drawScrim(graphics, now);
        drawBursts(graphics, now);
        drawClock(graphics, now);
        drawStreak(graphics, now);
        drawUrgency(graphics, now);
        drawOverlay(graphics, now);

        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ----------------------------------------------------------------- logic

    private void advance(long now) {
        switch (phase) {
            case COUNTDOWN -> {
                beat(now);
                if (now - phaseStartedNanos >= COUNTDOWN_NANOS) {
                    startLevel(now);
                }
            }
            case PLAYING -> {
                metronome(now);
                if (now >= deadlineNanos) {
                    finish(now);
                }
            }
            case CLEARED -> {
                long hold = level + 1 == SHAPE_LEVEL ? SHIFT_NANOS : CLEARED_NANOS;
                if (now - phaseStartedNanos >= hold) {
                    level++;
                    if (level == SHAPE_LEVEL) {
                        shapeShiftAtNanos = now;
                        shapeShifted = true;
                    }
                    rollPattern(now);
                    phase = Phase.COUNTDOWN;
                    phaseStartedNanos = now;
                    countdownBeat = -1;
                }
            }
            case OVER -> {
                // waits for the player to close; result packet already sent
            }
        }
    }

    /** One countdown note per beat, last one is the GO. */
    private void beat(long now) {
        int elapsed = (int) ((now - phaseStartedNanos) / BEAT_NANOS);
        if (elapsed == countdownBeat) {
            return;
        }
        countdownBeat = elapsed;
        MinigameSounds.countdown(Math.max(0, 3 - elapsed));
    }

    private void startLevel(long now) {
        phase = Phase.PLAYING;
        phaseStartedNanos = now;
        typed = 0;
        perfect = false;
        lastTickSecond = -1;
        deadlineNanos = now + (long) (secondsFor(level) * 1_000_000_000L);
    }

    private void rollPattern(long now) {
        // Length grows by 2 every other level. Keeping it even means the run stays
        // symmetric around the centred streak dial.
        int length = Math.min(BOARD_SLOTS, 4 + (level - 1) / 2 * 2);
        pattern = new int[length];
        for (int index = 0; index < length; index++) {
            pattern[index] = random.nextInt(4);
        }
        steps = layoutFor(length);
        revealStartedNanos = now;
        typed = 0;
        MinigameSounds.reveal(length);
    }

    /**
     * Maps pattern steps to board slots. Fills the 8-slot top row first, centred,
     * then spills into the 4-slot bottom row. Without the centring, short patterns
     * would sit against the left edge.
     */
    private static int[] layoutFor(int length) {
        int[] slots = new int[length];
        int top = Math.min(length, TOP_ROW_X.length);
        int topStart = (TOP_ROW_X.length - top) / 2;
        for (int index = 0; index < top; index++) {
            slots[index] = topStart + index;
        }
        int bottom = length - top;
        int bottomStart = (BOTTOM_ROW_X.length - bottom) / 2;
        for (int index = 0; index < bottom; index++) {
            slots[top + index] = TOP_ROW_X.length + bottomStart + index;
        }
        return slots;
    }

    /** 15s on level 1, one second less per level, floored at 5s. */
    private static float secondsFor(int level) {
        return Math.max(MIN_SECONDS, START_SECONDS - (level - 1));
    }

    private void metronome(long now) {
        int remaining = (int) Math.ceil((deadlineNanos - now) / 1.0E9D);
        if (remaining <= 3 && remaining >= 1 && remaining != lastTickSecond) {
            lastTickSecond = remaining;
            MinigameSounds.tick(remaining <= 1);
        }
    }

    private void press(int direction, long now) {
        if (phase != Phase.PLAYING) {
            return;
        }

        if (pattern[typed] != direction) {
            // Wrong key resets progress on the same pattern and drops the streak.
            // The round timer keeps running.
            typed = 0;
            streak = 0;
            wrongAtNanos = now;
            popStep = -1;
            bursts.clear();
            MinigameSounds.buzz();
            return;
        }

        popStep = typed;
        popAtNanos = now;
        addBurst(typed, now, COLOR_CYAN);
        MinigameSounds.hit(typed, pattern.length);
        typed++;
        if (typed >= pattern.length) {
            clearLevel(now);
        }
    }

    private void clearLevel(long now) {
        levelsCleared++;
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        streakBumpAtNanos = now;
        perfect = (deadlineNanos - now) / (secondsFor(level) * 1.0E9F) >= PERFECT_SHARE;
        phase = Phase.CLEARED;
        phaseStartedNanos = now;

        // bursts cascade left to right, 30ms apart
        for (int index = 0; index < pattern.length; index++) {
            addBurst(index, now + index * 30_000_000L, perfect ? COLOR_GOLD : COLOR_GREEN);
        }
        if (perfect) {
            MinigameSounds.perfect();
        } else {
            MinigameSounds.clear(streak);
        }
        MinigameSounds.streakUp(streak);
        if (level + 1 == SHAPE_LEVEL) {
            MinigameSounds.shapeShift();
        }
    }

    private void finish(long now) {
        phase = Phase.OVER;
        phaseStartedNanos = now;
        report();
        if (levelsCleared > 0) {
            MinigameSounds.victory();
        } else {
            MinigameSounds.defeat();
        }
    }

    private void report() {
        if (reported) {
            return;
        }
        reported = true;
        ClientPlayNetworking.send(new PatternResultC2S(levelsCleared));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        long now = System.nanoTime();
        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> press(0, now);
            case GLFW.GLFW_KEY_DOWN -> press(1, now);
            case GLFW.GLFW_KEY_LEFT -> press(2, now);
            case GLFW.GLFW_KEY_RIGHT -> press(3, now);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (phase == Phase.OVER) {
                    onClose();
                }
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    /** Closing early still reports the levels already cleared. */
    @Override
    public void onClose() {
        report();
        MinigameSounds.reset();
        super.onClose();
    }

    // --------------------------------------------------------------- drawing

    /** Crossfades the base board into the shape-mode board over FADE_NANOS. */
    private void drawBackdrop(GuiGraphics graphics, long now) {
        float shift = shapeShifted
            ? Mth.clamp((now - shapeShiftAtNanos) / (float) FADE_NANOS, 0.0F, 1.0F)
            : 0.0F;
        // icon layer alpha pulses 0.68 to 1.0
        float breathe = 0.84F + (float) Math.sin(now / 5.2E8D) * 0.16F;

        if (shift < 1.0F) {
            sheet(graphics, BACKGROUND, 1.0F - shift);
            sheet(graphics, ICONS, (1.0F - shift) * breathe);
        }
        if (shift > 0.0F) {
            sheet(graphics, BACKGROUND_X10, shift);
            sheet(graphics, ICONS_X10, shift * breathe);
        }
    }

    private void drawSlots(GuiGraphics graphics, long now) {
        boolean flashing = now - wrongAtNanos < WRONG_NANOS;
        // invert steps into slot -> step, -1 for slots this pattern does not use
        int[] slotToStep = new int[BOARD_SLOTS];
        Arrays.fill(slotToStep, -1);
        for (int step = 0; step < steps.length; step++) {
            slotToStep[steps[step]] = step;
        }

        for (int slot = 0; slot < BOARD_SLOTS; slot++) {
            int x = slotX(slot);
            int y = slotY(slot);
            int step = slotToStep[slot];

            if (step < 0) {
                // unused slot: single dimmed tile at (50,26) on the sheet
                graphics.blit(SLOT_DISABLED, x, y, 50, 26, SLOT, SLOT, DESIGN, DESIGN);
                continue;
            }

            // staggered drop-in, REVEAL_STEP_NANOS apart, 240ms each
            float reveal = Mth.clamp(
                (now - revealStartedNanos - step * REVEAL_STEP_NANOS) / 2.4E8F, 0.0F, 1.0F);
            if (reveal <= 0.0F) {
                graphics.blit(SLOT_DISABLED, x, y, 50, 26, SLOT, SLOT, DESIGN, DESIGN);
                continue;
            }

            float pop = overshoot(reveal);
            // half-sine bump on the slot that was just hit
            if (step == popStep && now - popAtNanos < POP_NANOS) {
                pop += (float) Math.sin((now - popAtNanos) / (double) POP_NANOS * Math.PI) * 0.32F;
            }

            graphics.pose().pushPose();
            graphics.pose().translate(x + SLOT / 2.0F, y + SLOT / 2.0F, 0.0F);
            graphics.pose().scale(pop, pop, 1.0F);
            graphics.pose().translate(-x - SLOT / 2.0F, -y - SLOT / 2.0F, 0.0F);

            graphics.blit(SLOTS, x, y, x, y, SLOT, SLOT, DESIGN, DESIGN);

            int innerX = x + SLOT_INSET;
            int innerY = y + SLOT_INSET;
            if (flashing) {
                graphics.fill(innerX, innerY, innerX + SLOT_INNER, innerY + SLOT_INNER, COLOR_WRONG);
            } else if (step < typed) {
                graphics.fill(innerX, innerY, innerX + SLOT_INNER, innerY + SLOT_INNER, COLOR_DONE);
            } else if (step == typed && phase == Phase.PLAYING) {
                // Current slot gets a pulsing cyan fill plus a ring. Long patterns
                // are hard to track without it.
                float glow = 0.45F + (float) Math.sin(now / 1.2E8D) * 0.35F;
                graphics.fill(innerX, innerY, innerX + SLOT_INNER, innerY + SLOT_INNER,
                    fade(COLOR_CURRENT, glow));
                ring(graphics, x + SLOT / 2, y + SLOT / 2, SLOT / 2 + 1, 1, fade(COLOR_CYAN, glow));
            }

            drawSign(graphics, pattern[step], x, y, reveal);
            graphics.pose().popPose();
        }
    }

    /** Blits the arrow or shape glyph centred in a 16x16 slot. */
    private void drawSign(GuiGraphics graphics, int direction, int slotX, int slotY, float reveal) {
        boolean shapes = level >= SHAPE_LEVEL;
        int[] rect = shapes ? SHAPE_RECTS[direction] : ARROW_RECTS[direction];
        int x = slotX + (SLOT - rect[2]) / 2;
        int y = slotY + (SLOT - rect[3]) / 2;

        graphics.setColor(1.0F, 1.0F, 1.0F, reveal);
        graphics.blit(shapes ? SHAPES : ARROWS, x, y, rect[0], rect[1], rect[2], rect[3],
            DESIGN, DESIGN);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawClock(GuiGraphics graphics, long now) {
        graphics.blit(CLOCK, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);

        // during the countdown the readout holds at the full round time
        long remaining = phase == Phase.COUNTDOWN
            ? (long) (secondsFor(level) * 1.0E9F)
            : Math.max(0L, deadlineNanos - now);
        float share = Mth.clamp(remaining / (secondsFor(level) * 1.0E9F), 0.0F, 1.0F);

        int centis = (int) (remaining / 10_000_000L);
        String text = String.format("%02d:%02d", centis / 100, centis % 100);

        // green at full, gold at half, red at empty, with a white flash under 20%
        int color = share > 0.5F
            ? blend(COLOR_GOLD, COLOR_GREEN, (share - 0.5F) * 2.0F)
            : blend(COLOR_RED, COLOR_GOLD, share * 2.0F);
        if (share < 0.2F && ((now / 110_000_000L) & 1L) == 0L) {
            color = 0xFFFFFFFF;
        }

        // one-pixel horizontal jitter under 20% remaining
        int jitter = share < 0.2F ? (int) (now / 60_000_000L) % 2 : 0;
        int x = CLOCK_X + (CLOCK_W - font.width(text)) / 2 + jitter;
        graphics.drawString(font, text, x, CLOCK_Y + 1, color, false);
    }

    private void drawStreak(GuiGraphics graphics, long now) {
        float bump = 1.0F;
        long age = now - streakBumpAtNanos;
        if (age >= 0 && age < STREAK_BUMP_NANOS) {
            bump += (float) Math.sin(age / (double) STREAK_BUMP_NANOS * Math.PI) * 0.45F;
            // ring expands from radius 10 to 24 and fades out
            int wave = 10 + (int) (age / (float) STREAK_BUMP_NANOS * 14.0F);
            ring(graphics, STREAK_X, STREAK_Y, wave, 1,
                fade(COLOR_GOLD, 1.0F - age / (float) STREAK_BUMP_NANOS));
        }

        int color = streakColor(now);

        graphics.pose().pushPose();
        graphics.pose().translate(STREAK_X, STREAK_Y, 0.0F);
        graphics.pose().scale(bump, bump, 1.0F);
        graphics.pose().translate(-STREAK_X, -STREAK_Y, 0.0F);
        graphics.blit(STREAK, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);

        // One orbiting spark per streak point, capped at 8. Radius 12 clears the dial art.
        int sparks = Math.min(streak, 8);
        for (int index = 0; index < sparks; index++) {
            double angle = now / 6.0E8D + index * (Math.PI * 2.0D / sparks);
            int sparkX = STREAK_X + (int) Math.round(Math.cos(angle) * 12.0D);
            int sparkY = STREAK_Y + (int) Math.round(Math.sin(angle) * 12.0D);
            graphics.fill(sparkX - 1, sparkY - 1, sparkX + 1, sparkY + 1, color);
        }

        String text = String.valueOf(streak);
        graphics.pose().pushPose();
        graphics.pose().translate(STREAK_X, STREAK_Y, 0.0F);
        graphics.pose().scale(0.9F, 0.9F, 1.0F);
        int half = font.width(text) / 2;
        graphics.drawString(font, text, -half + 1, -3, COLOR_SHADOW, false);
        graphics.drawString(font, text, -half, -4, color, false);
        graphics.pose().popPose();
        graphics.pose().popPose();
    }

    /** 0 cyan, 1-2 green, 3-5 gold, 6+ cycles hue. */
    private int streakColor(long now) {
        if (streak <= 0) {
            return COLOR_CYAN;
        }
        if (streak < 3) {
            return COLOR_GREEN;
        }
        if (streak < 6) {
            return COLOR_GOLD;
        }
        return 0xFF000000 | Mth.hsvToRgb((float) ((now / 3.0E8D) % 1.0D), 0.85F, 1.0F);
    }

    /** Pulsing red outline inside the frame under 25% of the timer. */
    private void drawUrgency(GuiGraphics graphics, long now) {
        if (phase != Phase.PLAYING) {
            return;
        }
        float share = Mth.clamp((deadlineNanos - now) / (secondsFor(level) * 1.0E9F), 0.0F, 1.0F);
        if (share > 0.25F) {
            return;
        }
        float strength = (0.25F - share) * 4.0F
            * (0.55F + (float) Math.sin(now / 1.0E8D) * 0.45F);
        outline(graphics, PANEL_X0 + 3, PANEL_Y0 + 3, PANEL_X1 - 3, PANEL_Y1 - 3, 2,
            fade(COLOR_RED, strength * 0.8F));
    }

    private void drawOverlay(GuiGraphics graphics, long now) {
        switch (phase) {
            case COUNTDOWN -> {
                long age = now - phaseStartedNanos;
                int index = (int) (age / BEAT_NANOS);
                String text = index >= 3 ? "GO!" : String.valueOf(3 - index);
                float within = (age % BEAT_NANOS) / (float) BEAT_NANOS;
                // Digit starts at 3.4x and shrinks across the beat. Alpha peaks at
                // 0.85 to keep the pattern behind it readable.
                float size = 3.4F - within * 1.1F;
                int color = index >= 3 ? COLOR_GREEN : COLOR_CYAN;
                text(graphics, text, PANEL_CENTER_Y, size, (1.0F - within * 0.7F) * 0.85F, color);
            }
            case CLEARED -> {
                boolean shifting = level + 1 == SHAPE_LEVEL;
                String headline = shifting ? "SHAPE MODE" : perfect ? "PERFECT!" : "CLEAR!";
                int color = shifting ? COLOR_MAGENTA : perfect ? COLOR_GOLD : COLOR_GREEN;
                text(graphics, headline, 30, shifting ? 1.6F : 2.2F, 1.0F, color);
                text(graphics, shifting
                    ? "arrows become shapes, same colours"
                    : "streak " + streak + "  -  next round is faster",
                    52, 0.7F, 1.0F, COLOR_CYAN);
            }
            case OVER -> {
                text(graphics, "TIME!", 26, 2.4F, 1.0F,
                    levelsCleared > 0 ? COLOR_GOLD : COLOR_RED);
                text(graphics, "cleared " + levelsCleared
                    + "  -  best streak " + bestStreak
                    + "  -  record " + Math.max(best, levelsCleared),
                    46, 0.7F, 1.0F, COLOR_CYAN);
                text(graphics, "enter or esc to leave", 60, 0.6F, 1.0F, COLOR_MAGENTA);
            }
            default -> {
                // PLAYING draws no overlay
            }
        }
    }

    /** Dims the board for CLEARED and OVER, the two phases with a headline. */
    private void drawScrim(GuiGraphics graphics, long now) {
        if (phase != Phase.CLEARED && phase != Phase.OVER) {
            return;
        }
        float in = Mth.clamp((now - phaseStartedNanos) / 1.6E8F, 0.0F, 1.0F);
        graphics.fill(PANEL_X0 + 4, PANEL_Y0 + 4, PANEL_X1 - 4, PANEL_Y1 - 4,
            fade(COLOR_SCRIM, in));
    }

    // ---------------------------------------------------------------- pieces

    private void addBurst(int step, long startNanos, int color) {
        if (step >= steps.length) {
            return;
        }
        int slot = steps[step];
        bursts.add(new Burst(slotX(slot) + SLOT / 2, slotY(slot) + SLOT / 2, startNanos, color));
    }

    private void drawBursts(GuiGraphics graphics, long now) {
        bursts.removeIf(burst -> now - burst.startNanos() > BURST_NANOS);
        for (Burst burst : bursts) {
            long age = now - burst.startNanos();
            if (age < 0) {
                // scheduled for a later frame
                continue;
            }
            float progress = age / (float) BURST_NANOS;
            int radius = SLOT / 2 + Math.round(progress * 12.0F);
            ring(graphics, burst.x(), burst.y(), radius, 1, fade(burst.color(), 1.0F - progress));
        }
    }

    /** Square ring of given radius, drawn as four fills. */
    private static void ring(GuiGraphics graphics, int centerX, int centerY, int radius,
                             int thickness, int color) {
        outline(graphics, centerX - radius, centerY - radius,
            centerX + radius, centerY + radius, thickness, color);
    }

    private static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                int thickness, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        graphics.fill(x0, y0, x1, y0 + thickness, color);
        graphics.fill(x0, y1 - thickness, x1, y1, color);
        graphics.fill(x0, y0, x0 + thickness, y1, color);
        graphics.fill(x1 - thickness, y0, x1, y1, color);
    }

    /** Panel-centred text with a 1px drop shadow. y is the vertical middle of the line. */
    private void text(GuiGraphics graphics, String text, int y, float size, float alpha,
                      int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(PANEL_CENTER_X, y, 0.0F);
        graphics.pose().scale(size, size, 1.0F);
        int half = font.width(text) / 2;
        int top = -font.lineHeight / 2;
        graphics.drawString(font, text, -half + 1, top + 1, fade(COLOR_SHADOW, alpha), false);
        graphics.drawString(font, text, -half, top, fade(color, alpha), false);
        graphics.pose().popPose();
    }

    /** Blits a full 256x256 layer at the origin with the given alpha. */
    private static void sheet(GuiGraphics graphics, ResourceLocation texture, float alpha) {
        graphics.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ----------------------------------------------------------------- maths

    /** Decaying sine offset in design pixels, zero once WRONG_NANOS has elapsed. */
    private float shakeAmount(long now) {
        long age = now - wrongAtNanos;
        if (age < 0 || age >= WRONG_NANOS) {
            return 0.0F;
        }
        float decay = 1.0F - age / (float) WRONG_NANOS;
        return (float) Math.sin(age / 1.6E7D) * 3.0F * decay;
    }

    private static int slotX(int slot) {
        return slot < TOP_ROW_X.length
            ? TOP_ROW_X[slot]
            : BOTTOM_ROW_X[slot - TOP_ROW_X.length];
    }

    private static int slotY(int slot) {
        return slot < TOP_ROW_X.length ? TOP_ROW_Y : BOTTOM_ROW_Y;
    }

    private static float overshoot(float progress) {
        if (progress >= 1.0F) {
            return 1.0F;
        }
        // damped cosine: overshoots 1 and settles back
        return 1.0F - (float) (Math.pow(2.0D, -9.0D * progress) * Math.cos(progress * 14.0D));
    }

    private static int fade(int argb, float alpha) {
        int scaled = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    /** Lerps RGB only. Result is always opaque. */
    private static int blend(int from, int to, float amount) {
        float clamped = Mth.clamp(amount, 0.0F, 1.0F);
        int red = Mth.lerpInt(clamped, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int green = Mth.lerpInt(clamped, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int blue = Mth.lerpInt(clamped, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static ResourceLocation texture(String fileName) {
        return ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "textures/gui/pattern/" + fileName);
    }

    private record Burst(int x, int y, long startNanos, int color) {
    }

    private enum Phase {
        COUNTDOWN,
        PLAYING,
        CLEARED,
        OVER
    }
}
