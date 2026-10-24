package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.quest.QuestReward;
import com.cobblemondimensions.stories.quest.QuestSaveData;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.ui.StoriesMenu;
import com.cobblemondimensions.stories.ui.StoriesText;
import com.cobblemondimensions.stories.ui.art.PokesweeperArt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntConsumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-owned arcade sessions rendered through ordinary Minecraft chest menus. */
public final class MinigameService {
    public static final int POKEWRLE_ATTEMPTS = PokewrleRound.ATTEMPTS;
    public static final int ALL_GENERATIONS = 0;
    private static final int RECORD_CAP = PokesweeperRound.RECORD_CAP;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private MinigameService() {
    }

    public static void open(ServerPlayer player, Minigame game) {
        if (game == Minigame.POKEWRLE) {
            setup(player, ALL_GENERATIONS);
            return;
        }
        long now = System.nanoTime();
        if (game == Minigame.PATTERN) {
            closeCurrent(player, now);
            PatternGame.start(player);
            return;
        }
        closeCurrent(player, now);
        Session session = new SweepSession(player, PokesweeperMode.of(game));
        SESSIONS.put(player.getUUID(), session);
        session.show(now);
    }

    /** Selecting a generation deliberately starts a fresh six-attempt round. */
    public static void setup(ServerPlayer player, int generation) {
        int wanted = generation >= 1 && generation <= 9 ? generation : ALL_GENERATIONS;
        List<PokedexSource.Entry> pool = PokedexSource.ofGeneration(wanted);
        if (pool.isEmpty()) {
            wanted = ALL_GENERATIONS;
            pool = PokedexSource.all();
        }
        if (pool.isEmpty()) {
            player.displayClientMessage(StoriesText.translatable(
                "message.cobblemon_dimensions_stories.pokewrle_unavailable").withStyle(ChatFormatting.RED), false);
            return;
        }
        PokedexSource.Entry answer = pool.get(player.getRandom().nextInt(pool.size()));
        long now = System.nanoTime();
        closeCurrent(player, now);
        Session session = new PokewrleSession(player, species(answer), wanted);
        SESSIONS.put(player.getUUID(), session);
        session.show(now);
    }

    /** Ordinary player command input; the active round supplies its own answer. */
    public static void guess(ServerPlayer player, String typed) {
        Session session = SESSIONS.get(player.getUUID());
        if (!(session instanceof PokewrleSession pokewrle)) {
            player.displayClientMessage(text("Open Pokewrle at the arcade before guessing.", ChatFormatting.YELLOW), false);
            return;
        }
        pokewrle.guess(typed, System.nanoTime());
    }

    /** Whether this is a stand a Pattern round seats someone on; nobody else may interact with it. */
    public static boolean isSeat(net.minecraft.world.entity.Entity entity) {
        return PatternGame.isSeat(entity);
    }

    /** An armor stand left over from a Pattern round that never got to clean up after itself. */
    public static boolean strayedSeat(net.minecraft.world.entity.Entity entity) {
        return PatternGame.strayed(entity);
    }

    public static int generationOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session instanceof PokewrleSession pokewrle ? pokewrle.round.generation() : ALL_GENERATIONS;
    }

    /** State ticks without input, so a client cannot hold a timer open. */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (!session.current()) {
                continue;
            }
            if (server.getPlayerList().getPlayer(session.player.getUUID()) != session.player) {
                SESSIONS.remove(session.player.getUUID(), session);
                session.settlement.discard();
                continue;
            }
            if (session.player.containerMenu != session.menu && !session.keepClosed()) {
                SESSIONS.remove(session.player.getUUID(), session);
                session.settle(now);
                continue;
            }
            session.tick(now);
        }
    }

    /** Disconnect discards unfinished games; completed games were already paid while online. */
    public static void forget(ServerPlayer player) {
        PatternGame.forget(player);
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            session.settlement.discard();
        }
    }

    /** Reloads and server stops deliberately discard sessions. */
    public static void stop() {
        PatternGame.stop();
        List<Session> sessions = List.copyOf(SESSIONS.values());
        SESSIONS.clear();
        for (Session session : sessions) {
            session.settlement.discard();
            if (session.player.containerMenu == session.menu) {
                session.player.closeContainer();
            }
        }
    }

    private static void closeCurrent(ServerPlayer player, long now) {
        PatternGame.leave(player);
        Session old = SESSIONS.remove(player.getUUID());
        if (old != null) {
            old.settle(now);
            if (player.containerMenu == old.menu) {
                player.closeContainer();
            }
        }
    }

    private static PokewrleRound.Species species(PokedexSource.Entry entry) {
        return new PokewrleRound.Species(entry.id().toString(), entry.name(), entry.generation(),
            entry.primaryType(), entry.secondaryType(), entry.eggGroup(), entry.stage());
    }

    /** A Poke Ball marks a flag, with a banner standing in when Cobblemon's item is missing. */
    private static Item flagItem() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
            .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball"))
            .orElse(Items.ORANGE_BANNER);
    }

    private static Component text(String value) {
        return Component.literal(value);
    }

    private static Component text(String value, ChatFormatting color) {
        return Component.literal(value).withStyle(color);
    }

    private abstract static class Session {
        final ServerPlayer player;
        final Minigame game;
        final SessionSettlement settlement = new SessionSettlement();
        StoriesMenu menu;
        private boolean opening;

        Session(ServerPlayer player, Minigame game) {
            this.player = player;
            this.game = game;
        }

        final boolean current() {
            return SESSIONS.get(player.getUUID()) == this;
        }

        final void show(long now) {
            opening = true;
            try {
                menu = StoriesMenu.open(player, title(), rows(), configured -> { }, this::closed);
            } finally {
                opening = false;
            }
            if (menu == null) {
                SESSIONS.remove(player.getUUID(), this);
                settlement.discard();
                return;
            }
            render(now);
            menu.broadcastChanges();
        }

        final void closed() {
            if (opening || !current() || keepClosed()) {
                return;
            }
            SESSIONS.remove(player.getUUID(), this);
            settle(System.nanoTime());
        }

        boolean keepClosed() {
            return false;
        }

        final void button(int slot, ItemStack icon, IntConsumer action) {
            menu.set(slot, icon, (button, type) -> {
                if (current() && player.containerMenu == menu) {
                    action.accept(button);
                }
            });
        }

        final void display(int slot, Item item, Component name, Component... lore) {
            menu.set(slot, StoriesMenu.icon(item, name, lore), null);
        }

        final int best() {
            return QuestSaveData.get(player.server).bestOf(player.getUUID(), game.getSerializedName());
        }

        final void record(int score) {
            QuestSaveData.get(player.server).recordBest(player.getUUID(), game.getSerializedName(), score);
        }

        final void grant(List<QuestReward> rewards) {
            rewards.forEach(reward -> QuestService.grant(player, reward));
        }

        final void sound(SoundEvent event, float pitch) {
            player.playNotifySound(event, SoundSource.PLAYERS, 0.55F, pitch);
        }

        /** Chest rows the game's menu needs. */
        int rows() {
            return StoriesMenu.SIZE / StoriesMenu.COLUMNS;
        }

        abstract Component title();
        abstract void render(long now);
        abstract void settle(long now);
        void tick(long now) { }
    }

    private static final class SweepSession extends Session {
        private final PokesweeperMode mode;
        private final PokesweeperRound round;
        private final PokesweeperViewport view;
        private int lastSecond = -1;

        SweepSession(ServerPlayer player, PokesweeperMode mode) {
            super(player, mode.minigame());
            this.mode = mode;
            round = new PokesweeperRound(new Random(player.getRandom().nextLong()), mode.cols(), mode.rows(), mode.mines());
            view = new PokesweeperViewport(mode.cols(), mode.rows());
        }

        @Override
        int rows() {
            return PokesweeperView.ROWS;
        }

        @Override
        Component title() {
            return PokesweeperView.compose(mode, round.board(), view, 0, bestSeconds());
        }

        private int bestSeconds() {
            int record = best();
            return record > 0 ? RECORD_CAP - record : 0;
        }

        @Override
        void tick(long now) {
            int seconds = round.displaySeconds(now);
            if (seconds != lastSecond) {
                lastSecond = seconds;
                render(now);
            }
        }

        @Override
        void render(long now) {
            menu.clear();
            PokesweeperBoard board = round.board();
            menu.retitle(PokesweeperView.compose(mode, board, view, round.displaySeconds(now), bestSeconds()));
            for (int slot = 0; slot < PokesweeperArt.VIEW_COLUMNS * PokesweeperArt.VIEW_ROWS; slot++) {
                int cell = view.cellAt(slot);
                if (cell < 0) {
                    continue;
                }
                button(slot, cellIcon(board, cell), button -> clickCell(cell, button));
            }
            int control = PokesweeperView.CONTROL_ROW * StoriesMenu.COLUMNS;
            navigation(control + PokesweeperView.PREVIOUS_COLUMNS, "Previous columns", -1, 0, view.firstCol() > 0);
            navigation(control + PokesweeperView.NEXT_COLUMNS, "Next columns", 1, 0, view.lastCol() < board.cols());
            navigation(control + PokesweeperView.PREVIOUS_ROWS, "Previous rows", 0, -1, view.firstRow() > 0);
            navigation(control + PokesweeperView.NEXT_ROWS, "Next rows", 0, 1, view.lastRow() < board.rows());
            button(control + PokesweeperView.RESTART, StoriesMenu.button(text("Restart this size"),
                text("Settles this board and deals a new one.")), button -> open(player, mode.minigame()));
            difficulty(control + PokesweeperView.FIRST_SIZE, PokesweeperMode.EASY);
            difficulty(control + PokesweeperView.FIRST_SIZE + 1, PokesweeperMode.MEDIUM);
            difficulty(control + PokesweeperView.FIRST_SIZE + 2, PokesweeperMode.HARD);
        }

        private ItemStack cellIcon(PokesweeperBoard board, int cell) {
            String position = "(" + (cell % board.cols() + 1) + ", " + (cell / board.cols() + 1) + ") ";
            switch (board.state(cell)) {
                case HIDDEN:
                    return StoriesMenu.button(text(position + "Buried ground"), text("Left click: dig. Right click: flag."));
                case FLAGGED:
                    boolean wrong = board.over() && board.wrongFlag(cell);
                    return StoriesMenu.icon(flagItem(),
                        text(position + (wrong ? "Wrong flag" : "Flag"), wrong ? ChatFormatting.RED : ChatFormatting.GOLD),
                        text("Right click: remove flag."));
                default:
                    if (board.mine(cell)) {
                        return StoriesMenu.icon(Items.TNT, text(position + "TNT", ChatFormatting.RED));
                    }
                    int count = board.count(cell);
                    return StoriesMenu.button(
                        text(position + (count == 0 ? "Clear ground" : count + " neighbouring TNT"), ChatFormatting.GREEN),
                        text("Left click: chord when adjacent flags match the number."));
            }
        }

        private void navigation(int slot, String label, int horizontal, int vertical, boolean enabled) {
            if (!enabled) {
                return;
            }
            button(slot, StoriesMenu.button(text(label)), button -> {
                view.move(horizontal, vertical);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
                render(System.nanoTime());
            });
        }

        private void difficulty(int slot, PokesweeperMode selected) {
            button(slot, StoriesMenu.button(text((selected == mode ? "Restart " : "Start ") + selected.name()),
                text(selected.cols() + " x " + selected.rows() + " / " + selected.mines() + " TNT")),
                button -> open(player, selected.minigame()));
        }

        private void clickCell(int cell, int button) {
            PokesweeperBoard board = round.board();
            if (board.over()) {
                return;
            }
            long now = System.nanoTime();
            if (button == 1) {
                PokesweeperBoard.Cell before = board.state(cell);
                round.toggleFlag(cell);
                if (before != board.state(cell)) {
                    sound(SoundEvents.UI_BUTTON_CLICK.value(), 0.9F);
                }
            } else {
                int[] opened = board.state(cell) == PokesweeperBoard.Cell.REVEALED
                    ? round.chord(cell, now) : round.dig(cell, now);
                if (board.lost()) {
                    sound(SoundEvents.GENERIC_EXPLODE.value(), 0.8F);
                } else if (board.won()) {
                    sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F);
                } else if (opened.length > 0) {
                    sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F);
                }
            }
            if (board.over()) {
                settle(now);
            }
            render(now);
        }

        @Override
        void settle(long now) {
            round.finish(now);
            settlement.runOnce(() -> {
                PokesweeperBoard board = round.board();
                if (!board.started()) {
                    return;
                }
                int seconds = round.seconds(now);
                if (board.won()) {
                    record(RECORD_CAP - seconds);
                }
                grant(MinigameRewards.forPokesweeper(board.won(), board.uncovered(), board.safeCells()));
                Component result = board.won() ? StoriesText.translatable(
                    "message.cobblemon_dimensions_stories.pokesweeper_cleared", seconds)
                    : board.lost() ? StoriesText.translatable(
                        "message.cobblemon_dimensions_stories.pokesweeper_result", board.uncovered())
                    : text("Sweep ended: " + board.uncovered() + " cells uncovered.");
                player.displayClientMessage(StoriesText.resolve(result)
                    .withStyle(board.won() ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
            });
        }
    }

    private static final class PokewrleSession extends Session {
        private final PokewrleRound round;
        private boolean choosingGeneration;

        PokewrleSession(ServerPlayer player, PokewrleRound.Species answer, int generation) {
            super(player, Minigame.POKEWRLE);
            round = new PokewrleRound(answer, generation);
        }

        @Override
        Component title() {
            return StoriesText.translatable("screen.cobblemon_dimensions_stories.pokewrle");
        }

        @Override
        boolean keepClosed() {
            return round.awaitingInput();
        }

        @Override
        void tick(long now) {
            expireInput(now);
        }

        private boolean expireInput(long now) {
            if (!round.expire(now)) {
                return false;
            }
            SESSIONS.remove(player.getUUID(), this);
            settlement.discard();
            player.displayClientMessage(text("Pokewrle input expired. Open a new round at the arcade.", ChatFormatting.YELLOW), false);
            return true;
        }

        private void guess(String typed, long now) {
            if (expireInput(now)) {
                return;
            }
            if (round.over()) {
                player.displayClientMessage(text("This round is over. Select New round to play again.", ChatFormatting.YELLOW), false);
                return;
            }
            Optional<PokedexSource.Entry> found = typed == null ? Optional.empty() : PokedexSource.byName(typed);
            PokewrleRound.Feedback result = round.guess(found.map(MinigameService::species).orElse(null));
            if (result.outcome() == PokewrleRound.Outcome.REJECTED) {
                String filter = round.generation() == 0 ? "the implemented Pokedex" : "generation " + round.generation();
                player.displayClientMessage(text("Choose a Pokemon from " + filter + ". No attempt was used.", ChatFormatting.YELLOW), false);
                sound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7F);
            } else {
                sound(result.outcome() == PokewrleRound.Outcome.WON ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F);
                if (round.over()) {
                    settle(now);
                    PokewrleRound.Species answer = result.answer().orElseThrow();
                    player.displayClientMessage(text("Answer: " + answer.name(), round.won() ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                }
            }
            round.resume();
            choosingGeneration = false;
            if (player.containerMenu == menu) {
                render(now);
            } else {
                show(now);
            }
        }

        @Override
        void render(long now) {
            menu.clear();
            if (choosingGeneration) {
                renderGenerationSelector();
                return;
            }
            List<PokewrleRound.Feedback> history = round.history();
            for (int attempt = 0; attempt < POKEWRLE_ATTEMPTS; attempt++) {
                int slot = attempt * 9;
                if (attempt < history.size()) {
                    PokewrleRound.Feedback feedback = history.get(attempt);
                    display(slot, Items.PAPER, text((attempt + 1) + ". " + feedback.name()));
                    for (int category = 0; category < feedback.hints().size(); category++) {
                        PokewrleHint hint = feedback.hints().get(category);
                        Item item = switch (hint.state()) {
                            case CORRECT -> Items.LIME_STAINED_GLASS_PANE;
                            case WRONG -> Items.RED_STAINED_GLASS_PANE;
                            case HIGHER -> Items.YELLOW_STAINED_GLASS_PANE;
                            case LOWER -> Items.LIGHT_BLUE_STAINED_GLASS_PANE;
                        };
                        String verdict = switch (hint.state()) {
                            case CORRECT -> "Matches the answer";
                            case WRONG -> "Different from the answer";
                            case HIGHER -> "Answer is from a higher generation";
                            case LOWER -> "Answer is from a lower generation";
                        };
                        display(slot + category + 1, item, text(hint.category().label() + ": " + hint.value()), text(verdict));
                    }
                } else {
                    display(slot, Items.GRAY_STAINED_GLASS_PANE, text("Attempt " + (attempt + 1)));
                }
            }
            String filter = round.generation() == 0 ? "All generations" : "Generation " + round.generation();
            display(7, Items.CLOCK, text(round.over() ? round.won() ? "Solved!" : "Round over" : round.remaining() + " guesses remaining"),
                text(filter), text("Best score: " + best() + " (higher is better)"));
            button(8, StoriesMenu.icon(Items.BOOK, text(round.over() ? "Round finished" : "Guess in chat"),
                text("Opens chat input for /stories guess <Pokemon>."), text("Use command autocomplete to find accepted names.")),
                button -> inputInChat());
            button(16, StoriesMenu.icon(Items.ARROW, text("Previous generation"), text("Starts a new round.")),
                button -> setup(player, Math.floorMod(round.generation() - 1, 10)));
            button(17, StoriesMenu.icon(Items.ARROW, text("Next generation"), text("Starts a new round.")),
                button -> setup(player, (round.generation() + 1) % 10));
            button(25, StoriesMenu.icon(Items.COMPASS, text("Select generation"), text(filter)), button -> {
                choosingGeneration = true;
                render(System.nanoTime());
            });
            display(26, Items.WRITABLE_BOOK, text("Five hints per guess"),
                text("Primary type, secondary type, egg group, evolution stage, generation."),
                text("Green matches; red differs; generation hints point higher or lower."));
            if (round.over() && !history.isEmpty()) {
                String answer = history.getLast().answer().orElseThrow().name();
                display(34, Items.NAME_TAG, text("Answer: " + answer, ChatFormatting.GOLD));
            }
            button(44, StoriesMenu.icon(Items.EMERALD, text("New round"), text(filter)), button -> setup(player, round.generation()));
            button(53, StoriesMenu.icon(Items.BARRIER, text("Leave Pokewrle")), button -> player.closeContainer());
        }

        private void renderGenerationSelector() {
            display(4, Items.COMPASS, text("Choose a generation"), text("Choosing starts a fresh six-attempt round."));
            for (int generation = 0; generation <= 9; generation++) {
                int chosen = generation;
                int available = PokedexSource.ofGeneration(generation).size();
                button(9 + generation, StoriesMenu.icon(generation == round.generation() ? Items.EMERALD : Items.PAPER,
                    text(generation == 0 ? "All generations" : "Generation " + generation),
                    text(available + " implemented species"),
                    text(available == 0 ? "Unavailable generations use the full Pokedex." : "Start a new round in this generation.")),
                    button -> setup(player, chosen));
            }
            button(49, StoriesMenu.icon(Items.ARROW, text("Back to this round")), button -> {
                choosingGeneration = false;
                render(System.nanoTime());
            });
        }

        private void inputInChat() {
            if (round.over()) {
                return;
            }
            round.awaitInput(System.nanoTime());
            player.closeContainer();
            player.displayClientMessage(Component.literal("[Guess a Pokemon] ")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/stories guess ")))
                .append(text("Type /stories guess <Pokemon> within five minutes. The board reopens after your guess.")), false);
        }

        @Override
        void settle(long now) {
            settlement.runOnce(() -> {
                if (round.won()) {
                    record(POKEWRLE_ATTEMPTS - round.used() + 1);
                    grant(MinigameRewards.forPokewrle(round.used()));
                    player.displayClientMessage(StoriesText.translatable(
                        "message.cobblemon_dimensions_stories.pokewrle_result", round.used()).withStyle(ChatFormatting.GREEN), false);
                }
            });
        }
    }
}
