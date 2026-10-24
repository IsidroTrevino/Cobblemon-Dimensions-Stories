package com.cobblemondimensions.stories.ui;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The item that carries the pack's icon models. Minecraft 1.21.1 picks a model
 * from an item's own model file, so one rarely used item stands in for every
 * icon, and a custom model number says which one to show. The pack builder
 * writes the matching overrides.
 */
public final class UiCarrier {
    public static final Item ITEM = Items.KNOWLEDGE_BOOK;
    /** The fully transparent icon, used wherever the artwork is drawn by the title. */
    public static final int BLANK = 1;

    private UiCarrier() {
    }
}
