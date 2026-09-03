package com.cobblemondimensions.stories.client.minigame;

import java.util.Comparator;
import java.util.PriorityQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Shared SFX for the minigame boards. Everything is vanilla note block events. */
public final class MinigameSounds {
    /**
     * Pending notes, ordered by due time. SimpleSoundInstance fires immediately,
     * so anything with delay between notes gets scheduled here and drained from
     * the screen's render loop.
     */
    private static final PriorityQueue<Note> QUEUE =
        new PriorityQueue<>(Comparator.comparingLong(Note::dueNanos));

    private MinigameSounds() {
    }

    /** Plays every note that is due. Call once per frame. */
    public static void tickQueue(long now) {
        while (!QUEUE.isEmpty() && QUEUE.peek().dueNanos() <= now) {
            Note note = QUEUE.poll();
            play(note.sound(), note.pitch(), note.volume());
        }
    }

    /** Drops queued notes. Call on screen close or nothing stops playing. */
    public static void reset() {
        QUEUE.clear();
    }

    /** Pitch climbs with step, capped at 11 so it stays in range. */
    public static void chirp(int step) {
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 0.9F + Math.min(step, 11) * 0.09F, 0.55F);
    }

    /** Correct key. Pitch tracks position in the pattern so a run sounds like a phrase. */
    public static void hit(int step, int total) {
        float progress = total <= 1 ? 1.0F : step / (float) (total - 1);
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 0.8F + progress * 1.1F, 0.55F);
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6F + progress * 0.3F, 0.22F);
    }

    // wrong key
    public static void buzz() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.55F, 0.7F);
        play(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.5F, 0.5F);
    }

    /** Three ascending chimes, all on the same frame. */
    public static void fanfare() {
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 0.6F);
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.26F, 0.5F);
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 0.5F);
    }

    /** Level cleared. The whole arpeggio shifts up with the streak, capped at 8. */
    public static void clear(int streak) {
        float lift = Math.min(streak, 8) * 0.045F;
        play(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F + lift, 0.6F);
        after(70, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.26F + lift, 0.55F);
        after(140, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.5F + lift, 0.5F);
        after(210, SoundEvents.NOTE_BLOCK_BELL.value(), 1.6F + lift, 0.5F);
    }

    /** Cleared with most of the timer left. */
    public static void perfect() {
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4F, 0.6F);
        after(60, SoundEvents.NOTE_BLOCK_BELL.value(), 1.7F, 0.6F);
        after(120, SoundEvents.NOTE_BLOCK_BELL.value(), 2.0F, 0.6F);
        after(180, SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0F, 0.7F);
        after(180, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.6F, 0.35F);
    }

    /** One hat per slot, 26ms apart, under the slot drop-in animation. */
    public static void reveal(int slots) {
        for (int index = 0; index < slots; index++) {
            after(index * 26L, SoundEvents.NOTE_BLOCK_HAT.value(),
                1.0F + index * 0.06F, 0.2F);
        }
    }

    /** One countdown beat. count == 0 is the GO beat and gets the brighter pair. */
    public static void countdown(int count) {
        if (count > 0) {
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.55F);
        } else {
            play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5F, 0.7F);
            play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 0.4F);
        }
    }

    /** Streak counter incremented. Pitch is clamped at 2.0. */
    public static void streakUp(int streak) {
        play(SoundEvents.NOTE_BLOCK_PLING.value(), Math.min(2.0F, 1.0F + streak * 0.08F), 0.5F);
    }

    /** Transition into shape mode at level 10. Runs about 400ms. */
    public static void shapeShift() {
        play(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6F, 0.7F);
        after(90, SoundEvents.NOTE_BLOCK_BIT.value(), 1.2F, 0.6F);
        after(180, SoundEvents.NOTE_BLOCK_BIT.value(), 1.6F, 0.6F);
        after(270, SoundEvents.NOTE_BLOCK_BIT.value(), 2.0F, 0.6F);
        after(360, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2F, 0.5F);
    }

    /**
     * One hint box flip. Boxes flip in sequence, so the caller passes the delay
     * that matches this box's index.
     */
    public static void flipAfter(long delayMillis, boolean correct, int index) {
        if (correct) {
            after(delayMillis, SoundEvents.NOTE_BLOCK_PLING.value(), 1.2F + index * 0.12F, 0.5F);
            after(delayMillis, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.4F + index * 0.1F, 0.3F);
        } else {
            after(delayMillis, SoundEvents.NOTE_BLOCK_BASS.value(), 0.85F - index * 0.05F, 0.45F);
        }
    }

    /** Guess rejected: unknown species, or outside the selected generation. */
    public static void reject() {
        play(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.6F, 0.5F);
        after(90, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 0.5F, 0.45F);
    }

    /** Guess accepted and moved out of the input bar. */
    public static void submit() {
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 1.4F, 0.45F);
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.8F, 0.25F);
    }

    /**
     * Answer reveal. Caller passes the delay that clears the hint box flips;
     * everything here is offset from it.
     */
    public static void revealAfter(long delayMillis, boolean won) {
        after(delayMillis, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.8F, 0.5F);
        after(delayMillis + 80, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0F, 0.5F);
        after(delayMillis + 160, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.3F, 0.55F);
        after(delayMillis + 240, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6F, 0.6F);
        if (won) {
            after(delayMillis + 340, SoundEvents.PLAYER_LEVELUP, 1.3F, 0.7F);
            after(delayMillis + 340, SoundEvents.NOTE_BLOCK_BELL.value(), 1.8F, 0.6F);
        } else {
            after(delayMillis + 340, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.7F);
            after(delayMillis + 420, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.6F);
        }
    }

    /** Generation selector stepped. Both notes rise with the generation number. */
    public static void dial(int generation) {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F + generation * 0.06F, 0.4F);
        play(SoundEvents.NOTE_BLOCK_BIT.value(), 1.0F + generation * 0.08F, 0.35F);
    }

    /**
     * Cell uncovered. Pitch tracks the neighbour count, so an empty cell and one
     * ringed by TNT are distinguishable by ear.
     */
    public static void digAfter(long delayMillis, int neighbours) {
        after(delayMillis, SoundEvents.NOTE_BLOCK_HAT.value(), 1.5F - neighbours * 0.08F, 0.28F);
        if (neighbours > 0) {
            after(delayMillis, SoundEvents.NOTE_BLOCK_BIT.value(), 0.9F + neighbours * 0.12F, 0.3F);
        }
    }

    /** Flag placed or removed. */
    public static void flag(boolean placed) {
        if (placed) {
            play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.7F, 0.45F);
            play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.9F, 0.2F);
        } else {
            play(SoundEvents.NOTE_BLOCK_BASS.value(), 1.4F, 0.35F);
        }
    }

    // three hat ticks; play this before boom()
    public static void fuse() {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.4F);
        after(70, SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.4F);
        after(140, SoundEvents.NOTE_BLOCK_HAT.value(), 2.0F, 0.4F);
    }

    /** Mine detonation. Called per mine with staggered delays when the board blows. */
    public static void boom(long delayMillis) {
        after(delayMillis, SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.9F);
        after(delayMillis, SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.8F);
    }

    /** Board fully cleared. */
    public static void sweep() {
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2F, 0.6F);
        after(90, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5F, 0.6F);
        after(180, SoundEvents.NOTE_BLOCK_BELL.value(), 1.8F, 0.6F);
        after(280, SoundEvents.PLAYER_LEVELUP, 1.4F, 0.7F);
        after(280, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.4F, 0.4F);
    }

    public static void victory() {
        play(SoundEvents.PLAYER_LEVELUP, 1.3F, 0.7F);
        play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.8F, 0.6F);
    }

    public static void defeat() {
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.7F);
        play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5F, 0.6F);
    }

    /** Timer metronome. urgent is the last second: higher and louder. */
    public static void tick(boolean urgent) {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), urgent ? 1.6F : 1.2F, urgent ? 0.5F : 0.3F);
    }

    public static void type() {
        play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.9F, 0.25F);
    }

    public static void select() {
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F);
    }

    private static void after(long delayMillis, SoundEvent sound, float pitch, float volume) {
        QUEUE.add(new Note(System.nanoTime() + delayMillis * 1_000_000L, sound, pitch, volume));
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private record Note(long dueNanos, SoundEvent sound, float pitch, float volume) {
    }
}
