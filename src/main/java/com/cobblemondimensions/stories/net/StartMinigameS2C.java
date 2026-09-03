package com.cobblemondimensions.stories.net;

import com.cobblemondimensions.stories.minigame.Minigame;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server has a session open and the client may show the board.
 *
 * @param best this player's record for the game, drawn on the results screen
 * @param attempts guesses Pokewrle is allowing, ignored by the other games
 * @param generation generation the Pokewrle answer came from, 0 for all of them.
 *     Ignored by the other games.
 */
public record StartMinigameS2C(Minigame game, int best, int attempts, int generation)
    implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StartMinigameS2C> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "start_minigame"));

    // ordinal goes on the wire, so Minigame constants are append-only
    public static final StreamCodec<RegistryFriendlyByteBuf, StartMinigameS2C> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.idMapper(index -> Minigame.values()[index], Minigame::ordinal),
            StartMinigameS2C::game,
            ByteBufCodecs.VAR_INT, StartMinigameS2C::best,
            ByteBufCodecs.VAR_INT, StartMinigameS2C::attempts,
            ByteBufCodecs.VAR_INT, StartMinigameS2C::generation,
            StartMinigameS2C::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
