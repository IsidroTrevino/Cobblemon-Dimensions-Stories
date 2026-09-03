package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.net.PokewrleFeedbackS2C;
import com.cobblemondimensions.stories.net.StartMinigameS2C;
import com.cobblemondimensions.stories.quest.QuestSaveData;
import com.cobblemondimensions.stories.quest.QuestService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the minigames: opens sessions, scores Pokewrle guesses against
 * an answer the client never sees, and pays out. Pattern and Pokesweeper are
 * scored on the client, and the server only accepts a result for a session it
 * opened for that player, which stops an idle client minting rewards in a loop.
 */
public final class MinigameService {
    /** Guesses per Pokewrle round before the answer is revealed. */
    public static final int POKEWRLE_ATTEMPTS = 6;

    /** Generation filter value meaning "any generation". */
    public static final int ALL_GENERATIONS = 0;

    /** Sweep times are stored as this minus the seconds taken. Records only ever grow. */
    private static final int RECORD_CAP = 3600;

    // open sessions, one entry per player
    private static final Map<UUID, PokewrleRound> POKEWRLE = new HashMap<>();
    private static final Map<UUID, Boolean> PATTERN = new HashMap<>();
    private static final Map<UUID, Minigame> POKESWEEPER = new HashMap<>();

    private MinigameService() {
    }

    /** Opens a session and tells the client to draw the board. */
    public static void open(ServerPlayer player, Minigame game) {
        if (PokedexSource.all().isEmpty() && game == Minigame.POKEWRLE) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_dimensions_stories.pokewrle_unavailable")
                    .withStyle(ChatFormatting.RED),
                false);
            return;
        }

        switch (game) {
            case PATTERN -> {
                PATTERN.put(player.getUUID(), Boolean.TRUE);
                ServerPlayNetworking.send(
                    player, new StartMinigameS2C(game, best(player, game), POKEWRLE_ATTEMPTS, 0));
            }
            case POKEWRLE -> setup(player, ALL_GENERATIONS);
            case POKESWEEPER, POKESWEEPER_EASY, POKESWEEPER_MEDIUM -> {
                POKESWEEPER.put(player.getUUID(), game);
                ServerPlayNetworking.send(
                    player, new StartMinigameS2C(game, best(player, game), POKEWRLE_ATTEMPTS, 0));
            }
        }
    }

    /**
     * Rolls a fresh Pokewrle answer out of {@code generation}. Picking a generation
     * is what starts a round, so switching it mid-game rerolls the answer and gives
     * the attempts back.
     */
    public static void setup(ServerPlayer player, int generation) {
        int wanted = generation < 1 || generation > 9 ? ALL_GENERATIONS : generation;
        List<PokedexSource.Entry> pool = PokedexSource.ofGeneration(wanted);
        if (pool.isEmpty()) {
            // nothing implemented for that gen, fall back to the whole dex
            wanted = ALL_GENERATIONS;
            pool = PokedexSource.all();
        }
        if (pool.isEmpty()) {
            return;
        }

        PokedexSource.Entry answer = pool.get(player.getRandom().nextInt(pool.size()));
        POKEWRLE.put(player.getUUID(), new PokewrleRound(answer, wanted));
        ServerPlayNetworking.send(player, new StartMinigameS2C(
            Minigame.POKEWRLE, best(player, Minigame.POKEWRLE), POKEWRLE_ATTEMPTS, wanted));
    }

    private static int best(ServerPlayer player, Minigame game) {
        return QuestSaveData.get(player.server).bestOf(player.getUUID(), game.getSerializedName());
    }

    /** Pays out a Pattern run. Does nothing if the server never opened one. */
    public static void finishPattern(ServerPlayer player, int levelsCleared) {
        if (PATTERN.remove(player.getUUID()) == null) {
            return;
        }

        int cleared = Math.max(0, Math.min(levelsCleared, 40));
        QuestSaveData.get(player.server)
            .recordBest(player.getUUID(), Minigame.PATTERN.getSerializedName(), cleared);
        MinigameRewards.forPattern(cleared).forEach(reward -> QuestService.grant(player, reward));
        if (cleared > 0) {
            player.displayClientMessage(
                Component.translatable("message.cobblemon_dimensions_stories.pattern_result", cleared)
                    .withStyle(ChatFormatting.AQUA),
                false);
        }
    }

    /**
     * Pays out a finished Pokesweeper board. A clear records the time taken, which
     * makes the saved record the fastest sweep rather than the most recent one.
     */
    public static void finishPokesweeper(ServerPlayer player, boolean cleared, int uncovered,
                                         int seconds) {
        Minigame game = POKESWEEPER.remove(player.getUUID());
        if (game == null) {
            return;
        }

        int safeCells = PokesweeperMode.of(game).safeCells();
        int safe = Math.max(0, Math.min(uncovered, safeCells));
        int taken = Math.max(1, Math.min(seconds, RECORD_CAP));
        if (cleared && safe >= safeCells) {
            // recordBest keeps the larger value, so store time saved against the
            // cap. Each size keys its own record; an Easy clear is not comparable
            // to a Hard one.
            QuestSaveData.get(player.server).recordBest(
                player.getUUID(), game.getSerializedName(), RECORD_CAP - taken);
        }

        MinigameRewards.forPokesweeper(cleared, safe, safeCells)
            .forEach(reward -> QuestService.grant(player, reward));
        player.displayClientMessage(
            Component.translatable(
                    cleared
                        ? "message.cobblemon_dimensions_stories.pokesweeper_cleared"
                        : "message.cobblemon_dimensions_stories.pokesweeper_result",
                    cleared ? taken : safe)
                .withStyle(cleared ? ChatFormatting.GREEN : ChatFormatting.RED),
            false);
    }

    /** Scores one guess. On the last attempt or a win it pays out and reveals the answer. */
    public static void guess(ServerPlayer player, String typed) {
        PokewrleRound round = POKEWRLE.get(player.getUUID());
        if (round == null) {
            return;
        }

        // A name outside the chosen generation cannot be the answer. Reject it like
        // a misspelling instead of charging an attempt for it.
        Optional<PokedexSource.Entry> found = PokedexSource.byName(typed)
            .filter(entry -> round.generation == ALL_GENERATIONS
                || entry.generation() == round.generation);
        if (found.isEmpty()) {
            ServerPlayNetworking.send(player, new PokewrleFeedbackS2C(
                typed,
                List.of(),
                POKEWRLE_ATTEMPTS - round.used,
                PokewrleFeedbackS2C.Outcome.REJECTED,
                Optional.empty(),
                Optional.empty()));
            return;
        }

        PokedexSource.Entry guess = found.get();
        round.used++;
        boolean won = guess.id().equals(round.answer.id());
        boolean over = won || round.used >= POKEWRLE_ATTEMPTS;

        PokewrleFeedbackS2C.Outcome outcome;
        if (won) {
            outcome = PokewrleFeedbackS2C.Outcome.WON;
        } else if (over) {
            outcome = PokewrleFeedbackS2C.Outcome.LOST;
        } else {
            outcome = PokewrleFeedbackS2C.Outcome.CONTINUE;
        }

        if (over) {
            POKEWRLE.remove(player.getUUID());
        }
        if (won) {
            // record is guesses left over, so an early win scores higher
            QuestSaveData.get(player.server).recordBest(
                player.getUUID(),
                Minigame.POKEWRLE.getSerializedName(),
                POKEWRLE_ATTEMPTS - round.used + 1);
            MinigameRewards.forPokewrle(round.used)
                .forEach(reward -> QuestService.grant(player, reward));
            player.displayClientMessage(
                Component.translatable("message.cobblemon_dimensions_stories.pokewrle_result", round.used)
                    .withStyle(ChatFormatting.GREEN),
                false);
        }

        ServerPlayNetworking.send(player, new PokewrleFeedbackS2C(
            guess.name(),
            score(guess, round.answer),
            POKEWRLE_ATTEMPTS - round.used,
            outcome,
            over ? Optional.of(round.answer.id()) : Optional.empty(),
            over ? Optional.of(round.answer.name()) : Optional.empty()));
    }

    /** Drops every open session for this player. */
    public static void forget(ServerPlayer player) {
        POKEWRLE.remove(player.getUUID());
        PATTERN.remove(player.getUUID());
        POKESWEEPER.remove(player.getUUID());
    }

    private static List<PokewrleHint> score(PokedexSource.Entry guess, PokedexSource.Entry answer) {
        List<PokewrleHint> hints = new ArrayList<>(PokewrleHint.Category.values().length);
        hints.add(match(PokewrleHint.Category.TYPE_1, guess.primaryType(), answer.primaryType()));
        hints.add(match(
            PokewrleHint.Category.TYPE_2,
            guess.secondaryType().orElse("None"),
            answer.secondaryType().orElse("None")));
        hints.add(match(PokewrleHint.Category.EGG_GROUP, guess.eggGroup(), answer.eggGroup()));
        hints.add(match(PokewrleHint.Category.STAGE, guess.stage(), answer.stage()));
        // generation is the only category with an ordering to hint at
        hints.add(compare(
            PokewrleHint.Category.GENERATION,
            "G" + guess.generation(), guess.generation(), answer.generation()));
        return hints;
    }

    private static PokewrleHint match(PokewrleHint.Category category, String guess, String answer) {
        return new PokewrleHint(
            category,
            guess,
            guess.equalsIgnoreCase(answer) ? PokewrleHint.State.CORRECT : PokewrleHint.State.WRONG);
    }

    private static PokewrleHint compare(
        PokewrleHint.Category category,
        String shown,
        double guess,
        double answer
    ) {
        PokewrleHint.State state;
        if (Math.abs(guess - answer) < 0.0001D) {
            state = PokewrleHint.State.CORRECT;
        } else {
            state = answer > guess ? PokewrleHint.State.HIGHER : PokewrleHint.State.LOWER;
        }
        return new PokewrleHint(category, shown, state);
    }

    private static final class PokewrleRound {
        private final PokedexSource.Entry answer;
        private final int generation;
        private int used;

        private PokewrleRound(PokedexSource.Entry answer, int generation) {
            this.answer = answer;
            this.generation = generation;
        }
    }
}
