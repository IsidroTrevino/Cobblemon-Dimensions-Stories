package com.cobblemondimensions.stories.minigame;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Flattened view of Cobblemon's species registry, read entirely by reflection
 * because Cobblemon is not a compile-time dependency. Everything Pokewrle needs
 * gets copied into plain records once, so nothing else in the mod handles a
 * Cobblemon type. If Cobblemon is absent or has not synced its species yet,
 * {@link #all()} is empty and the minigame refuses to start.
 */
public final class PokedexSource {
    private static final String SPECIES_REGISTRY = "com.cobblemon.mod.common.api.pokemon.PokemonSpecies";

    /** Placeholder for any trait reflection could not read. Keeps scoring total. */
    private static final String UNKNOWN = "Unknown";

    private static List<Entry> cache = List.of();

    private PokedexSource() {
    }

    /**
     * One species cut down to what Pokewrle quizzes on. Traits only, no stats or
     * measurements.
     */
    public record Entry(
        ResourceLocation id,
        String name,
        int dex,
        int generation,
        String primaryType,
        Optional<String> secondaryType,
        String eggGroup,
        String stage
    ) {
        /** Lower-case with punctuation stripped: "Mr. Mime" and "mr mime" both match. */
        public String key() {
            return normalise(name);
        }
    }

    /** Every implemented species. Loaded on first call, then cached. */
    public static synchronized List<Entry> all() {
        if (cache.isEmpty()) {
            cache = load();
        }
        return cache;
    }

    /** Species in one generation. Zero means all of them. */
    public static List<Entry> ofGeneration(int generation) {
        if (generation <= 0) {
            return all();
        }
        return all().stream().filter(entry -> entry.generation() == generation).toList();
    }

    public static Optional<Entry> byName(String name) {
        String wanted = normalise(name);
        return all().stream().filter(entry -> entry.key().equals(wanted)).findFirst();
    }

    /**
     * Autocomplete: prefix matches first, then substring matches. Limited to
     * {@code generation} so the board never suggests a name that cannot be the
     * answer.
     */
    public static List<Entry> suggest(String typed, int generation, int limit) {
        String wanted = normalise(typed);
        if (wanted.isEmpty()) {
            return List.of();
        }
        List<Entry> starts = new ArrayList<>();
        List<Entry> contains = new ArrayList<>();
        for (Entry entry : ofGeneration(generation)) {
            if (entry.key().startsWith(wanted)) {
                starts.add(entry);
            } else if (entry.key().contains(wanted)) {
                contains.add(entry);
            }
        }
        starts.addAll(contains);
        return starts.size() > limit ? starts.subList(0, limit) : starts;
    }

    private static String normalise(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static List<Entry> load() {
        try {
            Class<?> registry = Class.forName(SPECIES_REGISTRY);
            Object instance = registry.getField("INSTANCE").get(null);
            Object raw = registry.getMethod("getImplemented").invoke(instance);
            if (!(raw instanceof Collection<?> species) || species.isEmpty()) {
                return List.of();
            }

            List<Entry> entries = new ArrayList<>(species.size());
            for (Object candidate : species) {
                readSpecies(candidate).ifPresent(entries::add);
            }
            return List.copyOf(entries);
        } catch (ReflectiveOperationException | RuntimeException cobblemonAbsent) {
            return List.of();
        }
    }

    private static Optional<Entry> readSpecies(Object species) {
        try {
            Class<?> type = species.getClass();
            String name = (String) type.getMethod("getName").invoke(species);
            int dex = (Integer) type.getMethod("getNationalPokedexNumber").invoke(species);
            ResourceLocation id =
                (ResourceLocation) type.getMethod("getResourceIdentifier").invoke(species);

            String primary = typeName(type.getMethod("getPrimaryType").invoke(species));
            Object rawSecondary = type.getMethod("getSecondaryType").invoke(species);
            Optional<String> secondary =
                rawSecondary == null ? Optional.empty() : Optional.of(typeName(rawSecondary));

            Object labels = type.getMethod("getLabels").invoke(species);
            return Optional.of(new Entry(
                id, name, dex, generationOf(labels, dex), primary, secondary,
                eggGroupOf(species), stageOf(species)));
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * First egg group by showdown id. Cobblemon hands these back in a hash set,
     * so sort before picking or the hint changes between loads.
     */
    private static String eggGroupOf(Object species) {
        try {
            Object raw = species.getClass().getMethod("getEggGroups").invoke(species);
            if (raw instanceof Collection<?> groups && !groups.isEmpty()) {
                List<String> names = new ArrayList<>(groups.size());
                for (Object group : groups) {
                    names.add((String) group.getClass().getMethod("getShowdownID").invoke(group));
                }
                names.sort(Comparator.naturalOrder());
                return names.get(0);
            }
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return UNKNOWN;
        }
        return UNKNOWN;
    }

    /** Position in the evolution line, derived from the pre-evolution and evolutions. */
    private static String stageOf(Object species) {
        try {
            Class<?> type = species.getClass();
            boolean evolves = type.getMethod("getEvolutions").invoke(species)
                instanceof Collection<?> evolutions && !evolutions.isEmpty();
            boolean evolved = type.getMethod("getPreEvolution").invoke(species) != null;
            if (evolved) {
                return evolves ? "Middle" : "Final";
            }
            return evolves ? "Basic" : "Solo";
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            return UNKNOWN;
        }
    }

    private static String typeName(Object elementalType) throws ReflectiveOperationException {
        Method getName = elementalType.getClass().getMethod("getName");
        String name = (String) getName.invoke(elementalType);
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * Reads the {@code genN} label Cobblemon tags species with. Some regional
     * forms have no such label; those fall back to the dex number.
     */
    private static int generationOf(Object labels, int dex) {
        if (labels instanceof Set<?> set) {
            for (Object label : set) {
                if (label instanceof String text && text.length() == 4 && text.startsWith("gen")) {
                    char digit = text.charAt(3);
                    if (digit >= '1' && digit <= '9') {
                        return digit - '0';
                    }
                }
            }
        }
        return generationOfDex(dex);
    }

    private static int generationOfDex(int dex) {
        // last national dex number of each generation
        int[] lastOfGeneration = {151, 251, 386, 493, 649, 721, 809, 905, 1025};
        for (int index = 0; index < lastOfGeneration.length; index++) {
            if (dex <= lastOfGeneration[index]) {
                return index + 1;
            }
        }
        return lastOfGeneration.length;
    }
}
