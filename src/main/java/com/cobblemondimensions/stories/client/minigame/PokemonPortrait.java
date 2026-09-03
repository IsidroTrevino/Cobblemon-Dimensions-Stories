package com.cobblemondimensions.stories.client.minigame;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a species using Cobblemon's own PokemonItem stack. Cobblemon is not a
 * compile-time dependency, so the lookup is reflective and returns an empty
 * stack when the mod is absent.
 */
public final class PokemonPortrait {
    private static final String SPECIES_REGISTRY = "com.cobblemon.mod.common.api.pokemon.PokemonSpecies";
    private static final String POKEMON_ITEM = "com.cobblemon.mod.common.item.PokemonItem";

    private static final Map<ResourceLocation, ItemStack> CACHE = new HashMap<>();

    private PokemonPortrait() {
    }

    /** Cached stack for the species id, or EMPTY if it could not be built. */
    public static ItemStack stackFor(ResourceLocation id) {
        return CACHE.computeIfAbsent(id, PokemonPortrait::build);
    }

    /** Draws the species horizontally centred on centerX, sitting on bottomY. */
    public static void render(GuiGraphics graphics, ResourceLocation id, int centerX, int bottomY, float scale) {
        ItemStack stack = stackFor(id);
        if (stack.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, bottomY, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        // renderItem takes the top-left of a 16x16 box, hence the -8/-16 offset.
        graphics.renderItem(stack, -8, -16);
        graphics.pose().popPose();
    }

    private static ItemStack build(ResourceLocation id) {
        try {
            Class<?> registry = Class.forName(SPECIES_REGISTRY);
            Object instance = registry.getField("INSTANCE").get(null);
            Object species = registry
                .getMethod("getByIdentifier", ResourceLocation.class)
                .invoke(instance, id);
            if (species == null) {
                return ItemStack.EMPTY;
            }

            // PokemonItem has several static `from` overloads. Pick the 4-arg one whose
            // first param accepts the species object and whose second is a Set.
            Class<?> item = Class.forName(POKEMON_ITEM);
            for (Method method : item.getMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!method.getName().equals("from")
                    || parameters.length != 4
                    || !parameters[1].equals(Set.class)
                    || !parameters[0].isInstance(species)) {
                    continue;
                }
                Object stack = method.invoke(null, species, Set.of(), 1, null);
                return stack instanceof ItemStack built ? built : ItemStack.EMPTY;
            }
            return ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }
}
