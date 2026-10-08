package com.cobblemondimensions.stories.ui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/** Buttons sent through the vanilla chest protocol, with no custom menu type. */
public final class StoriesMenu extends ChestMenu {
    public static final int SIZE = 54;

    private final ServerPlayer owner;
    private final Map<Integer, BiConsumer<Integer, ClickType>> handlers = new HashMap<>();
    private final Runnable onClose;
    private Runnable ticker = () -> { };
    private boolean closed;

    private StoriesMenu(int id, ServerPlayer player, Runnable onClose) {
        super(MenuType.GENERIC_9x6, id, player.getInventory(), new SimpleContainer(SIZE), 6);
        this.owner = player;
        this.onClose = onClose;
    }

    public static StoriesMenu open(ServerPlayer player, Component title,
                                   Consumer<StoriesMenu> configure, Runnable onClose) {
        StoriesMenu[] opened = new StoriesMenu[1];
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> {
            StoriesMenu menu = new StoriesMenu(id, player, onClose);
            opened[0] = menu;
            configure.accept(menu);
            return menu;
        }, StoriesText.resolve(title)));
        return opened[0];
    }

    public void set(int slot, ItemStack icon, BiConsumer<Integer, ClickType> handler) {
        if (slot < 0 || slot >= SIZE) {
            throw new IndexOutOfBoundsException("Stories button slot: " + slot);
        }
        getContainer().setItem(slot, icon.copy());
        if (handler == null) {
            handlers.remove(slot);
        } else {
            handlers.put(slot, handler);
        }
    }

    public void clear() {
        handlers.clear();
        getContainer().clearContent();
    }

    public void onTick(Runnable callback) {
        ticker = callback;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof StoriesMenu menu && !menu.closed) {
                menu.ticker.run();
                if (player.containerMenu == menu) {
                    menu.broadcastChanges();
                }
            }
        }
    }

    public static ItemStack icon(Item item, Component name, Component... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, StoriesText.resolve(name));
        if (lore.length > 0) {
            stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
                .map(component -> (Component) StoriesText.resolve(component)).toList()));
        }
        return stack;
    }

    public static boolean isButtonClick(int slot, int button, ClickType type) {
        return slot >= 0 && slot < SIZE && (button == 0 || button == 1) && type == ClickType.PICKUP;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != owner || closed || owner.containerMenu != this) {
            return;
        }
        try {
            if (isButtonClick(slot, button, type)) {
                BiConsumer<Integer, ClickType> handler = handlers.get(slot);
                if (handler != null) {
                    handler.accept(button, type);
                }
            }
        } finally {
            // Undo vanilla client's optimistic pickup without mutating either inventory.
            if (owner.containerMenu == this) {
                broadcastFullState();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return false;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && !closed;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!closed) {
            closed = true;
            onClose.run();
        }
    }
}
