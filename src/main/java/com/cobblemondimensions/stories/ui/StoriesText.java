package com.cobblemondimensions.stories.ui;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Bundled text stays readable when the player's client has no Stories assets. */
public final class StoriesText {
    private static final Map<String, String> ENGLISH = loadEnglish();

    private StoriesText() {
    }

    public static MutableComponent translatable(String key, Object... arguments) {
        return resolve(Component.translatable(key, arguments));
    }

    public static MutableComponent resolve(Component original) {
        MutableComponent result;
        if (original.getContents() instanceof TranslatableContents translated) {
            Object[] arguments = translated.getArgs().clone();
            for (int i = 0; i < arguments.length; i++) {
                if (arguments[i] instanceof Component component) {
                    arguments[i] = resolve(component);
                }
            }
            String fallback = translated.getFallback();
            if (fallback == null) {
                fallback = ENGLISH.get(translated.getKey());
            }
            result = Component.translatableWithFallback(translated.getKey(), fallback, arguments);
        } else {
            result = original.plainCopy();
        }
        result.setStyle(original.getStyle());
        original.getSiblings().forEach(sibling -> result.append(resolve(sibling)));
        return result;
    }

    private static Map<String, String> loadEnglish() {
        String path = "/assets/cobblemon_dimensions_stories/lang/en_us.json";
        try (var stream = StoriesText.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled Stories language: " + path);
            }
            Map<String, String> translations = new Gson().fromJson(
                new InputStreamReader(stream, StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() { });
            return Map.copyOf(translations);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read bundled Stories language", exception);
        }
    }
}
