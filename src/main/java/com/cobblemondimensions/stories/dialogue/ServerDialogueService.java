package com.cobblemondimensions.stories.dialogue;

import com.cobblemondimensions.stories.minigame.MinigameService;
import com.cobblemondimensions.stories.npc.StoryNpcs;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.quest.QuestStatus;
import com.cobblemondimensions.stories.ui.StoriesText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Walking conversations through vanilla chat; all input authority stays on the server. */
public final class ServerDialogueService {
    private static final double ABANDON_DISTANCE_SQUARED = 20.0D * 20.0D;
    private static final int IDLE_TICKS = 20 * 120;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ServerDialogueService() {
    }

    public static void start(ServerPlayer player, WalkingDialogue dialogue,
                             Optional<ResourceLocation> quest, int speaker) {
        Session previous = SESSIONS.remove(player.getUUID());
        if (previous != null) {
            previous.cursor.cancel();
        }
        // NPC interaction cancels the previous conversation before reserving the
        // new speaker. Releasing here would also release that new reservation.
        if (speaker < 0) {
            StoryNpcs.endConversation(player);
        }
        if (dialogue.pages().isEmpty()) {
            StoryNpcs.endConversation(player);
            return;
        }

        Session session = new Session(player, dialogue, quest, speaker);
        SESSIONS.put(player.getUUID(), session);
        if (!valid(session)) {
            cancel(player);
            return;
        }
        showPage(session);
    }

    public static void choose(ServerPlayer player, String token, int choice) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.player != player) {
            expiredLink(player);
            return;
        }
        if (player.server.getTickCount() - session.lastInputTick >= IDLE_TICKS) {
            cancel(player);
            expiredLink(player);
            return;
        }
        if (!valid(session)) {
            cancel(player);
            walkedAway(player);
            return;
        }

        WalkingDialoguePage page = session.dialogue.pages().get(session.cursor.pageIndex());
        DialogueCursor.Result result = session.cursor.choose(player.getUUID(), token, choice);
        switch (result) {
            case REJECTED -> expiredLink(player);
            case PAGE -> {
                session.lastInputTick = player.server.getTickCount();
                showPage(session);
            }
            case CANCELLED -> {
                detach(session);
                player.sendSystemMessage(Component.literal("Conversation cancelled.")
                    .withStyle(ChatFormatting.GRAY));
            }
            case COMPLETED, MINIGAME -> {
                // Detach before quest completion: it can synchronously start the
                // next stage's dialogue, which must survive this callback.
                detach(session);
                QuestService.onDialogueFinished(player, session.quest, true, session.speaker);
                if (result == DialogueCursor.Result.MINIGAME) {
                    page.minigame().ifPresent(game -> MinigameService.open(player, game));
                }
            }
        }
    }

    /** Closing or abandoning a conversation never advances a quest. */
    public static void cancel(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            session.cursor.cancel();
        }
        StoryNpcs.endConversation(player);
    }

    public static void tick(MinecraftServer server) {
        // Cancellation mutates the map, and future completion callbacks can open
        // another session, so iterate a snapshot rather than the live values.
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = session.player;
            if (server.getPlayerList().getPlayer(player.getUUID()) != player) {
                cancel(player);
            } else if (!valid(session)) {
                cancel(player);
                walkedAway(player);
            } else if (server.getTickCount() - session.lastInputTick >= IDLE_TICKS) {
                cancel(player);
                player.sendSystemMessage(Component.literal("Conversation expired. Talk to the speaker to restart.")
                    .withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public static void stop() {
        for (Session session : List.copyOf(SESSIONS.values())) {
            cancel(session.player);
        }
    }

    private static void detach(Session session) {
        SESSIONS.remove(session.player.getUUID(), session);
        StoryNpcs.endConversation(session.player);
    }

    private static boolean valid(Session session) {
        ServerPlayer player = session.player;
        if (!player.isAlive() || player.isRemoved() || !player.level().dimension().equals(session.dimension)) {
            return false;
        }
        if (session.speaker >= 0) {
            Entity speaker = player.serverLevel().getEntity(session.speaker);
            if (speaker == null || speaker.isRemoved() || !speaker.getUUID().equals(session.speakerUuid)
                || player.distanceToSqr(speaker) > ABANDON_DISTANCE_SQUARED) {
                return false;
            }
        }
        return session.questStamp.map(stamp ->
            QuestDefinitions.get().byId(stamp.quest().id()).filter(quest -> quest == stamp.quest())
                .filter(quest -> QuestService.status(player, quest) == stamp.status())
                .filter(quest -> QuestService.progressOf(player, quest).stage() == stamp.stage())
                .isPresent()).orElse(true);
    }

    private static void showPage(Session session) {
        ServerPlayer player = session.player;
        WalkingDialoguePage page = session.dialogue.pages().get(session.cursor.pageIndex());
        Component speaker = page.speaker().map(StoriesText::resolve)
            .orElseGet(() -> Component.literal("Dialogue"));
        player.sendSystemMessage(Component.literal("— ").withStyle(ChatFormatting.GOLD)
            .append(speaker).append(" —"));
        for (Component line : page.lines()) {
            player.sendSystemMessage(StoriesText.resolve(line));
        }
        String token = session.cursor.token();
        if (page.hasOptions()) {
            for (int index = 0; index < page.options().size(); index++) {
                MutableComponent label = Component.literal("[" + (index + 1) + "] ")
                    .append(StoriesText.resolve(page.options().get(index).text()));
                player.sendSystemMessage(link(label, token, index, ChatFormatting.GREEN));
            }
        } else {
            Component label = Component.literal(page.minigame().isPresent() ? "[Play]" : "[Next]");
            player.sendSystemMessage(link(label, token, -1, ChatFormatting.GREEN));
        }
        player.sendSystemMessage(link(Component.literal("[Cancel]"), token, -2, ChatFormatting.GRAY));
    }

    private static MutableComponent link(Component label, String token, int choice, ChatFormatting color) {
        return label.copy().withStyle(style -> style.withColor(color).withUnderlined(true)
            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/stories choose " + token + " " + choice))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal("Click to continue this conversation"))));
    }

    private static void expiredLink(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("That dialogue link has expired. Use the latest choices.")
            .withStyle(ChatFormatting.GRAY));
    }

    private static void walkedAway(ServerPlayer player) {
        player.displayClientMessage(StoriesText.translatable("message.cobblemon_dimensions_stories.walked_away"), true);
    }

    private record QuestStamp(Quest quest, QuestStatus status, int stage) {
    }

    private static final class Session {
        private final ServerPlayer player;
        private final WalkingDialogue dialogue;
        private final Optional<ResourceLocation> quest;
        private final int speaker;
        private final UUID speakerUuid;
        private final ResourceKey<Level> dimension;
        private final Optional<QuestStamp> questStamp;
        private final DialogueCursor cursor;
        private int lastInputTick;

        private Session(ServerPlayer player, WalkingDialogue dialogue,
                        Optional<ResourceLocation> quest, int speaker) {
            this.player = player;
            this.dialogue = dialogue;
            this.quest = quest;
            this.speaker = speaker;
            Entity entity = speaker < 0 ? null : player.serverLevel().getEntity(speaker);
            this.speakerUuid = entity == null ? null : entity.getUUID();
            this.dimension = player.level().dimension();
            this.questStamp = quest.flatMap(QuestDefinitions.get()::byId).map(definition ->
                new QuestStamp(definition, QuestService.status(player, definition),
                    QuestService.progressOf(player, definition).stage()));
            this.cursor = new DialogueCursor(player.getUUID(), dialogue.pages().stream().map(page ->
                new DialogueCursor.Page(page.id(), page.options().stream().map(WalkingDialogueOption::next).toList(),
                    page.next(), page.minigame().isPresent())).toList());
            this.lastInputTick = player.server.getTickCount();
        }
    }
}
