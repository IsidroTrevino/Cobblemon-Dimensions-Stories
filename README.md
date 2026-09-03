# Cobblemon Dimensions Stories

A Fabric addon that puts a quest layer on top of Cobblemon: story NPCs you can
walk up to, dialogue that plays as a HUD overlay instead of locking you into a
screen, a quest journal, and three arcade minigames the NPCs hand out.

Quests, NPCs and dialogue are plain datapack JSON. The mod supplies the reload
listeners that read them, plus the client-side renderers, because Cobblemon's
stock dialogue screen cannot express the layered pixel-art scenes this uses.

## What's in the box

- **Quests** with multiple stages, item and kill objectives, escort/waypoint
  steps, and rewards. Progress is per player and saved with the world.
- **Story NPCs** placed from datapack functions. They show a marker overhead
  when they have something for you, and walk back to their post if they drift.
- **Walking dialogue** drawn as a HUD overlay, so you keep control of your
  character mid-conversation. Text types out, portraits animate, choices slide
  in above the bar.
- **Quest journal** on a keybind, with a badge in the corner when something is
  unread.
- **Two shopkeepers**, Mara and The Curator, with branching catalog dialogue.
  The Curator's shop runs a working Emerald transaction.
- **Three minigames**: Arrow Pattern, Pokewrle, and Pokesweeper in Easy (9x9,
  10 mines), Medium (12x12, 20) and Hard (16x16, 40). Each size keeps its own
  best-time record.

## Compatibility

- Minecraft 1.21.1
- Fabric Loader 0.17.2 or newer
- Fabric API
- Cobblemon 1.8.x
- Java 21

Cobblemon 1.4.0 will not work. It predates the NPC and dialogue APIs this
depends on.

## Build

```bash
./gradlew build
```

Two things land in `build/`:

- `libs/cobblemon-dimensions-stories-0.1.0.jar` is the mod. Install it on the
  server **and** on every client. The server reads the quest and NPC data, the
  client needs the textures and the custom renderers.
- `distributions/cobblemon-dimensions-stories-0.1.0-datapack.zip` is the story
  data on its own, if you would rather keep it in the world's `datapacks`
  folder and edit it without rebuilding. Clients still need the JAR either way.

## Running a dev client

With the sibling `cobblemon-main` source workspace checked out next to this
repo:

```bash
./run-test-client.sh     # macOS / Linux
run-test-client.cmd      # Windows
```

That builds the addon, drops the JAR into the Fabric development client and
launches Minecraft with Cobblemon. It uses the local Gradle caches, so it works
offline once the first setup has succeeded.

## Commands

All of these need permission level 2. `targets` is optional and defaults to the
sender.

```
/stories list                          list every loaded quest
/stories start <dialogue> [targets]    play a walking dialogue
/stories complete <quest> [targets]    force-complete a quest
/stories reset [targets] [quest]       wipe one quest, or all of them
/stories minigame <game> [targets]     pattern | pokewrle | pokesweeper |
                                       pokesweeper_easy | pokesweeper_medium
```

## Testing in game

Open a world with cheats on, then:

1. Stand where the shopkeeper should go and run
   `/function cobblemon_dimensions_stories:spawn_shopkeeper`. Right-click Mara.
2. For The Curator, run
   `/function cobblemon_dimensions_stories:spawn_hand_shopkeeper`.
3. `/give @s minecraft:emerald 64` for test funds, then pick `See the shop`,
   choose a product, press `Buy` and confirm.

To open a shop UI without spawning anything, use
`cobblemon_dimensions_stories:open_shop` or
`cobblemon_dimensions_stories:open_hand_shop`.

The Curator's shop takes Emeralds and ships with four sample products: Poké
Ball (3), Potion (4), Great Ball (6) and Revive (8). A successful trade removes
the exact price and gives the item. Not enough Emeralds and the inventory is
left alone.

While text is typing, the first click reveals the whole line and the next one
turns the page. The pulsing arrow only shows up on pages that actually accept a
continue click.

## Editing the story

Everything lives under `src/main/resources/data/cobblemon_dimensions_stories/`:

| Folder | What goes in it |
| --- | --- |
| `quests/` | quest definitions: stages, objectives, rewards |
| `story_npcs/` | which NPC gives which quest, and where it stands |
| `walking_dialogues/` | HUD dialogue pages, keyed by quest stage |
| `dialogues/` | the two Cobblemon shop dialogues |
| `npcs/` | Cobblemon NPC class definitions |
| `function/` | spawn and open helpers |

In the shop dialogues, a page with an `option` input becomes a button menu and
a page with a plain action string becomes a click-to-continue story page. The
Curator's catalog item IDs, display names, prices and the Emerald transaction
all live in `dialogues/hand_shopkeeper.json`.

## Notes for contributors

Cobblemon is not a compile-time dependency here. Anything touching its species
data, NPC classes or dialogue screens goes through reflection or a
string-targeted mixin. That keeps the build from breaking every time Cobblemon
reshuffles its internals, but it moves the failures to runtime, so test against
a real client before shipping.

GUI art is authored as 256x256 layered sheets and every layer is blitted whole
at `(0, 0)`. The layers self-align that way and there is no per-sprite offset
table to keep in sync.
