package com.cobblemondimensions.stories.dialogue;

import com.cobblemondimensions.stories.minigame.Minigame;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * One page of a walking dialogue. A page either advances to {@code next} on
 * click, or stops and waits for the player to pick from {@code options}.
 */
public record WalkingDialoguePage(
    String id,
    Optional<Component> speaker,
    List<Component> lines,
    List<WalkingDialogueOption> options,
    Optional<String> next,
    Optional<Minigame> minigame
) {
    public static final Codec<WalkingDialoguePage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(WalkingDialoguePage::id),
        ComponentSerialization.CODEC.optionalFieldOf("speaker").forGetter(WalkingDialoguePage::speaker),
        ComponentSerialization.CODEC.listOf().fieldOf("lines").forGetter(WalkingDialoguePage::lines),
        WalkingDialogueOption.CODEC.listOf().optionalFieldOf("options", List.of())
            .forGetter(WalkingDialoguePage::options),
        Codec.STRING.optionalFieldOf("next").forGetter(WalkingDialoguePage::next),
        Minigame.CODEC.optionalFieldOf("minigame").forGetter(WalkingDialoguePage::minigame)
    ).apply(instance, WalkingDialoguePage::new));

    public boolean hasOptions() {
        return !options.isEmpty();
    }

    /** All lines as a single string, blank line between each. This is what the typewriter types. */
    public String text() {
        return String.join("\n\n", lines.stream().map(Component::getString).toList());
    }

    public String speakerText() {
        return speaker.map(Component::getString).orElse("");
    }
}
