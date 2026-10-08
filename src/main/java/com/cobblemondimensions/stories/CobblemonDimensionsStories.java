package com.cobblemondimensions.stories;

import com.cobblemondimensions.stories.dialogue.WalkingDialogueDefinitions;
import com.cobblemondimensions.stories.dialogue.ServerDialogueService;
import com.cobblemondimensions.stories.minigame.MinigameService;
import com.cobblemondimensions.stories.npc.StoryNpcDefinitions;
import com.cobblemondimensions.stories.npc.StoryNpcs;
import com.cobblemondimensions.stories.npc.ServerQuestMarkers;
import com.cobblemondimensions.stories.quest.QuestCommand;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.ui.QuestJournal;
import com.cobblemondimensions.stories.ui.StoriesMenu;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
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
        registerReloadListeners();

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

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerDialogueService.tick(server);
            StoryNpcs.tick(server);
            MinigameService.tick(server);
            StoriesMenu.tick(server);
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            ServerDialogueService.cancel(player);
            MinigameService.forget(player);
            QuestJournal.forget(player);
            ServerQuestMarkers.clear(player);
            QuestService.sync(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> server.execute(() -> {
            ServerPlayer departed = handler.getPlayer();
            ServerPlayer present = server.getPlayerList().getPlayer(departed.getUUID());
            if (present != null && present != departed) {
                return;
            }
            ServerDialogueService.cancel(departed);
            StoryNpcs.onPlayerDisconnect(departed);
            MinigameService.forget(departed);
            QuestJournal.forget(departed);
            ServerQuestMarkers.clear(departed);
        }));

        // Invalidate chat links when a datapack changes the pages they point to.
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
            MinigameService.stop();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerDialogueService.cancel(player);
                ServerQuestMarkers.clear(player);
            }
            QuestService.syncAll(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ServerDialogueService.stop();
            MinigameService.stop();
            QuestJournal.stop();
            StoryNpcs.stop();
            ServerQuestMarkers.stop();
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            QuestCommand.register(dispatcher));
    }

    private static void registerReloadListeners() {
        ResourceManagerHelper serverData = ResourceManagerHelper.get(PackType.SERVER_DATA);
        serverData.registerReloadListener(QuestDefinitions.get());
        serverData.registerReloadListener(WalkingDialogueDefinitions.get());
        serverData.registerReloadListener(StoryNpcDefinitions.get());
    }
}
