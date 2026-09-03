package com.cobblemondimensions.stories.npc;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side reload listener for {@code data/<namespace>/story_npcs/*.json}. */
public final class StoryNpcDefinitions extends SimpleJsonResourceReloadListener
    implements IdentifiableResourceReloadListener {
    public static final ResourceLocation LISTENER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "story_npcs");

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_dimensions_stories/story_npcs");
    private static final StoryNpcDefinitions INSTANCE = new StoryNpcDefinitions();

    // keyed by npc class: two definitions on the same class would collide
    private Map<ResourceLocation, StoryNpc> byNpcClass = Map.of();

    private StoryNpcDefinitions() {
        super(new Gson(), "story_npcs");
    }

    public static StoryNpcDefinitions get() {
        return INSTANCE;
    }

    /** Lookup by the Cobblemon NPC class the entity was summoned with. */
    public Optional<StoryNpc> forNpcClass(ResourceLocation npcClass) {
        return Optional.ofNullable(byNpcClass.get(npcClass));
    }

    public Optional<StoryNpc> byId(ResourceLocation id) {
        return byNpcClass.values().stream().filter(npc -> npc.id().equals(id)).findFirst();
    }

    /** First NPC whose quest list contains {@code questId}. */
    public Optional<StoryNpc> questGiver(ResourceLocation questId) {
        return byNpcClass.values().stream().filter(npc -> npc.quests().contains(questId)).findFirst();
    }

    @Override
    public ResourceLocation getFabricId() {
        return LISTENER_ID;
    }

    @Override
    protected void apply(
        Map<ResourceLocation, JsonElement> entries,
        ResourceManager resourceManager,
        ProfilerFiller profiler
    ) {
        Map<ResourceLocation, StoryNpc> loaded = new HashMap<>(entries.size());
        entries.forEach((id, json) -> StoryNpc.BODY_CODEC
            .parse(JsonOps.INSTANCE, json)
            .resultOrPartial(error -> LOGGER.error("Skipping story NPC {}: {}", id, error))
            .ifPresent(npc -> loaded.put(npc.npcClass(), npc.withId(id))));

        byNpcClass = Map.copyOf(loaded);
        LOGGER.info("Loaded {} Cobblemon Dimensions Stories NPCs", byNpcClass.size());
    }
}
