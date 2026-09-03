package com.cobblemondimensions.stories.net;

import com.cobblemondimensions.stories.minigame.PokewrleHint;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * Verdict on one guess.
 *
 * @param answer filled in only once the round is over. Keeps the species off the
 *     client until there is nothing left to spoil.
 */
public record PokewrleFeedbackS2C(
    String guess,
    List<PokewrleHint> hints,
    int attemptsLeft,
    Outcome outcome,
    Optional<ResourceLocation> answer,
    Optional<String> answerName
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PokewrleFeedbackS2C> TYPE =
        new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "pokewrle_feedback"));

    public static final Codec<PokewrleFeedbackS2C> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("guess").forGetter(PokewrleFeedbackS2C::guess),
        PokewrleHint.CODEC.listOf().fieldOf("hints").forGetter(PokewrleFeedbackS2C::hints),
        Codec.INT.fieldOf("attempts_left").forGetter(PokewrleFeedbackS2C::attemptsLeft),
        Outcome.CODEC.fieldOf("outcome").forGetter(PokewrleFeedbackS2C::outcome),
        ResourceLocation.CODEC.optionalFieldOf("answer").forGetter(PokewrleFeedbackS2C::answer),
        Codec.STRING.optionalFieldOf("answer_name").forGetter(PokewrleFeedbackS2C::answerName)
    ).apply(instance, PokewrleFeedbackS2C::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PokewrleFeedbackS2C> STREAM_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Outcome implements StringRepresentable {
        /** Wrong, guesses left. */
        CONTINUE("continue"),
        /** Not a species the server would accept. No attempt spent. */
        REJECTED("rejected"),
        WON("won"),
        LOST("lost");

        public static final Codec<Outcome> CODEC = StringRepresentable.fromEnum(Outcome::values);

        private final String name;

        Outcome(String name) {
            this.name = name;
        }

        public boolean isOver() {
            return this == WON || this == LOST;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
