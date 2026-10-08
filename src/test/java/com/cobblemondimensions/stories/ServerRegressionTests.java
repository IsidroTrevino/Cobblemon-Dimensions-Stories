package com.cobblemondimensions.stories;

import com.cobblemondimensions.stories.dialogue.DialogueCursorRegression;
import com.cobblemondimensions.stories.dialogue.StoryDataRegression;
import com.cobblemondimensions.stories.minigame.MinigameRegressionTests;
import com.cobblemondimensions.stories.quest.QuestCompatibilityRegression;
import com.cobblemondimensions.stories.ui.QuestJournalRegression;
import com.cobblemondimensions.stories.ui.UiRegressionTests;

public final class ServerRegressionTests {
    private ServerRegressionTests() {
    }

    public static void main(String[] args) throws Exception {
        UiRegressionTests.main(args);
        DialogueCursorRegression.main(args);
        QuestJournalRegression.main(args);
        MinigameRegressionTests.main(args);
        QuestCompatibilityRegression.main(args);
        StoryDataRegression.main(args);
    }
}
