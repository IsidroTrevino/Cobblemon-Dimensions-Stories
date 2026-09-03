package com.cobblemondimensions.stories.npc;

/**
 * Icon shown above a story NPC, evaluated per player. Declared least to most
 * urgent; when an NPC covers several quests the highest ordinal wins.
 */
public enum QuestMarker {
    /** Nothing to show. */
    NONE,
    /** Player is on one of this NPC's quests but cannot turn it in yet. */
    IN_PROGRESS,
    /** Has a quest the player can start. */
    AVAILABLE,
    /** A stage is finished and can be handed in now. */
    READY;

    private static final QuestMarker[] VALUES = values();

    public static QuestMarker byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }
}
