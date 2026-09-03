package com.cobblemondimensions.stories;

import com.cobblemondimensions.stories.dialogue.WalkingDialogueDefinitions;
import com.cobblemondimensions.stories.minigame.MinigameService;
import com.cobblemondimensions.stories.net.OpenMinigameC2S;
import com.cobblemondimensions.stories.net.PatternResultC2S;
import com.cobblemondimensions.stories.net.PokesweeperResultC2S;
import com.cobblemondimensions.stories.net.PokewrleFeedbackS2C;
import com.cobblemondimensions.stories.net.PokewrleGuessC2S;
import com.cobblemondimensions.stories.net.PokewrleSetupC2S;
import com.cobblemondimensions.stories.net.QuestMarkersS2C;
import com.cobblemondimensions.stories.net.QuestSyncS2C;
import com.cobblemondimensions.stories.net.StartMinigameS2C;
import com.cobblemondimensions.stories.net.StartWalkingDialogueS2C;
import com.cobblemondimensions.stories.net.WalkingDialogueDoneC2S;
import com.cobblemondimensions.stories.npc.StoryNpcDefinitions;
import com.cobblemondimensions.stories.npc.StoryNpcs;
import com.cobblemondimensions.stories.quest.QuestCommand;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;

public final class CobblemonDimensionsStories implements ModInitializer {
    public static final String MOD_ID = "cobblemon_dimensions_stories";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        registerPayloads();
        registerReloadListeners();

        ServerPlayNetworking.registerGlobalReceiver(WalkingDialogueDoneC2S.TYPE, (payload, context) -> {
            StoryNpcs.endConversation(context.player());
            QuestService.onDialogueFinished(
                context.player(), payload.quest(), payload.completed(), payload.speaker());
        });

        ServerPlayNetworking.registerGlobalReceiver(OpenMinigameC2S.TYPE, (payload, context) ->
            MinigameService.open(context.player(), payload.game()));

        ServerPlayNetworking.registerGlobalReceiver(PatternResultC2S.TYPE, (payload, context) ->
            MinigameService.finishPattern(context.player(), payload.levelsCleared()));

        ServerPlayNetworking.registerGlobalReceiver(PokesweeperResultC2S.TYPE, (payload, context) ->
            MinigameService.finishPokesweeper(
                context.player(), payload.cleared(), payload.uncovered(), payload.seconds()));

        ServerPlayNetworking.registerGlobalReceiver(PokewrleGuessC2S.TYPE, (payload, context) ->
            MinigameService.guess(context.player(), payload.species()));

        ServerPlayNetworking.registerGlobalReceiver(PokewrleSetupC2S.TYPE, (payload, context) ->
            MinigameService.setup(context.player(), payload.generation()));

        // hunt stages only count kills credited to a player
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer killer) {
                QuestService.onEntityKilled(killer, entity);
            }
        });

        // Talking to the owning NPC is how a quest gets started and turned in.
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            return StoryNpcs.interact(serverPlayer, entity)
                ? InteractionResult.SUCCESS
                : InteractionResult.PASS;
        });

        ServerTickEvents.END_SERVER_TICK.register(StoryNpcs::tick);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            QuestService.sync(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StoryNpcs.onPlayerDisconnect(handler.getPlayer());
            MinigameService.forget(handler.getPlayer());
        });

        // /reload can rewrite the quest definitions. Resync every client.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) ->
            QuestService.syncAll(server));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            QuestCommand.register(dispatcher));
    }

    private static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(QuestSyncS2C.TYPE, QuestSyncS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
            .register(StartWalkingDialogueS2C.TYPE, StartWalkingDialogueS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(QuestMarkersS2C.TYPE, QuestMarkersS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StartMinigameS2C.TYPE, StartMinigameS2C.STREAM_CODEC);
        PayloadTypeRegistry.playS2C()
            .register(PokewrleFeedbackS2C.TYPE, PokewrleFeedbackS2C.STREAM_CODEC);
        PayloadTypeRegistry.playC2S()
            .register(WalkingDialogueDoneC2S.TYPE, WalkingDialogueDoneC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(OpenMinigameC2S.TYPE, OpenMinigameC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PatternResultC2S.TYPE, PatternResultC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S()
            .register(PokesweeperResultC2S.TYPE, PokesweeperResultC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S()
            .register(PokewrleGuessC2S.TYPE, PokewrleGuessC2S.STREAM_CODEC);
        PayloadTypeRegistry.playC2S()
            .register(PokewrleSetupC2S.TYPE, PokewrleSetupC2S.STREAM_CODEC);
    }

    private static void registerReloadListeners() {
        ResourceManagerHelper serverData = ResourceManagerHelper.get(PackType.SERVER_DATA);
        serverData.registerReloadListener(QuestDefinitions.get());
        serverData.registerReloadListener(WalkingDialogueDefinitions.get());
        serverData.registerReloadListener(StoryNpcDefinitions.get());
    }
}
