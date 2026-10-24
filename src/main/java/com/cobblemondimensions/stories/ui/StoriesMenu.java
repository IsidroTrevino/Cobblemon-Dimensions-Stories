package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.ui.art.Grid;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Buttons sent through the vanilla chest protocol, with no custom menu type.
 *
 * <p>The player's own inventory is never shown: the menu is built on an empty
 * stand-in, so nothing real can be moved, and the resource pack's artwork has
 * nothing to cover. The title is where the picture lives (see {@link Canvas}).
 */
public final class StoriesMenu extends ChestMenu {
    private static final Logger LOGGER = LoggerFactory.getLogger("cobblemon_dimensions_stories/menus");
    public static final int SIZE = 54;
    public static final int COLUMNS = Grid.COLUMNS;

    private final ServerPlayer owner;
    /** Stand-in for the player's inventory; its slots show the pack's lower buttons. */
    private final Inventory stand;
    private final int size;
    private final Map<Integer, BiConsumer<Integer, ClickType>> handlers = new HashMap<>();
    private final Runnable onClose;
    private Runnable ticker = () -> { };
    private Component title = Component.empty();
    private boolean closed;

    private StoriesMenu(int id, ServerPlayer player, int rows, Inventory stand, Runnable onClose) {
        super(typeFor(rows), id, stand, new SimpleContainer(rows * COLUMNS), rows);
        this.owner = player;
        this.stand = stand;
        this.size = rows * COLUMNS;
        this.onClose = onClose;
    }

    private static MenuType<ChestMenu> typeFor(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            case 6 -> MenuType.GENERIC_9x6;
            default -> throw new IllegalArgumentException("Chest rows: " + rows);
        };
    }

    public static StoriesMenu open(ServerPlayer player, Component title,
                                   Consumer<StoriesMenu> configure, Runnable onClose) {
        return open(player, title, 6, configure, onClose);
    }

    public static StoriesMenu open(ServerPlayer player, Component title, int rows,
                                   Consumer<StoriesMenu> configure, Runnable onClose) {
        StoriesMenu[] opened = new StoriesMenu[1];
        Component resolved = StoriesText.resolve(title);
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> {
            StoriesMenu menu = new StoriesMenu(id, player, rows, new Inventory(player), onClose);
            menu.title = resolved;
            opened[0] = menu;
            configure.accept(menu);
            return menu;
        }, resolved));
        return opened[0];
    }

    /**
     * Swaps the picture without closing the menu. The client builds a fresh
     * screen for the same menu id, so this is as quick as redrawing; nothing
     * is sent when the title has not changed.
     */
    public void retitle(Component next) {
        Component resolved = StoriesText.resolve(next);
        if (closed || owner.containerMenu != this || resolved.equals(title)) {
            return;
        }
        title = resolved;
        owner.connection.send(new ClientboundOpenScreenPacket(containerId, getType(), resolved));
        sendAllDataToRemote();
    }

    public int rows() {
        return size / COLUMNS;
    }

    /** Fills a block of slots with one invisible button, so wide artwork reacts as one piece. */
    public void area(int column, int row, int columns, int rows, Component name,
                     BiConsumer<Integer, ClickType> handler, Component... lore) {
        for (int r = row; r < row + rows; r++) {
            for (int c = column; c < column + columns; c++) {
                set(r * COLUMNS + c, button(name, lore), handler);
            }
        }
    }

    /** An invisible icon: the resource pack draws the picture, the slot only catches the click. */
    public static ItemStack button(Component name, Component... lore) {
        ItemStack stack = new ItemStack(UiCarrier.ITEM);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(UiCarrier.BLANK));
        if (name == null) {
            stack.set(DataComponents.HIDE_TOOLTIP, Unit.INSTANCE);
            return stack;
        }
        stack.set(DataComponents.CUSTOM_NAME, StoriesText.resolve(name));
        if (lore.length > 0) {
            stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
                .map(component -> (Component) StoriesText.resolve(component)).toList()));
        }
        return stack;
    }

    /** Menu slot index of a slot in the lower block: {@code row} 0 to 2, then 3 for the bottom strip. */
    public int lowerSlot(int row, int column) {
        return size + (row < 3 ? row * COLUMNS : 3 * COLUMNS) + column;
    }

    /**
     * Puts an icon in a slot and makes it a button. Indexes past the chest rows
     * reach the lower block, which belongs to the stand-in inventory. A click
     * reaches the server even on an empty slot, so buttons there need no icon;
     * leaving them empty keeps stray items out of the client's hotbar view.
     */
    public void set(int slot, ItemStack icon, BiConsumer<Integer, ClickType> handler) {
        if (slot < 0 || slot >= slots.size()) {
            throw new IndexOutOfBoundsException("Stories button slot: " + slot);
        }
        getSlot(slot).set(icon.copy());
        if (handler == null) {
            handlers.remove(slot);
        } else {
            handlers.put(slot, handler);
        }
    }

    public void clear() {
        handlers.clear();
        getContainer().clearContent();
        stand.clearContent();
    }

    public void onTick(Runnable callback) {
        ticker = callback;
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.containerMenu instanceof StoriesMenu menu && !menu.closed) {
                menu.guarded(menu.ticker);
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

    private boolean isButton(int slot, int button, ClickType type) {
        return slot >= 0 && slot < slots.size() && (button == 0 || button == 1) && type == ClickType.PICKUP;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (player != owner || closed || owner.containerMenu != this) {
            return;
        }
        try {
            if (isButton(slot, button, type)) {
                BiConsumer<Integer, ClickType> handler = handlers.get(slot);
                if (handler != null) {
                    guarded(() -> handler.accept(button, type));
                }
            }
        } finally {
            // Undo vanilla client's optimistic pickup without mutating either inventory.
            if (owner.containerMenu == this) {
                broadcastFullState();
            }
        }
    }

    /**
     * Runs one of the menu's own callbacks. A fault in one should cost the player
     * this menu, not the server or their connection, so it is logged and the menu
     * is closed.
     */
    private void guarded(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            LOGGER.error("Closing a broken menu for {}", owner.getGameProfile().getName(), exception);
            if (owner.containerMenu == this) {
                owner.closeContainer();
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
        // The client showed the stand-in's empty slots in place of its own
        // inventory; send the real one back.
        owner.inventoryMenu.sendAllDataToRemote();
        if (!closed) {
            closed = true;
            onClose.run();
        }
    }
}
