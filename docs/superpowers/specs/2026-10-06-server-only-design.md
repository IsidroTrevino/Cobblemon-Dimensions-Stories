# Server-only Stories migration

The user wants Cobblemon Dimensions Stories installed only on the Cobblemon
Dimensions server. Players continue to use the server's normal Cobblemon client
without the Stories JAR. The user approved replacing custom screens, HUD elements,
and keybinds with server-controlled interfaces while preserving gameplay.

## Compatibility and persistence

- Retain Minecraft 1.21.1, Fabric, Java 21, and Cobblemon 1.8.x.
- Keep quest IDs, JSON formats, stage ordering, objective rules, rewards, NPC
  routes and escorts, and operator commands.
- Keep `cobblemon_dimensions_stories_quests.dat` and its existing player, quest,
  progress, and minigame record keys. No destructive save migration.
- Server-only metadata and packaging must have no Stories client entrypoint,
  client mixins, custom networking payloads, blocks, entities, or menu registries.

## Player interfaces

- Walking dialogue uses clickable chat choices and next/cancel buttons. The
  server owns the active dialogue and verifies its page and player before
  advancing it. Players remain free to move. Leaving the NPC's range cancels
  the conversation without completing a dialogue objective.
- `/stories journal` opens a vanilla chest menu. Quest descriptions, objectives,
  progress, prerequisite state, giver, and rewards remain visible. Updates are
  announced through readable chat/actionbar messages rather than a custom HUD
  badge. Ordinary players can access journal and dialogue/minigame inputs;
  administrative mutations and targeting remain permission level 2.
- Existing shop dialogues and Emerald transactions continue through Cobblemon's
  built-in dialogue interface. Bundled text and NPC names must display without
  Stories' client language or texture resources.
- Quest markers remain per player, using vanilla entity packets; different
  players must never see another player's quest state.
- Shared chest menu controls cannot move, clone, drop, swap, or collect display
  items, including shift-click, creative clicks, number keys, and drag actions.

## Minigames

- Pattern: preserve random direction sequences, length growth from 4 to 12,
  shrinking 15-to-5-second timers, reset on wrong input, streaks, and shape
  transition at level 10. Inputs are chest menu direction buttons. All scoring
  and time limits are server-owned; existing score and reward rules remain.
- Pokewrle: preserve six attempts, generation selection 0 through 9, accepted
  species, all five hints, answer revelation, records, and rewards. Provide
  generation/menu controls and a normal-player guess command with autocomplete.
- Pokesweeper: preserve 9x9/10, 12x12/20, and 16x16/40 boards, first-dig safe
  neighbourhood, flood reveal, flagging, chording, records per difficulty, and
  existing payout rules. Display a paginated viewport in a chest menu; navigation
  must reach every cell. The server owns mines, board state, and elapsed time.
- Session completion pays out at most once. Closing, switching games,
  disconnecting, reloads, and server stop must clear or settle state deliberately.

## Validation

Run the existing build first. Add executable regression tests against the real
server game logic, localization fallback, dialogue progression, and menu click
policy. Inspect the final JAR for client code and custom networking registrations.
Build the release JAR and datapack. Start a disposable dedicated server if local
Cobblemon runtime dependencies permit it. Full acceptance requires joining with
a Cobblemon client that does not contain Stories and exercising quests, both
shops, escorts, markers, and all minigames; report any unperformed checks.
