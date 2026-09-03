package com.cobblemondimensions.stories.dialogue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Dialogue drawn as a HUD overlay instead of a screen. The player keeps control
 * and can walk around while it plays. Loaded from
 * {@code data/<namespace>/walking_dialogues/<path>.json} and shipped to the
 * client in full when it starts.
 */
public record WalkingDialogue(ResourceLocation id, List<WalkingDialoguePage> pages) {
    private static final ResourceLocation PLACEHOLDER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "unnamed");

    // file body only; the id comes from the file path
    public static final Codec<WalkingDialogue> BODY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        WalkingDialoguePage.CODEC.listOf().fieldOf("pages").forGetter(WalkingDialogue::pages)
    ).apply(instance, pages -> new WalkingDialogue(PLACEHOLDER_ID, pages)));

    public static final Codec<WalkingDialogue> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(WalkingDialogue::id),
        WalkingDialoguePage.CODEC.listOf().fieldOf("pages").forGetter(WalkingDialogue::pages)
    ).apply(instance, WalkingDialogue::new));

    public WalkingDialogue withId(ResourceLocation dialogueId) {
        return new WalkingDialogue(dialogueId, pages);
    }

    public Optional<WalkingDialoguePage> firstPage() {
        return pages.isEmpty() ? Optional.empty() : Optional.of(pages.get(0));
    }

    /** -1 when no page carries that id. */
    public int indexOf(String pageId) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).id().equals(pageId)) {
                return index;
            }
        }
        return -1;
    }
}
