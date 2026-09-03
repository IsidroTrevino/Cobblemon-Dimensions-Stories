package com.cobblemondimensions.stories.client.minigame;

import com.cobblemondimensions.stories.minigame.Minigame;
import com.cobblemondimensions.stories.minigame.PokesweeperMode;
import com.cobblemondimensions.stories.net.OpenMinigameC2S;
import com.cobblemondimensions.stories.net.PokesweeperResultC2S;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/**
 * Pokesweeper: minesweeper on a field of buried TNT.
 *
 * <p>{@link PokesweeperBoard} owns the game state. This screen draws it and
 * turns clicks into digs, chords and flags. Panel, top bar and readout art are
 * picked per {@link PokesweeperMode} because easy and medium ship their own
 * smaller sheets rather than cropping the hard one.
 *
 * <p>Every art layer is a full 256x256 sheet blitted at (0,0), which is what
 * keeps the layers aligned without per-mode offsets. Cells are stamped out of
 * {@code cell.png} (the raised face of cell 0,0); one file serves all three
 * sizes since the grid origin and pitch are identical.
 */
public final class PokesweeperScreen extends Screen {
    private static final int DESIGN = 256;

    /** Grid origin and cell pitch, in design pixels. Same for all three modes. */
    private static final int GRID_X = 56;
    private static final int GRID_Y = 47;
    private static final int CELL_W = 9;
    private static final int CELL_H = 8;

    /** Raised face inside a cell, inset 1px. Counts and TNT centre on this. */
    private static final int FACE_W = 8;
    private static final int FACE_H = 7;

    // top-bar wells, plus the left edge of the status strip between them
    private static final int WELL_Y = 21;
    private static final int WELL_H = 19;
    private static final int MINES_CENTER_X = 73;
    private static final int STATUS_X0 = 88;

    private static final long OPEN_NANOS = 320_000_000L;
    private static final long LID_NANOS = 130_000_000L;
    private static final long SHAKE_NANOS = 620_000_000L;
    private static final long FLASH_NANOS = 520_000_000L;
    private static final long RING_NANOS = 900_000_000L;

    /** Cap on dig sounds per click. A big pocket would otherwise queue dozens. */
    private static final int MAX_DIG_CUES = 10;

    private static final int COLOR_LED = 0xFFFF4136;
    private static final int COLOR_LED_GHOST = 0xFF3D1512;
    private static final int COLOR_LABEL = 0xFF2A1C2E;
    private static final int COLOR_LABEL_HOT = 0xFF7A2018;
    private static final int COLOR_WIN = 0xFF4BE07A;
    private static final int COLOR_HOVER = 0x40FFFFFF;
    private static final int COLOR_WRONG = 0xFFE04B4B;

    /** Classic 1-8 count colours, warmed up to read against this palette. Index 0 is unused. */
    private static final int[] COUNT_COLORS = {
        0xFF000000, 0xFF6FA8FF, 0xFF63D471, 0xFFFF6B6B, 0xFFFFD166,
        0xFFC98BFF, 0xFF5FE3D8, 0xFFFFFFFF, 0xFFB9AEC4
    };

    /** Not per-mode. Every size stamps from the same sheet. */
    private static final ResourceLocation CELL = texture("cell.png");

    private static ItemStack flagStack = ItemStack.EMPTY;

    private final int best;
    private final PokesweeperMode mode;
    private final Layout layout;
    private final ResourceLocation corners;
    private final ResourceLocation topbar;
    private final ResourceLocation screens;
    private final ResourceLocation gridOpen;

    private PokesweeperBoard board;
    private long openedNanos;
    private long startedNanos = Long.MIN_VALUE;
    private long endedNanos = Long.MIN_VALUE;
    private long shakenNanos = Long.MIN_VALUE;
    private long countChangedNanos = Long.MIN_VALUE;
    private boolean reported;
    private int hovered = -1;
    private int lastSecond = -1;

    private float panelScale = 1.0F;
    private float originX;
    private float originY;

    public PokesweeperScreen(Minigame game, int best) {
        super(Component.translatable("screen.cobblemon_dimensions_stories.pokesweeper"));
        this.best = best;
        this.mode = PokesweeperMode.of(game);
        this.layout = Layout.of(mode);
        String folder = "textures/gui/pokesweeper/" + mode.name().toLowerCase(Locale.ROOT) + "/";
        this.corners = ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", folder + "corners.png");
        this.topbar = ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", folder + "topbar.png");
        this.screens = ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", folder + "screens.png");
        this.gridOpen = ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", folder + "grid_open.png");
        this.board = new PokesweeperBoard(RandomSource.create(), mode);
    }

    /** Which mode is on screen. Retry re-requests this same one. */
    public Minigame minigame() {
        return mode.minigame();
    }

    /** Deals a fresh field. Called when the server answers a retry. */
    public void restart() {
        board = new PokesweeperBoard(RandomSource.create(), mode);
        openedNanos = System.nanoTime();
        startedNanos = Long.MIN_VALUE;
        endedNanos = Long.MIN_VALUE;
        shakenNanos = Long.MIN_VALUE;
        countChangedNanos = Long.MIN_VALUE;
        reported = false;
        hovered = -1;
        lastSecond = -1;
        MinigameSounds.reset();
        MinigameSounds.select();
    }

    @Override
    protected void init() {
        openedNanos = System.nanoTime();
    }

    @Override
    public void removed() {
        MinigameSounds.reset();
    }

    // must not pause, the run timer is real time
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

        int scale = Mth.clamp(
            Math.min(graphics.guiWidth() * 9 / 10 / layout.panelW,
                graphics.guiHeight() * 9 / 10 / layout.panelH),
            1, 4);
        panelScale = scale;
        originX = graphics.guiWidth() / 2.0F - scale * layout.panelCenterX;
        originY = graphics.guiHeight() / 2.0F - scale * layout.panelCenterY;

        float grow = overshoot(Mth.clamp((now - openedNanos) / (float) OPEN_NANOS, 0.0F, 1.0F));
        float shake = shakeAmount(now);
        int designX = Math.round((mouseX - originX) / scale);
        int designY = Math.round((mouseY - originY) / scale);
        hovered = cellAt(designX, designY);
        tickClock(now);

        graphics.pose().pushPose();
        graphics.pose().translate(graphics.guiWidth() / 2.0F, graphics.guiHeight() / 2.0F, 0.0F);
        graphics.pose().scale(scale * grow, scale * grow, 1.0F);
        graphics.pose().translate(-layout.panelCenterX + shake, -layout.panelCenterY, 0.0F);

        sheet(graphics, corners, 1.0F);
        sheet(graphics, topbar, 1.0F);
        sheet(graphics, screens, 1.0F);

        drawReadouts(graphics, now);
        drawStatus(graphics, now, designX, designY);
        drawGrid(graphics, now);
        drawBlast(graphics, now);

        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The two dark wells: mines still unflagged on the left, seconds on the right. */
    private void drawReadouts(GuiGraphics graphics, long now) {
        float pop = countChangedNanos == Long.MIN_VALUE
            ? 1.0F
            : 1.0F + 0.25F * (1.0F - Mth.clamp((now - countChangedNanos) / 2.0E8F, 0.0F, 1.0F));
        led(graphics, digits(board.minesLeft()), MINES_CENTER_X, pop);

        // one pulse per second, frozen once the board is over
        float beat = 1.0F + 0.12F * Math.max(0.0F, 1.0F - (elapsed(now) % 1.0F) * 6.0F);
        led(graphics, digits((int) elapsed(now)), layout.timerCenterX, board.over() ? 1.0F : beat);
    }

    /** One three-digit readout, drawn over a dim "888" so the unlit segments show. */
    private void led(GuiGraphics graphics, String value, int centerX, float scale) {
        int width = font.width(value);
        float centerY = WELL_Y + WELL_H / 2.0F;
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, "888", -width / 2, -font.lineHeight / 2 + 1, COLOR_LED_GHOST, false);
        graphics.drawString(font, value, -width / 2, -font.lineHeight / 2 + 1, COLOR_LED, false);
        graphics.pose().popPose();
    }

    /** Label strip between the two wells. Doubles as the retry button. */
    private void drawStatus(GuiGraphics graphics, long now, int mouseX, int mouseY) {
        boolean hoveredHere = mouseX >= STATUS_X0 && mouseX <= layout.statusX1
            && mouseY >= WELL_Y && mouseY <= WELL_Y + WELL_H;

        String label;
        int color;
        if (board.lost()) {
            label = "BOOM - RETRY";
            color = COLOR_LABEL_HOT;
        } else if (board.won()) {
            label = "SWEPT CLEAN";
            color = COLOR_WIN;
        } else if (!board.started()) {
            label = "DIG ANYWHERE";
            color = COLOR_LABEL;
        } else {
            label = board.uncovered() + "/" + board.safeCells();
            color = COLOR_LABEL;
        }

        // pulse and lift on hover only once the board is finished, to hint at retry
        float pulse = board.over()
            ? 0.75F + 0.25F * (float) Math.sin(now / 1.8E8D)
            : 1.0F;
        float lift = hoveredHere && board.over() ? 1.0F : 0.0F;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, -lift, 0.0F);
        int width = font.width(label);
        float size = Math.min(1.0F, (layout.statusX1 - STATUS_X0 - 6) / (float) width);
        graphics.pose().scale(size, size, 1.0F);
        graphics.drawString(
            font, label,
            Math.round((layout.statusCenterX - width * size / 2.0F) / size),
            Math.round((WELL_Y + WELL_H / 2.0F - font.lineHeight * size / 2.0F + 1.0F) / size),
            fade(color, pulse), false);
        graphics.pose().popPose();

        if (best > 0 && !board.started()) {
            // best is stored as seconds saved off the 3600 cap, invert it to show a time
            caption(graphics, "BEST " + (3600 - best) + "S", layout.statusCenterX, WELL_Y + WELL_H - 3);
        }
    }

    private void drawGrid(GuiGraphics graphics, long now) {
        // start from the fully-dug sheet, then stamp the raised face back onto
        // whatever is still shut
        sheet(graphics, gridOpen, 1.0F);

        for (int index = 0; index < board.cells(); index++) {
            int col = index % board.cols();
            int row = index / board.cols();
            int x = GRID_X + col * CELL_W;
            int y = GRID_Y + row * CELL_H;
            float lid = lidAmount(index, now);

            if (lid > 0.0F) {
                stampCell(graphics, x, y, lid, index == hovered && !board.over());
            }
            if (lid < 1.0F) {
                drawContents(graphics, index, col, row, now, 1.0F - lid);
            }
            if (board.state(index) == PokesweeperBoard.Cell.FLAGGED) {
                drawFlag(graphics, col, row, now, board.over() && board.wrongFlag(index));
            }
        }
    }

    /**
     * 1 while the lid is on, easing to 0 over LID_NANOS as it comes off.
     * Negative age means the board scheduled this cell for later in a cascade.
     */
    private float lidAmount(int index, long now) {
        if (board.state(index) != PokesweeperBoard.Cell.REVEALED) {
            return 1.0F;
        }
        long age = now - board.openAt(index);
        if (age < 0) {
            return 1.0F;
        }
        return 1.0F - Mth.clamp(age / (float) LID_NANOS, 0.0F, 1.0F);
    }

    /**
     * Stamps one raised cell face, squashing it toward the centre as the lid
     * comes off. Pulls a CELL_W+1 x CELL_H+1 sub-rect from the grid origin
     * rather than translating the whole 256x256 sheet into place, which would
     * be a full-sheet blit per cell per frame.
     */
    private void stampCell(GuiGraphics graphics, int x, int y, float lid, boolean lit) {
        graphics.pose().pushPose();
        if (lid < 1.0F) {
            float centerX = x + CELL_W / 2.0F;
            float centerY = y + CELL_H / 2.0F;
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().scale(lid, lid, 1.0F);
            graphics.pose().translate(-centerX, -centerY, 0.0F);
        }
        graphics.blit(CELL, x, y, GRID_X, GRID_Y, CELL_W + 1, CELL_H + 1, DESIGN, DESIGN);
        graphics.pose().popPose();

        if (lit) {
            graphics.fill(x + 1, y + 1, x + 1 + FACE_W, y + 1 + FACE_H, COLOR_HOVER);
        }
    }

    /** What was under the lid: nothing, a count, or a stick of TNT. */
    private void drawContents(GuiGraphics graphics, int index, int col, int row, long now,
                              float open) {
        float centerX = GRID_X + col * CELL_W + 1 + FACE_W / 2.0F;
        float centerY = GRID_Y + row * CELL_H + 1 + FACE_H / 2.0F;

        if (board.mine(index)) {
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0.0F);
            // half size, plus an overshoot pop so it lands instead of appearing
            graphics.pose().scale(0.5F * (0.6F + 0.4F * overshoot(open)),
                0.5F * (0.6F + 0.4F * overshoot(open)), 1.0F);
            graphics.renderItem(new ItemStack(Items.TNT), -8, -8);
            graphics.pose().popPose();
            return;
        }

        int count = board.count(index);
        if (count == 0) {
            return;
        }
        String text = Integer.toString(count);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().scale(open, open, 1.0F);
        graphics.drawString(font, text, -font.width(text) / 2, -font.lineHeight / 2 + 1,
            COUNT_COLORS[Math.min(count, COUNT_COLORS.length - 1)], false);
        graphics.pose().popPose();
    }

    /** Poke Ball marker on a flagged cell, bobbing on an offset sine per cell. */
    private void drawFlag(GuiGraphics graphics, int col, int row, long now, boolean wrong) {
        float centerX = GRID_X + col * CELL_W + 1 + FACE_W / 2.0F;
        float centerY = GRID_Y + row * CELL_H + 1 + FACE_H / 2.0F;
        float bob = (float) Math.sin(now / 3.0E8D + col + row) * 0.4F;

        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY + bob, 0.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.renderItem(flag(), -8, -8);
        graphics.pose().popPose();

        if (wrong) {
            // 1px bar through the face, marking a flag that was in the wrong place
            int x = GRID_X + col * CELL_W + 1;
            int y = GRID_Y + row * CELL_H + 1;
            graphics.fill(x, y + FACE_H / 2, x + FACE_W, y + FACE_H / 2 + 1, COLOR_WRONG);
        }
    }

    /** Red wash over the grid plus three rings expanding from the mine that was hit. */
    private void drawBlast(GuiGraphics graphics, long now) {
        if (!board.lost() || endedNanos == Long.MIN_VALUE) {
            return;
        }
        float age = (now - endedNanos) / (float) FLASH_NANOS;
        if (age < 1.0F) {
            graphics.fill(GRID_X, GRID_Y,
                GRID_X + board.cols() * CELL_W,
                GRID_Y + board.rows() * CELL_H,
                fade(0xFFFF3B30, 0.35F * (1.0F - age)));
        }

        int epicentre = board.epicentre();
        if (epicentre < 0) {
            return;
        }
        float ring = Mth.clamp((now - endedNanos) / (float) RING_NANOS, 0.0F, 1.0F);
        if (ring >= 1.0F) {
            return;
        }
        int centerX = GRID_X + (epicentre % board.cols()) * CELL_W + CELL_W / 2;
        int centerY = GRID_Y + (epicentre / board.cols()) * CELL_H + CELL_H / 2;
        for (int wave = 0; wave < 3; wave++) {
            float phase = ring - wave * 0.16F;
            if (phase <= 0.0F || phase >= 1.0F) {
                continue;
            }
            int radius = Math.round(phase * 62.0F);
            ring(graphics, centerX - radius, centerY - radius, centerX + radius, centerY + radius,
                fade(0xFFFFC44D, 0.7F * (1.0F - phase)));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = Math.round((float) (mouseX - originX) / panelScale);
        int y = Math.round((float) (mouseY - originY) / panelScale);

        if (x >= STATUS_X0 && x <= layout.statusX1 && y >= WELL_Y && y <= WELL_Y + WELL_H) {
            if (board.over()) {
                retry();
            }
            return true;
        }

        int index = cellAt(x, y);
        if (index < 0 || board.over()) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        long now = System.nanoTime();
        if (button == 1) {
            boolean placed = board.toggleFlag(index);
            countChangedNanos = now;
            MinigameSounds.flag(placed);
            return true;
        }

        // left click on an already-open cell means chord, otherwise dig
        int[] opened = board.state(index) == PokesweeperBoard.Cell.REVEALED
            ? board.chord(index, now)
            : board.dig(index, now);
        if (board.started() && startedNanos == Long.MIN_VALUE) {
            startedNanos = now;
        }
        announce(opened, now);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_R && board.over()) {
            retry();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ logic

    /**
     * Queues the dig cues for one click and, if that click ended the board,
     * plays the win or lose sequence and reports the result once.
     */
    private void announce(int[] opened, long now) {
        for (int slot = 0; slot < Math.min(opened.length, MAX_DIG_CUES); slot++) {
            int cell = opened[slot];
            long delay = Math.max(0L, (board.openAt(cell) - now) / 1_000_000L);
            MinigameSounds.digAfter(delay, board.count(cell));
        }

        if (!board.over() || endedNanos != Long.MIN_VALUE) {
            return;
        }
        endedNanos = now;
        if (board.lost()) {
            shakenNanos = now;
            MinigameSounds.fuse();
            MinigameSounds.boom(150L);
            // two more booms trailing the first, matching the chain on the board
            MinigameSounds.boom(420L);
            MinigameSounds.boom(700L);
        } else {
            MinigameSounds.sweep();
        }
        report();
    }

    /**
     * Sends the result, once per board. Scoring happens here on the client and
     * the server only pays out against a session it opened itself.
     */
    private void report() {
        if (reported) {
            return;
        }
        reported = true;
        ClientPlayNetworking.send(new PokesweeperResultC2S(
            board.won(), board.uncovered(), Math.max(1, Math.round(elapsed(System.nanoTime())))));
    }

    /** Asks the server for another session. The reply lands in restart(). */
    private void retry() {
        ClientPlayNetworking.send(new OpenMinigameC2S(mode.minigame()));
    }

    /** Audible tick every 30s on a live board. Past 2 minutes it switches to the urgent one. */
    private void tickClock(long now) {
        if (!board.started() || board.over()) {
            return;
        }
        int second = (int) elapsed(now);
        if (second != lastSecond) {
            lastSecond = second;
            if (second > 0 && second % 30 == 0) {
                MinigameSounds.tick(second >= 120);
            }
        }
    }

    private float elapsed(long now) {
        if (startedNanos == Long.MIN_VALUE) {
            return 0.0F;
        }
        long end = board.over() && endedNanos != Long.MIN_VALUE ? endedNanos : now;
        return Math.max(0.0F, (end - startedNanos) / 1.0E9F);
    }

    /** Cell under a point in design space, or -1 if it misses the grid. */
    private int cellAt(int x, int y) {
        int col = Math.floorDiv(x - GRID_X, CELL_W);
        int row = Math.floorDiv(y - GRID_Y, CELL_H);
        return board.onBoard(col, row) ? board.index(col, row) : -1;
    }

    private static String digits(int value) {
        int clamped = Mth.clamp(value, -99, 999);
        return clamped < 0
            ? "-" + String.format("%02d", -clamped)
            : String.format("%03d", clamped);
    }

    private static ItemStack flag() {
        if (flagStack.isEmpty()) {
            flagStack = BuiltInRegistries.ITEM
                .getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"))
                .map(ItemStack::new)
                .orElseGet(() -> new ItemStack(Items.RED_BANNER));
        }
        return flagStack;
    }

    // ---------------------------------------------------------------- drawing

    private void caption(GuiGraphics graphics, String text, int centerX, int centerY) {
        int width = font.width(text);
        graphics.pose().pushPose();
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.drawString(font, text,
            Math.round((centerX - width * 0.25F) / 0.5F),
            Math.round((centerY - 2.0F) / 0.5F),
            fade(COLOR_LABEL, 0.8F), false);
        graphics.pose().popPose();
    }

    private static void ring(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    // one art layer: full 256x256 sheet at (0,0)
    private static void sheet(GuiGraphics graphics, ResourceLocation texture, float alpha) {
        graphics.setColor(1.0F, 1.0F, 1.0F, Mth.clamp(alpha, 0.0F, 1.0F));
        graphics.blit(texture, 0, 0, 0.0F, 0.0F, DESIGN, DESIGN, DESIGN, DESIGN);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** Horizontal jolt after a detonation, damped to nothing over SHAKE_NANOS. */
    private float shakeAmount(long now) {
        if (shakenNanos == Long.MIN_VALUE) {
            return 0.0F;
        }
        float age = (now - shakenNanos) / (float) SHAKE_NANOS;
        if (age >= 1.0F) {
            return 0.0F;
        }
        return (float) Math.sin(age * 34.0D) * 2.6F * (1.0F - age);
    }

    /** Back ease. Overshoots past 1 then settles. Used by the panel open and the TNT pop. */
    private static float overshoot(float progress) {
        float back = 1.7F;
        float shifted = progress - 1.0F;
        return shifted * shifted * ((back + 1.0F) * shifted + back) + 1.0F;
    }

    private static int fade(int color, float alpha) {
        int base = Math.round(((color >>> 24) == 0 ? 255 : color >>> 24) * Mth.clamp(alpha, 0.0F, 1.0F));
        return base << 24 | color & 0x00FFFFFF;
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            "cobblemon_dimensions_stories", "textures/gui/pokesweeper/" + name);
    }

    /**
     * Frame bounds and readout positions that differ per board size, measured
     * off each mode's own corners and screens sheets. Grid origin and pitch are
     * shared by all three and stay as constants above.
     */
    private record Layout(
        int panelW, int panelH, int panelCenterX, int panelCenterY,
        int timerCenterX, int statusX1, int statusCenterX
    ) {
        static Layout of(PokesweeperMode mode) {
            return switch (mode) {
                case EASY -> new Layout(92, 112, 97, 69, 121, 104, 96);
                case MEDIUM -> new Layout(119, 136, 110, 81, 148, 131, 109);
                case HARD -> new Layout(155, 168, 128, 97, 183, 166, 127);
            };
        }
    }
}
