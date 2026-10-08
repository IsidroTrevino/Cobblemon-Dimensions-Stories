package com.cobblemondimensions.stories.quest;

import com.mojang.brigadier.CommandDispatcher;
import java.util.UUID;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/** Legacy saves and permission boundaries must survive the interface migration. */
public final class QuestCompatibilityRegression {
    private QuestCompatibilityRegression() {
    }

    public static void main(String[] args) throws Exception {
        UUID player = UUID.fromString("5e222b24-57c5-4a52-b020-0a26f7c9c521");
        ResourceLocation active = ResourceLocation.parse("cobblemon_dimensions_stories:the_night_watch");
        ResourceLocation completed = ResourceLocation.parse("cobblemon_dimensions_stories:first_steps");
        CompoundTag legacy = TagParser.parseTag("""
            {players:{"5e222b24-57c5-4a52-b020-0a26f7c9c521":{
              active:["cobblemon_dimensions_stories:the_night_watch"],
              completed:["cobblemon_dimensions_stories:first_steps"],
              progress:{"cobblemon_dimensions_stories:the_night_watch":{stage:2,count:3}},
              minigame_bests:{pattern:14,pokewrle:5,pokesweeper:3520,pokesweeper_easy:3574,pokesweeper_medium:3500}
            }}}
            """);
        QuestSaveData data = QuestSaveData.FACTORY.deserializer().apply(legacy, null);
        check(data.isActive(player, active), "legacy active quest lost");
        check(data.isCompleted(player, completed), "legacy completed quest lost");
        check(data.progressOf(player, active).equals(new QuestProgress(2, 3)), "legacy stage/count lost");
        check(data.bestOf(player, "pattern") == 14, "Pattern record changed");
        check(data.bestOf(player, "pokewrle") == 5, "Pokewrle record changed");
        check(data.bestOf(player, "pokesweeper") == 3520, "Hard sweep record changed");
        check(data.bestOf(player, "pokesweeper_easy") == 3574, "Easy sweep record changed");
        check(data.bestOf(player, "pokesweeper_medium") == 3500, "Medium sweep record changed");
        check(data.save(new CompoundTag(), null).equals(legacy), "saving changes the legacy data format");
        check(!data.isActive(UUID.fromString("a9a4517d-6d39-4809-8603-a91cd657e5d5"), active),
            "quest state leaked to another player");

        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        QuestCommand.register(dispatcher);
        var root = dispatcher.getRoot().getChild("stories");
        CommandSourceStack ordinary = source(0);
        CommandSourceStack operator = source(2);
        check(root.getRequirement().test(ordinary), "normal players cannot access Stories interfaces");
        for (String branch : new String[] {"journal", "choose", "guess", "cancel"}) {
            check(root.getChild(branch).getRequirement().test(ordinary), "player input blocked: " + branch);
        }
        for (String branch : new String[] {"complete", "start", "reset", "minigame", "list"}) {
            check(!root.getChild(branch).getRequirement().test(ordinary), "operator control exposed: " + branch);
            check(root.getChild(branch).getRequirement().test(operator), "operator control lost: " + branch);
        }
        System.out.println("Quest compatibility regressions passed (legacy saves and command permissions).");
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null,
            permission, "Test player", Component.literal("Test player"), null, null);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
