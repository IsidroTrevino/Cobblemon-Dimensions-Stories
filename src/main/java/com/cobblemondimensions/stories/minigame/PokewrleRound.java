package com.cobblemondimensions.stories.minigame;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The hidden answer, accepted attempts and all five hints live on the server. */
final class PokewrleRound {
    static final int ATTEMPTS = 6;
    record Species(String id, String name, int generation, String primaryType,
                   Optional<String> secondaryType, String eggGroup, String stage) { }

    enum Outcome {
        REJECTED, CONTINUE, WON, LOST, INACTIVE
    }

    record Feedback(String name, List<PokewrleHint> hints, int remaining, Outcome outcome,
                    Optional<Species> answer) { }

    private final Species answer;
    private final int generation;
    private final List<Feedback> history = new ArrayList<>();
    private int used;
    private Outcome outcome = Outcome.CONTINUE;
    private boolean awaitingInput;
    private long inputDeadline;

    PokewrleRound(Species answer, int generation) {
        this.answer = Objects.requireNonNull(answer);
        this.generation = generation >= 1 && generation <= 9 ? generation : 0;
        if (this.generation != 0 && answer.generation() != this.generation) {
            throw new IllegalArgumentException("Answer must belong to the selected generation");
        }
    }

    Feedback guess(Species guess) {
        if (over()) {
            return new Feedback(guess == null ? "" : guess.name(), List.of(), remaining(),
                Outcome.INACTIVE, Optional.empty());
        }
        if (guess == null || generation != 0 && guess.generation() != generation) {
            return new Feedback(guess == null ? "" : guess.name(), List.of(), remaining(),
                Outcome.REJECTED, Optional.empty());
        }
        used++;
        outcome = guess.id().equals(answer.id()) ? Outcome.WON
            : remaining() == 0 ? Outcome.LOST : Outcome.CONTINUE;
        Feedback result = new Feedback(guess.name(), score(guess), remaining(), outcome,
            over() ? Optional.of(answer) : Optional.empty());
        history.add(result);
        return result;
    }

    private List<PokewrleHint> score(Species guess) {
        PokewrleHint.State direction = answer.generation() == guess.generation()
            ? PokewrleHint.State.CORRECT
            : answer.generation() > guess.generation() ? PokewrleHint.State.HIGHER : PokewrleHint.State.LOWER;
        return List.of(
            match(PokewrleHint.Category.TYPE_1, guess.primaryType(), answer.primaryType()),
            match(PokewrleHint.Category.TYPE_2, guess.secondaryType().orElse("None"), answer.secondaryType().orElse("None")),
            match(PokewrleHint.Category.EGG_GROUP, guess.eggGroup(), answer.eggGroup()),
            match(PokewrleHint.Category.STAGE, guess.stage(), answer.stage()),
            new PokewrleHint(PokewrleHint.Category.GENERATION, "G" + guess.generation(), direction));
    }

    private static PokewrleHint match(PokewrleHint.Category category, String guessed, String wanted) {
        return new PokewrleHint(category, guessed,
            guessed.equalsIgnoreCase(wanted) ? PokewrleHint.State.CORRECT : PokewrleHint.State.WRONG);
    }

    int used() {
        return used;
    }

    int remaining() {
        return ATTEMPTS - used;
    }

    int generation() {
        return generation;
    }

    boolean over() {
        return outcome == Outcome.WON || outcome == Outcome.LOST || outcome == Outcome.INACTIVE;
    }

    boolean won() {
        return outcome == Outcome.WON;
    }

    List<Feedback> history() {
        return List.copyOf(history);
    }

    void awaitInput(long now) {
        if (!over()) {
            awaitingInput = true;
            inputDeadline = now + 300_000_000_000L;
        }
    }

    boolean expire(long now) {
        if (awaitingInput && now >= inputDeadline) {
            awaitingInput = false;
            outcome = Outcome.INACTIVE;
            return true;
        }
        return false;
    }

    void resume() {
        awaitingInput = false;
    }

    boolean awaitingInput() {
        return awaitingInput;
    }
}
