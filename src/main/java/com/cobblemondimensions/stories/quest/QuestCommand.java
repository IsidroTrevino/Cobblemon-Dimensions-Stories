package com.cobblemondimensions.stories.quest;

import com.cobblemondimensions.stories.dialogue.WalkingDialogueDefinitions;
import com.cobblemondimensions.stories.dialogue.ServerDialogueService;
import com.cobblemondimensions.stories.minigame.Minigame;
import com.cobblemondimensions.stories.minigame.MinigameService;
import com.cobblemondimensions.stories.minigame.PokedexSource;
import com.cobblemondimensions.stories.shop.ShopDialogueDefinitions;
import com.cobblemondimensions.stories.shop.ShopService;
import com.cobblemondimensions.stories.ui.QuestJournal;
import com.cobblemondimensions.stories.ui.StoriesText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Player interfaces and operator controls. Administrative branches keep their
 * permission check even though reading the journal and submitting input is public.
 */
public final class QuestCommand {
    private static final SuggestionProvider<CommandSourceStack> QUEST_IDS = (context, builder) ->
        SharedSuggestionProvider.suggestResource(
            QuestDefinitions.get().all().stream().map(Quest::id).toList(), builder);

    private static final SuggestionProvider<CommandSourceStack> DIALOGUE_IDS = (context, builder) ->
        SharedSuggestionProvider.suggestResource(WalkingDialogueDefinitions.get().ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> SHOP_IDS = (context, builder) ->
        SharedSuggestionProvider.suggestResource(ShopDialogueDefinitions.get().ids(), builder);

    private static final SuggestionProvider<CommandSourceStack> GAME_IDS = (context, builder) ->
        SharedSuggestionProvider.suggest(
            Arrays.stream(Minigame.values()).map(Minigame::getSerializedName).toList(), builder);

    private QuestCommand() {
    }

    // every branch runs on the sender when no targets are given
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stories")
            .executes(QuestCommand::journal)
            .then(Commands.literal("journal").executes(QuestCommand::journal))
            .then(Commands.literal("choose")
                .then(Commands.argument("token", StringArgumentType.word())
                    .then(Commands.argument("choice", IntegerArgumentType.integer(-2))
                        .executes(context -> {
                            ServerDialogueService.choose(self(context),
                                StringArgumentType.getString(context, "token"),
                                IntegerArgumentType.getInteger(context, "choice"));
                            return 1;
                        }))))
            .then(Commands.literal("cancel").executes(context -> {
                ServerDialogueService.cancel(self(context));
                return 1;
            }))
            .then(Commands.literal("guess")
                .then(Commands.argument("pokemon", StringArgumentType.greedyString())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        PokedexSource.suggest(builder.getRemaining(),
                            MinigameService.generationOf(self(context)), 25)
                            .stream().map(PokedexSource.Entry::name), builder))
                    .executes(context -> {
                        MinigameService.guess(self(context),
                            StringArgumentType.getString(context, "pokemon"));
                        return 1;
                    })))
            .then(Commands.literal("complete")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("quest", ResourceLocationArgument.id())
                    .suggests(QUEST_IDS)
                    .executes(context -> complete(context, List.of(self(context))))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> complete(context, targets(context))))))
            .then(Commands.literal("start")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("dialogue", ResourceLocationArgument.id())
                    .suggests(DIALOGUE_IDS)
                    .executes(context -> start(context, List.of(self(context))))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> start(context, targets(context))))))
            .then(Commands.literal("reset")
                .requires(source -> source.hasPermission(2))
                .executes(context -> reset(context, List.of(self(context)), null))
                .then(Commands.argument("targets", EntityArgument.players())
                    .executes(context -> reset(context, targets(context), null))
                    .then(Commands.argument("quest", ResourceLocationArgument.id())
                        .suggests(QUEST_IDS)
                        .executes(context -> reset(
                            context,
                            targets(context),
                            ResourceLocationArgument.getId(context, "quest")
                        )))))
            .then(Commands.literal("shop")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("shop", ResourceLocationArgument.id())
                    .suggests(SHOP_IDS)
                    .executes(context -> shop(context, List.of(self(context))))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> shop(context, targets(context))))))
            .then(Commands.literal("minigame")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("game", StringArgumentType.word())
                    .suggests(GAME_IDS)
                    .executes(context -> minigame(context, List.of(self(context))))
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> minigame(context, targets(context))))))
            .then(Commands.literal("list")
                .requires(source -> source.hasPermission(2))
                .executes(QuestCommand::list)));
    }

    private static int journal(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        QuestJournal.open(self(context));
        return 1;
    }

    private static int shop(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        ResourceLocation id = ResourceLocationArgument.getId(context, "shop");
        if (ShopDialogueDefinitions.get().byId(id).isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown shop dialogue: " + id));
            return 0;
        }
        players.forEach(player -> ShopService.open(player, id));
        return players.size();
    }

    private static int minigame(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) {
        String name = StringArgumentType.getString(context, "game");
        for (Minigame game : Minigame.values()) {
            if (game.getSerializedName().equals(name)) {
                players.forEach(player -> MinigameService.open(player, game));
                return players.size();
            }
        }
        context.getSource().sendFailure(
            StoriesText.translatable("command.cobblemon_dimensions_stories.unknown_minigame", name));
        return 0;
    }

    private static int complete(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation quest = ResourceLocationArgument.getId(context, "quest");
        if (QuestDefinitions.get().byId(quest).isEmpty()) {
            context.getSource().sendFailure(
                StoriesText.translatable("command.cobblemon_dimensions_stories.unknown_quest", quest.toString()));
            return 0;
        }

        int changed = 0;
        for (ServerPlayer player : players) {
            if (QuestService.complete(player, quest)) {
                changed++;
            }
        }
        int total = changed;
        context.getSource().sendSuccess(
            () -> StoriesText.translatable("command.cobblemon_dimensions_stories.completed", quest.toString(), total), true);
        return changed;
    }

    private static int start(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ResourceLocation dialogue = ResourceLocationArgument.getId(context, "dialogue");
        if (WalkingDialogueDefinitions.get().byId(dialogue).isEmpty()) {
            context.getSource().sendFailure(
                StoriesText.translatable("command.cobblemon_dimensions_stories.unknown_dialogue", dialogue.toString()));
            return 0;
        }

        // no entity behind a command-triggered dialogue
        players.forEach(player -> QuestService.startDialogue(
            player, dialogue, Optional.empty(), QuestService.NO_SPEAKER));
        context.getSource().sendSuccess(
            () -> StoriesText.translatable("command.cobblemon_dimensions_stories.started", dialogue.toString()), true);
        return players.size();
    }

    private static int reset(
        CommandContext<CommandSourceStack> context,
        Collection<ServerPlayer> players,
        ResourceLocation quest
    ) {
        QuestSaveData data = QuestSaveData.get(context.getSource().getServer());
        for (ServerPlayer player : players) {
            ServerDialogueService.cancel(player);
            data.reset(player.getUUID(), quest);
            QuestService.sync(player);
        }
        context.getSource().sendSuccess(
            () -> StoriesText.translatable("command.cobblemon_dimensions_stories.reset", players.size()), true);
        return players.size();
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        List<Quest> quests = QuestDefinitions.get().all();
        context.getSource().sendSuccess(
            () -> StoriesText.translatable("command.cobblemon_dimensions_stories.list", quests.size()), false);
        quests.forEach(quest -> context.getSource().sendSuccess(
            () -> Component.literal(" - " + quest.id()).append(" ").append(StoriesText.resolve(quest.title())), false));
        return quests.size();
    }

    private static ServerPlayer self(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return context.getSource().getPlayerOrException();
    }

    private static Collection<ServerPlayer> targets(CommandContext<CommandSourceStack> context)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return EntityArgument.getPlayers(context, "targets");
    }
}
