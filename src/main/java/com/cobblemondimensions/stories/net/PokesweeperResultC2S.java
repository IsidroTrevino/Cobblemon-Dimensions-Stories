package com.cobblemondimensions.stories.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A finished Pokesweeper board. Client-scored like Pattern, and only accepted if
 * the server dealt the session.
 *
 * @param cleared every safe cell was uncovered
 * @param uncovered safe cells uncovered, win or lose
 * @param seconds how long the board took, used for the personal record
 */
public record PokesweeperResultC2S(boolean cleared, int uncovered, int seconds)
    implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PokesweeperResultC2S> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "pokesweeper_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PokesweeperResultC2S> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, PokesweeperResultC2S::cleared,
            ByteBufCodecs.VAR_INT, PokesweeperResultC2S::uncovered,
            ByteBufCodecs.VAR_INT, PokesweeperResultC2S::seconds,
            PokesweeperResultC2S::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
