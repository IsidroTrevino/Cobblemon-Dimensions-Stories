package com.cobblemondimensions.stories.shop;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side reload listener for {@code data/<namespace>/shop_dialogues/*.json}. */
public final class ShopDialogueDefinitions extends SimpleJsonResourceReloadListener
    implements IdentifiableResourceReloadListener {
    public static final ResourceLocation LISTENER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "shop_dialogues");

    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_dimensions_stories/shop_dialogues");
    private static final ShopDialogueDefinitions INSTANCE = new ShopDialogueDefinitions();

    private Map<ResourceLocation, ShopDialogue> dialogues = Map.of();

    private ShopDialogueDefinitions() {
        super(new Gson(), "shop_dialogues");
    }

    public static ShopDialogueDefinitions get() {
        return INSTANCE;
    }

    public Optional<ShopDialogue> byId(ResourceLocation id) {
        return Optional.ofNullable(dialogues.get(id));
    }

    public Set<ResourceLocation> ids() {
        return dialogues.keySet();
    }

    @Override
    public ResourceLocation getFabricId() {
        return LISTENER_ID;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        // one bad file is logged and dropped, the rest of the pack still loads
        Map<ResourceLocation, ShopDialogue> loaded = new HashMap<>(entries.size());
        entries.forEach((id, json) -> ShopDialogue.BODY_CODEC
            .parse(JsonOps.INSTANCE, json)
            .resultOrPartial(error -> LOGGER.error("Skipping shop dialogue {}: {}", id, error))
            .ifPresent(dialogue -> loaded.put(id, dialogue.withId(id))));
        dialogues = Map.copyOf(loaded);
        LOGGER.info("Loaded {} Cobblemon Dimensions Stories shop dialogues", dialogues.size());
    }
}
