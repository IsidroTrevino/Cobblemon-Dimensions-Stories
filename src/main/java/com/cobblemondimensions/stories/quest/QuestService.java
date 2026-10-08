package com.cobblemondimensions.stories.quest;

import com.cobblemondimensions.stories.dialogue.WalkingDialogueDefinitions;
import com.cobblemondimensions.stories.dialogue.ServerDialogueService;
import com.cobblemondimensions.stories.ui.QuestJournal;
import com.cobblemondimensions.stories.ui.StoriesText;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/** Server-side quest rules: who can accept what, how stages advance, what a finish pays out. */
public final class QuestService {
    /** Speaker id for dialogue with no entity behind it, e.g. from a command. */
    public static final int NO_SPEAKER = -1;

    private QuestService() {
    }

    public static Map<ResourceLocation, QuestStatus> statuses(ServerPlayer player) {
        QuestSaveData data = QuestSaveData.get(player.server);
        Map<ResourceLocation, QuestStatus> statuses = new HashMap<>();
        for (Quest quest : QuestDefinitions.get().all()) {
            statuses.put(quest.id(), statusOf(data, player, quest));
        }
        return statuses;
    }

    public static QuestStatus status(ServerPlayer player, Quest quest) {
        return statusOf(QuestSaveData.get(player.server), player, quest);
    }

    public static QuestProgress progressOf(ServerPlayer player, Quest quest) {
        return QuestSaveData.get(player.server).progressOf(player.getUUID(), quest.id());
    }

    public static QuestObjective currentObjective(ServerPlayer player, Quest quest) {
        return quest.stage(progressOf(player, quest).stage());
    }

    /**
     * True when the current stage is done and the giver can push the quest on.
     * Dialogue stages are excluded: they close themselves, not on a turn-in.
     */
    public static boolean canAdvance(ServerPlayer player, Quest quest) {
        if (status(player, quest) != QuestStatus.ACTIVE) {
            return false;
        }
        QuestObjective objective = currentObjective(player, quest);
        return !objective.completesWithDialogue() && isSatisfied(player, quest, objective);
    }

    private static boolean isSatisfied(ServerPlayer player, Quest quest, QuestObjective objective) {
        return switch (objective.kind()) {
            case ITEM -> countItem(player, objective) >= objective.required();
            case KILL, ESCORT -> progressOf(player, quest).count() >= objective.required();
            case DIALOGUE, COMMAND -> false;
        };
    }

    // locked until every id in "requires" is completed
    private static QuestStatus statusOf(QuestSaveData data, ServerPlayer player, Quest quest) {
        if (data.isCompleted(player.getUUID(), quest.id())) {
            return QuestStatus.COMPLETED;
        }
        if (data.isActive(player.getUUID(), quest.id())) {
            return QuestStatus.ACTIVE;
        }
        boolean unlocked = quest.requires().stream()
            .allMatch(required -> data.isCompleted(player.getUUID(), required));
        return unlocked ? QuestStatus.AVAILABLE : QuestStatus.LOCKED;
    }

    /** Refreshes the server-controlled journal and its quest notices. */
    public static void sync(ServerPlayer player) {
        QuestJournal.refresh(player);
    }

    public static void syncAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(QuestService::sync);
    }

    public static void accept(ServerPlayer player, ResourceLocation questId, int speaker) {
        Optional<Quest> found = QuestDefinitions.get().byId(questId);
        if (found.isEmpty()) {
            return;
        }
        Quest quest = found.get();
        QuestSaveData data = QuestSaveData.get(player.server);
        if (statusOf(data, player, quest) != QuestStatus.AVAILABLE) {
            return;
        }

        data.setActive(player.getUUID(), quest.id());
        sync(player);
        startStageDialogue(player, quest, 0, speaker);
    }

    /** The giver moves an active quest on. Rejects with an actionbar message if the stage is not done. */
    public static void advance(ServerPlayer player, ResourceLocation questId, int speaker) {
        Optional<Quest> found = QuestDefinitions.get().byId(questId);
        if (found.isEmpty()) {
            return;
        }
        Quest quest = found.get();
        QuestProgress progress = progressOf(player, quest);
        QuestObjective objective = quest.stage(progress.stage());
        if (status(player, quest) != QuestStatus.ACTIVE || !isSatisfied(player, quest, objective)) {
            player.displayClientMessage(
                StoriesText.translatable("message.cobblemon_dimensions_stories.objective_unfinished")
                    .withStyle(ChatFormatting.RED),
                true
            );
            return;
        }
        if (objective.isTurnIn() && objective.consume()) {
            removeItem(player, objective, objective.required());
        }
        advanceStage(player, quest, progress, speaker);
    }

    private static void advanceStage(
        ServerPlayer player,
        Quest quest,
        QuestProgress progress,
        int speaker
    ) {
        int next = progress.stage() + 1;
        if (next >= quest.stageCount()) {
            complete(player, quest, speaker);
            return;
        }

        QuestSaveData.get(player.server)
            .setProgress(player.getUUID(), quest.id(), new QuestProgress(next, 0));
        sync(player);
        player.displayClientMessage(
            StoriesText.translatable("message.cobblemon_dimensions_stories.stage_complete", next, quest.stageCount())
                .withStyle(ChatFormatting.YELLOW),
            true
        );
        startStageDialogue(player, quest, next, speaker);
    }

    /** Force-completes a quest. False when the id is not loaded. */
    public static boolean complete(ServerPlayer player, ResourceLocation questId) {
        Optional<Quest> found = QuestDefinitions.get().byId(questId);
        if (found.isEmpty()) {
            return false;
        }
        complete(player, found.get(), NO_SPEAKER);
        return true;
    }

    private static void complete(ServerPlayer player, Quest quest, int speaker) {
        QuestSaveData data = QuestSaveData.get(player.server);
        if (data.isCompleted(player.getUUID(), quest.id())) {
            return;
        }

        data.setCompleted(player.getUUID(), quest.id());
        quest.rewards().forEach(reward -> grant(player, reward));
        sync(player);
        player.displayClientMessage(
            StoriesText.translatable("message.cobblemon_dimensions_stories.quest_complete", quest.title())
                .withStyle(ChatFormatting.GOLD),
            false
        );
        quest.completionDialogue()
            .ifPresent(dialogue -> startDialogue(player, dialogue, Optional.empty(), speaker));
    }

    /**
     * Pays out one reward. Item rewards that do not fit get dropped at the
     * player's feet. Command rewards run at permission level 2 with output off.
     */
    public static void grant(ServerPlayer player, QuestReward reward) {
        if (reward.kind() == QuestReward.Kind.COMMAND) {
            reward.command().ifPresent(command -> player.server.getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(2).withSuppressedOutput(),
                command
            ));
            return;
        }

        reward.stack().ifPresent(stack -> {
            ItemStack copy = stack.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) {
                player.drop(copy, false);
            }
        });
    }

    /** Opening line for a stage. Stage 0 falls back to the quest-level dialogue. */
    public static Optional<ResourceLocation> stageDialogue(Quest quest, int stage) {
        Optional<ResourceLocation> own = quest.stage(stage).dialogue();
        if (own.isPresent()) {
            return own;
        }
        return stage == 0 ? quest.dialogue() : Optional.empty();
    }

    /** Line the giver repeats while the stage is unfinished. Falls back to the quest-level reminder. */
    public static Optional<ResourceLocation> stageReminder(Quest quest, int stage) {
        Optional<ResourceLocation> own = quest.stage(stage).reminder();
        return own.isPresent() ? own : quest.reminderDialogue();
    }

    private static void startStageDialogue(ServerPlayer player, Quest quest, int stage, int speaker) {
        stageDialogue(quest, stage)
            .ifPresent(id -> startDialogue(player, id, Optional.of(quest.id()), speaker));
    }

    public static void startDialogue(
        ServerPlayer player,
        ResourceLocation dialogueId,
        Optional<ResourceLocation> questId,
        int speaker
    ) {
        WalkingDialogueDefinitions.get().byId(dialogueId).ifPresent(dialogue ->
            ServerDialogueService.start(player, dialogue, questId, speaker));
    }

    /**
     * The server dialogue session ended. {@code completed} is false when the player
     * bailed out part way; that changes nothing and the giver replays the line
     * on the next interaction.
     */
    public static void onDialogueFinished(
        ServerPlayer player,
        Optional<ResourceLocation> questId,
        boolean completed,
        int speaker
    ) {
        if (!completed) {
            return;
        }
        questId.flatMap(id -> QuestDefinitions.get().byId(id)).ifPresent(quest -> {
            QuestProgress progress = progressOf(player, quest);
            if (quest.stage(progress.stage()).completesWithDialogue()
                && status(player, quest) == QuestStatus.ACTIVE) {
                advanceStage(player, quest, progress, speaker);
            }
        });
    }

    /** Credits a kill to every active KILL stage that asked for this entity type. */
    public static void onEntityKilled(ServerPlayer player, Entity victim) {
        QuestSaveData data = QuestSaveData.get(player.server);
        for (Quest quest : QuestDefinitions.get().all()) {
            if (statusOf(data, player, quest) != QuestStatus.ACTIVE) {
                continue;
            }

            QuestProgress progress = data.progressOf(player.getUUID(), quest.id());
            QuestObjective objective = quest.stage(progress.stage());
            boolean wanted = objective.kind() == QuestObjective.Kind.KILL
                && objective.resolveEntity().filter(type -> type == victim.getType()).isPresent();
            if (!wanted || progress.count() >= objective.required()) {
                continue;
            }

            int counted = progress.count() + 1;
            data.setProgress(player.getUUID(), quest.id(), new QuestProgress(progress.stage(), counted));
            sync(player);
            player.displayClientMessage(
                StoriesText.translatable(
                    "message.cobblemon_dimensions_stories.progress", quest.title(), counted, objective.required()
                ).withStyle(ChatFormatting.AQUA),
                true
            );
        }
    }

    /** Records escort distance from the NPC's spawn post. Only ever moves up. */
    public static void recordEscort(ServerPlayer player, Quest quest, int blocks) {
        QuestSaveData data = QuestSaveData.get(player.server);
        QuestProgress progress = data.progressOf(player.getUUID(), quest.id());
        QuestObjective objective = quest.stage(progress.stage());
        if (objective.kind() != QuestObjective.Kind.ESCORT) {
            return;
        }

        int reached = Math.min(objective.required(), blocks);
        if (reached <= progress.count()) {
            return;
        }
        data.setProgress(player.getUUID(), quest.id(), new QuestProgress(progress.stage(), reached));
        sync(player);
        if (reached >= objective.required()) {
            player.displayClientMessage(
                StoriesText.translatable("message.cobblemon_dimensions_stories.escort_done", quest.title())
                    .withStyle(ChatFormatting.AQUA),
                true
            );
        }
    }

    // counts across the whole inventory container, not just the hotbar
    private static int countItem(ServerPlayer player, QuestObjective objective) {
        return objective.resolveItem().map(item -> {
            int total = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(item)) {
                    total += stack.getCount();
                }
            }
            return total;
        }).orElse(0);
    }

    private static void removeItem(ServerPlayer player, QuestObjective objective, int amount) {
        objective.resolveItem().ifPresent(item -> {
            int remaining = amount;
            for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                if (stack.is(item)) {
                    int taken = Math.min(remaining, stack.getCount());
                    stack.shrink(taken);
                    remaining -= taken;
                }
            }
        });
    }
}
