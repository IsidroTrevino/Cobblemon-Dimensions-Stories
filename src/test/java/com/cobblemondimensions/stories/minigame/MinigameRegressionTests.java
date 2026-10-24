package com.cobblemondimensions.stories.minigame;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** Executable tests of the server's real game state; no Minecraft runtime is needed. */
public final class MinigameRegressionTests {
    private static final long SECOND = 1_000_000_000L;

    private MinigameRegressionTests() {
    }

    public static void main(String[] arguments) {
        runAll();
    }

    public static void runAll() {
        firstDigOpensSafeNeighbourhoodsInEveryMode();
        flagsSurviveFloodAndFinishedBoardsIgnoreInputs();
        matchingFlagsChordSafeCellsAndWrongFlagsDetonate();
        viewportReachesEveryCellWithoutChangingTheBoard();
        sweepTimeStartsAtFirstDigAndFreezesAtTermination();
        patternCountdownResetAndDeadlineAreAuthoritative();
        patternLengthsTimersAndShapeTransitionMatchTheOriginalGame();
        patternKeysCountWhenPressedNotHeld();
        patternBoardDrawsInEveryPhaseAndAnimationState();
        pokewrleRejectsWrongGenerationsAndScoresAllFiveHints();
        pokewrleTerminatesAfterAWinOrSixGuesses();
        pokewrleChatInputExpiresWithoutAcceptingALateGuess();
        settlementPaysOnceEvenWhenClosingOrReentered();
        System.out.println("Minigame regression tests passed (13 scenarios).");
    }

    private static void firstDigOpensSafeNeighbourhoodsInEveryMode() {
        int[][] modes = {{9, 9, 10}, {12, 12, 20}, {16, 16, 40}};
        for (int[] mode : modes) {
            int[][] firstDigs = {{0, 0}, {mode[0] - 1, 0}, {0, mode[1] - 1},
                {mode[0] - 1, mode[1] - 1}, {mode[0] / 2, mode[1] / 2}};
            for (int seed = 0; seed < 12; seed++) {
                PokesweeperBoard board = new PokesweeperBoard(new Random(seed), mode[0], mode[1], mode[2]);
                int[] coordinate = firstDigs[seed % firstDigs.length];
                int first = board.index(coordinate[0], coordinate[1]);
                int[] opened = board.dig(first);
                check(opened.length >= (seed % firstDigs.length == 4 ? 9 : 4),
                    "first dig must open the safe neighbourhood at every corner and the centre");
                check(!board.lost(), "first dig must never detonate");
                int mines = 0;
                for (int cell = 0; cell < board.cells(); cell++) {
                    if (board.mine(cell)) {
                        mines++;
                    }
                }
                equal(mode[2], mines, "board must contain exactly the mode's mine count");
                for (int neighbour : neighbours(board, first)) {
                    check(!board.mine(neighbour), "first dig's neighbouring cells must be safe");
                    check(board.state(neighbour) == PokesweeperBoard.Cell.REVEALED,
                        "safe opening must flood reveal neighbours");
                }
                for (int cell = 0; cell < board.cells(); cell++) {
                    if (!board.mine(cell)) {
                        int expected = 0;
                        for (int neighbour : neighbours(board, cell)) {
                            if (board.mine(neighbour)) {
                                expected++;
                            }
                        }
                        equal(expected, board.count(cell), "number must match actual adjacent mines");
                    }
                }
            }
        }
    }

    private static void flagsSurviveFloodAndFinishedBoardsIgnoreInputs() {
        PokesweeperBoard board = new PokesweeperBoard(new Random(31), 9, 9, 10);
        check(board.toggleFlag(1), "flag must be placeable before the first dig");
        board.dig(0);
        check(board.state(1) == PokesweeperBoard.Cell.FLAGGED, "flood must preserve a flag");
        int before = board.uncovered();
        equal(0, board.dig(1).length, "digging a flag must do nothing");
        equal(before, board.uncovered(), "flagged dig must not change score");
        check(!board.toggleFlag(1), "second flag click must remove it");
        board.dig(1);
        for (int cell = 0; cell < board.cells(); cell++) {
            if (!board.mine(cell)) {
                board.dig(cell);
            }
        }
        check(board.won(), "revealing every safe cell must win");
        equal(71, board.uncovered(), "win must count safe cells only");
        int mine = firstMine(board);
        board.dig(mine);
        board.toggleFlag(mine);
        check(board.won() && !board.lost(), "finished board must ignore further digs and flags");
    }

    private static void matchingFlagsChordSafeCellsAndWrongFlagsDetonate() {
        PokesweeperBoard correct = chordFixture(false);
        int number = chordableNumber(correct, false);
        int before = correct.uncovered();
        equal(0, correct.chord(number).length, "chord without matching flags must do nothing");
        for (int neighbour : neighbours(correct, number)) {
            if (correct.mine(neighbour)) {
                correct.toggleFlag(neighbour);
            }
        }
        check(correct.chord(number).length > 0, "correct matching flags must open remaining neighbours");
        check(correct.uncovered() > before && !correct.lost(), "correct chord must improve score safely");

        PokesweeperBoard wrong = chordFixture(true);
        int wrongNumber = chordableNumber(wrong, true);
        int needed = wrong.count(wrongNumber);
        for (int neighbour : neighbours(wrong, wrongNumber)) {
            if (needed > 0 && !wrong.mine(neighbour)
                && wrong.state(neighbour) == PokesweeperBoard.Cell.HIDDEN) {
                wrong.toggleFlag(neighbour);
                needed--;
            }
        }
        equal(0, needed, "fixture must offer enough incorrect flag positions");
        wrong.chord(wrongNumber);
        check(wrong.lost(), "wrong flags with the right count must expose a mine");
        int frozen = wrong.uncovered();
        wrong.dig(80);
        equal(frozen, wrong.uncovered(), "loss must freeze score");
    }

    private static void viewportReachesEveryCellWithoutChangingTheBoard() {
        for (int size : new int[] {9, 12, 16}) {
            PokesweeperViewport view = new PokesweeperViewport(size, size);
            PokesweeperBoard board = new PokesweeperBoard(new Random(9), size, size, size == 9 ? 10 : size == 12 ? 20 : 40);
            Set<Integer> visited = new HashSet<>();
            for (int rowPage = 0; rowPage < (size + 4) / 5; rowPage++) {
                for (int columnPage = 0; columnPage < (size + 8) / 9; columnPage++) {
                    for (int slot = 0; slot < 45; slot++) {
                        int cell = view.cellAt(slot);
                        if (cell >= 0) {
                            check(cell < size * size, "viewport must never point outside the board");
                            visited.add(cell);
                        }
                    }
                    view.move(1, 0);
                }
                view.move(-100, 1);
            }
            equal(size * size, visited.size(), "all board cells must be reachable through navigation");
            check(!board.started() && board.uncovered() == 0, "navigation must not start or score the board");
            view.move(-100, -100);
            equal(0, view.cellAt(0), "navigation must clamp at the first page");
            equal(-1, view.cellAt(45), "control slots must never address a board cell");
        }
    }

    private static void sweepTimeStartsAtFirstDigAndFreezesAtTermination() {
        PokesweeperRound round = new PokesweeperRound(new Random(33), 9, 9, 10);
        round.toggleFlag(1);
        equal(1, round.seconds(100 * SECOND), "time spent before digging must not enter the record");
        round.dig(0, 100 * SECOND);
        equal(2, round.seconds(102 * SECOND), "timer must measure time after the first dig");
        round.finish(103 * SECOND);
        equal(3, round.seconds(900 * SECOND), "closing must freeze elapsed time");
        int before = round.board().uncovered();
        round.dig(80, 901 * SECOND);
        equal(before, round.board().uncovered(), "closed round must ignore gameplay input");

        PokesweeperRound won = new PokesweeperRound(new Random(3), 9, 9, 10);
        won.dig(0, 0);
        for (int cell = 0; cell < 81; cell++) {
            if (!won.board().mine(cell)) {
                won.dig(cell, 2 * SECOND);
            }
        }
        check(won.board().won(), "time fixture must win");
        equal(2, won.seconds(100 * SECOND), "winning must freeze the time immediately");

        PokesweeperRound capped = new PokesweeperRound(new Random(2), 9, 9, 10);
        capped.dig(0, 0);
        equal(3600, capped.seconds(9000 * SECOND), "records must retain the 3600-second cap");
    }

    private static void patternCountdownResetAndDeadlineAreAuthoritative() {
        PatternRound round = new PatternRound(new Random(7), 0);
        round.press(round.sequence()[0], SECOND);
        equal(0, round.typed(), "countdown must reject input");
        round.advance(1_900_000_000L);
        long deadline = round.deadlineNanos();
        equal(16_900_000_000L, deadline, "first level must offer fifteen seconds after countdown");
        round.press(round.sequence()[0], 2 * SECOND);
        equal(1, round.typed(), "correct input must advance progress");
        round.press((round.sequence()[1] + 1) % 4, 3 * SECOND);
        equal(0, round.typed(), "wrong input must reset progress");
        equal(0, round.streak(), "wrong input must reset streak");
        equal(deadline, round.deadlineNanos(), "wrong input must not extend the timer");
        round.press(round.sequence()[0], deadline);
        check(round.phase() == PatternRound.Phase.OVER, "input at the deadline must end the run");
        equal(0, round.levelsCleared(), "a late input must not score a clear");
        round.press(round.sequence()[0], deadline + SECOND);
        equal(0, round.levelsCleared(), "terminated run must ignore subsequent input");
    }

    private static void patternLengthsTimersAndShapeTransitionMatchTheOriginalGame() {
        PatternRound round = new PatternRound(new Random(11), 0);
        long now = 1_900_000_000L;
        int[] lengths = {4, 4, 6, 6, 8, 8, 10, 10, 12, 12, 12, 12};
        for (int level = 1; level <= lengths.length; level++) {
            round.advance(now);
            int[] sequence = round.sequence();
            equal(lengths[level - 1], sequence.length, "pattern length must grow every two levels and cap at twelve");
            equal(Math.max(5, 16 - level) * SECOND, round.deadlineNanos() - now,
                "per-level deadlines must shrink to five seconds");
            check(round.shapes() == (level >= 10), "shape mode must begin at level ten");
            now += 10;
            for (int direction : sequence) {
                round.press(direction, now);
            }
            equal(level, round.levelsCleared(), "matching the complete sequence must clear exactly one level");
            equal(level, round.streak(), "consecutive clears must grow the streak");
            long hold = level == 9 ? 2 * SECOND : 1_100_000_000L;
            round.advance(now + hold - 1);
            check(round.phase() == PatternRound.Phase.CLEARED, "clear overlay must retain its full hold");
            now += hold;
            round.advance(now);
            check(round.phase() == PatternRound.Phase.COUNTDOWN, "new level must include a fresh countdown");
            now += 1_900_000_000L;
        }
        round.advance(now);
        round.press((round.sequence()[0] + 1) % 4, now + 10);
        equal(0, round.streak(), "mistake after clears must reset current streak");
        equal(12, round.bestStreak(), "mistake must retain best streak");
        round.finish(now + 11);
        check(round.phase() == PatternRound.Phase.OVER, "closing must terminate Pattern");
    }

    private static void patternKeysCountWhenPressedNotHeld() {
        equal(0, PatternGame.pressed(0, 1, 0, 0), "forward going down is up");
        equal(1, PatternGame.pressed(0, -1, 0, 0), "back going down is down");
        equal(2, PatternGame.pressed(1, 0, 0, 0), "strafing left is left");
        equal(3, PatternGame.pressed(-1, 0, 0, 0), "strafing right is right");
        equal(-1, PatternGame.pressed(0, 1, 0, 1), "a held key must not repeat");
        equal(-1, PatternGame.pressed(0, 0, 0, 1), "letting go is not a press");
        equal(0, PatternGame.pressed(0, 1, 0, -1), "going straight from back to forward is a press");
        equal(2, PatternGame.pressed(1, 1, 0, 1), "a second key pressed while one is held still counts");
        equal(0, PatternGame.pressed(1, 1, 0, 0), "two keys at once pick forward");
    }

    /** Every state the board can be in has to compose into a picture the client's font can draw. */
    private static void patternBoardDrawsInEveryPhaseAndAnimationState() {
        com.cobblemondimensions.stories.ui.art.UiAtlas.load();
        PatternRound round = new PatternRound(new Random(3), 0);
        long now = 0;
        for (int level = 1; level <= 12; level++) {
            for (long age = 0; age <= 3 * SECOND; age += 40_000_000L) {
                PatternView.Effects idle = new PatternView.Effects(now - 60 * SECOND, now - 60 * SECOND, -1,
                    now - 60 * SECOND, now, 14);
                PatternView.Effects busy = new PatternView.Effects(now + age - 100_000_000L, now + age - 50_000_000L,
                    level % 4, now + age - 200_000_000L, now, 14);
                PatternView.compose(round, now + age, idle);
                PatternView.compose(round, now + age, busy);
            }
            now += 1_900_000_000L;
            round.advance(now);
            int[] sequence = round.sequence();
            for (int index = 0; index < sequence.length; index++) {
                PatternView.compose(round, now + index * 10L, new PatternView.Effects(0, now, index, 0, now, 0));
                round.press(sequence[index], now + index * 10L + 5);
            }
            PatternView.compose(round, now + 500_000_000L, new PatternView.Effects(0, now, 0, now, now, 0));
            now += 2 * SECOND;
            round.advance(now);
        }
        round.finish(now);
        PatternView.compose(round, now + SECOND, new PatternView.Effects(0, 0, -1, 0, 0, 99));
        // a wrong press, then running the clock out inside the last quarter
        PatternRound late = new PatternRound(new Random(5), 0);
        late.advance(1_900_000_000L);
        for (long age = 0; age <= 16 * SECOND; age += 100_000_000L) {
            PatternView.compose(late, 1_900_000_000L + age, new PatternView.Effects(0, 0, -1, 0, 0, 0));
        }
        // one frame is sent every tick the picture changes, so its size is worth watching
        PatternRound frameRound = new PatternRound(new Random(8), 0);
        frameRound.advance(1_900_000_000L);
        int[] size = new int[2];
        PatternView.compose(frameRound, 3 * SECOND, new PatternView.Effects(0, 0, -1, 0, 0, 14))
            .visit((style, text) -> {
                size[0]++;
                size[1] += text.length();
                return Optional.empty();
            }, net.minecraft.network.chat.Style.EMPTY);
        System.out.println("Pattern frame: " + size[0] + " styled runs, " + size[1] + " characters");
        check(true, "the board draws in every phase");
    }

    private static void pokewrleRejectsWrongGenerationsAndScoresAllFiveHints() {
        PokewrleRound.Species answer = species("answer", 4, "Grass", Optional.empty(), "monster", "Final");
        PokewrleRound selected = new PokewrleRound(answer, 4);
        var rejected = selected.guess(species("other-generation", 2, "Grass", Optional.empty(), "monster", "Final"));
        check(rejected.outcome() == PokewrleRound.Outcome.REJECTED, "wrong generation must be rejected");
        equal(6, rejected.remaining(), "rejection must not consume an attempt");

        PokewrleRound round = new PokewrleRound(answer, 0);
        var result = round.guess(species("guess", 2, "Grass", Optional.empty(), "fairy", "Basic"));
        equal(5, result.hints().size(), "guess must include all five hint categories");
        check(result.hints().get(0).state() == PokewrleHint.State.CORRECT, "primary type must match");
        check(result.hints().get(1).state() == PokewrleHint.State.CORRECT, "missing secondary types must match");
        check(result.hints().get(2).state() == PokewrleHint.State.WRONG, "different egg groups must be wrong");
        check(result.hints().get(3).state() == PokewrleHint.State.WRONG, "different evolution stages must be wrong");
        check(result.hints().get(4).state() == PokewrleHint.State.HIGHER, "generation must point toward a newer answer");
        var lower = round.guess(species("newer", 9, "Fire", Optional.of("Flying"), "monster", "Final"));
        check(lower.hints().get(4).state() == PokewrleHint.State.LOWER, "generation must point toward an older answer");
        check(result.answer().isEmpty(), "continuing guesses must not reveal the answer");
    }

    private static void pokewrleTerminatesAfterAWinOrSixGuesses() {
        PokewrleRound.Species answer = species("answer", 1, "Water", Optional.empty(), "water1", "Solo");
        PokewrleRound won = new PokewrleRound(answer, 1);
        var win = won.guess(answer);
        check(win.outcome() == PokewrleRound.Outcome.WON, "answer must win");
        equal(5, win.remaining(), "first guess win must consume one attempt");
        check(win.answer().orElseThrow().id().equals("answer"), "win must reveal the answer");
        var repeated = won.guess(answer);
        check(repeated.outcome() == PokewrleRound.Outcome.INACTIVE, "won round must reject repeated reward-bearing guesses");
        equal(1, won.used(), "repeated guess must not change score");

        PokewrleRound lost = new PokewrleRound(answer, 0);
        PokewrleRound.Species wrong = species("wrong", 2, "Fire", Optional.empty(), "field", "Basic");
        for (int attempt = 1; attempt <= 6; attempt++) {
            var result = lost.guess(wrong);
            check(result.outcome() == (attempt == 6 ? PokewrleRound.Outcome.LOST : PokewrleRound.Outcome.CONTINUE),
                "round must lose exactly on its sixth accepted guess");
            equal(6 - attempt, result.remaining(), "accepted guesses must consume one attempt each");
            check(result.answer().isPresent() == (attempt == 6), "only final attempt may reveal a losing answer");
        }
        check(lost.guess(answer).outcome() == PokewrleRound.Outcome.INACTIVE,
            "a seventh guess must not turn a loss into a win");
    }

    private static void settlementPaysOnceEvenWhenClosingOrReentered() {
        SessionSettlement settlement = new SessionSettlement();
        AtomicInteger payouts = new AtomicInteger();
        check(settlement.runOnce(() -> {
            payouts.incrementAndGet();
            settlement.runOnce(payouts::incrementAndGet);
        }), "first finish must settle");
        check(!settlement.runOnce(payouts::incrementAndGet), "close after finish must not settle again");
        equal(1, payouts.get(), "reentered settlement must pay exactly once");
        SessionSettlement stopped = new SessionSettlement();
        stopped.discard();
        check(!stopped.runOnce(payouts::incrementAndGet), "discarded session must never pay later");
        equal(1, payouts.get(), "server-stop discard must not grant a payout");
    }

    private static void pokewrleChatInputExpiresWithoutAcceptingALateGuess() {
        PokewrleRound.Species answer = species("answer", 1, "Water", Optional.empty(), "water1", "Solo");
        PokewrleRound round = new PokewrleRound(answer, 0);
        round.awaitInput(10 * SECOND);
        check(!round.expire(310 * SECOND - 1), "chat input must remain active for the full five minutes");
        check(round.expire(310 * SECOND), "chat input must expire at its deadline");
        check(round.guess(answer).outcome() == PokewrleRound.Outcome.INACTIVE,
            "expired chat session must not accept a reward-bearing guess");
        equal(0, round.used(), "late guesses must not change expired state");

        PokewrleRound resumed = new PokewrleRound(answer, 0);
        resumed.awaitInput(0);
        resumed.resume();
        check(!resumed.expire(400 * SECOND), "returning to the board must clear the input deadline");
        check(resumed.guess(answer).outcome() == PokewrleRound.Outcome.WON,
            "a resumed active board must still accept guesses");
    }

    private static PokesweeperBoard chordFixture(boolean wrong) {
        for (int seed = 0; seed < 100; seed++) {
            PokesweeperBoard board = new PokesweeperBoard(new Random(seed), 9, 9, 10);
            board.dig(40);
            if (chordableNumber(board, wrong) >= 0) {
                return board;
            }
        }
        throw new AssertionError("No deterministic chord fixture found");
    }

    private static int chordableNumber(PokesweeperBoard board, boolean wrong) {
        for (int cell = 0; cell < board.cells(); cell++) {
            if (board.state(cell) != PokesweeperBoard.Cell.REVEALED || board.count(cell) == 0) {
                continue;
            }
            int safeHidden = 0;
            int hiddenMines = 0;
            for (int neighbour : neighbours(board, cell)) {
                if (board.state(neighbour) == PokesweeperBoard.Cell.HIDDEN) {
                    if (board.mine(neighbour)) {
                        hiddenMines++;
                    } else {
                        safeHidden++;
                    }
                }
            }
            if (hiddenMines > 0 && safeHidden > 0 && (!wrong || safeHidden >= board.count(cell))) {
                return cell;
            }
        }
        return -1;
    }

    private static int[] neighbours(PokesweeperBoard board, int cell) {
        int[] result = new int[8];
        int size = 0;
        int x = cell % board.cols();
        int y = cell / board.cols();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if ((dx != 0 || dy != 0) && board.onBoard(x + dx, y + dy)) {
                    result[size++] = board.index(x + dx, y + dy);
                }
            }
        }
        return java.util.Arrays.copyOf(result, size);
    }

    private static int firstMine(PokesweeperBoard board) {
        for (int cell = 0; cell < board.cells(); cell++) {
            if (board.mine(cell)) {
                return cell;
            }
        }
        throw new AssertionError("Fixture has no mines");
    }

    private static PokewrleRound.Species species(String id, int generation, String primary,
                                                Optional<String> secondary, String eggGroup, String stage) {
        return new PokewrleRound.Species(id, id, generation, primary, secondary, eggGroup, stage);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void equal(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }
}
