package com.cobblemondimensions.stories.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/** One stage of a quest. A quest is a list of these, run in order. */
public record QuestObjective(
    Kind kind,
    Optional<ResourceLocation> item,
    Optional<ResourceLocation> entity,
    int count,
    boolean consume,
    Optional<Component> hint,
    Optional<ResourceLocation> dialogue,
    Optional<ResourceLocation> reminder
) {
    // stand-in stage for a quest that is only a conversation
    public static final QuestObjective DIALOGUE_ONLY = new QuestObjective(
        Kind.DIALOGUE,
        Optional.empty(),
        Optional.empty(),
        1,
        false,
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    public static final Codec<QuestObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Kind.CODEC.fieldOf("type").forGetter(QuestObjective::kind),
        ResourceLocation.CODEC.optionalFieldOf("item").forGetter(QuestObjective::item),
        ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(QuestObjective::entity),
        Codec.INT.optionalFieldOf("count", 1).forGetter(QuestObjective::count),
        Codec.BOOL.optionalFieldOf("consume", false).forGetter(QuestObjective::consume),
        ComponentSerialization.CODEC.optionalFieldOf("hint").forGetter(QuestObjective::hint),
        ResourceLocation.CODEC.optionalFieldOf("dialogue").forGetter(QuestObjective::dialogue),
        ResourceLocation.CODEC.optionalFieldOf("reminder").forGetter(QuestObjective::reminder)
    ).apply(instance, QuestObjective::new));

    /** Empty when the item id is not registered on this server. */
    public Optional<Item> resolveItem() {
        return item.flatMap(BuiltInRegistries.ITEM::getOptional);
    }

    /** Empty for an unknown entity id. It just never matches, which is fine if the mod is absent. */
    public Optional<EntityType<?>> resolveEntity() {
        return entity.flatMap(BuiltInRegistries.ENTITY_TYPE::getOptional);
    }

    public int required() {
        return Math.max(1, count);
    }

    /** Stage closes itself when the dialogue ends instead of waiting for a turn-in. */
    public boolean completesWithDialogue() {
        return kind == Kind.DIALOGUE;
    }

    public boolean isTurnIn() {
        return kind == Kind.ITEM;
    }

    /** Kinds the server tracks with a per-player counter in save data. */
    public boolean tracksCounter() {
        return kind == Kind.KILL || kind == Kind.ESCORT;
    }

    public enum Kind implements StringRepresentable {
        /** Done when the stage's walking dialogue hits its last page. */
        DIALOGUE("dialogue"),
        /** Done when the player hands the items to the giver. */
        ITEM("item"),
        /** Done after {@code count} of {@code entity} are defeated. */
        KILL("kill"),
        /** Done once the giver has been led {@code count} blocks from where it spawned. */
        ESCORT("escort"),
        /** Nothing closes this one except {@code /stories complete}. */
        COMMAND("command");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
