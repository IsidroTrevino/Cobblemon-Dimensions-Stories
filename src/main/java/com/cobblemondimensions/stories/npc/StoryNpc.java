package com.cobblemondimensions.stories.npc;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * Datapack binding from a Cobblemon NPC class to the story: the quests it hands
 * out, its idle line, and how it patrols when nobody is talking to it. Loaded
 * from {@code data/<namespace>/story_npcs/<path>.json}.
 *
 * <p>Route points are offsets from the spawn position, not world coordinates,
 * so one definition works wherever a builder drops the entity.
 */
public record StoryNpc(
    ResourceLocation id,
    ResourceLocation npcClass,
    Component name,
    List<ResourceLocation> quests,
    Optional<ResourceLocation> idleDialogue,
    List<BlockPos> route,
    double routeSpeed,
    int routePause,
    Optional<ResourceLocation> followDuring,
    double followSpeed,
    Optional<ResourceLocation> shop
) {
    private static final ResourceLocation PLACEHOLDER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "unnamed");

    /** File body only. The id is taken from the file path. */
    public static final Codec<StoryNpc> BODY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("npc_class").forGetter(StoryNpc::npcClass),
        ComponentSerialization.CODEC.fieldOf("name").forGetter(StoryNpc::name),
        ResourceLocation.CODEC.listOf().optionalFieldOf("quests", List.of()).forGetter(StoryNpc::quests),
        ResourceLocation.CODEC.optionalFieldOf("idle_dialogue").forGetter(StoryNpc::idleDialogue),
        BlockPos.CODEC.listOf().optionalFieldOf("route", List.of()).forGetter(StoryNpc::route),
        Codec.DOUBLE.optionalFieldOf("route_speed", 0.45D).forGetter(StoryNpc::routeSpeed),
        Codec.INT.optionalFieldOf("route_pause", 60).forGetter(StoryNpc::routePause),
        ResourceLocation.CODEC.optionalFieldOf("follow_during").forGetter(StoryNpc::followDuring),
        Codec.DOUBLE.optionalFieldOf("follow_speed", 0.6D).forGetter(StoryNpc::followSpeed),
        ResourceLocation.CODEC.optionalFieldOf("shop").forGetter(StoryNpc::shop)
    ).apply(instance, (npcClass, name, quests, idle, route, routeSpeed, routePause, follow, followSpeed, shop) ->
        new StoryNpc(
            PLACEHOLDER_ID,
            npcClass,
            name,
            quests,
            idle,
            route,
            routeSpeed,
            routePause,
            follow,
            followSpeed,
            shop
        )));

    public StoryNpc withId(ResourceLocation storyId) {
        return new StoryNpc(
            storyId,
            npcClass,
            name,
            quests,
            idleDialogue,
            route,
            routeSpeed,
            routePause,
            followDuring,
            followSpeed,
            shop
        );
    }

    // one point is not a patrol
    public boolean hasRoute() {
        return route.size() >= 2;
    }
}
