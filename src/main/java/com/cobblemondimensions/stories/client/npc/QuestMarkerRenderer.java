package com.cobblemondimensions.stories.client.npc;

import com.cobblemondimensions.stories.net.QuestMarkersS2C;
import com.cobblemondimensions.stories.npc.QuestMarker;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the "!"/"?" marker above story NPCs.
 *
 * <p>The server resends the full nearby set on a timer, so {@link #accept} just
 * replaces the map wholesale instead of diffing it.
 */
public final class QuestMarkerRenderer {
    private static final Map<Integer, QuestMarker> MARKERS = new HashMap<>();

    // x/y get negated at scale time, world axes are flipped relative to text space
    private static final float TEXT_SCALE = 0.035F;
    private static final double HEAD_CLEARANCE = 0.55D;
    private static final double MAX_DISTANCE_SQR = 48.0D * 48.0D;

    private QuestMarkerRenderer() {
    }

    public static void accept(QuestMarkersS2C payload) {
        MARKERS.clear();
        payload.markers().forEach((entityId, marker) -> MARKERS.put(entityId, QuestMarker.byId(marker)));
    }

    public static void clear() {
        MARKERS.clear();
    }

    public static void render(WorldRenderContext context) {
        ClientLevel level = context.world();
        Camera camera = context.camera();
        if (MARKERS.isEmpty() || level == null || camera == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getTimer().getGameTimeDeltaPartialTick(false);
        Font font = minecraft.font;
        MultiBufferSource buffers = context.consumers();
        if (buffers == null) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        PoseStack pose = context.matrixStack();
        float time = (level.getGameTime() + partialTick) * 0.12F;

        for (Map.Entry<Integer, QuestMarker> entry : MARKERS.entrySet()) {
            QuestMarker marker = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || marker == QuestMarker.NONE) {
                continue;
            }

            Vec3 feet = entity.getPosition(partialTick);
            if (feet.distanceToSqr(cameraPos) > MAX_DISTANCE_SQR) {
                continue;
            }

            // offset by entity id so a crowd of NPCs doesn't bob in lockstep
            double bob = Mth.sin(time + entity.getId()) * 0.06D;
            String glyph = glyph(marker);
            int width = font.width(glyph);

            pose.pushPose();
            pose.translate(
                feet.x - cameraPos.x,
                feet.y + entity.getBbHeight() + HEAD_CLEARANCE + bob - cameraPos.y,
                feet.z - cameraPos.z
            );
            pose.mulPose(camera.rotation());
            pose.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

            font.drawInBatch(
                glyph,
                -width / 2.0F,
                0.0F,
                color(marker),
                false,
                pose.last().pose(),
                buffers,
                Font.DisplayMode.NORMAL,
                0,
                LightTexture.FULL_BRIGHT
            );
            pose.popPose();
        }
    }

    private static String glyph(QuestMarker marker) {
        return marker == QuestMarker.AVAILABLE ? "!" : "?";
    }

    private static int color(QuestMarker marker) {
        return switch (marker) {
            case AVAILABLE -> 0xFFE0B24A;
            case READY -> 0xFF6FBF5B;
            default -> 0xFFB9B9C4;
        };
    }
}
