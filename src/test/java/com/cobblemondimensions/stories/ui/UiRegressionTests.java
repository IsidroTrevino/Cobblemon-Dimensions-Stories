package com.cobblemondimensions.stories.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.inventory.ClickType;

/** Executable regressions; no test-framework dependency is needed by the mod. */
public final class UiRegressionTests {
    private UiRegressionTests() {
    }

    public static void main(String[] args) {
        run();
    }

    public static void run() {
        Component title = Component.translatable("quest.cobblemon_dimensions_stories.first_steps.title");
        equal("First Steps", StoriesText.resolve(title).getString());
        equal("Quest complete: First Steps", StoriesText.resolve(Component.translatable(
            "message.cobblemon_dimensions_stories.quest_complete", title)).getString());
        Component styled = title.copy().withStyle(ChatFormatting.GOLD).append(Component.translatable(
            "npc.cobblemon_dimensions_stories.shopkeeper"));
        equal("First StepsMara", StoriesText.resolve(styled).getString());
        equal(ChatFormatting.GOLD.getColor(), StoriesText.resolve(styled).getStyle().getColor().getValue());
        equal("A datapack's own fallback", StoriesText.resolve(Component.translatableWithFallback(
            "quest.other_pack.title", "A datapack's own fallback")).getString());
        equal("English custom text", StoriesText.resolve(Component.translatableWithFallback(
            "quest.cobblemon_dimensions_stories.unlisted", "English custom text")).getString());

        Component rewardName = Component.literal("5x ").append(Component.translatable("item.cobblemon.poke_ball"));
        var rewardLine = QuestJournal.rewardLore(rewardName, false).getFirst();
        if (rewardLine.getSiblings().isEmpty()
            || !(rewardLine.getSiblings().getFirst().getContents() instanceof TranslatableContents contents)
            || !contents.getKey().equals("item.cobblemon.poke_ball")) {
            throw new AssertionError("Reward lore must retain the client's Cobblemon item translation.");
        }

        for (ClickType type : ClickType.values()) {
            equal(type == ClickType.PICKUP, StoriesMenu.isButtonClick(0, 0, type));
            equal(type == ClickType.PICKUP, StoriesMenu.isButtonClick(53, 1, type));
        }
        equal(false, StoriesMenu.isButtonClick(-999, 0, ClickType.PICKUP));
        equal(false, StoriesMenu.isButtonClick(54, 0, ClickType.PICKUP));
        equal(false, StoriesMenu.isButtonClick(0, 2, ClickType.PICKUP));
        System.out.println("Interface regressions passed (translation fallbacks and menu input policy).");
    }

    private static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }
}
