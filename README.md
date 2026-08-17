# Chilin Story

Chilin Story is a Cobblemon NPC/dialogue test flight. The built JAR contains:

- two datapack-defined shopkeeper NPCs with branching dialogue;
- the supplied Chilin and Hand Assets pixel-art textures as client resources;
- a client-only renderer for the layered scene that Cobblemon's stock datapack screen cannot express.

Only dialogues using the Chilin Story shop or hand backgrounds use a custom
renderer. Every other Cobblemon dialogue keeps its normal UI.

## Compatibility

- Minecraft 1.21.1
- Fabric Loader 0.17.2 or newer
- Cobblemon 1.8.0 or a compatible 1.8 development build
- Java 21

The provided `Cobblemon-fabric-1.4.0+1.20.1.jar` is not compatible. Cobblemon
1.4.0 predates the NPC/dialogue APIs used by this pack.

## Quick test launch

With the sibling `cobblemon-main` source workspace available, run:

```bash
./run-test-client.sh
```

This builds the addon, installs its JAR in the Fabric development client, and
starts Minecraft 1.21.1 with Cobblemon. It uses the local Gradle caches so the
launcher works offline after the first successful setup.

## Build

```bash
./gradlew build
```

The recommended installable output is `build/libs/chilin-story-0.1.0.jar`.
Install it on both the server and every client. The server consumes the
NPC/dialogue data; clients consume the textures and layered renderer.

The build also creates
`build/distributions/chilin-story-0.1.0-datapack.zip` for a server that keeps
story data in the world's `datapacks` folder. When using that split setup, the
main JAR is still required on every client for the textures and custom screen.

## Test in game

After launching Minecraft and opening a world with commands enabled:

1. Stand where the shopkeeper should appear.
2. Run `/function chilin_story:spawn_shopkeeper`.
3. Right-click Mara.

To open the UI without spawning an NPC, run
`/function chilin_story:open_shop`.

The Hand Assets shop can be tested separately:

1. Run `/function chilin_story:spawn_hand_shopkeeper` to place The Curator, or
   `/function chilin_story:open_hand_shop` to open it directly.
2. Run `/give @s minecraft:emerald 64` to give yourself test funds.
3. Choose `See the shop`, select a product, press `Buy`, and confirm the trade.

The Hand shop uses Emeralds and ships with four sample products: Poké Ball
(3), Potion (4), Great Ball (6), and Revive (8). A successful trade removes
the exact price and gives the item, while insufficient funds leave the
inventory unchanged.

The first click while text is typing reveals the entire line. A later click
advances the dialogue. The pulsing arrow is shown only on pages that accept a
continue click. The Hand shop additionally animates its opening, title decode,
talking hand, refusal sequence, and successful-purchase bob.

## Story editing

Edit `src/main/resources/data/chilin_story/dialogues/shopkeeper.json` for Mara,
or `src/main/resources/data/chilin_story/dialogues/hand_shopkeeper.json` for
The Curator. Pages with an `option` input become button menus. Pages with a
normal action string become click-to-continue story pages. The Hand shop's
catalog item IDs, display names, prices, and Emerald transaction live in its
dialogue file.

The character alternates between the casual and talking frames while letters
appear, then switches to the smiling frame when the page is fully visible.
