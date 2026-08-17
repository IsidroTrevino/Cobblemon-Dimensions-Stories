package com.chilinstory.mixin.client;

import com.chilinstory.client.StoryDialogueAccess;
import com.chilinstory.client.StoryDialogueAccess.Option;
import com.chilinstory.client.StoryDialogueAccess.Snapshot;
import com.chilinstory.client.StoryDialogueRenderer;
import com.chilinstory.client.StoryDialogueRenderer.Interaction;
import com.chilinstory.client.StoryDialogueRenderer.InteractionType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "com.cobblemon.mod.common.client.gui.dialogue.DialogueScreen")
public abstract class DialogueScreenMixin extends Screen {
    @Unique
    private static final double CHILIN_CHARACTERS_PER_SECOND = 34.0D;

    @Unique
    private long chilin$pageStartedNanos;

    @Unique
    private long chilin$screenStartedNanos;

    @Unique
    private String chilin$pageSignature = "";

    @Unique
    private boolean chilin$forceReveal;

    @Unique
    private boolean chilin$inputLocked;

    @Unique
    private int chilin$catalogSelection = -1;

    protected DialogueScreenMixin(Component title) {
        super(title);
    }

    // DialogueScreen overrides these Minecraft methods. A packaged Cobblemon
    // jar exposes Fabric's intermediary names, while a Cobblemon development
    // client exposes the mapped names. Supporting both keeps the same addon JAR
    // usable in the release game and this source-tree test client.
    @Inject(method = {"init", "method_25426"}, at = @At("TAIL"), remap = false)
    private void chilin$initializeStoryPage(CallbackInfo callbackInfo) {
        Snapshot snapshot = StoryDialogueAccess.snapshot(this);
        if (snapshot != null) {
            long now = System.nanoTime();
            if (chilin$screenStartedNanos == 0L) {
                chilin$screenStartedNanos = now;
            }
            chilin$ensureCurrentPage(snapshot, now);
        }
    }

    @Inject(method = {"render", "method_25394"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void chilin$renderStoryPage(
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
        chilin$ensureCurrentPage(snapshot, now);
        StoryDialogueRenderer.render(
            graphics,
            snapshot,
            chilin$visibleCharacters(snapshot, now),
            width,
            height,
            mouseX,
            mouseY,
            now,
            chilin$pageStartedNanos,
            chilin$screenStartedNanos,
            chilin$inputLocked,
            chilin$catalogSelection
        );
        callbackInfo.cancel();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Snapshot snapshot = StoryDialogueAccess.snapshot(this);
        if (snapshot == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0 || chilin$inputLocked) {
            return true;
        }

        long now = System.nanoTime();
        chilin$ensureCurrentPage(snapshot, now);
        if (!StoryDialogueRenderer.isOpeningComplete(snapshot, now, chilin$screenStartedNanos)) {
            return true;
        }
        if (chilin$visibleCharacters(snapshot, now) < snapshot.text().length()) {
            chilin$forceReveal = true;
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
                chilin$pageStartedNanos,
                chilin$catalogSelection
            );
            if (interaction.type() == InteractionType.SELECT_PRODUCT) {
                chilin$catalogSelection = interaction.index();
                return true;
            }
            if (interaction.type() == InteractionType.OPTION) {
                Option option = snapshot.options().get(interaction.index());
                if (option.selectable()) {
                    chilin$inputLocked = StoryDialogueAccess.sendInput(this, snapshot.inputId(), option.value());
                }
            }
            return true;
        }

        if (snapshot.hasContinueInput()) {
            chilin$inputLocked = StoryDialogueAccess.sendInput(this, snapshot.inputId(), "skip!");
        }
        return true;
    }

    @Unique
    private int chilin$visibleCharacters(Snapshot snapshot, long now) {
        if (chilin$forceReveal) {
            return snapshot.text().length();
        }
        long revealStart = StoryDialogueRenderer.typewriterStartNanos(
            snapshot,
            chilin$pageStartedNanos,
            chilin$screenStartedNanos
        );
        double seconds = Math.max(0L, now - revealStart) / 1_000_000_000.0D;
        return Math.min(snapshot.text().length(), (int) Math.floor(seconds * CHILIN_CHARACTERS_PER_SECOND));
    }

    @Unique
    private void chilin$ensureCurrentPage(Snapshot snapshot, long now) {
        if (!snapshot.signature().equals(chilin$pageSignature)) {
            chilin$resetPage(snapshot, now);
        }
    }

    @Unique
    private void chilin$resetPage(Snapshot snapshot, long now) {
        chilin$pageSignature = snapshot.signature();
        chilin$pageStartedNanos = now;
        chilin$forceReveal = false;
        chilin$inputLocked = false;
        chilin$catalogSelection = -1;
    }
}
