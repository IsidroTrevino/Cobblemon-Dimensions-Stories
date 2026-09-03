# Run while standing where the NPC should appear.
# Using summon keeps this function loadable during Cobblemon's first data-registry pass;
# the NPC class is resolved from NBT when the function actually runs.
summon cobblemon:npc ~ ~ ~ {NPCClass:"cobblemon_dimensions_stories:shopkeeper",Level:1}
