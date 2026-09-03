package com.cobblemondimensions.stories.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** One player's position in one quest: current stage, plus that stage's counter. */
public record QuestProgress(int stage, int count) {
    public static final QuestProgress START = new QuestProgress(0, 0);

    public static final Codec<QuestProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("stage").forGetter(QuestProgress::stage),
        Codec.INT.fieldOf("count").forGetter(QuestProgress::count)
    ).apply(instance, QuestProgress::new));
}
