package com.cobblemondimensions.stories.mixin.client;

import com.cobblemondimensions.stories.client.walking.WalkingDialogueSession;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scroll wheel moves the dialogue choice highlight instead of the hotbar. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void cds$scrollDialogueChoices(long window, double xOffset, double yOffset, CallbackInfo callback) {
        WalkingDialogueSession session = WalkingDialogueSession.active();
        if (session == null || !session.isChoosing(System.nanoTime())) {
            return;
        }
        // Vertical wheel wins; the horizontal axis is only a fallback.
        session.scroll(yOffset != 0.0D ? yOffset : xOffset);
        callback.cancel();
    }
}
