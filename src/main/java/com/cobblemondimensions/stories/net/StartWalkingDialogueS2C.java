package com.cobblemondimensions.stories.net;

import com.cobblemondimensions.stories.dialogue.WalkingDialogue;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Starts a walking dialogue on the client. The whole page graph ships with the
 * packet so branching choices resolve locally with no second round trip.
 * {@code speaker} is the entity id of the NPC talking, which lets the client drop
 * the conversation when the player wanders off. -1 means no speaker.
 */
public record StartWalkingDialogueS2C(
    WalkingDialogue dialogue,
    Optional<ResourceLocation> quest,
    int speaker
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StartWalkingDialogueS2C> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "start_walking_dialogue"));

    public static final Codec<StartWalkingDialogueS2C> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        WalkingDialogue.CODEC.fieldOf("dialogue").forGetter(StartWalkingDialogueS2C::dialogue),
        ResourceLocation.CODEC.optionalFieldOf("quest").forGetter(StartWalkingDialogueS2C::quest),
        Codec.INT.optionalFieldOf("speaker", -1).forGetter(StartWalkingDialogueS2C::speaker)
    ).apply(instance, StartWalkingDialogueS2C::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StartWalkingDialogueS2C> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
