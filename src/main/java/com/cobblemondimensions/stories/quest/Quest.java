package com.cobblemondimensions.stories.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

/**
 * A quest as written in a datapack. Loaded server-side from
 * {@code data/<namespace>/quests/<path>.json} and mirrored to every client.
 */
public record Quest(
    ResourceLocation id,
    Component title,
    List<Component> description,
    List<QuestObjective> stages,
    List<QuestReward> rewards,
    Optional<ResourceLocation> dialogue,
    Optional<ResourceLocation> reminderDialogue,
    Optional<ResourceLocation> completionDialogue,
    List<ResourceLocation> requires,
    int order
) {
    private static final ResourceLocation PLACEHOLDER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "unnamed");

    /** File body only. The id is taken from the file path. */
    public static final Codec<Quest> BODY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ComponentSerialization.CODEC.fieldOf("title").forGetter(Quest::title),
        ComponentSerialization.CODEC.listOf().optionalFieldOf("description", List.of())
            .forGetter(Quest::description),
        QuestObjective.CODEC.listOf().optionalFieldOf("stages", List.of()).forGetter(Quest::stages),
        // shorthand: a one-stage quest can write "objective" instead of "stages"
        QuestObjective.CODEC.optionalFieldOf("objective")
            .forGetter(quest -> Optional.<QuestObjective>empty()),
        QuestReward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Quest::rewards),
        ResourceLocation.CODEC.optionalFieldOf("dialogue").forGetter(Quest::dialogue),
        ResourceLocation.CODEC.optionalFieldOf("reminder_dialogue").forGetter(Quest::reminderDialogue),
        ResourceLocation.CODEC.optionalFieldOf("completion_dialogue").forGetter(Quest::completionDialogue),
        ResourceLocation.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(Quest::requires),
        Codec.INT.optionalFieldOf("order", 100).forGetter(Quest::order)
    ).apply(instance, (title, description, stages, objective, rewards, dialogue, reminder, completion,
        requires, order) ->
        new Quest(
            PLACEHOLDER_ID,
            title,
            description,
            normalise(stages, objective),
            rewards,
            dialogue,
            reminder,
            completion,
            requires,
            order
        )));

    /** Network sync version, id included. */
    public static final Codec<Quest> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(Quest::id),
        ComponentSerialization.CODEC.fieldOf("title").forGetter(Quest::title),
        ComponentSerialization.CODEC.listOf().fieldOf("description").forGetter(Quest::description),
        QuestObjective.CODEC.listOf().fieldOf("stages").forGetter(Quest::stages),
        QuestReward.CODEC.listOf().fieldOf("rewards").forGetter(Quest::rewards),
        ResourceLocation.CODEC.optionalFieldOf("dialogue").forGetter(Quest::dialogue),
        ResourceLocation.CODEC.optionalFieldOf("reminder_dialogue").forGetter(Quest::reminderDialogue),
        ResourceLocation.CODEC.optionalFieldOf("completion_dialogue").forGetter(Quest::completionDialogue),
        ResourceLocation.CODEC.listOf().fieldOf("requires").forGetter(Quest::requires),
        Codec.INT.fieldOf("order").forGetter(Quest::order)
    ).apply(instance, Quest::new));

    // everything downstream assumes at least one stage
    private static List<QuestObjective> normalise(
        List<QuestObjective> stages,
        Optional<QuestObjective> objective
    ) {
        if (!stages.isEmpty()) {
            return stages;
        }
        return List.of(objective.orElse(QuestObjective.DIALOGUE_ONLY));
    }

    public Quest withId(ResourceLocation questId) {
        return new Quest(
            questId,
            title,
            description,
            stages,
            rewards,
            dialogue,
            reminderDialogue,
            completionDialogue,
            requires,
            order
        );
    }

    /**
     * Index is clamped. Save data written against a longer version of the quest
     * must not blow up here after the pack is edited.
     */
    public QuestObjective stage(int index) {
        return stages.get(Math.max(0, Math.min(index, stages.size() - 1)));
    }

    public int stageCount() {
        return stages.size();
    }

    /** Description as one string for the client's typewriter. */
    public String descriptionText() {
        return String.join("\n\n", description.stream().map(Component::getString).toList());
    }
}
