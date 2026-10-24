package com.cobblemondimensions.stories.dialogue;

import com.cobblemondimensions.stories.minigame.MinigameService;
import com.cobblemondimensions.stories.npc.StoryNpcs;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.quest.QuestStatus;
import com.cobblemondimensions.stories.ui.Hud;
import com.cobblemondimensions.stories.ui.StoriesText;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Walking conversations, shown in the dialogue box above the hotbar so the player
 * can keep moving while an NPC talks.
 *
 * <p>Input works the way it did with the client mod: left click (or right-clicking
 * the speaker again) finishes the line or moves on, and the scroll wheel moves the
 * highlight when there is a choice. {@link Hud} reads both from what the client
 * sends; all input authority stays on the server.
 */
public final class ServerDialogueService {
    private static final double ABANDON_DISTANCE_SQUARED = 20.0D * 20.0D;
    private static final int IDLE_TICKS = 20 * 120;
    private static final int BLIP_STRIDE = 2;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private ServerDialogueService() {
    }

    public static void start(ServerPlayer player, WalkingDialogue dialogue,
                             Optional<ResourceLocation> quest, int speaker) {
        Session previous = SESSIONS.remove(player.getUUID());
        if (previous != null) {
            previous.cursor.cancel();
            Hud.close(player, previous);
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
        Hud.show(player, session);
        Hud.safely(player, session, () -> session.showPage(System.nanoTime()));
    }

    /** True while a conversation is on screen. */
    public static boolean active(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
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

        WalkingDialoguePage page = session.page();
        DialogueCursor.Result result = session.cursor.choose(player.getUUID(), token, choice);
        switch (result) {
            case REJECTED -> expiredLink(player);
            case PAGE -> {
                session.lastInputTick = player.server.getTickCount();
                Hud.safely(player, session, () -> session.showPage(System.nanoTime()));
            }
            case CANCELLED -> detach(session);
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
            Hud.close(player, session);
        }
        StoryNpcs.endConversation(player);
    }

    public static void stop() {
        for (Session session : List.copyOf(SESSIONS.values())) {
            cancel(session.player);
        }
    }

    private static void detach(Session session) {
        SESSIONS.remove(session.player.getUUID(), session);
        Hud.close(session.player, session);
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

    private static void expiredLink(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("That dialogue choice has expired.")
            .withStyle(ChatFormatting.GRAY));
    }

    private static void walkedAway(ServerPlayer player) {
        player.displayClientMessage(StoriesText.translatable("message.cobblemon_dimensions_stories.walked_away"), true);
    }

    private record QuestStamp(Quest quest, QuestStatus status, int stage) {
    }

    private static final class Session implements Hud.Screen {
        private final ServerPlayer player;
        private final WalkingDialogue dialogue;
        private final Optional<ResourceLocation> quest;
        private final int speaker;
        private final UUID speakerUuid;
        private final ResourceKey<Level> dimension;
        private final Optional<QuestStamp> questStamp;
        private final DialogueCursor cursor;
        private int lastInputTick;

        private long openedNanos;
        private long pageNanos;
        private long choicesNanos;
        private boolean forceReveal;
        private int selection;
        private int spoken;
        private String text = "";

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
            this.openedNanos = System.nanoTime();
        }

        private WalkingDialoguePage page() {
            return dialogue.pages().get(cursor.pageIndex());
        }

        private void showPage(long now) {
            WalkingDialoguePage page = page();
            text = String.join("\n\n", page.lines().stream()
                .map(line -> StoriesText.resolve(line).getString()).toList());
            pageNanos = Math.max(now, openedNanos + DialogueView.OPEN_NANOS + DialogueView.UNROLL_NANOS);
            forceReveal = false;
            selection = 0;
            choicesNanos = 0L;
            spoken = 0;
            draw(now);
        }

        private int visible(long now) {
            if (forceReveal) {
                return text.length();
            }
            double seconds = Math.max(0L, now - pageNanos) / 1_000_000_000.0D;
            return Math.min(text.length(), (int) Math.floor(seconds * DialogueView.CHARACTERS_PER_SECOND));
        }

        private boolean complete(long now) {
            return visible(now) >= text.length();
        }

        private boolean choosing(long now) {
            return page().hasOptions() && complete(now);
        }

        /** Skip the opening, finish the line, or move on. */
        @Override
        public void click() {
            long now = System.nanoTime();
            lastInputTick = player.server.getTickCount();
            if (now - openedNanos < DialogueView.OPEN_NANOS + DialogueView.UNROLL_NANOS) {
                openedNanos = now - DialogueView.OPEN_NANOS - DialogueView.UNROLL_NANOS;
                pageNanos = Math.max(pageNanos, now);
                return;
            }
            if (!complete(now)) {
                forceReveal = true;
                return;
            }
            WalkingDialoguePage page = page();
            int choice = page.hasOptions() ? Math.floorMod(selection, page.options().size()) : -1;
            choose(player, cursor.token(), choice);
        }

        @Override
        public boolean scrolls() {
            return choosing(System.nanoTime());
        }

        @Override
        public void scroll(int steps) {
            selection += steps;
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.25F, 1.4F);
            lastInputTick = player.server.getTickCount();
        }

        @Override
        public boolean belongsTo(Entity entity) {
            return speakerUuid != null && speakerUuid.equals(entity.getUUID());
        }

        @Override
        public void tick(long now) {
            if (SESSIONS.get(player.getUUID()) != this) {
                return;
            }
            if (!valid(this)) {
                cancel(player);
                walkedAway(player);
                return;
            }
            if (player.server.getTickCount() - lastInputTick >= IDLE_TICKS) {
                cancel(player);
                player.sendSystemMessage(Component.literal("Conversation expired. Talk to the speaker to restart.")
                    .withStyle(ChatFormatting.GRAY));
                return;
            }
            speak(now);
            if (choosing(now) && choicesNanos == 0L) {
                choicesNanos = now;
            }
            draw(now);
        }

        @Override
        public void hidden() {
            // Another overlay took the screen; the conversation is over.
            if (SESSIONS.remove(player.getUUID(), this)) {
                cursor.cancel();
                StoryNpcs.endConversation(player);
            }
        }

        /** One soft blip for every couple of letters typed, pitched per speaker. */
        private void speak(long now) {
            int visible = visible(now);
            if (visible <= spoken) {
                spoken = visible;
                return;
            }
            boolean blip = visible - spoken > BLIP_STRIDE * 2;
            for (int index = spoken; !blip && index < visible; index++) {
                blip = index % BLIP_STRIDE == 0 && !Character.isWhitespace(text.charAt(index));
            }
            spoken = visible;
            if (blip) {
                String name = page().speakerText();
                float pitch = name.isBlank() ? 1.2F : 0.9F + Math.floorMod(name.hashCode(), 7) * 0.09F;
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.MASTER, 0.35F, pitch);
            }
        }

        // the speaker and option strings of the page last drawn; resolving them every tick is wasted work
        private WalkingDialoguePage resolvedFor;
        private String speakerName = "";
        private List<String> optionTexts = List.of();

        private void draw(long now) {
            WalkingDialoguePage page = page();
            if (page != resolvedFor) {
                resolvedFor = page;
                speakerName = page.speaker().map(name -> StoriesText.resolve(name).getString()).orElse("");
                optionTexts = page.options().stream().map(option -> StoriesText.resolve(option.text()).getString()).toList();
            }
            DialogueView.Frame frame = new DialogueView.Frame(speakerName,
                text, visible(now), complete(now), page.hasOptions(), optionTexts,
                selection, openedNanos, pageNanos, choicesNanos);
            Hud.draw(player, this, DialogueView.compose(frame, now));
        }
    }
}
