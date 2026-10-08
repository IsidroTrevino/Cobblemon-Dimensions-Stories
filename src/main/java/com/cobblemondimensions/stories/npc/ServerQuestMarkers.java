package com.cobblemondimensions.stories.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Vanilla marker entities sent only to the player whose quest state they represent. */
public final class ServerQuestMarkers {
    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();

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
        Map<UUID, Marker> visible = viewer.markers;
        var iterator = visible.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            boolean stillWanted = wanted.keySet().stream()
                .anyMatch(entity -> entity.getUUID().equals(entry.getKey()));
            if (!stillWanted) {
                player.connection.send(new ClientboundRemoveEntitiesPacket(entry.getValue().entity.getId()));
                iterator.remove();
            }
        }
        wanted.forEach((npc, state) -> {
            Marker marker = visible.get(npc.getUUID());
            if (marker == null) {
                ArmorStand stand = new ArmorStand(player.level(), npc.getX(),
                    npc.getY() + npc.getBbHeight() + 0.4D, npc.getZ());
                stand.setInvisible(true);
                stand.setNoGravity(true);
                stand.getEntityData().set(ArmorStand.DATA_CLIENT_FLAGS, (byte) ArmorStand.CLIENT_FLAG_MARKER);
                stand.setCustomName(label(state));
                stand.setCustomNameVisible(true);
                marker = new Marker(stand, state);
                visible.put(npc.getUUID(), marker);
                player.connection.send(new ClientboundAddEntityPacket(stand.getId(), stand.getUUID(),
                    stand.getX(), stand.getY(), stand.getZ(), 0.0F, 0.0F,
                    EntityType.ARMOR_STAND, 0, Vec3.ZERO, 0.0D));
                sendData(player, stand);
            } else {
                ArmorStand stand = marker.entity;
                double y = npc.getY() + npc.getBbHeight() + 0.4D;
                if (stand.getX() != npc.getX() || stand.getY() != y || stand.getZ() != npc.getZ()) {
                    stand.setPos(npc.getX(), y, npc.getZ());
                    player.connection.send(new ClientboundTeleportEntityPacket(stand));
                }
                if (marker.state != state) {
                    marker.state = state;
                    stand.setCustomName(label(state));
                    sendData(player, stand);
                }
            }
        });
    }

    private static Component label(QuestMarker state) {
        return switch (state) {
            case AVAILABLE -> Component.literal("!").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            case READY -> Component.literal("?").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
            case IN_PROGRESS -> Component.literal("…").withStyle(ChatFormatting.GRAY);
            case NONE -> Component.empty();
        };
    }

    private static void sendData(ServerPlayer player, ArmorStand stand) {
        var data = stand.getEntityData().getNonDefaultValues();
        if (data != null) {
            player.connection.send(new ClientboundSetEntityDataPacket(stand.getId(), data));
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
        VIEWERS.clear();
    }

    private static final class Viewer {
        private final ResourceKey<Level> dimension;
        private final Map<UUID, Marker> markers = new HashMap<>();

        private Viewer(ResourceKey<Level> dimension) {
            this.dimension = dimension;
        }
    }

    private static final class Marker {
        private final ArmorStand entity;
        private QuestMarker state;

        private Marker(ArmorStand entity, QuestMarker state) {
            this.entity = entity;
            this.state = state;
        }
    }
}
