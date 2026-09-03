package com.cobblemondimensions.stories.mixin.client;

import com.cobblemondimensions.stories.client.walking.WalkingDialogueSession;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sends left clicks to the walking dialogue instead of swinging at the world. The
 * dialogue draws as a HUD overlay, not a Screen, so the player can keep walking during
 * it. Nothing intercepts clicks in that state, which is why these injects exist.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void cds$advanceDialogueOnAttack(CallbackInfoReturnable<Boolean> callback) {
        WalkingDialogueSession session = WalkingDialogueSession.active();
        if (session != null) {
            session.click();
            callback.setReturnValue(false);
        }
    }

    // startAttack alone isn't enough. Holding the button keeps calling continueAttack,
    // so the player would still mine through the dialogue.
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void cds$blockMiningDuringDialogue(boolean leftClickDown, CallbackInfo callback) {
        if (WalkingDialogueSession.isActive()) {
            callback.cancel();
        }
    }
}
