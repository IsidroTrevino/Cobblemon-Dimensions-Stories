package com.cobblemondimensions.stories.quest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** The indexed quest lookups must answer exactly what scanning the shipped quests would. */
public final class LookupRegression {
    private LookupRegression() {
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        List<Quest> quests = load(Path.of(System.getProperty("stories.resources", "src/main/resources"),
            "data/cobblemon_dimensions_stories/quests"));
        check(!quests.isEmpty(), "no shipped quests were found");
        QuestDefinitions definitions = QuestDefinitions.get();
        definitions.install(quests);
        check(definitions.all().size() == quests.size(), "install dropped quests");
        for (Quest quest : quests) {
            check(definitions.byId(quest.id()).orElseThrow() == definitions.all().stream()
                .filter(each -> each.id().equals(quest.id())).findFirst().orElseThrow(),
                "byId disagrees with a scan for " + quest.id());
        }
        check(definitions.byId(ResourceLocation.parse("cobblemon_dimensions_stories:no_such_quest")).isEmpty(),
            "an unknown quest id must find nothing");
        System.out.println("Quest lookups: " + quests.size() + " shipped quests index correctly.");
    }

    private static List<Quest> load(Path directory) throws IOException {
        List<Quest> quests = new ArrayList<>();
        try (var files = Files.walk(directory)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                String name = directory.relativize(path).toString().replace(java.io.File.separatorChar, '/');
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories",
                    name.substring(0, name.length() - ".json".length()));
                try (var reader = Files.newBufferedReader(path)) {
                    quests.add(Quest.BODY_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .getOrThrow().withId(id));
                }
            }
        }
        return quests;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
