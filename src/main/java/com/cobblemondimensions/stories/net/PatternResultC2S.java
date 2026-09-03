package com.cobblemondimensions.stories.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A finished Pattern run. Scored on the client, so the level count is taken on
 * trust; the only check is that the server opened the session.
 */
public record PatternResultC2S(int levelsCleared) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PatternResultC2S> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "pattern_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PatternResultC2S> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PatternResultC2S::levelsCleared,
            PatternResultC2S::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
