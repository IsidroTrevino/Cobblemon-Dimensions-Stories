package com.cobblemondimensions.stories.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Parse the real bundled dialogue graph and exercise its reachable arcade routes. */
public final class StoryDataRegression {
    private StoryDataRegression() {
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Path directory = Path.of(System.getProperty("stories.resources", "src/main/resources"),
            "data/cobblemon_dimensions_stories/walking_dialogues");
        Set<String> games = new HashSet<>();
        int parsed = 0;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                WalkingDialogue dialogue;
                try (var reader = Files.newBufferedReader(path)) {
                    dialogue = WalkingDialogue.BODY_CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(reader)).getOrThrow();
                }
                parsed++;
                Set<String> reached = new HashSet<>();
                if (!dialogue.pages().isEmpty()) {
                    visit(dialogue, dialogue.pages().getFirst().id(), reached, games);
                }
            }
        }
        if (!games.containsAll(Set.of("pattern", "pokewrle", "pokesweeper",
            "pokesweeper_easy", "pokesweeper_medium"))) {
            throw new AssertionError("An arcade game cannot be reached through story dialogue: " + games);
        }
        System.out.println("Story data regressions passed (" + parsed + " dialogue graphs and all arcade routes).");
    }

    private static void visit(WalkingDialogue dialogue, String pageId, Set<String> reached, Set<String> games) {
        if (!reached.add(pageId)) {
            return;
        }
        int index = dialogue.indexOf(pageId);
        if (index < 0) {
            throw new AssertionError("Missing dialogue target: " + pageId);
        }
        WalkingDialoguePage page = dialogue.pages().get(index);
        if (page.minigame().isPresent()) {
            games.add(page.minigame().get().getSerializedName());
            return;
        }
        for (WalkingDialogueOption option : page.options()) {
            option.next().ifPresent(next -> visit(dialogue, next, reached, games));
        }
        page.next().ifPresent(next -> visit(dialogue, next, reached, games));
    }
}
