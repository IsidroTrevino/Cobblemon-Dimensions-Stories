package com.cobblemondimensions.stories.npc;

import com.cobblemondimensions.stories.net.QuestMarkersS2C;
import com.cobblemondimensions.stories.quest.Quest;
import com.cobblemondimensions.stories.quest.QuestDefinitions;
import com.cobblemondimensions.stories.quest.QuestObjective;
import com.cobblemondimensions.stories.quest.QuestService;
import com.cobblemondimensions.stories.quest.QuestStatus;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

/**
 * Runtime glue between Cobblemon's NPC entities and the story. Cobblemon is not
 * a compile-time dependency, so entities get matched on their registered type id
 * plus the {@code NPCClass} in their NBT. That lookup runs once per entity and
 * the answer is cached in vanilla scoreboard tags, which persist with the entity
 * and are cheap to read back.
 */
public final class StoryNpcs {
    private static final ResourceLocation NPC_ENTITY_TYPE =
        ResourceLocation.fromNamespaceAndPath("cobblemon", "npc");
    private static final String NPC_CLASS_KEY = "NPCClass";
    private static final String TAG_PREFIX = "story_npc:";
    private static final String TAG_FOREIGN = TAG_PREFIX + "none";
    private static final String HOME_PREFIX = "story_home:";

    private static final double SCAN_RADIUS = 48.0D;
    private static final int SCAN_INTERVAL = 10;
    // blocks from a waypoint before we call it reached
    private static final double WAYPOINT_REACHED = 2.5D;
    private static final double FOLLOW_DISTANCE = 4.0D;
    private static final double FOLLOW_GIVE_UP = 32.0D;
    // fallback in case a client never reports its dialogue closing
    private static final int CONVERSATION_TIMEOUT = 20 * 120;

    private static final Map<UUID, RouteState> ROUTES = new HashMap<>();
    private static final Map<UUID, Conversation> CONVERSATIONS = new HashMap<>();
    // story NPCs the last scan saw; keeps the per-tick float pass small
    private static final Set<UUID> TRACKED = new HashSet<>();

    private static EntityType<?> npcType;
    private static boolean npcTypeResolved;

    private StoryNpcs() {
    }

    /**
     * Right click on a story NPC. Tries its quests in datapack order: offer,
     * then turn in, then remind. Falls through to the idle line.
     */
    public static boolean interact(ServerPlayer player, Entity entity) {
        Optional<StoryNpc> found = resolve(entity);
        if (found.isEmpty()) {
            return false;
        }

        StoryNpc npc = found.get();
        int speaker = entity.getId();
        for (ResourceLocation questId : npc.quests()) {
            Optional<Quest> maybe = QuestDefinitions.get().byId(questId);
            if (maybe.isEmpty()) {
                continue;
            }

            Quest quest = maybe.get();
            QuestStatus status = QuestService.status(player, quest);
            if (status == QuestStatus.AVAILABLE) {
                beginConversation(entity, player);
                QuestService.accept(player, questId, speaker);
                return true;
            }
            if (status != QuestStatus.ACTIVE) {
                continue;
            }
            if (QuestService.canAdvance(player, quest)) {
                beginConversation(entity, player);
                QuestService.advance(player, questId, speaker);
                return true;
            }

            // Still sitting on a dialogue stage means the player walked off part way
            // through. Replay it from the top and let them finish.
            int stage = QuestService.progressOf(player, quest).stage();
            Optional<ResourceLocation> line = quest.stage(stage).completesWithDialogue()
                ? QuestService.stageDialogue(quest, stage)
                : QuestService.stageReminder(quest, stage);
            if (line.isPresent()) {
                beginConversation(entity, player);
                QuestService.startDialogue(player, line.get(), Optional.of(questId), speaker);
                return true;
            }
        }

        if (npc.idleDialogue().isPresent()) {
            beginConversation(entity, player);
            QuestService.startDialogue(player, npc.idleDialogue().get(), Optional.empty(), speaker);
            return true;
        }
        return false;
    }

    /** The client reported its walking dialogue closed. Frees the NPC to move again. */
    public static void endConversation(ServerPlayer player) {
        CONVERSATIONS.values().removeIf(conversation -> conversation.player().equals(player.getUUID()));
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        endConversation(player);
    }

    /**
     * Markers and movement in one pass over the entities near each player. NPCs
     * with nobody nearby are never visited.
     */
    public static void tick(MinecraftServer server) {
        // this part is every tick, the scan below is throttled
        keepAfloat(server);

        int gameTime = server.getTickCount();
        if (gameTime % SCAN_INTERVAL != 0 || npcType() == null) {
            return;
        }

        TRACKED.clear();
        // two players in range of the same NPC must not both drive it
        Set<UUID> alreadyMoved = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Map<Integer, Integer> markers = new HashMap<>();
            AABB box = player.getBoundingBox().inflate(SCAN_RADIUS);
            for (Entity entity : player.level().getEntities(player, box)) {
                Optional<StoryNpc> found = resolve(entity);
                if (found.isEmpty()) {
                    continue;
                }

                StoryNpc npc = found.get();
                TRACKED.add(entity.getUUID());
                QuestMarker marker = markerFor(player, npc);
                if (marker != QuestMarker.NONE) {
                    markers.put(entity.getId(), marker.ordinal());
                }
                if (entity instanceof PathfinderMob mob && alreadyMoved.add(entity.getUUID())) {
                    tickMovement(mob, npc, player, gameTime);
                }
            }
            ServerPlayNetworking.send(player, new QuestMarkersS2C(markers));
        }
    }

    /** Strongest marker any of this NPC's quests earns for {@code player}. */
    public static QuestMarker markerFor(ServerPlayer player, StoryNpc npc) {
        QuestMarker strongest = QuestMarker.NONE;
        for (ResourceLocation questId : npc.quests()) {
            Optional<Quest> maybe = QuestDefinitions.get().byId(questId);
            if (maybe.isEmpty()) {
                continue;
            }

            Quest quest = maybe.get();
            QuestMarker marker = switch (QuestService.status(player, quest)) {
                case AVAILABLE -> QuestMarker.AVAILABLE;
                case ACTIVE -> QuestService.canAdvance(player, quest)
                    ? QuestMarker.READY
                    : QuestMarker.IN_PROGRESS;
                default -> QuestMarker.NONE;
            };
            if (marker.ordinal() > strongest.ordinal()) {
                strongest = marker;
            }
        }
        return strongest;
    }

    /** Checks the cached tags first. {@code story_npc:none} means we already rejected this entity. */
    public static Optional<StoryNpc> resolve(Entity entity) {
        if (entity.getType() != npcType()) {
            return Optional.empty();
        }
        for (String tag : entity.getTags()) {
            if (tag.equals(TAG_FOREIGN)) {
                return Optional.empty();
            }
            if (tag.startsWith(TAG_PREFIX)) {
                ResourceLocation id = ResourceLocation.tryParse(tag.substring(TAG_PREFIX.length()));
                return id == null ? Optional.empty() : StoryNpcDefinitions.get().byId(id);
            }
        }
        return classify(entity);
    }

    /**
     * Reads NPCClass out of NBT once and writes the answer back as tags. Also
     * stamps the spawn position, which routes and escorts measure against.
     */
    private static Optional<StoryNpc> classify(Entity entity) {
        String rawClass = entity.saveWithoutId(new CompoundTag()).getString(NPC_CLASS_KEY);
        ResourceLocation npcClass = rawClass.isEmpty() ? null : ResourceLocation.tryParse(rawClass);
        Optional<StoryNpc> found = npcClass == null
            ? Optional.empty()
            : StoryNpcDefinitions.get().forNpcClass(npcClass);
        if (found.isEmpty()) {
            entity.addTag(TAG_FOREIGN);
            return Optional.empty();
        }

        BlockPos home = entity.blockPosition();
        entity.addTag(TAG_PREFIX + found.get().id());
        entity.addTag(HOME_PREFIX + home.getX() + "," + home.getY() + "," + home.getZ());
        return found;
    }

    /**
     * Cobblemon NPCs ship without a float goal. One following a player into a
     * river sinks and trudges along the bottom. Poking the jump control while
     * submerged is what vanilla's {@code FloatGoal} does. It has to run every
     * tick or they still end up underwater.
     */
    private static void keepAfloat(MinecraftServer server) {
        if (TRACKED.isEmpty()) {
            return;
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (UUID id : TRACKED) {
                if (level.getEntity(id) instanceof Mob mob && (mob.isInWater() || mob.isInLava())) {
                    mob.getJumpControl().jump();
                }
            }
        }
    }

    private static void tickMovement(PathfinderMob mob, StoryNpc npc, ServerPlayer nearby, int gameTime) {
        // neither of these is persisted, so they have to be re-applied after
        // every world load rather than set once at spawn
        mob.getNavigation().setCanFloat(true);
        mob.setPathfindingMalus(PathType.WATER, 0.0F);

        if (isBusy(mob, gameTime)) {
            mob.getNavigation().stop();
            return;
        }
        if (followIfEscorting(mob, npc, nearby)) {
            return;
        }
        if (npc.hasRoute()) {
            walkRoute(mob, npc, gameTime);
        }
    }

    /** Follows the player during an escort. False hands the NPC back to its route. */
    private static boolean followIfEscorting(PathfinderMob mob, StoryNpc npc, ServerPlayer nearby) {
        Optional<Quest> escort = escortQuest(nearby, npc);
        if (escort.isEmpty()) {
            return false;
        }

        double distance = mob.distanceTo(nearby);
        if (distance > FOLLOW_GIVE_UP) {
            return false;
        }
        if (distance > FOLLOW_DISTANCE) {
            mob.getNavigation().moveTo(nearby, npc.followSpeed());
        } else {
            mob.getNavigation().stop();
        }

        // escort progress is measured from the spawn post, not from the player
        BlockPos home = homeOf(mob);
        if (home != null) {
            QuestService.recordEscort(
                nearby, escort.get(), (int) Math.sqrt(mob.blockPosition().distSqr(home)));
        }
        return true;
    }

    /**
     * The quest this NPC is currently walking for: one of its own quests sitting
     * on an escort stage, or the blanket {@code follow_during} from its definition.
     */
    private static Optional<Quest> escortQuest(ServerPlayer player, StoryNpc npc) {
        for (ResourceLocation questId : npc.quests()) {
            Optional<Quest> maybe = QuestDefinitions.get().byId(questId);
            if (maybe.isEmpty() || QuestService.status(player, maybe.get()) != QuestStatus.ACTIVE) {
                continue;
            }
            if (QuestService.currentObjective(player, maybe.get()).kind() == QuestObjective.Kind.ESCORT) {
                return maybe;
            }
        }
        return npc.followDuring()
            .flatMap(id -> QuestDefinitions.get().byId(id))
            .filter(quest -> QuestService.status(player, quest) == QuestStatus.ACTIVE);
    }

    /** Loops the patrol, waiting {@code route_pause} ticks at each point. */
    private static void walkRoute(PathfinderMob mob, StoryNpc npc, int gameTime) {
        BlockPos home = homeOf(mob);
        if (home == null) {
            return;
        }

        RouteState state = ROUTES.computeIfAbsent(mob.getUUID(), key -> new RouteState());
        if (gameTime < state.resumeAtTick) {
            return;
        }

        BlockPos target = home.offset(npc.route().get(state.index % npc.route().size()));
        if (mob.blockPosition().distSqr(target) <= WAYPOINT_REACHED * WAYPOINT_REACHED) {
            state.index = (state.index + 1) % npc.route().size();
            state.resumeAtTick = gameTime + npc.routePause();
            mob.getNavigation().stop();
            return;
        }
        if (mob.getNavigation().isDone()) {
            mob.getNavigation().moveTo(
                target.getX() + 0.5D,
                target.getY(),
                target.getZ() + 0.5D,
                npc.routeSpeed()
            );
        }
    }

    // reads back the "story_home:x,y,z" tag written by classify
    private static BlockPos homeOf(Entity entity) {
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(HOME_PREFIX)) {
                continue;
            }
            String[] parts = tag.substring(HOME_PREFIX.length()).split(",");
            if (parts.length != 3) {
                return null;
            }
            try {
                return new BlockPos(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
                );
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return null;
    }

    /** Parks the NPC while the player reads. */
    private static void beginConversation(Entity entity, ServerPlayer player) {
        CONVERSATIONS.put(
            entity.getUUID(),
            new Conversation(player.getUUID(), player.server.getTickCount() + CONVERSATION_TIMEOUT)
        );
        if (entity instanceof PathfinderMob mob) {
            mob.getNavigation().stop();
        }
    }

    private static boolean isBusy(Entity entity, int gameTime) {
        Conversation conversation = CONVERSATIONS.get(entity.getUUID());
        if (conversation == null) {
            return false;
        }
        if (gameTime >= conversation.expiresAtTick()) {
            CONVERSATIONS.remove(entity.getUUID());
            return false;
        }
        return true;
    }

    /** Null when Cobblemon is not installed. That switches the whole system off. */
    private static EntityType<?> npcType() {
        if (!npcTypeResolved) {
            npcType = BuiltInRegistries.ENTITY_TYPE.getOptional(NPC_ENTITY_TYPE).orElse(null);
            npcTypeResolved = true;
        }
        return npcType;
    }

    private static final class RouteState {
        private int index;
        private int resumeAtTick;
    }

    private record Conversation(UUID player, int expiresAtTick) {
    }
}
