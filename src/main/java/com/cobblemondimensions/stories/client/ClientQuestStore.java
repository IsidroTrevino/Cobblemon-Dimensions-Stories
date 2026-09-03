package com.cobblemondimensions.stories.client;

import com.cobblemondimensions.stories.net.QuestSyncS2C;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestProgress;
import com.cobblemondimensions.stories.quest.QuestStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Client copy of the quest list and this player's progress. Replaced wholesale on each sync. */
public final class ClientQuestStore {
    private static List<Quest> quests = List.of();
    private static Map<ResourceLocation, QuestStatus> statuses = Map.of();
    private static Map<ResourceLocation, QuestProgress> progress = Map.of();
    private static Map<ResourceLocation, Component> givers = Map.of();
    private static final Set<ResourceLocation> seen = new HashSet<>();
    private static long changedNanos = System.nanoTime();

    private ClientQuestStore() {
    }

    public static void accept(QuestSyncS2C payload) {
        quests = payload.quests();
        statuses = payload.statuses();
        progress = payload.progress();
        givers = payload.givers();
        changedNanos = System.nanoTime();
        // forget ids the server no longer knows about
        seen.retainAll(statuses.keySet());
    }

    public static void clear() {
        quests = List.of();
        statuses = Map.of();
        progress = Map.of();
        givers = Map.of();
        seen.clear();
    }

    public static QuestProgress progressOf(ResourceLocation id) {
        return progress.getOrDefault(id, QuestProgress.START);
    }

    /** Name of the NPC handing out the quest, if the datapack set one. */
    public static Optional<Component> giverOf(ResourceLocation id) {
        return Optional.ofNullable(givers.get(id));
    }

    public static List<Quest> quests() {
        return quests;
    }

    public static boolean isEmpty() {
        return quests.isEmpty();
    }

    public static QuestStatus statusOf(ResourceLocation id) {
        return statuses.getOrDefault(id, QuestStatus.LOCKED);
    }

    public static Optional<Quest> byId(ResourceLocation id) {
        return quests.stream().filter(quest -> quest.id().equals(id)).findFirst();
    }

    public static long changedNanos() {
        return changedNanos;
    }

    /** True if some AVAILABLE quest has not been opened in the journal yet. */
    public static boolean hasUnseenQuests() {
        return quests.stream().anyMatch(quest ->
            statusOf(quest.id()) == QuestStatus.AVAILABLE && !seen.contains(quest.id()));
    }

    public static boolean hasActiveQuests() {
        return quests.stream().anyMatch(quest -> statusOf(quest.id()) == QuestStatus.ACTIVE);
    }

    /** Quest screen calls this once it has drawn a quest. Clears the HUD badge. */
    public static void markSeen(ResourceLocation id) {
        seen.add(id);
    }
}
