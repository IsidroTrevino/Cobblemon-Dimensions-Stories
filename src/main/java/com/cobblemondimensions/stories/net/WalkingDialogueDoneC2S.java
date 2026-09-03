package com.cobblemondimensions.stories.net;

import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The client stopped playing a walking dialogue. {@code completed} is false when
 * the player walked out of earshot. That leaves the stage untouched and the giver
 * replays the line from the top.
 */
public record WalkingDialogueDoneC2S(
    ResourceLocation dialogue,
    Optional<ResourceLocation> quest,
    boolean completed,
    int speaker
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<WalkingDialogueDoneC2S> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "walking_dialogue_done"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WalkingDialogueDoneC2S> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            WalkingDialogueDoneC2S::dialogue,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            WalkingDialogueDoneC2S::quest,
            ByteBufCodecs.BOOL,
            WalkingDialogueDoneC2S::completed,
            ByteBufCodecs.VAR_INT,
            WalkingDialogueDoneC2S::speaker,
            WalkingDialogueDoneC2S::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
