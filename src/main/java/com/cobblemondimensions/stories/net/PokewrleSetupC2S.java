package com.cobblemondimensions.stories.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Player picked a generation on the Pokewrle board. Rerolls the answer.
 *
 * @param generation 1-9, or 0 for every generation
 */
public record PokewrleSetupC2S(int generation) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PokewrleSetupC2S> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "pokewrle_setup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PokewrleSetupC2S> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PokewrleSetupC2S::generation,
            PokewrleSetupC2S::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
