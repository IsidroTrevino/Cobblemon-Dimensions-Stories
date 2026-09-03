package com.cobblemondimensions.stories.client;

import com.cobblemondimensions.stories.client.minigame.PatternGameScreen;
import com.cobblemondimensions.stories.client.minigame.PokesweeperScreen;
import com.cobblemondimensions.stories.client.minigame.PokewrleScreen;
import com.cobblemondimensions.stories.client.npc.QuestMarkerRenderer;
import com.cobblemondimensions.stories.client.quest.QuestHudIcon;
import com.cobblemondimensions.stories.client.quest.QuestScreen;
import com.cobblemondimensions.stories.client.walking.WalkingDialogueHud;
import com.cobblemondimensions.stories.client.walking.WalkingDialogueSession;
import com.cobblemondimensions.stories.net.PokewrleFeedbackS2C;
import com.cobblemondimensions.stories.net.QuestMarkersS2C;
import com.cobblemondimensions.stories.net.QuestSyncS2C;
import com.cobblemondimensions.stories.net.StartMinigameS2C;
import com.cobblemondimensions.stories.net.StartWalkingDialogueS2C;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class CobblemonDimensionsStoriesClient implements ClientModInitializer {
    private static final int NUMBER_KEY_CHOICES = 4;

    private static KeyMapping questKey;

    @Override
    public void onInitializeClient() {
        questKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.cobblemon_dimensions_stories.quests",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.cobblemon_dimensions_stories"
        ));

        ClientPlayNetworking.registerGlobalReceiver(QuestSyncS2C.TYPE,
            (payload, context) -> context.client().execute(() -> ClientQuestStore.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(StartWalkingDialogueS2C.TYPE, (payload, context) ->
            context.client().execute(() -> {
                // dialogue takes over the screen, close the journal if it is still up
                if (context.client().screen instanceof QuestScreen) {
                    context.client().setScreen(null);
                }
                WalkingDialogueSession.start(payload.dialogue(), payload.quest(), payload.speaker());
            }));

        ClientPlayNetworking.registerGlobalReceiver(QuestMarkersS2C.TYPE,
            (payload, context) -> context.client().execute(() -> QuestMarkerRenderer.accept(payload)));

        ClientPlayNetworking.registerGlobalReceiver(StartMinigameS2C.TYPE, (payload, context) ->
            context.client().execute(() -> {
                // Generation switch on an open board. Restart it in place instead of
                // building a new screen, the server has already re-dealt the answer.
                if (context.client().screen instanceof PokewrleScreen board) {
                    board.restart(payload.generation());
                    return;
                }
                // same deal for a retry on the sweeper board
                if (context.client().screen instanceof PokesweeperScreen field
                    && payload.game() == field.minigame()) {
                    field.restart();
                    return;
                }
                // drop any dialogue still running before the board takes the screen
                WalkingDialogueSession.clear();
                context.client().setScreen(switch (payload.game()) {
                    case PATTERN -> new PatternGameScreen(payload.best());
                    case POKEWRLE ->
                        new PokewrleScreen(payload.best(), payload.attempts(), payload.generation());
                    case POKESWEEPER, POKESWEEPER_EASY, POKESWEEPER_MEDIUM ->
                        new PokesweeperScreen(payload.game(), payload.best());
                });
            }));

        ClientPlayNetworking.registerGlobalReceiver(PokewrleFeedbackS2C.TYPE,
            (payload, context) -> context.client().execute(() -> PokewrleScreen.accept(payload)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientQuestStore.clear();
            WalkingDialogueSession.clear();
            QuestMarkerRenderer.clear();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(QuestMarkerRenderer::render);

        // Must be START_CLIENT_TICK: it runs before vanilla reads the keybinds.
        // Consuming a hotbar key here picks a dialogue choice and the held item
        // never changes.
        ClientTickEvents.START_CLIENT_TICK.register(CobblemonDimensionsStoriesClient::tick);

        HudRenderCallback.EVENT.register((graphics, tickDelta) -> {
            QuestHudIcon.render(graphics);
            WalkingDialogueHud.render(graphics);
        });
    }

    private static void tick(Minecraft minecraft) {
        WalkingDialogueSession.tick();

        WalkingDialogueSession session = WalkingDialogueSession.active();
        if (session != null) {
            for (int index = 0; index < NUMBER_KEY_CHOICES; index++) {
                while (minecraft.options.keyHotbarSlots[index].consumeClick()) {
                    session.selectAndConfirm(index);
                }
            }
            // eat the journal key as well, it must not open mid-line
            while (questKey.consumeClick()) {
                // drained on purpose
            }
            return;
        }

        boolean opened = false;
        while (questKey.consumeClick()) {
            opened = true;
        }
        if (opened && minecraft.screen == null && minecraft.player != null) {
            minecraft.setScreen(new QuestScreen());
        }
    }
}
