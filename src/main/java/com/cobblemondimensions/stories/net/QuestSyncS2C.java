package com.cobblemondimensions.stories.net;

import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestProgress;
import com.cobblemondimensions.stories.quest.QuestStatus;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Full copy of the datapack quests plus this player's progress. {@code givers}
 * maps a quest to the NPC handing it out, which is what the journal names.
 */
public record QuestSyncS2C(
    List<Quest> quests,
    Map<ResourceLocation, QuestStatus> statuses,
    Map<ResourceLocation, QuestProgress> progress,
    Map<ResourceLocation, Component> givers
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuestSyncS2C> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "quest_sync"));

    public static final Codec<QuestSyncS2C> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Quest.CODEC.listOf().fieldOf("quests").forGetter(QuestSyncS2C::quests),
        Codec.unboundedMap(ResourceLocation.CODEC, QuestStatus.CODEC).fieldOf("statuses")
            .forGetter(QuestSyncS2C::statuses),
        Codec.unboundedMap(ResourceLocation.CODEC, QuestProgress.CODEC).fieldOf("progress")
            .forGetter(QuestSyncS2C::progress),
        Codec.unboundedMap(ResourceLocation.CODEC, ComponentSerialization.CODEC).fieldOf("givers")
            .forGetter(QuestSyncS2C::givers)
    ).apply(instance, QuestSyncS2C::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestSyncS2C> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
