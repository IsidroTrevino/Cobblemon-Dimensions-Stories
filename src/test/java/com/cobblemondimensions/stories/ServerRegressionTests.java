package com.cobblemondimensions.stories;

import com.cobblemondimensions.stories.dialogue.DialogueCursorRegression;
import com.cobblemondimensions.stories.dialogue.StoryDataRegression;
import com.cobblemondimensions.stories.minigame.MinigameRegressionTests;
import com.cobblemondimensions.stories.npc.LookupRegression;
import com.cobblemondimensions.stories.quest.QuestCompatibilityRegression;
import com.cobblemondimensions.stories.ui.OverlayRegression;
import com.cobblemondimensions.stories.ui.QuestJournalRegression;
import com.cobblemondimensions.stories.ui.UiRegressionTests;

public final class ServerRegressionTests {
    private ServerRegressionTests() {
    }

    public static void main(String[] args) throws Exception {
        UiRegressionTests.main(args);
        DialogueCursorRegression.main(args);
        QuestJournalRegression.main(args);
        OverlayRegression.main(args);
        MinigameRegressionTests.main(args);
        QuestCompatibilityRegression.main(args);
        StoryDataRegression.main(args);
        com.cobblemondimensions.stories.quest.LookupRegression.main(args);
        LookupRegression.main(args);
    }
}
