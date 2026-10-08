package com.cobblemondimensions.stories.minigame;

/**
 * One category's verdict on a server-owned guess. The value describes the guessed species.
 */
public record PokewrleHint(Category category, String value, State state) {
    /** The boxes on the board. GENERATION is the only one scored with an ordering. */
    public enum Category {
        TYPE_1("type_1", "TYPE 1"),
        TYPE_2("type_2", "TYPE 2"),
        EGG_GROUP("egg_group", "EGG GROUP"),
        STAGE("stage", "STAGE"),
        GENERATION("generation", "GEN");

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

        public String getSerializedName() {
            return name;
        }
    }

    public enum State {
        /** Exact match. */
        CORRECT("correct"),
        /** No match, and nothing to say about direction. */
        WRONG("wrong"),
        /** Answer sits above the guess. */
        HIGHER("higher"),
        /** Answer sits below the guess. */
        LOWER("lower");

        private final String name;

        State(String name) {
            this.name = name;
        }

        public boolean isCorrect() {
            return this == CORRECT;
        }

        public String getSerializedName() {
            return name;
        }
    }
}
