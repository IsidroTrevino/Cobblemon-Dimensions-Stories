package com.cobblemondimensions.stories.client.walking;

import com.cobblemondimensions.stories.dialogue.WalkingDialogue;
import com.cobblemondimensions.stories.dialogue.WalkingDialogueOption;
import com.cobblemondimensions.stories.dialogue.WalkingDialoguePage;
import com.cobblemondimensions.stories.minigame.Minigame;
import com.cobblemondimensions.stories.net.OpenMinigameC2S;
import com.cobblemondimensions.stories.net.WalkingDialogueDoneC2S;
import java.util.Optional;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

/**
 * Client-side state for one in-progress walking dialogue.
 *
 * <p>Deliberately not a {@link net.minecraft.client.gui.screens.Screen}. An open
 * screen makes {@code KeyMapping.isDown()} return false, which would pin the
 * player in place for the whole conversation. The bar is drawn as a HUD overlay
 * instead and input is routed here by hand.
 */
public final class WalkingDialogueSession {
    public static final long OPEN_NANOS = 420_000_000L;
    public static final long CLOSE_NANOS = 240_000_000L;
    public static final long CHOICE_NANOS = 190_000_000L;

    private static final double CHARACTERS_PER_SECOND = 38.0D;

    // Blocks from the speaker before the line is dropped. 20 leaves room to walk
    // around the NPC without cutting people off mid-sentence.
    private static final double ABANDON_DISTANCE = 20.0D;

    // One blip per two letters. At 38 cps a blip per character is just a buzz.
    private static final int BLIP_STRIDE = 2;
    private static final float BLIP_VOLUME = 0.35F;

    private static WalkingDialogueSession current;

    private final WalkingDialogue dialogue;
    private final Optional<ResourceLocation> quest;
    /** Entity id of the speaking NPC, or -1 for an unowned line (no walk-off check). */
    private final int speaker;
    private final long openedNanos;

    private int pageIndex;
    private long pageStartedNanos;
    private long skippedOpeningNanos;
    private boolean forceReveal;
    private int selection;
    private long closingStartedNanos;
    private long choicesShownNanos;
    /** High-water mark of characters already voiced. Reset on every page. */
    private int spokenCharacters;

    private WalkingDialogueSession(
        WalkingDialogue dialogue,
        Optional<ResourceLocation> quest,
        int speaker,
        long now
    ) {
        this.dialogue = dialogue;
        this.quest = quest;
        this.speaker = speaker;
        this.openedNanos = now;
        this.pageStartedNanos = now;
    }

    public static void start(WalkingDialogue dialogue, Optional<ResourceLocation> quest, int speaker) {
        if (dialogue.pages().isEmpty()) {
            return;
        }
        current = new WalkingDialogueSession(dialogue, quest, speaker, System.nanoTime());
    }

    public static WalkingDialogueSession active() {
        return current;
    }

    public static boolean isActive() {
        return current != null;
    }

    public static void clear() {
        current = null;
    }

    /** Advances typing/audio, checks for walk-off, and clears the session after the close animation. */
    public static void tick() {
        WalkingDialogueSession session = current;
        if (session == null) {
            return;
        }
        long now = System.nanoTime();
        if (session.isClosing()) {
            if (now - session.closingStartedNanos >= CLOSE_NANOS) {
                current = null;
            }
            return;
        }
        if (session.hasWalkedOff()) {
            session.abandon(now);
            return;
        }
        session.speak(now);
        if (session.choicesShownNanos == 0L && session.isChoosing(now)) {
            session.choicesShownNanos = now;
        }
    }

    /** Fires at most one blip for whatever characters appeared since the last tick. */
    private void speak(long now) {
        int visible = visibleCharacters(now);
        if (visible <= spokenCharacters) {
            spokenCharacters = visible;
            return;
        }

        // A click-to-reveal jumps the whole line at once. Anything bigger than a
        // couple of strides is treated as one blip instead of dozens stacked.
        String text = page().text();
        boolean jumped = visible - spokenCharacters > BLIP_STRIDE * 2;
        boolean blip = jumped;
        for (int index = spokenCharacters; !blip && index < visible; index++) {
            blip = index % BLIP_STRIDE == 0 && !Character.isWhitespace(text.charAt(index));
        }
        spokenCharacters = visible;
        if (blip) {
            playBlip();
        }
    }

    private void playBlip() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        float jitter = (minecraft.player.getRandom().nextFloat() - 0.5F) * 0.06F;
        minecraft.getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), voicePitch() + jitter, BLIP_VOLUME));
    }

    // pitch bucketed off the speaker name hash, gives each NPC a consistent voice
    private float voicePitch() {
        String speaker = page().speakerText();
        if (speaker.isBlank()) {
            return 1.2F;
        }
        return 0.9F + Math.floorMod(speaker.hashCode(), 7) * 0.09F;
    }

    /** True if the speaker is gone or further than {@link #ABANDON_DISTANCE} away. */
    private boolean hasWalkedOff() {
        if (speaker < 0) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return true;
        }
        Entity npc = minecraft.level.getEntity(speaker);
        return npc == null || minecraft.player.distanceTo(npc) > ABANDON_DISTANCE;
    }

    /** Closes out with completed=false. The player has to restart the dialogue from page one. */
    private void abandon(long now) {
        closingStartedNanos = now;
        ClientPlayNetworking.send(new WalkingDialogueDoneC2S(dialogue.id(), quest, false, speaker));
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(
                Component.translatable("message.cobblemon_dimensions_stories.walked_away"), false);
        }
    }

    public WalkingDialogue dialogue() {
        return dialogue;
    }

    public WalkingDialoguePage page() {
        return dialogue.pages().get(pageIndex);
    }

    public int selection() {
        return selection;
    }

    public long pageStartedNanos() {
        return pageStartedNanos;
    }

    public boolean isClosing() {
        return closingStartedNanos != 0L;
    }

    public long closingStartedNanos() {
        return closingStartedNanos;
    }

    /** 0 to 1 over {@link #OPEN_NANOS}. Clicking early sets skippedOpeningNanos to jump it to 1. */
    public float openProgress(long now) {
        long age = now - openedNanos - skippedOpeningNanos;
        return age >= OPEN_NANOS ? 1.0F : Math.max(0.0F, age / (float) OPEN_NANOS);
    }

    public float closeProgress(long now) {
        if (!isClosing()) {
            return 0.0F;
        }
        long age = now - closingStartedNanos;
        return age >= CLOSE_NANOS ? 1.0F : Math.max(0.0F, age / (float) CLOSE_NANOS);
    }

    public int visibleCharacters(long now) {
        String text = page().text();
        if (forceReveal) {
            return text.length();
        }
        if (openProgress(now) < 1.0F) {
            return 0;
        }
        double seconds = Math.max(0L, now - pageStartedNanos) / 1_000_000_000.0D;
        return Math.min(text.length(), (int) Math.floor(seconds * CHARACTERS_PER_SECOND));
    }

    public boolean isTextComplete(long now) {
        return visibleCharacters(now) >= page().text().length();
    }

    /** 0 to 1 as the choice box slides in. Stays 0 until tick() stamps choicesShownNanos. */
    public float choiceProgress(long now) {
        if (choicesShownNanos == 0L) {
            return 0.0F;
        }
        long age = now - choicesShownNanos;
        return age >= CHOICE_NANOS ? 1.0F : Math.max(0.0F, age / (float) CHOICE_NANOS);
    }

    /** Page has options and the text has finished typing. */
    public boolean isChoosing(long now) {
        return !isClosing() && page().hasOptions() && isTextComplete(now);
    }

    /**
     * Left click. Skips the open animation, dumps the rest of the line, or advances,
     * in that order of precedence.
     */
    public void click() {
        long now = System.nanoTime();
        if (isClosing()) {
            return;
        }
        if (openProgress(now) < 1.0F) {
            skippedOpeningNanos = OPEN_NANOS;
            return;
        }
        if (!isTextComplete(now)) {
            forceReveal = true;
            return;
        }

        WalkingDialoguePage page = page();
        if (page.hasOptions()) {
            WalkingDialogueOption option = page.options().get(Math.floorMod(selection, page.options().size()));
            goTo(option.next(), now);
        } else {
            goTo(page.next(), now);
        }
    }

    /** Scroll moves the choice highlight. The caller swallows the event so the hotbar stays put. */
    public void scroll(double delta) {
        long now = System.nanoTime();
        if (!isChoosing(now)) {
            return;
        }
        int count = page().options().size();
        selection = Math.floorMod(selection + (delta > 0 ? -1 : 1), count);
    }

    /** Number-key shortcut. Returns false if the index is out of range or we are not choosing. */
    public boolean selectAndConfirm(int index) {
        long now = System.nanoTime();
        if (!isChoosing(now) || index < 0 || index >= page().options().size()) {
            return false;
        }
        selection = index;
        click();
        return true;
    }

    private void goTo(Optional<String> next, long now) {
        // A minigame page is terminal. The board takes over the screen and there
        // is no sane page to come back to, so finish the dialogue here.
        Optional<Minigame> launching = page().minigame();
        if (launching.isPresent()) {
            ClientPlayNetworking.send(new OpenMinigameC2S(launching.get()));
            finish(now);
            return;
        }

        int target;
        if (next.isPresent()) {
            target = dialogue.indexOf(next.get());
        } else {
            // no explicit target, fall through to the next page or end
            target = pageIndex + 1 < dialogue.pages().size() ? pageIndex + 1 : -1;
        }

        if (target < 0) {
            finish(now);
            return;
        }
        pageIndex = target;
        pageStartedNanos = now;
        forceReveal = false;
        selection = 0;
        choicesShownNanos = 0L;
        spokenCharacters = 0;
    }

    private void finish(long now) {
        closingStartedNanos = now;
        ClientPlayNetworking.send(new WalkingDialogueDoneC2S(dialogue.id(), quest, true, speaker));
    }
}
