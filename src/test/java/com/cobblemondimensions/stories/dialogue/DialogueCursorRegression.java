package com.cobblemondimensions.stories.dialogue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Executable regression checks; no Minecraft world or JUnit is needed. */
public final class DialogueCursorRegression {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static int checks;

    public static void main(String[] args) {
        linearPagesFinishOnce();
        choicesFollowTheirOwnTargets();
        invalidInputCannotSkipAnOptionPage();
        missingTargetsFinishAndAbsentTargetsFallThrough();
        staleAndForeignTokensCannotAdvance();
        loopingPagesRotateTokens();
        cancellationNeverCompletes();
        emptyDialogueDoesNotComplete();
        minigamePageIsTerminal();
        System.out.println("Dialogue cursor: " + checks + " checks passed");
    }

    private static void linearPagesFinishOnce() {
        DialogueCursor cursor = cursor(page("a"), page("b"));
        expect(cursor.choose(OWNER, cursor.token(), -1) == DialogueCursor.Result.PAGE,
            "a linear dialogue advances to the second page");
        expect(cursor.pageIndex() == 1, "the second page is selected");
        String last = cursor.token();
        expect(cursor.choose(OWNER, last, -1) == DialogueCursor.Result.COMPLETED,
            "the last page completes");
        expect(cursor.choose(OWNER, last, -1) == DialogueCursor.Result.REJECTED,
            "completion cannot be replayed");
    }

    private static void choicesFollowTheirOwnTargets() {
        DialogueCursor cursor = cursor(new DialogueCursor.Page("choice",
            List.of(Optional.of("left"), Optional.of("right")), Optional.empty(), false),
            page("left"), page("right"));
        expect(cursor.choose(OWNER, cursor.token(), 1) == DialogueCursor.Result.PAGE,
            "the selected branch advances");
        expect(cursor.pageIndex() == 2, "choice 1 selects the right branch");
    }

    private static void invalidInputCannotSkipAnOptionPage() {
        DialogueCursor cursor = cursor(new DialogueCursor.Page("choice",
            List.of(Optional.of("end")), Optional.empty(), false));
        String token = cursor.token();
        for (int choice : new int[] {-3, -1, 1, Integer.MAX_VALUE}) {
            expect(cursor.choose(OWNER, token, choice) == DialogueCursor.Result.REJECTED,
                "invalid choice " + choice + " is rejected");
        }
        expect(cursor.isActive() && cursor.token().equals(token),
            "invalid input leaves the real choice usable");
        expect(cursor.choose(OWNER, token, 0) == DialogueCursor.Result.COMPLETED,
            "an explicit end target completes");
        DialogueCursor linear = cursor(page("a"));
        expect(linear.choose(OWNER, linear.token(), 0) == DialogueCursor.Result.REJECTED,
            "an option index cannot advance a page without options");
    }

    private static void missingTargetsFinishAndAbsentTargetsFallThrough() {
        DialogueCursor unknown = cursor(new DialogueCursor.Page("a", List.of(),
            Optional.of("missing"), false), page("b"));
        expect(unknown.choose(OWNER, unknown.token(), -1) == DialogueCursor.Result.COMPLETED,
            "an unknown explicit target finishes instead of falling through");
        DialogueCursor choice = cursor(new DialogueCursor.Page("a",
            List.of(Optional.empty()), Optional.of("wrong"), false), page("b"));
        expect(choice.choose(OWNER, choice.token(), 0) == DialogueCursor.Result.PAGE,
            "an option without a target falls through instead of using page.next");
        expect(choice.pageIndex() == 1, "the absent choice target selects the following page");
    }

    private static void staleAndForeignTokensCannotAdvance() {
        DialogueCursor cursor = cursor(page("a"), page("b"));
        String first = cursor.token();
        expect(cursor.choose(OTHER, first, -1) == DialogueCursor.Result.REJECTED,
            "another player cannot use a copied link");
        expect(cursor.choose(OWNER, "fabricated", -1) == DialogueCursor.Result.REJECTED,
            "a fabricated token cannot advance");
        expect(cursor.choose(OWNER, first, -1) == DialogueCursor.Result.PAGE,
            "the owner can still use the original link");
        expect(cursor.choose(OWNER, first, -1) == DialogueCursor.Result.REJECTED,
            "a previous-page link cannot complete a newer page");
        DialogueCursor replacement = cursor(page("new"));
        expect(replacement.choose(OWNER, cursor.token(), -1) == DialogueCursor.Result.REJECTED,
            "a previous-session token cannot complete a replacement dialogue");
    }

    private static void loopingPagesRotateTokens() {
        DialogueCursor cursor = cursor(new DialogueCursor.Page("loop", List.of(),
            Optional.of("loop"), false));
        String token = cursor.token();
        expect(cursor.choose(OWNER, token, -1) == DialogueCursor.Result.PAGE,
            "an explicit self-loop stays active");
        expect(!cursor.token().equals(token), "a self-loop produces a fresh page token");
        expect(cursor.choose(OWNER, token, -1) == DialogueCursor.Result.REJECTED,
            "a self-loop cannot reuse its previous link");
    }

    private static void cancellationNeverCompletes() {
        DialogueCursor cursor = cursor(page("a"));
        String token = cursor.token();
        expect(cursor.choose(OWNER, token, -2) == DialogueCursor.Result.CANCELLED,
            "the cancel link cancels");
        expect(!cursor.isActive(), "a cancelled cursor is closed");
        expect(cursor.choose(OWNER, token, -1) == DialogueCursor.Result.REJECTED,
            "a cancelled dialogue cannot complete");
        expect(!cursor.cancel(), "a cancelled dialogue is not cancelled twice");
        DialogueCursor abandoned = cursor(page("a"));
        expect(abandoned.cancel(), "server abandonment closes an active cursor");
        expect(abandoned.choose(OWNER, abandoned.token(), -1) == DialogueCursor.Result.REJECTED,
            "server abandonment cannot be replayed as completion");
    }

    private static void emptyDialogueDoesNotComplete() {
        DialogueCursor cursor = cursor();
        expect(!cursor.isActive(), "an empty dialogue never opens a page");
        expect(cursor.choose(OWNER, cursor.token(), -1) == DialogueCursor.Result.REJECTED,
            "an empty dialogue does not complete a quest");
    }

    private static void minigamePageIsTerminal() {
        DialogueCursor cursor = cursor(new DialogueCursor.Page("game", List.of(),
            Optional.of("after"), true), page("after"));
        String token = cursor.token();
        expect(cursor.choose(OWNER, token, -1) == DialogueCursor.Result.MINIGAME,
            "a minigame page launches instead of following its target");
        expect(cursor.choose(OWNER, token, -1) == DialogueCursor.Result.REJECTED,
            "the minigame launch cannot be replayed");
    }

    private static DialogueCursor cursor(DialogueCursor.Page... pages) {
        return new DialogueCursor(OWNER, List.of(pages));
    }

    private static DialogueCursor.Page page(String id) {
        return new DialogueCursor.Page(id, List.of(), Optional.empty(), false);
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
