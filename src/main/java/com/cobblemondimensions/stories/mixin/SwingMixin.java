package com.cobblemondimensions.stories.mixin;

import com.cobblemondimensions.stories.ui.Hud;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A swing is the only thing the server hears when a player left-clicks thin air,
 * which is how the dialogue box, the shops and the journal are clicked.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SwingMixin {
    @Shadow
    public ServerPlayer player;

    // TAIL: the handler first hops onto the server thread, so only that run gets here
    @Inject(method = "handleAnimate", at = @At("TAIL"))
    private void cds$clickOverlay(ServerboundSwingPacket packet, CallbackInfo callback) {
        Hud.swing(player);
    }
}
