package com.cobblemondimensions.stories.minigame;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import net.minecraft.network.chat.Component;

/** Pattern boards in the states worth looking at, for the preview tool. */
public final class PatternPreviewScenes {
    private static final long SECOND = 1_000_000_000L;

    private PatternPreviewScenes() {
    }

    public static Map<String, Component> scenes() {
        Map<String, Component> scenes = new LinkedHashMap<>();
        long idle = -60 * SECOND;

        PatternRound countdown = new PatternRound(new Random(4), 0);
        scenes.put("pattern_countdown", PatternView.compose(countdown, 600_000_000L,
            new PatternView.Effects(idle, idle, -1, idle, 0, 14)));

        PatternRound playing = new PatternRound(new Random(4), 0);
        playing.advance(1_900_000_000L);
        int[] sequence = playing.sequence();
        long now = 2 * SECOND;
        playing.press(sequence[0], now);
        playing.press(sequence[1], now);
        scenes.put("pattern_playing", PatternView.compose(playing, now + 60_000_000L,
            new PatternView.Effects(idle, now, 1, idle, 0, 14)));

        scenes.put("pattern_wrong", PatternView.compose(playing, now + 5 * SECOND,
            new PatternView.Effects(now + 5 * SECOND - 100_000_000L, idle, -1, idle, 0, 14)));

        PatternRound urgent = new PatternRound(new Random(9), 0);
        urgent.advance(1_900_000_000L);
        scenes.put("pattern_urgent", PatternView.compose(urgent, urgent.deadlineNanos() - 1_500_000_000L,
            new PatternView.Effects(idle, idle, -1, idle, 0, 14)));

        PatternRound cleared = new PatternRound(new Random(4), 0);
        cleared.advance(1_900_000_000L);
        long at = 3 * SECOND;
        for (int direction : cleared.sequence()) {
            cleared.press(direction, at);
        }
        scenes.put("pattern_cleared", PatternView.compose(cleared, at + 150_000_000L,
            new PatternView.Effects(idle, at, 3, at, 0, 14)));

        PatternRound shapes = new PatternRound(new Random(6), 0);
        long time = 0;
        for (int level = 1; level < 10; level++) {
            time += 1_900_000_000L;
            shapes.advance(time);
            for (int direction : shapes.sequence()) {
                shapes.press(direction, time + 10);
            }
            time += 2 * SECOND;
            shapes.advance(time);
        }
        time += 1_900_000_000L;
        shapes.advance(time);
        shapes.press(shapes.sequence()[0], time + 10);
        scenes.put("pattern_shapes", PatternView.compose(shapes, time + 800_000_000L,
            new PatternView.Effects(idle, time + 10, 0, idle, time - 1_900_000_000L, 14)));

        PatternRound over = new PatternRound(new Random(4), 0);
        over.advance(1_900_000_000L);
        over.finish(3 * SECOND);
        scenes.put("pattern_over", PatternView.compose(over, 4 * SECOND,
            new PatternView.Effects(idle, idle, -1, idle, 0, 14)));
        return scenes;
    }
}
