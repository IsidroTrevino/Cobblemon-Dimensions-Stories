package com.cobblemondimensions.stories.net;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Markers the player should be seeing, keyed by entity id. The server resends the
 * whole nearby set on a timer; a missing id means the marker is gone.
 */
public record QuestMarkersS2C(Map<Integer, Integer> markers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<QuestMarkersS2C> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "quest_markers"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestMarkersS2C> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_INT),
        QuestMarkersS2C::markers,
        QuestMarkersS2C::new
    );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
