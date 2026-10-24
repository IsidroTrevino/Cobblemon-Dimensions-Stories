package com.cobblemondimensions.stories.shop;

import com.cobblemondimensions.stories.dialogue.ServerDialogueService;
import com.cobblemondimensions.stories.ui.StoriesMenu;
import com.cobblemondimensions.stories.ui.StoriesText;
import com.cobblemondimensions.stories.ui.art.ShopArt;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Runs a shopkeeper conversation in a menu the resource pack draws. Paging,
 * catalog picks and purchases are all decided here; the client only clicks.
 */
public final class ShopService {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Redraw every other tick: enough for the typing, the flapping hands and the title. */
    private static final int REDRAW_TICKS = 2;
    /** A clicked button shows pressed this long before its page changes. */
    private static final long PRESS_DELAY_NANOS = 120_000_000L;

    private ShopService() {
    }

    public static boolean open(ServerPlayer player, ResourceLocation id) {
        Optional<ShopDialogue> found = ShopDialogueDefinitions.get().byId(id);
        if (found.isEmpty() || found.get().pages().isEmpty()) {
            return false;
        }
        ServerDialogueService.cancel(player);
        Session previous = SESSIONS.remove(player.getUUID());
        if (previous != null && player.containerMenu == previous.menu) {
            player.closeContainer();
        }
        Session session = new Session(player, found.get());
        SESSIONS.put(player.getUUID(), session);
        session.menu = StoriesMenu.open(player, session.compose(System.nanoTime()), ShopArt.MENU_ROWS, menu -> {
            menu.onTick(() -> {
                if (SESSIONS.get(player.getUUID()) == session) {
                    session.tick(player.server.getTickCount());
                }
            });
        }, () -> SESSIONS.remove(player.getUUID(), session));
        if (session.menu == null) {
            SESSIONS.remove(player.getUUID(), session);
            return false;
        }
        session.buttons();
        return true;
    }

    public static void forget(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void stop() {
        for (Session session : List.copyOf(SESSIONS.values())) {
            if (session.player.containerMenu == session.menu) {
                session.player.closeContainer();
            }
        }
        SESSIONS.clear();
    }

    private static final class Session {
        private final ServerPlayer player;
        private final ShopDialogue dialogue;
        private final long openedNanos = System.nanoTime();
        private StoriesMenu menu;
        private ShopDialogue.Page page;
        private long pageNanos;
        private boolean firstPage = true;
        private boolean forceReveal;
        private int picked = -1;
        private ShopDialogue.Product product;
        /** The button row last clicked, shown pressed for a moment. */
        private int pressed = -1;
        private long pressedNanos;
        /** What a clicked button does, once it has been seen pressed. */
        private Runnable pending;

        private Session(ServerPlayer player, ShopDialogue dialogue) {
            this.player = player;
            this.dialogue = dialogue;
            this.page = dialogue.pages().getFirst();
            // the Curator starts talking once the closet has opened
            this.pageNanos = dialogue.theme() == ShopDialogue.Theme.HAND
                ? openedNanos + ShopView.HAND_OPENING_NANOS : openedNanos;
        }

        private List<String> lines() {
            String name = player.getGameProfile().getName();
            String item = product == null ? "" : product.name();
            String price = product == null ? "" : String.valueOf(product.price());
            return page.lines().stream()
                .map(line -> line.replace("{player}", name).replace("{item}", item).replace("{price}", price))
                .toList();
        }

        private int length() {
            return String.join("\n\n", lines()).length();
        }

        private int visible(long now) {
            if (forceReveal) {
                return length();
            }
            double seconds = Math.max(0L, now - pageNanos) / 1_000_000_000.0D;
            return Math.min(length(), (int) Math.floor(seconds * ShopView.CHARACTERS_PER_SECOND));
        }

        private boolean complete(long now) {
            return visible(now) >= length();
        }

        private Component compose(long now) {
            String speaker = StoriesText.resolve(dialogue.speaker()).getString();
            return ShopView.compose(new ShopView.Frame(dialogue.theme(), page, speaker, lines(), visible(now),
                complete(now), picked, pressed, pressedNanos, openedNanos, pageNanos, firstPage), now);
        }

        private void tick(int tick) {
            long now = System.nanoTime();
            if (pending != null && now - pressedNanos >= PRESS_DELAY_NANOS) {
                Runnable action = pending;
                pending = null;
                action.run();
                return;
            }
            if (tick % REDRAW_TICKS == 0) {
                render();
            }
        }

        private void render() {
            if (player.containerMenu != menu) {
                return;
            }
            menu.retitle(compose(System.nanoTime()));
            buttons();
        }

        /**
         * Every slot does something: buttons and catalog rows act, and anywhere
         * else finishes the line or moves on, as clicking the old screen did.
         */
        private void buttons() {
            menu.clear();
            int slots = menu.slots.size();
            for (int slot = 0; slot < slots; slot++) {
                menu.set(slot, ItemStack.EMPTY, (button, click) -> advance());
            }
            long now = System.nanoTime();
            boolean hand = dialogue.theme() == ShopDialogue.Theme.HAND;
            // the columns under each theme's buttons
            int firstColumn = hand ? 6 : 0;
            int lastColumn = hand ? 8 : 4;
            if (page.isCatalog()) {
                int count = ShopView.catalogCount(page);
                for (int row = 0; row < count; row++) {
                    int index = row;
                    for (int column = 0; column <= 5; column++) {
                        menu.set(menu.lowerSlot(row, column), ItemStack.EMPTY, (button, click) -> pick(index));
                    }
                }
                for (int column = 6; column <= 8; column++) {
                    menu.set(menu.lowerSlot(0, column), ItemStack.EMPTY, (button, click) -> press(0, this::buy));
                    menu.set(menu.lowerSlot(1, column), ItemStack.EMPTY, (button, click) -> press(1, this::back));
                }
                return;
            }
            if (!complete(now)) {
                return;
            }
            int count = Math.min(page.options().size(), ShopArt.BUTTON_ROWS.length);
            for (int row = 0; row < count; row++) {
                int index = row;
                ShopDialogue.Option option = page.options().get(row);
                for (int column = firstColumn; column <= lastColumn; column++) {
                    menu.set(menu.lowerSlot(row, column), ItemStack.EMPTY,
                        (button, click) -> press(index, () -> choose(option)));
                }
            }
        }

        /** Shows the button pressed straight away, and acts once the press has been seen. */
        private void press(int row, Runnable action) {
            if (pending != null) {
                return;
            }
            pressed = row;
            pressedNanos = System.nanoTime();
            pending = action;
            click();
            render();
        }

        private void advance() {
            long now = System.nanoTime();
            if (pending != null || firstPage && now - openedNanos < ShopView.HAND_OPENING_NANOS
                && dialogue.theme() == ShopDialogue.Theme.HAND) {
                return;
            }
            if (page.isCatalog()) {
                return;
            }
            if (!complete(now)) {
                forceReveal = true;
                render();
                return;
            }
            if (!page.options().isEmpty()) {
                return;
            }
            click();
            if (page.close()) {
                player.closeContainer();
            } else {
                go(page.next().orElse(null));
            }
        }

        private void pick(int index) {
            if (pending != null || System.nanoTime() - pageNanos < ShopView.catalogReady(ShopView.catalogCount(page))) {
                return;
            }
            picked = index;
            click();
            render();
        }

        private void buy() {
            if (picked < 0 || picked >= page.catalog().size()) {
                render();
                return;
            }
            product = page.catalog().get(picked);
            go(page.confirm().orElse(null));
        }

        private void back() {
            go(page.next().orElse(dialogue.pages().getFirst().id()));
        }

        private void choose(ShopDialogue.Option option) {
            if (option.buy()) {
                go(settle() ? page.bought().orElse(null) : page.refused().orElse(null));
            } else if (option.close()) {
                player.closeContainer();
            } else {
                go(option.go().orElse(null));
            }
        }

        /** Takes the price from the player's inventory and hands over the product, or changes nothing. */
        private boolean settle() {
            if (product == null) {
                return false;
            }
            Optional<Item> bought = BuiltInRegistries.ITEM.getOptional(product.item()).filter(item -> item != Items.AIR);
            Optional<Item> currency = BuiltInRegistries.ITEM.getOptional(dialogue.currency());
            if (bought.isEmpty() || currency.isEmpty()) {
                return false;
            }
            var inventory = player.getInventory();
            int wallet = 0;
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(currency.get())) {
                    wallet += stack.getCount();
                }
            }
            if (wallet < product.price()) {
                return false;
            }
            int remaining = product.price();
            for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.is(currency.get())) {
                    int take = Math.min(remaining, stack.getCount());
                    stack.shrink(take);
                    remaining -= take;
                }
            }
            ItemStack reward = new ItemStack(bought.get());
            if (!inventory.add(reward)) {
                player.drop(reward, false);
            }
            inventory.setChanged();
            return true;
        }

        private void go(String pageId) {
            Optional<ShopDialogue.Page> next = pageId == null ? Optional.empty() : dialogue.page(pageId);
            if (next.isEmpty()) {
                player.closeContainer();
                return;
            }
            page = next.get();
            pageNanos = System.nanoTime();
            firstPage = false;
            forceReveal = false;
            pressed = -1;
            if (page.isCatalog()) {
                picked = -1;
            }
            render();
        }

        private void click() {
            player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.MASTER, 0.4F, 1.0F);
        }
    }
}
