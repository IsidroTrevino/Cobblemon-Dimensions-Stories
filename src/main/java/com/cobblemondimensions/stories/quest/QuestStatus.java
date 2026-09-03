package com.cobblemondimensions.stories.quest;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** Per-player state of a single quest. */
public enum QuestStatus implements StringRepresentable {
    /** Prerequisites are not met yet; shown greyed out. */
    LOCKED("locked"),
    /** Can be accepted. */
    AVAILABLE("available"),
    /** Accepted and in progress. */
    ACTIVE("active"),
    /** Finished and rewarded. */
    COMPLETED("completed");

    public static final Codec<QuestStatus> CODEC = StringRepresentable.fromEnum(QuestStatus::values);

    private final String name;

    QuestStatus(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
