# Server-only Stories Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task by task. Independent minigame logic and dialogue/journal work may use superpowers:dispatching-parallel-agents with explicit file ownership.

**Goal:** Make the complete story and arcade playable with Stories installed only on the server.

**Architecture:** Reuse the quest engine and Cobblemon shop dialogues. Replace Stories packets and client renderers with clickable chat, vanilla chest menus, and per-player vanilla marker packets. Own minigame state and scoring on the server.

**Tech Stack:** Java 21, Minecraft 1.21.1 Mojang mappings, Fabric API, Cobblemon 1.8.x.

**Spec:** `../specs/2026-10-06-server-only-design.md`

## Global Constraints

- Retain Minecraft 1.21.1, Fabric, Java 21, and Cobblemon 1.8.x.
- Preserve existing quest IDs, definition schemas, saved-data keys, and rewards.
- No required Stories client mod or resource pack.
- Normal player inputs must not permit arbitrary quest completion, targeting another player, or reward claims.
- Work in the existing clean checkout; keep changes uncommitted for user review and do not push.

## Review Focus

- Replayed chat links must not complete another page or a newer session.
- Menu clicks must not mint display items or consume inventory/cursor items.
- Text and NPC names must be readable without client-side Stories translations.
- Every cell of the largest Pokesweeper board must be reachable.
- Disconnect, close, and game switching must not duplicate payouts or leak sessions.

### Task 1: Menu and text boundary

**Files:** `ui/StoriesMenu.java`, `ui/StoriesText.java`, test runner and build configuration.

**Interfaces:** `StoriesMenu.open(ServerPlayer, Component, Consumer<StoriesMenu>, Runnable)`;
`set(int, ItemStack, BiConsumer<Integer, ClickType>)`, `clear()`, `onTick(Runnable)`;
`StoriesMenu.icon(Item, Component, Component...)`;
`StoriesText.resolve(Component)` and `StoriesText.translatable(String, Object...)`.

- [x] Add failing regression tests for click filtering and nested translation fallbacks.
- [x] Implement a six-row vanilla chest menu whose input handler never delegates display clicks to inventory mutation.
- [x] Resolve bundled translation keys on the server, preserve styles and nested arguments, and add English fallbacks to bundled JSON components.
- [x] Run the regression tests.

### Task 2: Server minigames

**Files:** `minigame/MinigameService.java`, server board/round classes, game menu classes, minigame tests.

**Interfaces:** retain `open(ServerPlayer, Minigame)`, `setup(ServerPlayer, int)`, `guess(ServerPlayer, String)`, `forget(ServerPlayer)`; add `tick(MinecraftServer)` and `stop()`.

- [x] Write failing tests for safe first dig, flag/chord behavior, all board sizes, Pattern deadlines/reset/length progression, and round termination.
- [x] Move real board logic out of the client package and implement server-owned Pattern rounds.
- [x] Replace minigame payloads with chest menu controls and server scoring; retain species scoring and payout/save formulas.
- [x] Verify all regression tests and one-session/one-payout behavior.

### Task 3: Dialogue and journal

**Files:** `dialogue/ServerDialogueService.java`, `dialogue/DialogueCursor.java`, `ui/QuestJournal.java`, dialogue tests.

**Interfaces:** `ServerDialogueService.start(ServerPlayer, WalkingDialogue, Optional<ResourceLocation>, int)`, `choose(ServerPlayer, String, int)`, `cancel(ServerPlayer)`, `tick(MinecraftServer)`, `stop()`; `QuestJournal.open(ServerPlayer)` and `refresh(ServerPlayer)`.

- [x] Add failing tests for linear and branching dialogue, cancellation, and stale tokens.
- [x] Implement player-bound chat sessions that use authoritative quest and speaker data.
- [x] Render all quest statuses, descriptions, objectives, giver, and rewards through a paginated chest journal.
- [x] Test progression and review journal pagination and inventory safety.

### Task 4: Integration and distribution

**Files:** initializer, quest service/commands, NPC service and marker packets, Fabric metadata, resource JSON, launch scripts, README.

- [x] Wire normal-player journal, dialogue choices, and Pokewrle guesses without weakening administrative command permissions.
- [x] Remove Stories payloads, client entrypoint/mixins, and client code from the shipping mod.
- [x] Keep per-player quest markers using built-in entity packets and clean them up on disconnect/reload/stop.
- [x] Preserve built-in Cobblemon shops; fix names, language dependencies, and client-only portrait references.
- [x] Update build/install instructions and provide a dedicated-server test workflow.
- [x] Run regression tests, build artifacts, inspect the JAR, and attempt a disposable dedicated-server smoke test.
- [x] Review the complete diff and report verification limits precisely.

## Verification outcome

Implementation, regressions, packaging, artifact inspection, and independent
reviews are complete. The dedicated-server smoke test was attempted but blocked
by an unavailable Gradle wrapper download. Live acceptance with a client without
Stories, and the pinned Fabric API download/build, remain unverified. See
`../../server-migration-validation.md` for evidence and the deployment checklist.
