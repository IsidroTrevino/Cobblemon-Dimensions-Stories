package com.cobblemondimensions.stories.mixin;

import com.cobblemondimensions.stories.ui.Hud;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A seated player's client keeps sending which movement keys are held. The Pattern
 * game reads them per packet: several can arrive before one server tick, and a quick
 * tap would be gone by the time the tick looked.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PlayerInputMixin {
    @Shadow
    public ServerPlayer player;

    // TAIL: the handler first hops onto the server thread, so only that run gets here
    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void cds$readKeys(ServerboundPlayerInputPacket packet, CallbackInfo callback) {
        Hud.input(player, packet.getXxa(), packet.getZza(), packet.isJumping(), packet.isShiftKeyDown());
    }
}
