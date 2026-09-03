package com.cobblemondimensions.stories.net;

import com.cobblemondimensions.stories.minigame.Minigame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent when the player reaches a dialogue page tagged with a minigame. The server
 * owns the session, so it replies with {@link StartMinigameS2C} instead of the
 * client opening the screen on its own.
 */
public record OpenMinigameC2S(Minigame game) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenMinigameC2S> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "open_minigame"));

    // idMapper keys on the enum ordinal. Append new Minigame constants, never insert.
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMinigameC2S> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> Minigame.values()[index], Minigame::ordinal),
            OpenMinigameC2S::game,
            OpenMinigameC2S::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
