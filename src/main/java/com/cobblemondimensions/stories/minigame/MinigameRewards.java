package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.quest.QuestReward;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Reward tables for the minigames. Unknown item ids resolve to nothing instead
 * of throwing; without Cobblemon installed the player just gets the vanilla half
 * of each list.
 */
public final class MinigameRewards {
    private MinigameRewards() {
    }

    /** Paid once for the whole run, not per level. One 10-level streak beats two 5-level runs. */
    public static List<QuestReward> forPattern(int levelsCleared) {
        List<QuestReward> rewards = new ArrayList<>();
        if (levelsCleared <= 0) {
            return rewards;
        }

        if (levelsCleared <= 2) {
            rewards.add(item("minecraft:emerald", 2));
        } else if (levelsCleared <= 4) {
            rewards.add(item("minecraft:emerald", 4));
            rewards.add(item("cobblemon:poke_ball", 2));
        } else if (levelsCleared <= 7) {
            rewards.add(item("minecraft:emerald", 6));
            rewards.add(item("cobblemon:great_ball", 3));
        } else if (levelsCleared <= 10) {
            rewards.add(item("minecraft:emerald", 10));
            rewards.add(item("cobblemon:ultra_ball", 3));
            rewards.add(item("cobblemon:rare_candy", 1));
        } else {
            rewards.add(item("minecraft:emerald", 16));
            rewards.add(item("cobblemon:ultra_ball", 4));
            rewards.add(item("cobblemon:rare_candy", 2));
        }

        // stacks on top of the tier above
        if (levelsCleared >= 15) {
            rewards.add(item("cobblemon:master_ball", 1));
        }
        return rewards;
    }

    /** Fewer guesses burned, better haul. */
    public static List<QuestReward> forPokewrle(int attemptsUsed) {
        List<QuestReward> rewards = new ArrayList<>();
        switch (Math.max(1, attemptsUsed)) {
            case 1 -> {
                rewards.add(item("minecraft:emerald", 12));
                rewards.add(item("cobblemon:master_ball", 1));
            }
            case 2 -> {
                rewards.add(item("minecraft:emerald", 8));
                rewards.add(item("cobblemon:ultra_ball", 2));
                rewards.add(item("cobblemon:rare_candy", 1));
            }
            case 3 -> {
                rewards.add(item("minecraft:emerald", 6));
                rewards.add(item("cobblemon:great_ball", 2));
            }
            case 4 -> {
                rewards.add(item("minecraft:emerald", 4));
                rewards.add(item("cobblemon:poke_ball", 2));
            }
            case 5 -> {
                rewards.add(item("minecraft:emerald", 3));
                rewards.add(item("cobblemon:poke_ball", 2));
            }
            default -> {
                rewards.add(item("minecraft:emerald", 2));
                rewards.add(item("cobblemon:poke_ball", 1));
            }
        }
        return rewards;
    }

    /**
     * Flat payout for a full sweep, otherwise scaled by ground covered so a board
     * lost near the end still pays. Thresholds are fractions of that board's own
     * safe cell count, which keeps one ladder working for all three sizes.
     */
    public static List<QuestReward> forPokesweeper(boolean cleared, int uncovered, int safeCells) {
        List<QuestReward> rewards = new ArrayList<>();
        if (cleared) {
            rewards.add(item("minecraft:emerald", 14));
            rewards.add(item("cobblemon:ultra_ball", 3));
            rewards.add(item("cobblemon:rare_candy", 2));
            return rewards;
        }

        if (uncovered >= safeCells * 70 / 100) {
            rewards.add(item("minecraft:emerald", 6));
            rewards.add(item("cobblemon:great_ball", 2));
        } else if (uncovered >= safeCells * 37 / 100) {
            rewards.add(item("minecraft:emerald", 3));
            rewards.add(item("cobblemon:poke_ball", 2));
        } else if (uncovered >= safeCells * 12 / 100) {
            rewards.add(item("minecraft:emerald", 1));
            rewards.add(item("cobblemon:poke_ball", 1));
        }
        return rewards;
    }

    private static QuestReward item(String id, int count) {
        return new QuestReward(
            QuestReward.Kind.ITEM,
            Optional.of(ResourceLocation.parse(id)),
            count,
            Optional.empty(),
            Optional.empty()
        );
    }
}
