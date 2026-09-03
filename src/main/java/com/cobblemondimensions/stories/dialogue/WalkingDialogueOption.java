package com.cobblemondimensions.stories.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** One selectable choice on a page. {@code next} is the page it jumps to. */
public record WalkingDialogueOption(Component text, Optional<String> next) {
    public static final Codec<WalkingDialogueOption> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ComponentSerialization.CODEC.fieldOf("text").forGetter(WalkingDialogueOption::text),
        Codec.STRING.optionalFieldOf("next").forGetter(WalkingDialogueOption::next)
    ).apply(instance, WalkingDialogueOption::new));
}
