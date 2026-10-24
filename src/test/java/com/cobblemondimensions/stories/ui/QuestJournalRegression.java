package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.quest.QuestStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Checks the paging and notification state used by the real journal. */
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
        for (int size : new int[] {0, 1, 6, 7, 12, 45}) {
            List<JournalView.Row> rows = new ArrayList<>();
            for (int index = 0; index < size; index++) {
                rows.add(new JournalView.Row("Quest " + index, QuestStatus.AVAILABLE));
            }
            int pages = frame(rows, 0).pages();
            expect(pages == Math.max(1, (size + 5) / 6), "a " + size + "-quest pack has the right number of pages");
            List<Integer> shown = new ArrayList<>();
            for (int page = 0; page < pages; page++) {
                int first = frame(rows, page).shownPage() * 6;
                for (int index = first; index < Math.min(size, first + 6); index++) {
                    shown.add(index);
                }
            }
            expect(shown.size() == size, "every entry in a " + size + "-quest pack can be visited");
            for (int index = 0; index < size; index++) {
                expect(shown.get(index) == index, "paging neither skips nor duplicates quest " + index);
            }
        }
    }

    private static void pagesClampAfterReload() {
        List<JournalView.Row> two = List.of(new JournalView.Row("a", QuestStatus.AVAILABLE),
            new JournalView.Row("b", QuestStatus.AVAILABLE));
        expect(frame(two, 3).shownPage() == 0, "a shrinking datapack clamps the old page");
        expect(frame(two, -1).shownPage() == 0, "a page can never go negative");
        JournalView.Frame empty = frame(List.of(), 7);
        expect(empty.pages() == 1 && empty.shownPage() == 0, "an empty pack has one empty page");
    }

    private static JournalView.Frame frame(List<JournalView.Row> rows, int page) {
        return new JournalView.Frame(rows, page, -1, "", "", "", 0L, 0L);
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
