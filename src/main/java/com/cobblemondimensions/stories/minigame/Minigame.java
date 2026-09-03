package com.cobblemondimensions.stories.minigame;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Minigames an NPC can start. Ordinals go on the wire (see the idMapper codecs
 * in OpenMinigameC2S and StartMinigameS2C), so new constants go on the end.
 */
public enum Minigame implements StringRepresentable {
    /** Repeat a generated arrow pattern before the timer runs out. */
    PATTERN("pattern"),
    /** Guess the hidden species from category hints. */
    POKEWRLE("pokewrle"),
    /** Minesweeper board, largest size. Dimensions live in {@link PokesweeperMode}. */
    POKESWEEPER("pokesweeper"),
    /** Smallest board, fewest mines. */
    POKESWEEPER_EASY("pokesweeper_easy"),
    /** One size up from {@link #POKESWEEPER_EASY}. */
    POKESWEEPER_MEDIUM("pokesweeper_medium");

    public static final Codec<Minigame> CODEC = StringRepresentable.fromEnum(Minigame::values);

    private final String name;

    Minigame(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
