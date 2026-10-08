# Server migration validation — 2026-10-06

The implementation and installable artifacts are complete. Automated build and
regression checks passed. Full acceptance on a dedicated Cobblemon server with a
client that does not have Stories installed remains **not verified**.

## Build evidence

Java: Temurin 21.0.9. Gradle: 9.1.0. Minecraft: 1.21.1. Fabric Loader: 0.18.1.
The source still pins Fabric API `0.116.15+1.21.1`. That version is not in this
machine's cache and the configured network proxy cannot resolve; direct Maven
access also timed out. Validation used the cached Minecraft 1.21.1 Fabric API
`0.116.6+1.21.1` through a command-line override. The pinned dependency was not
silently changed.

The interrupted network build made Loom request intermediary mappings again.
A temporary init script set the intermediary provider's `refreshDeps` to false,
allowing use of the already cached 1.21.1 mappings. It was a validation-only
environment adjustment, not a change to shipped source or mappings.

Successful command:

```sh
env JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home \
  ./gradlew --offline -I /private/tmp/cds-cached-mappings.gradle \
  -Pfabric_api_version=0.116.6+1.21.1 build
```

This command compiled against Minecraft's server classes, ran the executable
regression suite, generated the remapped mod JAR and datapack ZIP, and ran
`verifyServerArtifact`. `./gradlew test` also invokes the executable regression
suite; the project's framework-based Test task is disabled because these are
plain Java regression entrypoints with no JUnit dependency.

## Passing regression groups

- Interface: recursive Stories text fallbacks, formatting, preservation of
  client-side Cobblemon reward translations, and menu click-type/slot filtering.
- Dialogue: 34 checks for page transitions, branches, cancellation, rotating
  player-bound tokens, replay, loops, and minigame termination.
- Journal: 341 checks for pagination, full record reachability, clamping,
  prerequisite/unread notice state, visits, and changed definitions.
- Arcade: 11 scenarios, with repeated board fixtures across all sizes and opening
  positions. Covers first-dig safety, mine counts, adjacency, flood/flags,
  successful and failed chords, all 256 Hard cells reachable, server timers,
  Pattern reset/deadlines/length/shape progression, Pokewrle generations and all
  hints, six attempts, input expiry, termination, and one-shot settlement.
- Quest compatibility: a legacy NBT fixture round-trips without changing active
  or completed quests, stage/count values, player isolation, or the Pattern,
  Pokewrle, Easy/Medium/Hard record keys. Checks that player input commands are
  available while all administrative branches remain permission level 2.
- Story data: all 20 bundled walking dialogue graphs parse with the actual
  Minecraft component codec. Branch targets exist, and NPC dialogue reaches
  Pattern, Pokewrle, and all Pokesweeper difficulties.

The reachable-game test failed before adding the missing Pokesweeper NPC routes.
The reward-name test failed before preserving its translation component.

## Artifact and source checks

- JAR metadata declares `environment: server` with only the main entrypoint.
- No client classes, client mixins, custom Stories payload classes, or client API
  references in the development or remapped release JAR.
- Original GUI textures are retained in the repository and excluded from the
  installed server JAR. Stock Cobblemon shop backgrounds are used.
- Both distributions retain six quests and 20 walking dialogue definitions.
- `git diff --check` and shell syntax checks for both launchers passed.
- The sibling Cobblemon source checkout remains unchanged.

Independent reviews checked minigame rules and integration boundaries. Confirmed
issues were fixed: reload discards active arcade sessions, item reward lore
preserves client translations, and disconnect cleanup runs on the server thread.
Unfinished games are discarded on disconnect, as in the original implementation;
completed results pay immediately and normal close/switch settles progress once.
Joining a new connection clears stale transient state. No remaining confirmed
code defect was reported by either review.

## Unperformed runtime checks

No compiled Cobblemon runtime JAR was available in the inspected project/cache.
Attempting `./gradlew --offline -PfabricOnly :fabric:assemble` in the sibling
Cobblemon checkout stopped when its uncached Gradle 8.14.3 wrapper download timed
out. Consequently, dedicated-server startup, a no-Stories client connection,
live Emerald trades, real NPC escort/navigation, per-player marker rendering,
inventory-click synchronization, and cross-mod compatibility were not exercised.
The default build with pinned Fabric API 0.116.15 also remains unverified.

Use the dedicated-server and ordinary-client workflow and acceptance checklist
in `../README.md` once dependencies are available. No live server was deployed,
no existing world was changed, and no changes were committed or pushed.
