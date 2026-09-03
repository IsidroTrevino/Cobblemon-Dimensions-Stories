package com.cobblemondimensions.stories.mixin.client;

import com.cobblemondimensions.stories.client.StoryDialogueAccess;
import com.cobblemondimensions.stories.client.StoryDialogueAccess.Option;
import com.cobblemondimensions.stories.client.StoryDialogueAccess.Snapshot;
import com.cobblemondimensions.stories.client.StoryDialogueRenderer;
import com.cobblemondimensions.stories.client.StoryDialogueRenderer.Interaction;
import com.cobblemondimensions.stories.client.StoryDialogueRenderer.InteractionType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Takes over drawing and input for Cobblemon's DialogueScreen when the page belongs to
 * a story. Targeted by string because Cobblemon is not on the compile classpath.
 */
@Mixin(targets = "com.cobblemon.mod.common.client.gui.dialogue.DialogueScreen")
public abstract class DialogueScreenMixin extends Screen {
    @Unique
    private static final double CHARACTERS_PER_SECOND = 34.0D;

    @Unique
    private long cds$pageStartedNanos;

    @Unique
    private long cds$screenStartedNanos;

    /** Identity of the page we last set up. A change means the server advanced us. */
    @Unique
    private String cds$pageSignature = "";

    /** Player clicked mid-typewriter; dump the rest of the text immediately. */
    @Unique
    private boolean cds$forceReveal;

    /** Set once input is sent, to stop double submits while waiting on the server. */
    @Unique
    private boolean cds$inputLocked;

    /** Highlighted catalog row, -1 when nothing is picked. */
    @Unique
    private int cds$catalogSelection = -1;

    protected DialogueScreenMixin(Component title) {
        super(title);
    }

    // Two method names on purpose. A released Cobblemon jar has the intermediary
    // name (method_25426), a Cobblemon dev client has the mapped one (init). Listing
    // both lets the same addon jar run against the shipped game and the test client.
    @Inject(method = {"init", "method_25426"}, at = @At("TAIL"), remap = false)
    private void cds$initializeStoryPage(CallbackInfo callbackInfo) {
        // Null snapshot means this isn't a story dialogue. Leave the stock screen alone.
        Snapshot snapshot = StoryDialogueAccess.snapshot(this);
        if (snapshot != null) {
            long now = System.nanoTime();
            if (cds$screenStartedNanos == 0L) {
                cds$screenStartedNanos = now;
            }
            cds$ensureCurrentPage(snapshot, now);
        }
    }

    @Inject(method = {"render", "method_25394"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void cds$renderStoryPage(
        GuiGraphics graphics,
        int mouseX,
        int mouseY,
        float delta,
        CallbackInfo callbackInfo
    ) {
        Snapshot snapshot = StoryDialogueAccess.snapshot(this);
        if (snapshot == null) {
            return;
        }

        long now = System.nanoTime();
        // Page changes arrive without another init call, so the check has to happen
        // here as well. Cancelling means Cobblemon's own drawing never runs.
        cds$ensureCurrentPage(snapshot, now);
        StoryDialogueRenderer.render(
            graphics,
            snapshot,
            cds$visibleCharacters(snapshot, now),
            width,
            height,
            mouseX,
            mouseY,
            now,
            cds$pageStartedNanos,
            cds$screenStartedNanos,
            cds$inputLocked,
            cds$catalogSelection
        );
        callbackInfo.cancel();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Snapshot snapshot = StoryDialogueAccess.snapshot(this);
        if (snapshot == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        // From here down every path returns true. While a story page is up we eat all
        // clicks rather than letting them fall through to Cobblemon's widgets.
        if (button != 0 || cds$inputLocked) {
            return true;
        }

        long now = System.nanoTime();
        cds$ensureCurrentPage(snapshot, now);
        if (!StoryDialogueRenderer.isOpeningComplete(snapshot, now, cds$screenStartedNanos)) {
            return true;
        }
        // First click skips the typewriter, the next one actually picks something.
        if (cds$visibleCharacters(snapshot, now) < snapshot.text().length()) {
            cds$forceReveal = true;
            return true;
        }

        if (snapshot.hasOptionInput()) {
            Interaction interaction = StoryDialogueRenderer.interactionAt(
                snapshot,
                width,
                height,
                mouseX,
                mouseY,
                now,
                cds$pageStartedNanos,
                cds$catalogSelection
            );
            // Selecting a catalog row only moves the highlight, it doesn't submit.
            if (interaction.type() == InteractionType.SELECT_PRODUCT) {
                cds$catalogSelection = interaction.index();
                return true;
            }
            if (interaction.type() == InteractionType.OPTION) {
                Option option = snapshot.options().get(interaction.index());
                if (option.selectable()) {
                    cds$inputLocked = StoryDialogueAccess.sendInput(this, snapshot.inputId(), option.value());
                }
            }
            return true;
        }

        if (snapshot.hasContinueInput()) {
            // NONE/AUTO_CONTINUE pages carry no options, so the value is a sentinel.
            cds$inputLocked = StoryDialogueAccess.sendInput(this, snapshot.inputId(), "skip!");
        }
        return true;
    }

    /** How much of the page text the typewriter has uncovered by now. */
    @Unique
    private int cds$visibleCharacters(Snapshot snapshot, long now) {
        if (cds$forceReveal) {
            return snapshot.text().length();
        }
        long revealStart = StoryDialogueRenderer.typewriterStartNanos(
            snapshot,
            cds$pageStartedNanos,
            cds$screenStartedNanos
        );
        double seconds = Math.max(0L, now - revealStart) / 1_000_000_000.0D;
        return Math.min(snapshot.text().length(), (int) Math.floor(seconds * CHARACTERS_PER_SECOND));
    }

    /** Restarts the page animation when the signature no longer matches. */
    @Unique
    private void cds$ensureCurrentPage(Snapshot snapshot, long now) {
        if (!snapshot.signature().equals(cds$pageSignature)) {
            cds$resetPage(snapshot, now);
        }
    }

    @Unique
    private void cds$resetPage(Snapshot snapshot, long now) {
        cds$pageSignature = snapshot.signature();
        cds$pageStartedNanos = now;
        cds$forceReveal = false;
        cds$inputLocked = false;
        cds$catalogSelection = -1;
    }
}
