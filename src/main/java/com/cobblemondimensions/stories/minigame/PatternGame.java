package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.quest.QuestReward;
import com.cobblemondimensions.stories.quest.QuestSaveData;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.ui.Hud;
import com.cobblemondimensions.stories.ui.StoriesText;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * One round of Pattern for one player.
 *
 * <p>The player is seated on an invisible armor stand, which keeps them in place,
 * and a seated client still reports its movement keys to the server every tick.
 * Forward, back and the two strafe keys are the four directions, sneaking leaves,
 * and jumping starts another round once one has ended. The board itself is an
 * overlay (see {@link PatternView}), not a menu, because a menu would take the
 * keyboard away from the game.
 */
final class PatternGame implements Hud.Screen {
    /** Marks the stands this game spawns, so any left behind by a crash can be found and removed. */
    static final String SEAT_TAG = "cds_pattern_seat";

    private static final Map<UUID, PatternGame> GAMES = new HashMap<>();
    private static final Set<UUID> SEATS = new HashSet<>();
    private static final long LONG_AGO = -60_000_000_000L;

    private final ServerPlayer player;
    private final PatternRound round;
    private final SessionSettlement settlement = new SessionSettlement();
    private final double originX;
    private final double originY;
    private final double originZ;
    private final float originYaw;
    private final float originPitch;
    private final int best;
    private ArmorStand seat;
    private int bestRecord;

    private PatternRound.Phase lastPhase;
    private int countdownBeat = -1;
    private int ticking = -1;
    private long wrongAt = LONG_AGO;
    private long poppedAt = LONG_AGO;
    private int poppedStep = -1;
    private long clearedAt = LONG_AGO;
    private long revealAt;
    private float lastStrafe;
    private float lastForward;
    private boolean lastJump;

    /** A round that follows another keeps the first one's starting spot, so a rematch cannot drift. */
    private PatternGame(ServerPlayer player, long now, PatternGame previous) {
        this.player = player;
        this.round = new PatternRound(new Random(player.getRandom().nextLong()), now);
        this.originX = previous != null ? previous.originX : player.getX();
        this.originY = previous != null ? previous.originY : player.getY();
        this.originZ = previous != null ? previous.originZ : player.getZ();
        this.originYaw = previous != null ? previous.originYaw : player.getYRot();
        this.originPitch = previous != null ? previous.originPitch : player.getXRot();
        this.best = QuestSaveData.get(player.server).bestOf(player.getUUID(), Minigame.PATTERN.getSerializedName());
        this.revealAt = now;
        this.lastPhase = round.phase();
    }

    /** Starts a round, replacing whatever overlay the player had up. */
    static void start(ServerPlayer player) {
        if (player.isPassenger() && !seatOf(player)) {
            player.displayClientMessage(Component.literal("Get off whatever you are riding to play.")
                .withStyle(ChatFormatting.YELLOW), false);
            return;
        }
        long now = System.nanoTime();
        PatternGame game = new PatternGame(player, now, GAMES.get(player.getUUID()));
        Hud.show(player, game);
        GAMES.put(player.getUUID(), game);
        game.sit();
        Hud.safely(player, game, () -> game.draw(now));
    }

    private static boolean seatOf(ServerPlayer player) {
        return player.getVehicle() instanceof ArmorStand stand && stand.getTags().contains(SEAT_TAG);
    }

    private void sit() {
        seat = new ArmorStand(player.serverLevel(), originX, originY, originZ);
        // a rider sits at the top of the stand's box, so start the stand low enough that
        // they stay at the height they stood at
        double lift = seat.getPassengerRidingPosition(player).y - player.getVehicleAttachmentPoint(seat).y - seat.getY();
        seat.setPos(originX, originY - lift, originZ);
        seat.setInvisible(true);
        seat.setNoGravity(true);
        seat.setInvulnerable(true);
        seat.setSilent(true);
        seat.addTag(SEAT_TAG);
        SEATS.add(seat.getUUID());
        player.serverLevel().addFreshEntity(seat);
        if (!player.startRiding(seat, true)) {
            Hud.close(player, this);
        }
    }

    /** Whether this is one of the stands this game seats players on. */
    static boolean isSeat(Entity entity) {
        return entity instanceof ArmorStand && entity.getTags().contains(SEAT_TAG);
    }

    /** Discards a seat that was saved with the world, such as after a crash. */
    static boolean strayed(Entity entity) {
        return isSeat(entity) && !SEATS.contains(entity.getUUID());
    }

    /** A player leaving mid-round forfeits it. */
    static void forget(ServerPlayer player) {
        PatternGame game = GAMES.get(player.getUUID());
        if (game != null) {
            game.settlement.discard();
        }
    }

    /** Reloads and server stops discard unfinished rounds, as they do for the menu games. */
    static void stop() {
        for (PatternGame game : List.copyOf(GAMES.values())) {
            game.settlement.discard();
            Hud.close(game.player, game);
        }
        GAMES.clear();
    }

    /** Ends the player's round, paying out what it earned, before another game takes over. */
    static void leave(ServerPlayer player) {
        PatternGame game = GAMES.get(player.getUUID());
        if (game != null) {
            Hud.close(player, game);
        }
    }

    // ------------------------------------------------------------ Hud.Screen

    @Override
    public void click() {
        // clicking does nothing here; the movement keys play the game
    }

    @Override
    public void move(float strafe, float forward, boolean jump, boolean sneak) {
        if (sneak) {
            Hud.close(player, this);
            return;
        }
        int direction = pressed(strafe, forward, lastStrafe, lastForward);
        boolean jumped = jump && !lastJump;
        lastStrafe = strafe;
        lastForward = forward;
        lastJump = jump;
        if (direction >= 0) {
            press(direction);
        } else if (jumped && round.phase() == PatternRound.Phase.OVER) {
            MinigameService.open(player, Minigame.PATTERN);
        }
    }

    /**
     * Which direction key was just pressed, or -1. A key counts when it goes down, not while
     * it is held; if two go down at once the forward and back keys win.
     */
    static int pressed(float strafe, float forward, float lastStrafe, float lastForward) {
        if (forward > 0 && lastForward <= 0) {
            return 0;
        } else if (forward < 0 && lastForward >= 0) {
            return 1;
        } else if (strafe > 0 && lastStrafe <= 0) {
            return 2;
        } else if (strafe < 0 && lastStrafe >= 0) {
            return 3;
        }
        return -1;
    }

    @Override
    public void tick(long now) {
        if (player.getVehicle() != seat) {
            // dismounted by sneaking, a teleport, or the seat being removed
            Hud.close(player, this);
            return;
        }
        round.advance(now);
        if (round.phase() == PatternRound.Phase.OVER) {
            settle(now);
        }
        if (round.phase() != lastPhase) {
            if (round.phase() == PatternRound.Phase.COUNTDOWN) {
                revealAt = now;
            }
            lastPhase = round.phase();
        }
        if (round.phase() == PatternRound.Phase.COUNTDOWN) {
            int beat = round.countdown(now);
            if (beat != countdownBeat) {
                countdownBeat = beat;
                sound(SoundEvents.NOTE_BLOCK_BELL.value(), beat == 0 ? 1.6F : 0.7F + (3 - beat) * 0.2F);
            }
        } else {
            countdownBeat = -1;
        }
        // the last three seconds tick, as in the original game
        int left = round.remainingSeconds(now);
        if (round.phase() == PatternRound.Phase.PLAYING && left >= 1 && left <= 3) {
            if (left != ticking) {
                ticking = left;
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6F + (3 - left) * 0.15F);
            }
        } else {
            ticking = -1;
        }
        draw(now);
    }

    @Override
    public void hidden() {
        settle(System.nanoTime());
        GAMES.remove(player.getUUID(), this);
        if (seat == null) {
            return;
        }
        // someone moved them (a teleport, a command): leave them where they are now
        boolean seated = player.getVehicle() == seat
            && player.server.getPlayerList().getPlayer(player.getUUID()) == player && player.isAlive();
        if (player.getVehicle() == seat) {
            player.stopRiding();
        }
        SEATS.remove(seat.getUUID());
        seat.discard();
        seat = null;
        if (seated) {
            player.teleportTo(player.serverLevel(), originX, originY, originZ, originYaw, originPitch);
        }
    }

    // ------------------------------------------------------------- the round

    private void press(int direction) {
        long now = System.nanoTime();
        PatternRound.Input result = round.press(direction, now);
        switch (result) {
            case WRONG -> {
                wrongAt = now;
                sound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6F);
            }
            case CORRECT -> {
                poppedAt = now;
                poppedStep = round.typed() - 1;
                sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F + round.typed() * 0.05F);
            }
            case CLEARED -> {
                poppedAt = now;
                poppedStep = round.sequence().length - 1;
                clearedAt = now;
                sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1F);
            }
            case IGNORED -> { }
        }
        if (round.phase() == PatternRound.Phase.OVER) {
            settle(now);
        }
        draw(now);
    }

    private void draw(long now) {
        Hud.draw(player, this, PatternView.compose(round, now,
            new PatternView.Effects(wrongAt, poppedAt, poppedStep, clearedAt, revealAt, Math.max(best, bestRecord))));
    }

    private void settle(long now) {
        round.finish(now);
        settlement.runOnce(() -> {
            int cleared = Math.min(40, round.levelsCleared());
            QuestSaveData.get(player.server).recordBest(player.getUUID(), Minigame.PATTERN.getSerializedName(), cleared);
            bestRecord = cleared;
            List<QuestReward> rewards = MinigameRewards.forPattern(cleared);
            rewards.forEach(reward -> QuestService.grant(player, reward));
            if (cleared > 0) {
                player.displayClientMessage(StoriesText.translatable(
                    "message.cobblemon_dimensions_stories.pattern_result", cleared).withStyle(ChatFormatting.AQUA), false);
            }
        });
    }

    private void sound(SoundEvent event, float pitch) {
        player.playNotifySound(event, SoundSource.PLAYERS, 0.55F, pitch);
    }
}
