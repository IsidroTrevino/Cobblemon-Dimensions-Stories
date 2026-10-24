package com.cobblemondimensions.stories.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The overlay a player has on screen: the walking dialogue box or the Pattern game
 * (see {@link com.cobblemondimensions.stories.ui.art.Overlay}).
 *
 * <p>Overlays are action bar text, so the player keeps looking around and, unless
 * the screen seats them, walking while one is up. Input has to be read from what the client tells the
 * server anyway: a left click arrives as an arm swing, and the scroll wheel as a
 * change of hotbar slot, which is put straight back. Opening an overlay closes
 * whichever one was there before.
 */
public final class Hud {
    /**
     * Holding the button keeps swinging the arm every tick against a block, so a
     * swing only counts as a click after a quiet gap. Two quick separate clicks
     * are further apart than this.
     */
    private static final long HOLD_GAP_NANOS = 120_000_000L;
    /** The action bar fades after three seconds, so a resting picture is sent again well before that. */
    private static final int KEEP_ALIVE_TICKS = 20;
    /**
     * Clicking through a conversation while looking at the ground would crack the
     * block. A short, invisible Mining Fatigue keeps the client from digging; the
     * server refuses the break either way.
     */
    private static final int FATIGUE_TICKS = 60;
    private static final int FATIGUE_AMPLIFIER = 4;

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_dimensions_stories/overlays");
    private static final Map<UUID, Entry> ACTIVE = new HashMap<>();

    private Hud() {
    }

    /** Something drawn as an overlay. */
    public interface Screen {
        /** A left click: activate, skip ahead or move on. */
        void click();

        /** The scroll wheel moved by {@code steps}, positive towards the next item. */
        default void scroll(int steps) {
        }

        /** Whether the wheel is being read right now; the held item is locked while it is. */
        default boolean scrolls() {
            return false;
        }

        /** The NPC this overlay belongs to; right-clicking it again counts as a click. */
        default boolean belongsTo(Entity entity) {
            return false;
        }

        /**
         * The movement keys as the client last reported them, which a seated player
         * keeps sending: strafe is positive to the left, forward positive ahead.
         */
        default void move(float strafe, float forward, boolean jump, boolean sneak) {
        }

        /** Animate and redraw; call {@link Hud#draw} with the picture. */
        void tick(long now);

        /** It was replaced or closed. */
        void hidden();
    }

    private static final class Entry {
        private final ServerPlayer player;
        private final Screen screen;
        private long lastSwingNanos;
        private int heldSlot = -1;
        private boolean fatigued;
        private Component lastSent;
        private int lastSentTick = Integer.MIN_VALUE;

        private Entry(ServerPlayer player, Screen screen) {
            this.player = player;
            this.screen = screen;
        }
    }

    /** Puts {@code screen} up for the player, replacing whatever overlay was showing. */
    public static void show(ServerPlayer player, Screen screen) {
        Entry previous = ACTIVE.get(player.getUUID());
        if (previous != null && previous.screen == screen) {
            return;
        }
        Entry entry = new Entry(player, screen);
        // the click that opened it also swung the arm
        entry.lastSwingNanos = System.nanoTime();
        if (previous != null) {
            ACTIVE.remove(player.getUUID());
            release(previous);
            entry.fatigued = previous.fatigued;
            previous.screen.hidden();
        }
        ACTIVE.put(player.getUUID(), entry);
        fatigue(entry, true);
    }

    /** Takes {@code screen} down if it is the one showing. */
    public static void close(ServerPlayer player, Screen screen) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null || entry.screen != screen) {
            return;
        }
        ACTIVE.remove(player.getUUID());
        release(entry);
        fatigue(entry, false);
        player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
        screen.hidden();
    }

    public static Optional<Screen> current(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        return entry == null ? Optional.empty() : Optional.of(entry.screen);
    }

    public static boolean active(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** Sends the overlay's picture, skipping repeats except to keep it from fading. */
    public static void draw(ServerPlayer player, Screen screen, Component picture) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null || entry.screen != screen) {
            return;
        }
        int tick = player.server.getTickCount();
        if (picture.equals(entry.lastSent) && tick - entry.lastSentTick < KEEP_ALIVE_TICKS) {
            return;
        }
        player.connection.send(new ClientboundSetActionBarTextPacket(picture));
        entry.lastSent = picture;
        entry.lastSentTick = tick;
    }

    /** An arm swing from the player. Only the first of a held-down stream is a click. */
    public static void swing(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null) {
            return;
        }
        long now = System.nanoTime();
        boolean press = now - entry.lastSwingNanos > HOLD_GAP_NANOS;
        entry.lastSwingNanos = now;
        if (press) {
            run(entry, entry.screen::click);
        }
    }

    /** Right clicks swing the arm too; the swing that follows one is not a left click. */
    public static void ignoreNextSwing(ServerPlayer player) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry != null) {
            entry.lastSwingNanos = System.nanoTime();
        }
    }

    /**
     * Right-clicking the NPC an overlay belongs to counts as a click on it, so it
     * moves the conversation on instead of starting it again. Returns whether the
     * interaction was used.
     */
    public static boolean useOn(ServerPlayer player, Entity entity) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry == null || player.isShiftKeyDown() || !entry.screen.belongsTo(entity)) {
            return false;
        }
        long now = System.nanoTime();
        boolean press = now - entry.lastSwingNanos > HOLD_GAP_NANOS;
        entry.lastSwingNanos = now;
        if (press) {
            run(entry, entry.screen::click);
        }
        return true;
    }

    /** A movement report from the player, read straight off the packet so quick taps are not lost. */
    public static void input(ServerPlayer player, float strafe, float forward, boolean jump, boolean sneak) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry != null) {
            run(entry, () -> entry.screen.move(strafe, forward, jump, sneak));
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (Entry entry : List.copyOf(ACTIVE.values())) {
            ServerPlayer player = entry.player;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player || !player.isAlive()) {
                ACTIVE.remove(player.getUUID(), entry);
                entry.screen.hidden();
                continue;
            }
            scroll(entry);
            if (server.getTickCount() % 20 == 0) {
                fatigue(entry, true);
            }
            if (ACTIVE.get(player.getUUID()) == entry) {
                run(entry, () -> entry.screen.tick(now));
            }
        }
    }

    /** Drops a player who left without anything being sent to them. */
    public static void forget(ServerPlayer player) {
        Entry entry = ACTIVE.remove(player.getUUID());
        if (entry != null) {
            entry.screen.hidden();
        }
    }

    public static void stop() {
        for (Entry entry : List.copyOf(ACTIVE.values())) {
            close(entry.player, entry.screen);
        }
        ACTIVE.clear();
    }

    /** Runs {@code action} for an overlay that is showing, under the same guard as its ticks. */
    public static void safely(ServerPlayer player, Screen screen, Runnable action) {
        Entry entry = ACTIVE.get(player.getUUID());
        if (entry != null && entry.screen == screen) {
            run(entry, action);
        }
    }

    /**
     * Calls into an overlay. A fault in one should cost the player that screen, not
     * crash the server, so it is logged and the overlay is taken down.
     */
    private static void run(Entry entry, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Closing a broken {} for {}", entry.screen.getClass().getSimpleName(),
                entry.player.getGameProfile().getName(), exception);
            if (ACTIVE.get(entry.player.getUUID()) == entry) {
                ACTIVE.remove(entry.player.getUUID());
                release(entry);
                fatigue(entry, false);
                entry.player.connection.send(new ClientboundSetActionBarTextPacket(Component.empty()));
                try {
                    entry.screen.hidden();
                } catch (RuntimeException ignored) {
                    // already logged the original fault
                }
            }
        }
    }

    /** Turns a change of hotbar slot into scroll steps, then puts the slot back. */
    private static void scroll(Entry entry) {
        ServerPlayer player = entry.player;
        if (!entry.screen.scrolls()) {
            entry.heldSlot = -1;
            return;
        }
        int current = player.getInventory().selected;
        if (entry.heldSlot < 0) {
            entry.heldSlot = current;
            return;
        }
        if (current == entry.heldSlot) {
            return;
        }
        // the wheel moves one slot per notch; a far jump is the wheel wrapping round
        int delta = Math.floorMod(current - entry.heldSlot, 9);
        int steps = delta <= 4 ? delta : delta - 9;
        player.getInventory().selected = entry.heldSlot;
        player.connection.send(new ClientboundSetCarriedItemPacket(entry.heldSlot));
        run(entry, () -> entry.screen.scroll(steps));
    }

    private static void release(Entry entry) {
        entry.heldSlot = -1;
    }

    private static void fatigue(Entry entry, boolean on) {
        ServerPlayer player = entry.player;
        MobEffectInstance current = player.getEffect(MobEffects.DIG_SLOWDOWN);
        if (on) {
            // never cover up a real Mining Fatigue the player already has
            if (current == null || entry.fatigued && ours(current)) {
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, FATIGUE_TICKS, FATIGUE_AMPLIFIER,
                    false, false, false));
                entry.fatigued = true;
            }
        } else if (entry.fatigued && current != null && ours(current)) {
            player.removeEffect(MobEffects.DIG_SLOWDOWN);
            entry.fatigued = false;
        }
    }

    private static boolean ours(MobEffectInstance effect) {
        return effect.getAmplifier() == FATIGUE_AMPLIFIER && !effect.isVisible() && !effect.showIcon();
    }
}
