package com.cobblemondimensions.stories.minigame;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;

/**
 * One category's verdict on a guess. {@code value} is the guessed species' own
 * reading, which is what the box draws.
 */
public record PokewrleHint(Category category, String value, State state) {
    public static final Codec<PokewrleHint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Category.CODEC.fieldOf("category").forGetter(PokewrleHint::category),
        Codec.STRING.fieldOf("value").forGetter(PokewrleHint::value),
        State.CODEC.fieldOf("state").forGetter(PokewrleHint::state)
    ).apply(instance, PokewrleHint::new));

    /** The boxes on the board. GENERATION is the only one scored with an ordering. */
    public enum Category implements StringRepresentable {
        TYPE_1("type_1", "TYPE 1"),
        TYPE_2("type_2", "TYPE 2"),
        EGG_GROUP("egg_group", "EGG GROUP"),
        STAGE("stage", "STAGE"),
        GENERATION("generation", "GEN");

        public static final Codec<Category> CODEC = StringRepresentable.fromEnum(Category::values);

        private final String name;
        private final String label;

        Category(String name, String label) {
            this.name = name;
            this.label = label;
        }

        /** Heading drawn above the value in the hint box. */
        public String label() {
            return label;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum State implements StringRepresentable {
        /** Exact match. */
        CORRECT("correct"),
        /** No match, and nothing to say about direction. */
        WRONG("wrong"),
        /** Answer sits above the guess. */
        HIGHER("higher"),
        /** Answer sits below the guess. */
        LOWER("lower");

        public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);

        private final String name;

        State(String name) {
            this.name = name;
        }

        public boolean isCorrect() {
            return this == CORRECT;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}
