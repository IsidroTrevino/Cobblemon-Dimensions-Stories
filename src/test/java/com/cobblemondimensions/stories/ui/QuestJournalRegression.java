package com.cobblemondimensions.stories.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Checks the pagination and notification state used by the real journal. */
public final class QuestJournalRegression {
    private static int checks;

    public static void main(String[] args) {
        everyQuestRemainsReachable();
        pagesClampAfterReload();
        unreadQuestsNotifyOnceUntilVisited();
        newlyUnlockedQuestsNotify();
        removedQuestsLoseTheirVisitState();
        System.out.println("Quest journal: " + checks + " checks passed");
    }

    private static void everyQuestRemainsReachable() {
        for (int size : new int[] {0, 1, 45, 46, 90, 137}) {
            QuestJournal.PageWindow window = new QuestJournal.PageWindow(size, 0);
            List<Integer> visible = new ArrayList<>();
            for (int page = 0; page < window.pages(); page++) {
                for (int index = window.first(); index < window.last(); index++) {
                    visible.add(index);
                }
                window = window.next();
            }
            expect(visible.size() == size, "every entry in a " + size + "-quest pack can be visited");
            for (int index = 0; index < size; index++) {
                expect(visible.get(index) == index, "pagination neither skips nor duplicates quest " + index);
            }
            expect(window.next().page() == window.page(), "next clamps at the final page");
        }
    }

    private static void pagesClampAfterReload() {
        QuestJournal.PageWindow shrunk = new QuestJournal.PageWindow(2, 3);
        expect(shrunk.page() == 0 && shrunk.first() == 0 && shrunk.last() == 2,
            "a shrinking datapack clamps the old page");
        QuestJournal.PageWindow empty = new QuestJournal.PageWindow(0, 7);
        expect(empty.pages() == 1 && empty.page() == 0 && empty.first() == empty.last(),
            "an empty pack has one empty page");
        expect(new QuestJournal.PageWindow(46, -1).previous().page() == 0,
            "previous cannot create a negative page");
    }

    private static void unreadQuestsNotifyOnceUntilVisited() {
        QuestJournal.Unread unread = new QuestJournal.Unread();
        expect(unread.update(Set.of("a", "b"), List.of("a", "b")).equals(List.of("a", "b")),
            "initial available quests notify in journal order");
        expect(unread.update(Set.of("a", "b"), List.of("a", "b")).isEmpty(),
            "repeated syncs do not repeat unread announcements");
        unread.markSeen(List.of("a"));
        expect(!unread.isUnread("a") && unread.isUnread("b"),
            "visiting one page does not mark another quest seen");
        expect(unread.update(Set.of("a", "b"), List.of("a", "b")).isEmpty(),
            "opening the journal does not trigger another announcement");
    }

    private static void newlyUnlockedQuestsNotify() {
        QuestJournal.Unread unread = new QuestJournal.Unread();
        unread.update(Set.of("a", "b"), List.of("a"));
        unread.markSeen(List.of("a"));
        expect(unread.update(Set.of("a", "b"), List.of("b")).equals(List.of("b")),
            "a newly unlocked quest receives its own announcement");
        expect(!unread.isUnread("a") && unread.isUnread("b"),
            "only currently available unvisited quests are unread");
    }

    private static void removedQuestsLoseTheirVisitState() {
        QuestJournal.Unread unread = new QuestJournal.Unread();
        unread.update(Set.of("a"), List.of("a"));
        unread.markSeen(List.of("a"));
        unread.update(Set.of(), List.of());
        expect(unread.update(Set.of("a"), List.of("a")).equals(List.of("a")),
            "an id removed and later reintroduced can be unread again");
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
