package com.cobblemondimensions.stories.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One payout on a quest. Either an item stack or a command to run. */
public record QuestReward(
    Kind kind,
    Optional<ResourceLocation> item,
    int count,
    Optional<String> command,
    Optional<Component> label
) {
    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Kind.CODEC.optionalFieldOf("type", Kind.ITEM).forGetter(QuestReward::kind),
        ResourceLocation.CODEC.optionalFieldOf("item").forGetter(QuestReward::item),
        Codec.INT.optionalFieldOf("count", 1).forGetter(QuestReward::count),
        Codec.STRING.optionalFieldOf("command").forGetter(QuestReward::command),
        ComponentSerialization.CODEC.optionalFieldOf("label").forGetter(QuestReward::label)
    ).apply(instance, QuestReward::new));

    /** Label if the pack set one, else the item name with its count, else a plain "Reward". */
    public Component displayName() {
        if (label.isPresent()) {
            return label.get();
        }
        if (kind == Kind.ITEM) {
            return stack().map(stack -> count > 1
                ? Component.literal(count + "x ").append(stack.getHoverName())
                : stack.getHoverName().copy()).orElse(Component.literal("?"));
        }
        return Component.literal("Reward");
    }

    // empty for an unknown id, and air is treated as unset
    public Optional<ItemStack> stack() {
        return item.flatMap(BuiltInRegistries.ITEM::getOptional)
            .filter(entry -> entry != net.minecraft.world.item.Items.AIR)
            .map(entry -> new ItemStack((Item) entry, Math.max(1, count)));
    }

    public enum Kind implements StringRepresentable {
        ITEM("item"),
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
