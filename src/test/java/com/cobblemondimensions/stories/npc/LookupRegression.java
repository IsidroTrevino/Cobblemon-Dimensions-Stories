package com.cobblemondimensions.stories.npc;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** The indexed NPC lookups must answer exactly what scanning the shipped NPCs would. */
public final class LookupRegression {
    private LookupRegression() {
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        Path directory = Path.of(System.getProperty("stories.resources", "src/main/resources"),
            "data/cobblemon_dimensions_stories/story_npcs");
        Map<ResourceLocation, StoryNpc> loaded = new HashMap<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                String name = path.getFileName().toString();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories",
                    name.substring(0, name.length() - ".json".length()));
                try (var reader = Files.newBufferedReader(path)) {
                    StoryNpc npc = StoryNpc.BODY_CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .getOrThrow().withId(id);
                    loaded.put(npc.npcClass(), npc);
                }
            }
        }
        check(!loaded.isEmpty(), "no shipped story NPCs were found");
        StoryNpcDefinitions definitions = StoryNpcDefinitions.get();
        definitions.install(loaded);

        Set<ResourceLocation> quests = new HashSet<>();
        for (StoryNpc npc : loaded.values()) {
            check(definitions.byId(npc.id()).orElseThrow() == npc, "byId disagrees for " + npc.id());
            check(definitions.forNpcClass(npc.npcClass()).orElseThrow() == npc, "class lookup lost " + npc.id());
            quests.addAll(npc.quests());
        }
        for (ResourceLocation quest : quests) {
            long givers = loaded.values().stream().filter(npc -> npc.quests().contains(quest)).count();
            check(givers == 1, quest + " is listed by " + givers + " NPCs, so its giver would be ambiguous");
            check(definitions.questGiver(quest).orElseThrow().quests().contains(quest),
                "questGiver returned an NPC that does not list " + quest);
        }
        check(definitions.questGiver(ResourceLocation.parse("cobblemon_dimensions_stories:no_such_quest")).isEmpty(),
            "an unknown quest has no giver");
        System.out.println("NPC lookups: " + loaded.size() + " shipped NPCs index correctly.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
