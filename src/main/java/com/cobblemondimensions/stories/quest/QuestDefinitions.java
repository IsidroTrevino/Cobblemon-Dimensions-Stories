package com.cobblemondimensions.stories.quest;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side reload listener for {@code data/<namespace>/quests/*.json}. */
public final class QuestDefinitions extends SimpleJsonResourceReloadListener
    implements IdentifiableResourceReloadListener {
    public static final ResourceLocation LISTENER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "quests");

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_dimensions_stories/quests");
    private static final QuestDefinitions INSTANCE = new QuestDefinitions();

    private List<Quest> ordered = List.of();

    private QuestDefinitions() {
        super(new Gson(), "quests");
    }

    public static QuestDefinitions get() {
        return INSTANCE;
    }

    /** Every quest, sorted by {@code order} then id. Ties break on id to keep reloads stable. */
    public List<Quest> all() {
        return ordered;
    }

    public Optional<Quest> byId(ResourceLocation id) {
        return ordered.stream().filter(quest -> quest.id().equals(id)).findFirst();
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
        List<Quest> loaded = new ArrayList<>(entries.size());
        entries.forEach((id, json) -> Quest.BODY_CODEC
            .parse(JsonOps.INSTANCE, json)
            .resultOrPartial(error -> LOGGER.error("Skipping quest {}: {}", id, error))
            .ifPresent(quest -> loaded.add(quest.withId(id))));

        loaded.sort(Comparator.comparingInt(Quest::order)
            .thenComparing(quest -> quest.id().toString()));
        ordered = List.copyOf(loaded);
        LOGGER.info("Loaded {} Cobblemon Dimensions Stories quests", ordered.size());
    }
}
