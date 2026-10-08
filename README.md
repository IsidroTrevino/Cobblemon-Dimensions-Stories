# Cobblemon Dimensions Stories

A **server-only Fabric addon** that adds story NPCs, quests, shops, and three
arcade minigames to Cobblemon Dimensions. Install the Stories JAR on the server.
Players use the server's existing Cobblemon modpack; they do not install Stories
or a Stories resource pack.

Quests, NPCs, and dialogue remain datapack JSON. The server handles progression,
choices, minigame boards, scoring, and rewards. Player interfaces use clickable
chat, standard chest menus, vanilla quest-marker entities, and Cobblemon's
built-in shop dialogue.

## Compatibility

- Minecraft 1.21.1
- Fabric Loader 0.17.2 or newer
- Fabric API
- Cobblemon 1.8.x
- Java 21

This addon targets Fabric. Cobblemon 1.4.0 and NeoForge servers are not supported.

## Build and installation

```bash
./gradlew build
```

The build runs the executable server regression suite and checks that the JAR
has no client entrypoint, client mixins, client API references, or Stories custom
payload classes. Compilation uses Minecraft's server classes only.

- `build/libs/cobblemon-dimensions-stories-0.1.0.jar`: install in the **server's**
  `mods` directory, then restart the server. Remove any old Stories client JAR
  so its legacy interface overrides do not interfere with the new shop dialogue.
- `build/distributions/cobblemon-dimensions-stories-0.1.0-datapack.zip`: optional
  standalone story data for the world's `datapacks` directory. The server mod is
  still required. The same data is already bundled in its JAR; avoid installing
  two independently edited copies with competing definitions.

Existing quest progress and minigame records remain in
`world/data/cobblemon_dimensions_stories_quests.dat`. The file name, player UUIDs,
quest IDs, stages, counts, and best-score keys are unchanged. No reset or save
conversion is required. Back up your server world before deploying a new build.

## Playing

- Right-click a story NPC to accept or continue its quests. Dialogue appears in
  chat; click the latest choice or `[Next]`. You can keep walking. `[Cancel]`,
  walking more than 20 blocks from the speaker, changing dimensions, or two
  minutes without choosing cancels the conversation without advancing its quest.
- Use `/stories` or `/stories journal` to read the quest journal. Click a quest
  to see its giver, prerequisites, descriptions, stages, current objective,
  inventory counts, and rewards. Pagination covers larger datapacks. Chat
  notices link to newly available quests.
- **Sneak-right-click** Mara or The Curator to open their normal Cobblemon shop
  dialogue, including while a quest is active. After their story quests, normal
  interaction opens the arcade. Mara's catalog remains a demo; The Curator's
  Emerald transactions work as before.
- `!` above an NPC means a quest is available, `?` means a stage can be handed in,
  and `…` means work remains. These markers reflect each player's own progress.

The original Stories pixel-art screens, animated portraits, custom HUD badge,
mouse overlay, and journal keybind have been replaced by these server interfaces.
The artwork remains in the source repository and is excluded from the server JAR.

## Arcade controls

**Arrow Pattern:** click the Up, Down, Left, and Right buttons to repeat the
sequence shown in the menu. A wrong click resets sequence progress and the
streak without resetting the timer. Patterns grow from 4 to 12 inputs, deadlines
shrink from 15 to 5 seconds, and shape symbols begin at level 10. Closing settles
the levels already cleared. Scores and deadlines are checked by the server.

**Pokewrle:** six guesses, with generation filters from All through Generation 9.
Click `Guess in chat`, then type `/stories guess <Pokemon>`; command autocomplete
suggests implemented species in the chosen generation. The round stays open for
five minutes while you enter a guess, and the menu reopens afterward. Invalid
names or the wrong generation do not spend an attempt. Each guess shows primary
type, secondary type, egg group, evolution stage, and generation hints. Selecting
a generation starts a fresh round. Closing through `Leave Pokewrle` or Escape
abandons the round. Finished rounds reveal the answer and retain their results.

**Pokesweeper:** Easy is 9x9 with 10 TNT, Medium is 12x12 with 20, and Hard is 16x16
with 40. Left-click to dig; right-click to toggle a flag. Left-click an open number
to chord when its neighbouring flags match. The first dig and its neighbours are
safe. The chest displays nine columns and five rows; the bottom-row navigation
buttons expose the rest of the field without changing the board. Cell names show
absolute coordinates. The server measures time from the first dig and saves
fastest clears separately for each difficulty. Closing settles uncovered-cell
rewards once; restarting or changing difficulty settles the previous board.

All minigame rewards and existing record formulas are retained. Sessions cannot
claim rewards twice by closing, repeating input, or switching games.
Disconnects, server shutdown, and datapack reloads discard unfinished games;
results from games already completed remain saved.

## Commands

Ordinary players:

```text
/stories                              open the quest journal
/stories journal                      open the quest journal
/stories guess <Pokemon>              guess in an active Pokewrle round
/stories cancel                       cancel a walking conversation
```

Dialogue chat buttons use `/stories choose <token> <choice>` internally. The
server verifies the player, active page, and current quest stage; old links expire.

Operator commands require permission level 2. Optional targets default to the
sender:

```text
/stories list
/stories start <dialogue> [targets]
/stories complete <quest> [targets]
/stories reset [targets] [quest]
/stories minigame <game> [targets]
```

Games: `pattern`, `pokewrle`, `pokesweeper` (Hard), `pokesweeper_easy`, and
`pokesweeper_medium`. Administrative targeting and quest mutations remain
restricted even though journal and gameplay inputs are public.

## Development and acceptance testing

With the sibling `cobblemon` source workspace available:

```bash
./run-test-server.sh      # dedicated server with Stories
./run-test-client.sh      # normal Cobblemon client without Stories
```

Windows equivalents: `run-test-server.cmd` and `run-test-client.cmd`. Set
`COBBLEMON_SOURCE_DIR` if the sibling has another location. The server launcher
builds Stories and copies it only to `fabric/runServer/mods`. Review and accept
the Minecraft EULA yourself on first launch. The client launcher refuses to run
if its mods folder still contains a Stories JAR. Dependencies must be downloaded
before using an offline Gradle build.

Test in a disposable world, with the client connected to that dedicated server:

1. Spawn Mara with `/function cobblemon_dimensions_stories:spawn_shopkeeper` and
   The Curator with `/function cobblemon_dimensions_stories:spawn_hand_shopkeeper`.
2. Test dialogue completion/cancellation, quest prerequisites, item turn-ins,
   kill objectives, escort stages, rewards, journal counts, and relog/restart
   persistence. Old chat links must not advance a new conversation.
3. Compare quest markers with two players at different stages.
4. Sneak-right-click both shops. Give test Emeralds and buy from The Curator:
   Poké Ball (3), Potion (4), Great Ball (6), and Revive (8). Insufficient funds
   must leave the inventory unchanged. The `open_shop` and `open_hand_shop`
   functions remain available to operators.
5. Play all three games and every Pokesweeper difficulty. Test flags, chording,
   navigation, expiry, generation selection, wrong guesses, records, early close,
   switching games, disconnect, and `/reload`.
6. Try shift-click, number-key swaps, drag, drop, and creative clone clicks on
   menu icons. They must not create items or alter the player's inventory.

Run the executable regressions with `./gradlew serverRegressionTest` or
`./gradlew test`. See `docs/server-migration-validation.md` for this migration's
actual verification evidence and remaining live-test requirements.

## Editing story data

Under `src/main/resources/data/cobblemon_dimensions_stories/`:

| Folder | Contents |
| --- | --- |
| `quests/` | Definitions, stages, objectives, prerequisites, rewards |
| `story_npcs/` | Givers, idle dialogue, routes, movement |
| `walking_dialogues/` | Server chat dialogue pages, choices, minigame routes |
| `dialogues/` | Cobblemon shop dialogue and transactions |
| `npcs/` | Cobblemon NPC classes |
| `function/` | Spawn and open helpers |

Use literal text or translation components with a `fallback` for your custom
keys, because clients have no Stories language resources. Bundled keys receive
server fallbacks automatically. Custom shop backgrounds or NPC model assets must
already exist in the server's client modpack; the addon registers no client
blocks, entities, items, screens, or menu types.

Cobblemon is still accessed through its existing entity type, dialogue data, and
reflection-based species adapter. A build alone does not prove compatibility
with every mod in the live Dimensions server; run the acceptance checks against
its actual modpack before deployment.
