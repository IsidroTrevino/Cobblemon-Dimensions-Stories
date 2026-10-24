package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.dialogue.ServerDialogueService;
import com.cobblemondimensions.stories.npc.StoryNpcDefinitions;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestObjective;
import com.cobblemondimensions.stories.quest.QuestProgress;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.quest.QuestStatus;
import com.cobblemondimensions.stories.ui.art.QuestArt;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * The quest journal: the quest list on the left, the selected quest on the
 * right. Clicking a quest shows it, the arrows turn the page, and Esc closes the
 * journal like any other menu. Locked quests stay hidden until they unlock.
 */
public final class QuestJournal {
    private static final Map<UUID, Unread> READ_STATE = new HashMap<>();
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    /** Redraw every other tick: enough for the typewriter and the slow pulses. */
    private static final int REDRAW_TICKS = 2;

    private QuestJournal() {
    }

    public static void open(ServerPlayer player) {
        // the conversation box above the hotbar would only sit behind the journal
        ServerDialogueService.cancel(player);
        View view = new View(player);
        refresh(player);
        StoriesMenu.open(player, view.compose(System.nanoTime()), QuestArt.MENU_ROWS, menu -> {
            view.menu = menu;
            VIEWS.put(player.getUUID(), view);
            view.buttons();
            menu.onTick(() -> {
                if (player.server.getTickCount() % REDRAW_TICKS == 0 && VIEWS.get(player.getUUID()) == view) {
                    view.render();
                }
            });
        }, () -> VIEWS.remove(player.getUUID(), view));
    }

    /** Quest syncs update an open journal and announce newly available, unvisited quests once. */
    public static void refresh(ServerPlayer player) {
        List<Quest> quests = QuestDefinitions.get().all();
        Unread unread = unread(player);
        Map<ResourceLocation, QuestStatus> statuses = QuestService.statuses(player);
        List<String> available = quests.stream()
            .filter(quest -> statuses.get(quest.id()) == QuestStatus.AVAILABLE)
            .map(quest -> quest.id().toString()).toList();
        List<String> newlyUnread = unread.update(quests.stream()
            .map(quest -> quest.id().toString()).collect(Collectors.toSet()), available);
        View view = VIEWS.get(player.getUUID());
        if (view != null && player.containerMenu == view.menu) {
            // a reload can rename quests, so the cached titles go
            view.titles.clear();
            view.render();
        }
        long remaining = newlyUnread.stream().filter(unread::isUnread).count();
        if (remaining > 0) {
            MutableComponent message = Component.literal(remaining == 1
                ? "A new quest is available in your journal. "
                : remaining + " new quests are available in your journal. ")
                .withStyle(ChatFormatting.YELLOW);
            message.append(Component.literal("[Open journal]").withStyle(style ->
                style.withColor(ChatFormatting.GREEN).withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stories journal"))));
            player.sendSystemMessage(message);
        }
    }

    /** A quest's counter moved. Availability did not change, so only an open journal needs redrawing. */
    public static void progressed(ServerPlayer player) {
        View view = VIEWS.get(player.getUUID());
        if (view != null && player.containerMenu == view.menu) {
            view.render();
        }
    }

    public static void forget(ServerPlayer player) {
        VIEWS.remove(player.getUUID());
        READ_STATE.remove(player.getUUID());
    }

    public static void stop() {
        for (View view : List.copyOf(VIEWS.values())) {
            if (view.player.containerMenu == view.menu) {
                view.player.closeContainer();
            }
        }
        VIEWS.clear();
        READ_STATE.clear();
    }

    private static Unread unread(ServerPlayer player) {
        return READ_STATE.computeIfAbsent(player.getUUID(), id -> new Unread());
    }

    private static final class View {
        private final ServerPlayer player;
        private final long openedNanos = System.nanoTime();
        private StoriesMenu menu;
        /** Resolved quest titles; they only change when the datapack reloads. */
        private final Map<ResourceLocation, String> titles = new HashMap<>();
        /** The quests and page the click handlers were last laid out for. */
        private List<ResourceLocation> laidOut = List.of();
        private int laidOutPage = -1;
        private int page;
        private ResourceLocation selected;
        private long selectedNanos = openedNanos;

        private View(ServerPlayer player) {
            this.player = player;
        }

        /** Everything except locked quests, which stay hidden until they unlock. */
        private List<Quest> visible(Map<ResourceLocation, QuestStatus> statuses) {
            return QuestDefinitions.get().all().stream()
                .filter(quest -> statuses.get(quest.id()) != QuestStatus.LOCKED)
                .toList();
        }

        private String title(Quest quest) {
            return titles.computeIfAbsent(quest.id(), id -> plain(quest.title()));
        }

        /** Where the selection is in the list, falling back to the top after a reload. */
        private int index(List<Quest> quests) {
            for (int index = 0; index < quests.size(); index++) {
                if (quests.get(index).id().equals(selected)) {
                    return index;
                }
            }
            if (quests.isEmpty()) {
                return -1;
            }
            selected = quests.getFirst().id();
            selectedNanos = System.nanoTime();
            return 0;
        }

        private void render() {
            Map<ResourceLocation, QuestStatus> statuses = QuestService.statuses(player);
            List<Quest> quests = visible(statuses);
            int pages = Math.max(1, (quests.size() + QuestArt.ROWS_PER_PAGE - 1) / QuestArt.ROWS_PER_PAGE);
            page = Math.max(0, Math.min(page, pages - 1));
            menu.retitle(JournalView.compose(frame(quests, statuses), System.nanoTime()));
            // the click handlers only depend on which quests are listed and which page is up
            List<ResourceLocation> ids = quests.stream().map(Quest::id).toList();
            if (page != laidOutPage || !ids.equals(laidOut)) {
                buttons(quests, pages);
                laidOut = ids;
                laidOutPage = page;
            }
        }

        /** The first layout, once the menu exists. */
        private void buttons() {
            Map<ResourceLocation, QuestStatus> statuses = QuestService.statuses(player);
            List<Quest> quests = visible(statuses);
            int pages = Math.max(1, (quests.size() + QuestArt.ROWS_PER_PAGE - 1) / QuestArt.ROWS_PER_PAGE);
            page = Math.max(0, Math.min(page, pages - 1));
            buttons(quests, pages);
            laidOut = quests.stream().map(Quest::id).toList();
            laidOutPage = page;
        }

        /** Every slot under a quest row selects it; the bottom strip holds the page arrows. */
        private void buttons(List<Quest> quests, int pages) {
            menu.clear();
            int first = page * QuestArt.ROWS_PER_PAGE;
            for (int row = 0; row < QuestArt.ROWS_PER_PAGE && first + row < quests.size(); row++) {
                Quest quest = quests.get(first + row);
                for (int column = 0; column < 5; column++) {
                    menu.set(row * StoriesMenu.COLUMNS + column, ItemStack.EMPTY, (button, click) -> select(quest));
                }
            }
            if (page > 0) {
                for (int column = 0; column < 2; column++) {
                    menu.set(menu.lowerSlot(QuestArt.BUTTON_ROW, column), ItemStack.EMPTY, (button, click) -> turn(-1));
                }
            }
            if (page + 1 < pages) {
                for (int column = 2; column < 4; column++) {
                    menu.set(menu.lowerSlot(QuestArt.BUTTON_ROW, column), ItemStack.EMPTY, (button, click) -> turn(1));
                }
            }
        }

        private void select(Quest quest) {
            if (!quest.id().equals(selected)) {
                selected = quest.id();
                selectedNanos = System.nanoTime();
                click();
                render();
            }
        }

        private void turn(int pages) {
            page += pages;
            click();
            render();
        }

        private void click() {
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4F, 1.0F);
        }

        private Component compose(long now) {
            Map<ResourceLocation, QuestStatus> statuses = QuestService.statuses(player);
            return JournalView.compose(frame(visible(statuses), statuses), now);
        }

        private JournalView.Frame frame(List<Quest> quests, Map<ResourceLocation, QuestStatus> statuses) {
            int index = index(quests);
            List<JournalView.Row> rows = quests.stream()
                .map(quest -> new JournalView.Row(title(quest), statuses.get(quest.id())))
                .toList();
            int first = page * QuestArt.ROWS_PER_PAGE;
            if (first < quests.size()) {
                unread(player).markSeen(quests.subList(first, Math.min(quests.size(), first + QuestArt.ROWS_PER_PAGE))
                    .stream().map(quest -> quest.id().toString()).toList());
            }
            if (index < 0) {
                return new JournalView.Frame(rows, page, -1, "", "", "", openedNanos, selectedNanos);
            }
            Quest quest = quests.get(index);
            QuestStatus status = statuses.get(quest.id());
            String description = quest.descriptionText();
            String step = stepLine(quest, status);
            if (!step.isEmpty()) {
                description = description.isEmpty() ? step : description + "\n\n" + step;
            }
            return new JournalView.Frame(rows, page, index, description, giverHint(quest, status), rewardLine(quest),
                openedNanos, selectedNanos);
        }

        /** The current stage of an active quest, e.g. "Step 2/3: Find the shard (1/5)". */
        private String stepLine(Quest quest, QuestStatus status) {
            if (status != QuestStatus.ACTIVE) {
                return "";
            }
            QuestProgress progress = QuestService.progressOf(player, quest);
            QuestObjective objective = quest.stage(progress.stage());
            StringBuilder line = new StringBuilder();
            if (quest.stageCount() > 1) {
                line.append("Step ").append(Math.min(progress.stage(), quest.stageCount() - 1) + 1)
                    .append('/').append(quest.stageCount());
            }
            objective.hint().map(hint -> StoriesText.resolve(hint).getString()).ifPresent(hint -> {
                if (!line.isEmpty()) {
                    line.append(": ");
                }
                line.append(hint);
            });
            if (objective.tracksCounter()) {
                line.append(line.isEmpty() ? "" : " ")
                    .append('(').append(progress.count()).append('/').append(objective.required()).append(')');
            }
            return line.toString();
        }

        /** One line pointing at the giver. Empty once the quest is done. */
        private String giverHint(Quest quest, QuestStatus status) {
            if (status == QuestStatus.COMPLETED || status == QuestStatus.LOCKED) {
                return "";
            }
            String giver = StoryNpcDefinitions.get().questGiver(quest.id())
                .map(npc -> StoriesText.resolve(npc.name()).getString()).orElse("");
            if (giver.isEmpty()) {
                return status == QuestStatus.AVAILABLE ? "Find the quest giver" : "";
            }
            if (status == QuestStatus.AVAILABLE) {
                return "Talk to " + giver + " to start";
            }
            return quest.stage(QuestService.progressOf(player, quest).stage()).isTurnIn()
                ? "Bring it back to " + giver
                : "Return to " + giver;
        }
    }

    private static String plain(Component component) {
        return StoriesText.resolve(component).getString();
    }

    private static String rewardLine(Quest quest) {
        if (quest.rewards().isEmpty()) {
            return "No rewards";
        }
        return quest.rewards().stream()
            .map(reward -> StoriesText.resolve(reward.displayName()).getString())
            .collect(Collectors.joining(", "));
    }

    static final class Unread {
        private final Set<String> seen = new HashSet<>();
        private final Set<String> announced = new HashSet<>();
        private Set<String> available = Set.of();

        List<String> update(Set<String> definitions, List<String> available) {
            seen.retainAll(definitions);
            announced.retainAll(definitions);
            this.available = Set.copyOf(available);
            List<String> newlyUnread = available.stream().distinct()
                .filter(id -> !seen.contains(id) && !announced.contains(id)).toList();
            announced.addAll(newlyUnread);
            return newlyUnread;
        }

        void markSeen(Collection<String> ids) { seen.addAll(ids); }
        boolean isUnread(String id) { return available.contains(id) && !seen.contains(id); }
    }
}
