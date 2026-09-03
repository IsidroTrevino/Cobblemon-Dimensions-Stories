package com.cobblemondimensions.stories.quest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** SavedData for per-player quest state: accepted, finished, and mid-stage progress. */
public final class QuestSaveData extends SavedData {
    private static final String FILE_ID = "cobblemon_dimensions_stories_quests";
    private static final String PLAYERS = "players";
    private static final String ACTIVE = "active";
    private static final String COMPLETED = "completed";
    private static final String PROGRESS = "progress";
    private static final String STAGE = "stage";
    private static final String COUNT = "count";
    private static final String BESTS = "minigame_bests";

    public static final SavedData.Factory<QuestSaveData> FACTORY =
        new SavedData.Factory<>(QuestSaveData::new, QuestSaveData::load, null);

    private final Map<UUID, Set<ResourceLocation>> active = new HashMap<>();
    private final Map<UUID, Set<ResourceLocation>> completed = new HashMap<>();
    private final Map<UUID, Map<ResourceLocation, QuestProgress>> progress = new HashMap<>();
    // best score per minigame, keyed by the minigame's serialized name
    private final Map<UUID, Map<String, Integer>> bests = new HashMap<>();

    private QuestSaveData() {
    }

    public int bestOf(UUID player, String game) {
        return bests.getOrDefault(player, Map.of()).getOrDefault(game, 0);
    }

    /** Only writes when {@code score} beats the stored record. */
    public void recordBest(UUID player, String game, int score) {
        Map<String, Integer> records = bests.computeIfAbsent(player, key -> new HashMap<>());
        if (score > records.getOrDefault(game, 0)) {
            records.put(game, score);
            setDirty();
        }
    }

    public static QuestSaveData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    public boolean isActive(UUID player, ResourceLocation quest) {
        return active.getOrDefault(player, Set.of()).contains(quest);
    }

    public boolean isCompleted(UUID player, ResourceLocation quest) {
        return completed.getOrDefault(player, Set.of()).contains(quest);
    }

    public QuestProgress progressOf(UUID player, ResourceLocation quest) {
        return progress.getOrDefault(player, Map.of()).getOrDefault(quest, QuestProgress.START);
    }

    public void setProgress(UUID player, ResourceLocation quest, QuestProgress value) {
        progress.computeIfAbsent(player, key -> new HashMap<>()).put(quest, value);
        setDirty();
    }

    public void setActive(UUID player, ResourceLocation quest) {
        active.computeIfAbsent(player, key -> new HashSet<>()).add(quest);
        progress.computeIfAbsent(player, key -> new HashMap<>()).put(quest, QuestProgress.START);
        setDirty();
    }

    /** Moves a quest to completed and throws away its stage progress. */
    public void setCompleted(UUID player, ResourceLocation quest) {
        active.computeIfAbsent(player, key -> new HashSet<>()).remove(quest);
        completed.computeIfAbsent(player, key -> new HashSet<>()).add(quest);
        progress.computeIfPresent(player, (key, quests) -> {
            quests.remove(quest);
            return quests;
        });
        setDirty();
    }

    /** Wipes one quest for the player, or all of them when {@code quest} is null. */
    public void reset(UUID player, ResourceLocation quest) {
        if (quest == null) {
            active.remove(player);
            completed.remove(player);
            progress.remove(player);
        } else {
            forget(active, player, quest);
            forget(completed, player, quest);
            progress.computeIfPresent(player, (key, quests) -> {
                quests.remove(quest);
                return quests;
            });
        }
        setDirty();
    }

    private static void forget(
        Map<UUID, Set<ResourceLocation>> store,
        UUID player,
        ResourceLocation quest
    ) {
        Set<ResourceLocation> quests = store.get(player);
        if (quests != null) {
            quests.remove(quest);
        }
    }

    private static QuestSaveData load(CompoundTag tag, HolderLookup.Provider registries) {
        QuestSaveData data = new QuestSaveData();
        CompoundTag players = tag.getCompound(PLAYERS);
        for (String key : players.getAllKeys()) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                // not a uuid, drop the entry
                continue;
            }
            CompoundTag entry = players.getCompound(key);
            data.active.put(id, readIds(entry.getList(ACTIVE, Tag.TAG_STRING)));
            data.completed.put(id, readIds(entry.getList(COMPLETED, Tag.TAG_STRING)));
            data.progress.put(id, readProgress(entry.getCompound(PROGRESS)));
            data.bests.put(id, readBests(entry.getCompound(BESTS)));
        }
        return data;
    }

    private static Map<String, Integer> readBests(CompoundTag tag) {
        Map<String, Integer> records = new HashMap<>();
        tag.getAllKeys().forEach(key -> records.put(key, tag.getInt(key)));
        return records;
    }

    private static Map<ResourceLocation, QuestProgress> readProgress(CompoundTag tag) {
        Map<ResourceLocation, QuestProgress> quests = new HashMap<>();
        for (String key : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id == null) {
                continue;
            }
            CompoundTag entry = tag.getCompound(key);
            quests.put(id, new QuestProgress(entry.getInt(STAGE), entry.getInt(COUNT)));
        }
        return quests;
    }

    private static Set<ResourceLocation> readIds(ListTag list) {
        Set<ResourceLocation> ids = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(index));
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        // a player may appear in only some of the four maps, so union the keys
        Set<UUID> everyone = new HashSet<>(active.keySet());
        everyone.addAll(completed.keySet());
        everyone.addAll(progress.keySet());
        everyone.addAll(bests.keySet());
        for (UUID id : everyone) {
            CompoundTag entry = new CompoundTag();
            entry.put(ACTIVE, writeIds(active.getOrDefault(id, Set.of())));
            entry.put(COMPLETED, writeIds(completed.getOrDefault(id, Set.of())));
            entry.put(PROGRESS, writeProgress(progress.getOrDefault(id, Map.of())));
            CompoundTag records = new CompoundTag();
            bests.getOrDefault(id, Map.of()).forEach(records::putInt);
            entry.put(BESTS, records);
            players.put(id.toString(), entry);
        }
        tag.put(PLAYERS, players);
        return tag;
    }

    private static CompoundTag writeProgress(Map<ResourceLocation, QuestProgress> quests) {
        CompoundTag tag = new CompoundTag();
        quests.forEach((id, value) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt(STAGE, value.stage());
            entry.putInt(COUNT, value.count());
            tag.put(id.toString(), entry);
        });
        return tag;
    }

    private static ListTag writeIds(Set<ResourceLocation> ids) {
        ListTag list = new ListTag();
        ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }
}
