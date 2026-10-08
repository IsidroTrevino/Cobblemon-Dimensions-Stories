package com.cobblemondimensions.stories.minigame;

import com.cobblemondimensions.stories.quest.QuestReward;
import com.cobblemondimensions.stories.quest.QuestSaveData;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.ui.StoriesMenu;
import com.cobblemondimensions.stories.ui.StoriesText;
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
        closeCurrent(player, now);
        Session session = game == Minigame.PATTERN
            ? new PatternSession(player, now) : new SweepSession(player, PokesweeperMode.of(game));
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

    public static int generationOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session instanceof PokewrleSession pokewrle ? pokewrle.round.generation() : ALL_GENERATIONS;
    }

    /** State ticks without input, so a client cannot hold a timer open. */
    public static void tick(MinecraftServer server) {
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
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            session.settlement.discard();
        }
    }

    /** Reloads and server stops deliberately discard sessions. */
    public static void stop() {
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
                menu = StoriesMenu.open(player, title(), configured -> { }, this::closed);
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

        abstract Component title();
        abstract void render(long now);
        abstract void settle(long now);
        void tick(long now) { }
    }

    private static final class PatternSession extends Session {
        private static final Item[] ARROWS = {Items.MAGENTA_WOOL, Items.LIGHT_BLUE_WOOL, Items.ORANGE_WOOL, Items.LIME_WOOL};
        private static final Item[] SHAPES = {Items.AMETHYST_SHARD, Items.LIGHT_BLUE_CONCRETE, Items.FIRE_CHARGE, Items.LIME_DYE};
        private static final ChatFormatting[] COLORS = {ChatFormatting.LIGHT_PURPLE, ChatFormatting.AQUA, ChatFormatting.GOLD, ChatFormatting.GREEN};
        private static final String[] DIRECTIONS = {"Up", "Down", "Left", "Right"};
        private static final String[] SHAPE_NAMES = {"Pink diamond", "Blue square", "Orange circle", "Green cross"};
        private final PatternRound round;
        private long nextRefresh;
        private int countdownBeat = -1;

        PatternSession(ServerPlayer player, long now) {
            super(player, Minigame.PATTERN);
            round = new PatternRound(new Random(player.getRandom().nextLong()), now);
        }

        @Override
        Component title() {
            return StoriesText.translatable("screen.cobblemon_dimensions_stories.pattern");
        }

        @Override
        void tick(long now) {
            PatternRound.Phase before = round.phase();
            round.advance(now);
            if (round.phase() == PatternRound.Phase.OVER) {
                settle(now);
            }
            if (round.phase() == PatternRound.Phase.COUNTDOWN) {
                int beat = round.countdown(now);
                if (beat != countdownBeat) {
                    countdownBeat = beat;
                    sound(SoundEvents.NOTE_BLOCK_BELL.value(), beat == 0 ? 1.6F : 0.7F + (3 - beat) * 0.2F);
                }
            } else {
                countdownBeat = -1;
            }
            if (before != round.phase() || now >= nextRefresh) {
                render(now);
                nextRefresh = now + 200_000_000L;
            }
        }

        private ItemStack sign(int direction, String prefix) {
            String name = round.shapes() ? SHAPE_NAMES[direction] + " (" + DIRECTIONS[direction] + ")" : DIRECTIONS[direction];
            return StoriesMenu.icon(round.shapes() ? SHAPES[direction] : ARROWS[direction],
                text(prefix + name, COLORS[direction]));
        }

        @Override
        void render(long now) {
            menu.clear();
            int[] sequence = round.sequence();
            int top = Math.min(8, sequence.length);
            for (int step = 0; step < sequence.length; step++) {
                int slot = step < top ? (9 - top) / 2 + step
                    : 9 + (9 - (sequence.length - top)) / 2 + step - top;
                String prefix = (step < round.typed() ? "Done " : step == round.typed() ? "Next " : "") + (step + 1) + ". ";
                menu.set(slot, sign(sequence[step], prefix), null);
            }
            String state = switch (round.phase()) {
                case COUNTDOWN -> round.countdown(now) == 0 ? "GO!" : "Ready: " + round.countdown(now);
                case PLAYING -> "Level " + round.level() + " - " + round.remainingSeconds(now) + " seconds";
                case CLEARED -> round.level() == 9 ? "Shape mode begins next!" : round.perfect() ? "Perfect clear!" : "Level cleared!";
                case OVER -> "Time! " + round.levelsCleared() + " levels cleared";
            };
            display(22, Items.CLOCK, text(state, round.phase() == PatternRound.Phase.OVER ? ChatFormatting.GOLD : ChatFormatting.AQUA),
                text("Progress: " + round.typed() + " / " + sequence.length),
                text("Streak: " + round.streak() + " - best streak: " + round.bestStreak()),
                text("Record: " + Math.max(best(), Math.min(40, round.levelsCleared())) + " levels"));
            int[] controls = {31, 40, 39, 41};
            for (int direction = 0; direction < controls.length; direction++) {
                int input = direction;
                button(controls[direction], sign(direction, "Press "), button -> press(input));
            }
            display(45, Items.BOOK, text("Repeat the sequence in order"),
                text("Click the direction buttons before time runs out."),
                text("A wrong click resets progress and streak, but keeps the timer."),
                text("From level 10: diamond Up, square Down, circle Left, cross Right."));
            button(53, StoriesMenu.icon(Items.BARRIER, text("Finish and leave")), button -> player.closeContainer());
            if (round.phase() == PatternRound.Phase.OVER) {
                button(52, StoriesMenu.icon(Items.EMERALD, text("Play again")), button -> open(player, Minigame.PATTERN));
            }
        }

        private void press(int direction) {
            long now = System.nanoTime();
            PatternRound.Input result = round.press(direction, now);
            switch (result) {
                case WRONG -> sound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.6F);
                case CORRECT -> sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F + round.typed() * 0.05F);
                case CLEARED -> sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1F);
                case IGNORED -> { }
            }
            if (round.phase() == PatternRound.Phase.OVER) {
                settle(now);
            }
            render(now);
        }

        @Override
        void settle(long now) {
            round.finish(now);
            settlement.runOnce(() -> {
                int cleared = Math.min(40, round.levelsCleared());
                record(cleared);
                grant(MinigameRewards.forPattern(cleared));
                if (cleared > 0) {
                    player.displayClientMessage(StoriesText.translatable(
                        "message.cobblemon_dimensions_stories.pattern_result", cleared).withStyle(ChatFormatting.AQUA), false);
                }
            });
        }
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
        Component title() {
            return text("Pokesweeper - " + mode.name());
        }

        @Override
        void tick(long now) {
            int seconds = round.displaySeconds(now);
            if (seconds != lastSecond) {
                lastSecond = seconds;
                status(now);
            }
        }

        @Override
        void render(long now) {
            menu.clear();
            PokesweeperBoard board = round.board();
            for (int slot = 0; slot < 45; slot++) {
                int cell = view.cellAt(slot);
                if (cell < 0) {
                    continue;
                }
                String position = "(" + (cell % board.cols() + 1) + ", " + (cell / board.cols() + 1) + ") ";
                ItemStack icon;
                if (board.state(cell) == PokesweeperBoard.Cell.HIDDEN) {
                    icon = StoriesMenu.icon(Items.GRAY_CONCRETE, text(position + "Buried ground"),
                        text("Left click: dig. Right click: flag."));
                } else if (board.state(cell) == PokesweeperBoard.Cell.FLAGGED) {
                    boolean wrong = board.over() && board.wrongFlag(cell);
                    icon = StoriesMenu.icon(wrong ? Items.RED_BANNER : Items.ORANGE_BANNER,
                        text(position + (wrong ? "Wrong flag" : "Flag"), wrong ? ChatFormatting.RED : ChatFormatting.GOLD),
                        text("Right click: remove flag."));
                } else if (board.mine(cell)) {
                    icon = StoriesMenu.icon(Items.TNT, text(position + "TNT", ChatFormatting.RED));
                } else {
                    int count = board.count(cell);
                    icon = StoriesMenu.icon(count == 0 ? Items.WHITE_STAINED_GLASS_PANE : Items.PAPER,
                        text(position + (count == 0 ? "Clear ground" : count + " neighbouring TNT"), ChatFormatting.GREEN),
                        text("Left click: chord when adjacent flags match the number."));
                    if (count > 0) {
                        icon.setCount(count);
                    }
                }
                button(slot, icon, button -> clickCell(cell, button));
            }
            navigation(45, "Previous columns", -1, 0);
            navigation(46, "Next columns", 1, 0);
            navigation(47, "Previous rows", 0, -1);
            navigation(48, "Next rows", 0, 1);
            status(now);
            int record = best();
            display(50, Items.BOOK, text("Pokesweeper controls"),
                text("Left: dig / chord. Right: toggle flag."),
                text("Navigation changes the view only."),
                text("First dig and its neighbours are safe."),
                text(record > 0 ? "Best clear: " + (RECORD_CAP - record) + " seconds" : "No clear record yet."));
            difficulty(51, PokesweeperMode.EASY);
            difficulty(52, PokesweeperMode.MEDIUM);
            difficulty(53, PokesweeperMode.HARD);
        }

        private void navigation(int slot, String label, int horizontal, int vertical) {
            button(slot, StoriesMenu.icon(Items.ARROW, text(label)), button -> {
                view.move(horizontal, vertical);
                sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F);
                render(System.nanoTime());
            });
        }

        private void difficulty(int slot, PokesweeperMode selected) {
            button(slot, StoriesMenu.icon(selected == mode ? Items.EMERALD : Items.COMPASS,
                text((selected == mode ? "Restart " : "Start ") + selected.name()),
                text(selected.cols() + " x " + selected.rows() + " / " + selected.mines() + " TNT")),
                button -> open(player, selected.minigame()));
        }

        private void status(long now) {
            PokesweeperBoard board = round.board();
            String state = board.won() ? "Field swept clean!" : board.lost() ? "TNT! Round over" : "Uncovered " + board.uncovered() + " / " + board.safeCells();
            display(49, Items.CLOCK, text(state, board.lost() ? ChatFormatting.RED : ChatFormatting.GREEN),
                text("Time: " + round.displaySeconds(now) + " seconds"),
                text("TNT minus flags: " + board.minesLeft()),
                text("Columns " + (view.firstCol() + 1) + "-" + view.lastCol() + " of " + board.cols()),
                text("Rows " + (view.firstRow() + 1) + "-" + view.lastRow() + " of " + board.rows()));
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
