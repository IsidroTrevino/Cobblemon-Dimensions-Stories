package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.npc.StoryNpcDefinitions;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestObjective;
import com.cobblemondimensions.stories.quest.QuestProgress;
import com.cobblemondimensions.stories.quest.QuestReward;
import com.cobblemondimensions.stories.quest.QuestSaveData;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.quest.QuestStatus;
import java.util.ArrayList;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-controlled quest journal. */
public final class QuestJournal {
    private static final Map<UUID, Unread> READ_STATE = new HashMap<>();
    private static final Map<UUID, View> VIEWS = new HashMap<>();

    private QuestJournal() {
    }

    public static void open(ServerPlayer player) {
        View view = new View(player);
        refresh(player);
        StoriesMenu.open(player, StoriesText.translatable("screen.cobblemon_dimensions_stories.quests"), menu -> {
            view.menu = menu;
            VIEWS.put(player.getUUID(), view);
            view.render();
            menu.onTick(() -> {
                if (player.server.getTickCount() % 20 == 0 && VIEWS.get(player.getUUID()) == view) {
                    view.render();
                }
            });
        }, () -> VIEWS.remove(player.getUUID(), view));
    }

    /** Quest syncs update an open journal and announce newly available, unvisited quests once. */
    public static void refresh(ServerPlayer player) {
        List<Quest> quests = QuestDefinitions.get().all();
        Unread unread = unread(player);
        List<String> available = quests.stream()
            .filter(quest -> QuestService.status(player, quest) == QuestStatus.AVAILABLE)
            .map(quest -> quest.id().toString()).toList();
        List<String> newlyUnread = unread.update(quests.stream()
            .map(quest -> quest.id().toString()).collect(Collectors.toSet()), available);
        View view = VIEWS.get(player.getUUID());
        if (view != null && player.containerMenu == view.menu) {
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
        private StoriesMenu menu;
        private int listPage;
        private int detailPage;
        private ResourceLocation selected;

        private View(ServerPlayer player) {
            this.player = player;
        }

        private void render() {
            menu.clear();
            if (selected != null) {
                QuestDefinitions.get().byId(selected).ifPresentOrElse(this::details, () -> {
                    selected = null;
                    list();
                });
            } else {
                list();
            }
            button(53, Items.BARRIER, "Close journal", () -> player.closeContainer());
        }

        private void list() {
            List<Quest> quests = QuestDefinitions.get().all();
            PageWindow window = new PageWindow(quests.size(), listPage);
            listPage = window.page();
            List<String> visited = new ArrayList<>();
            for (int index = window.first(); index < window.last(); index++) {
                Quest quest = quests.get(index);
                QuestStatus status = QuestService.status(player, quest);
                List<Component> lore = new ArrayList<>();
                lore.add(statusLine(status));
                if (unread(player).isUnread(quest.id().toString())) {
                    lore.add(Component.literal("Unread quest").withStyle(ChatFormatting.YELLOW));
                }
                giver(quest).ifPresent(name -> lore.add(Component.literal("Giver: ").append(name)));
                if (status == QuestStatus.ACTIVE) {
                    QuestProgress progress = QuestService.progressOf(player, quest);
                    int stage = clampStage(quest, progress.stage());
                    lore.add(Component.literal("Stage " + (stage + 1) + "/" + quest.stageCount()));
                    lore.addAll(objectiveLore(player, quest.stage(stage), progress.count(), true));
                }
                lore.add(Component.literal("Click to read details").withStyle(ChatFormatting.GRAY));
                int slot = index - window.first();
                menu.set(slot, icon(statusItem(status), quest.title(), lore), (button, click) -> {
                    selected = quest.id();
                    detailPage = 0;
                    render();
                });
                // Locked quests remain unread when they eventually unlock.
                if (status != QuestStatus.LOCKED) {
                    visited.add(quest.id().toString());
                }
            }
            unread(player).markSeen(visited);
            if (quests.isEmpty()) {
                menu.set(22, StoriesMenu.icon(Items.BOOK, Component.literal("No quests are loaded")), null);
            }
            navigation(window, false);
        }

        private void details(Quest quest) {
            QuestStatus status = QuestService.status(player, quest);
            QuestProgress progress = QuestService.progressOf(player, quest);
            int currentStage = clampStage(quest, progress.stage());
            List<Card> cards = new ArrayList<>();
            cards.add(new Card(statusItem(status), quest.title(), List.of(statusLine(status),
                Component.literal("Stages: " + quest.stageCount()))));
            Component giver = giver(quest).orElse(Component.literal("Find the quest giver"));
            String guidance = switch (status) {
                case AVAILABLE -> "Talk to the giver to start this quest.";
                case ACTIVE -> QuestService.canAdvance(player, quest)
                    ? "Return to the giver to finish this stage."
                    : "Continue the current stage, then return to the giver.";
                case LOCKED -> "Complete the prerequisites to unlock this quest.";
                case COMPLETED -> "This quest is complete and its rewards have been granted.";
            };
            cards.add(new Card(Items.COMPASS, Component.literal("Quest giver"), List.of(giver,
                Component.literal(guidance).withStyle(ChatFormatting.GRAY))));
            if (quest.requires().isEmpty()) {
                cards.add(new Card(Items.IRON_DOOR, Component.literal("Prerequisites"),
                    List.of(Component.literal("No prerequisites"))));
            }
            for (ResourceLocation required : quest.requires()) {
                boolean completed = QuestSaveData.get(player.server).isCompleted(player.getUUID(), required);
                Component title = QuestDefinitions.get().byId(required).map(Quest::title)
                    .orElse(Component.literal(required.toString()));
                cards.add(new Card(completed ? Items.EMERALD : Items.IRON_DOOR,
                    Component.literal("Prerequisite"), List.of(title,
                        Component.literal(completed ? "Completed" : "Not completed")
                            .withStyle(completed ? ChatFormatting.GREEN : ChatFormatting.RED))));
            }
            if (quest.description().isEmpty()) {
                cards.add(new Card(Items.PAPER, Component.literal("Description"),
                    List.of(Component.literal("No description"))));
            }
            for (int index = 0; index < quest.description().size(); index++) {
                textCards(cards, "Description " + (index + 1), quest.description().get(index));
            }
            for (int index = 0; index < quest.stageCount(); index++) {
                QuestObjective objective = quest.stage(index);
                boolean current = status == QuestStatus.ACTIVE && index == currentStage;
                boolean completed = status == QuestStatus.COMPLETED
                    || status == QuestStatus.ACTIVE && index < currentStage;
                List<Component> lore = new ArrayList<>();
                lore.add(Component.literal(completed ? "Completed" : current ? "Current stage" : "Upcoming stage")
                    .withStyle(completed ? ChatFormatting.GREEN : current ? ChatFormatting.GOLD : ChatFormatting.GRAY));
                objective.hint().ifPresent(hint -> lore.addAll(wrap(hint)));
                lore.addAll(objectiveLore(player, objective, progress.count(), current));
                cards.add(new Card(objectiveItem(objective),
                    Component.literal("Stage " + (index + 1) + "/" + quest.stageCount()), lore));
            }
            if (quest.rewards().isEmpty()) {
                cards.add(new Card(Items.CHEST, Component.literal("Rewards"),
                    List.of(Component.literal("No rewards"))));
            }
            for (int index = 0; index < quest.rewards().size(); index++) {
                QuestReward reward = quest.rewards().get(index);
                List<Component> lore = rewardLore(reward.displayName(), status == QuestStatus.COMPLETED);
                cards.add(new Card(Items.CHEST, Component.literal("Reward " + (index + 1)), lore));
            }
            PageWindow window = new PageWindow(cards.size(), detailPage);
            detailPage = window.page();
            for (int index = window.first(); index < window.last(); index++) {
                Card card = cards.get(index);
                menu.set(index - window.first(), icon(card.item(), card.title(), card.lore()), null);
            }
            if (status != QuestStatus.LOCKED) {
                unread(player).markSeen(List.of(quest.id().toString()));
            }
            button(45, Items.BOOK, "All quests", () -> {
                selected = null;
                render();
            });
            navigation(window, true);
        }

        private void navigation(PageWindow window, boolean details) {
            if (window.page() > 0) {
                button(47, Items.ARROW, "Previous page", () -> {
                    if (details) {
                        detailPage = window.previous().page();
                    } else {
                        listPage = window.previous().page();
                    }
                    render();
                });
            }
            menu.set(49, StoriesMenu.icon(Items.PAPER,
                Component.literal("Page " + (window.page() + 1) + "/" + window.pages())), null);
            if (window.page() + 1 < window.pages()) {
                button(51, Items.ARROW, "Next page", () -> {
                    if (details) {
                        detailPage = window.next().page();
                    } else {
                        listPage = window.next().page();
                    }
                    render();
                });
            }
        }

        private void button(int slot, Item item, String title, Runnable action) {
            menu.set(slot, StoriesMenu.icon(item, Component.literal(title)), (button, click) -> action.run());
        }
    }

    private record Card(Item item, Component title, List<Component> lore) {
    }

    private static java.util.Optional<Component> giver(Quest quest) {
        return StoryNpcDefinitions.get().questGiver(quest.id()).map(npc -> StoriesText.resolve(npc.name()));
    }

    private static int clampStage(Quest quest, int stage) {
        return Math.max(0, Math.min(stage, quest.stageCount() - 1));
    }

    private static Item statusItem(QuestStatus status) {
        return switch (status) {
            case LOCKED -> Items.BARRIER;
            case AVAILABLE -> Items.WRITABLE_BOOK;
            case ACTIVE -> Items.BOOK;
            case COMPLETED -> Items.EMERALD;
        };
    }

    private static Component statusLine(QuestStatus status) {
        return switch (status) {
            case LOCKED -> Component.literal("Locked").withStyle(ChatFormatting.DARK_GRAY);
            case AVAILABLE -> Component.literal("Available").withStyle(ChatFormatting.YELLOW);
            case ACTIVE -> Component.literal("Active").withStyle(ChatFormatting.GOLD);
            case COMPLETED -> Component.literal("Completed").withStyle(ChatFormatting.GREEN);
        };
    }

    private static Item objectiveItem(QuestObjective objective) {
        return switch (objective.kind()) {
            case DIALOGUE -> Items.WRITABLE_BOOK;
            case ITEM -> Items.CHEST;
            case KILL -> Items.IRON_SWORD;
            case ESCORT -> Items.LEAD;
            case COMMAND -> Items.REDSTONE_TORCH;
        };
    }

    private static List<Component> objectiveLore(ServerPlayer player, QuestObjective objective,
                                                 int count, boolean current) {
        List<Component> lore = new ArrayList<>();
        switch (objective.kind()) {
            case DIALOGUE -> lore.add(Component.literal("Finish the conversation with the giver."));
            case ITEM -> {
                Component item = objective.resolveItem().map(entry -> entry.getDescription().copy())
                    .orElseGet(() -> Component.literal(objective.item().map(ResourceLocation::toString)
                        .orElse("required items")));
                lore.add(Component.literal("Collect " + objective.required() + "x ").append(item));
                if (current) {
                    int held = objective.resolveItem().map(entry -> inventoryCount(player, entry)).orElse(0);
                    lore.add(Component.literal("Held: " + held + "/" + objective.required()));
                }
                lore.add(Component.literal(objective.consume()
                    ? "Hand the items to the giver." : "Keep the items and return to the giver."));
            }
            case KILL -> {
                Component entity = objective.resolveEntity().map(entry -> entry.getDescription().copy())
                    .orElseGet(() -> Component.literal(objective.entity().map(ResourceLocation::toString)
                        .orElse("required enemies")));
                lore.add(Component.literal("Defeat " + objective.required() + "x ").append(entity));
                if (current) {
                    lore.add(Component.literal("Defeated: " + count + "/" + objective.required()));
                }
            }
            case ESCORT -> {
                lore.add(Component.literal("Lead the giver " + objective.required() + " blocks from their post."));
                if (current) {
                    lore.add(Component.literal("Distance: " + count + "/" + objective.required() + " blocks"));
                }
            }
            case COMMAND -> lore.add(Component.literal("Complete the server-defined objective."));
        }
        return lore;
    }

    private static int inventoryCount(ServerPlayer player, Item item) {
        int held = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) {
                held += stack.getCount();
            }
        }
        return held;
    }

    private static ItemStack icon(Item item, Component title, List<Component> lore) {
        return StoriesMenu.icon(item, title, lore.toArray(Component[]::new));
    }

    static List<Component> rewardLore(Component name, boolean granted) {
        // Cobblemon's item translations belong to the player's existing client.
        // Flattening them on the dedicated server would turn their keys into text.
        List<Component> lore = new ArrayList<>(List.of(StoriesText.resolve(name)));
        lore.add(Component.literal(granted
            ? "Granted on completion" : "Granted when the quest is complete").withStyle(ChatFormatting.GRAY));
        return lore;
    }

    private static void textCards(List<Card> cards, String title, Component text) {
        List<Component> lines = wrap(text);
        int parts = Math.max(1, (lines.size() + 5) / 6);
        for (int part = 0; part < parts; part++) {
            cards.add(new Card(Items.PAPER, Component.literal(title + (parts > 1
                ? " (" + (part + 1) + "/" + parts + ")" : "")),
                lines.subList(part * 6, Math.min(lines.size(), (part + 1) * 6))));
        }
    }

    private static List<Component> wrap(Component component) {
        List<Component> lines = new ArrayList<>();
        for (String paragraph : StoriesText.resolve(component).getString().split("\\n", -1)) {
            String remaining = paragraph;
            while (remaining.length() > 44) {
                int end = remaining.lastIndexOf(' ', 44);
                if (end <= 0) {
                    end = 44;
                    if (Character.isHighSurrogate(remaining.charAt(end - 1))) {
                        end--;
                    }
                }
                lines.add(Component.literal(remaining.substring(0, end)).withStyle(ChatFormatting.GRAY));
                remaining = remaining.substring(end).stripLeading();
            }
            lines.add(Component.literal(remaining).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    static record PageWindow(int total, int page) {
        private static final int CAPACITY = 45;

        PageWindow {
            total = Math.max(0, total);
            int pages = total == 0 ? 1 : (total - 1) / CAPACITY + 1;
            page = Math.max(0, Math.min(page, pages - 1));
        }

        int pages() { return total == 0 ? 1 : (total - 1) / CAPACITY + 1; }
        int first() { return page * CAPACITY; }
        int last() { return Math.min(total, first() + CAPACITY); }
        PageWindow previous() { return new PageWindow(total, page - 1); }
        PageWindow next() { return new PageWindow(total, page + 1); }
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
