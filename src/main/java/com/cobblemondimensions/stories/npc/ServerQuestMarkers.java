package com.cobblemondimensions.stories.npc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The "!" and "?" floating over quest givers. They are text displays that exist
 * only on the one player's client, since each player's quest state differs.
 */
public final class ServerQuestMarkers {
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    private static final Map<Integer, CompoundTag> STYLES = new HashMap<>();
    private static final double HEAD_CLEARANCE = 0.55D;
    /** The old marker drew its glyph at 0.035 blocks a pixel; text displays use 0.025. */
    private static final float SCALE = 1.4F;
    private static final float BOB = 0.06F;
    /** Matches how often the NPC scan calls {@link #update}. */
    private static final int BOB_TICKS = 10;

    private ServerQuestMarkers() {
    }

    public static void update(ServerPlayer player, Map<Entity, QuestMarker> wanted) {
        Viewer viewer = VIEWERS.get(player.getUUID());
        if (viewer != null && viewer.dimension != player.level().dimension()) {
            clear(player);
            viewer = null;
        }
        if (viewer == null) {
            viewer = new Viewer(player.level().dimension());
            VIEWERS.put(player.getUUID(), viewer);
        }
        Set<UUID> wantedIds = new HashSet<>();
        wanted.keySet().forEach(entity -> wantedIds.add(entity.getUUID()));
        Map<UUID, Marker> visible = viewer.markers;
        var iterator = visible.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!wantedIds.contains(entry.getKey())) {
                player.connection.send(new ClientboundRemoveEntitiesPacket(entry.getValue().entity.getId()));
                iterator.remove();
            }
        }
        viewer.bobUp = !viewer.bobUp;
        boolean up = viewer.bobUp;
        wanted.forEach((npc, state) -> {
            Marker marker = visible.get(npc.getUUID());
            double y = npc.getY() + npc.getBbHeight() + HEAD_CLEARANCE;
            if (marker == null) {
                Display.TextDisplay display = EntityType.TEXT_DISPLAY.create(player.level());
                if (display == null) {
                    return;
                }
                display.load(styleOf(state, up, player));
                display.setPos(npc.getX(), y, npc.getZ());
                marker = new Marker(display, state);
                visible.put(npc.getUUID(), marker);
                player.connection.send(new ClientboundAddEntityPacket(display.getId(), display.getUUID(),
                    display.getX(), display.getY(), display.getZ(), 0.0F, 0.0F,
                    EntityType.TEXT_DISPLAY, 0, Vec3.ZERO, 0.0D));
                sendData(player, display);
                return;
            }
            Display.TextDisplay display = marker.entity;
            if (display.getX() != npc.getX() || display.getY() != y || display.getZ() != npc.getZ()) {
                display.setPos(npc.getX(), y, npc.getZ());
                player.connection.send(new ClientboundTeleportEntityPacket(display));
            }
            // Each update eases the marker to the other end of its bob, so it floats
            // up and down on its own between server scans.
            marker.state = state;
            display.load(styleOf(state, up, player));
            display.setPos(npc.getX(), y, npc.getZ());
            sendData(player, display);
        });
    }

    /**
     * The marker as the client mod drew it: a plain coloured "!" or "?" with no
     * nameplate box, always lit, and scaled to the old size.
     */
    /** The same four marker looks are sent over and over, so each is built once. */
    private static CompoundTag styleOf(QuestMarker state, boolean up, ServerPlayer player) {
        return STYLES.computeIfAbsent(state.ordinal() * 2 + (up ? 1 : 0), key -> style(state, up, player));
    }

    private static CompoundTag style(QuestMarker state, boolean up, ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(label(state), player.registryAccess()));
        tag.putInt("background", 0);
        tag.putBoolean("shadow", false);
        tag.putString("billboard", "center");
        // the game logs an error for every text display loaded without one
        tag.putString("alignment", "center");
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", 15);
        brightness.putInt("sky", 15);
        tag.put("brightness", brightness);
        tag.putInt("interpolation_duration", BOB_TICKS);
        tag.putInt("start_interpolation", 0);
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0.0F, up ? BOB : -BOB, 0.0F));
        transformation.put("left_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        transformation.put("scale", floats(SCALE, SCALE, SCALE));
        transformation.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transformation);
        return tag;
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private static Component label(QuestMarker state) {
        return switch (state) {
            case AVAILABLE -> Component.literal("!").withColor(0xE0B24A);
            case READY -> Component.literal("?").withColor(0x6FBF5B);
            case IN_PROGRESS -> Component.literal("?").withColor(0xB9B9C4);
            case NONE -> Component.empty();
        };
    }

    private static void sendData(ServerPlayer player, Entity marker) {
        var data = marker.getEntityData().getNonDefaultValues();
        if (data != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(marker.getId(), data));
        }
    }

    public static void clear(ServerPlayer player) {
        Viewer viewer = VIEWERS.remove(player.getUUID());
        if (viewer != null && !viewer.markers.isEmpty()) {
            player.connection.send(new ClientboundRemoveEntitiesPacket(viewer.markers.values().stream()
                .mapToInt(marker -> marker.entity.getId()).toArray()));
        }
    }

    public static void stop() {
        STYLES.clear();
        VIEWERS.clear();
    }

    private static final class Viewer {
        private final ResourceKey<Level> dimension;
        private final Map<UUID, Marker> markers = new HashMap<>();
        private boolean bobUp;

        private Viewer(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final class Marker {
        private final Display.TextDisplay entity;
        private QuestMarker state;

        private Marker(Display.TextDisplay entity, QuestMarker state) {
            this.entity = entity;
            this.state = state;
        }
    }
}
