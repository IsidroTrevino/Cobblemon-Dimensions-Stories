package com.cobblemondimensions.stories.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One typed guess. Scoring happens server side, since that is where the answer lives. */
public record PokewrleGuessC2S(String species) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PokewrleGuessC2S> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "pokewrle_guess"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PokewrleGuessC2S> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), PokewrleGuessC2S::species,
            PokewrleGuessC2S::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
