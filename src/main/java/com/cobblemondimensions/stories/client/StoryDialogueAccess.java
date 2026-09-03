package com.cobblemondimensions.stories.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Reflection wrapper around Cobblemon's DialogueScreen. Cobblemon is not a compile
 * dependency, so every field and packet here is reached by name at runtime.
 */
public final class StoryDialogueAccess {
    // A page opts into our renderer by using one of these as its background
    // texture. Anything else returns a null theme and vanilla draws the page.
    private static final String MARKER_NAMESPACE = "cobblemon_dimensions_stories";
    private static final String CLASSIC_MARKER_PATH = "textures/gui/shop/shop_main.png";
    private static final String HAND_MARKER_PATH = "textures/gui/hand/hand_illustration_background.png";
    private static final String INPUT_PACKET =
        "com.cobblemon.mod.common.net.messages.server.dialogue.InputToDialoguePacket";

    private static boolean reportedReflectionFailure;

    private StoryDialogueAccess() {
    }

    public static Snapshot snapshot(Object screen) {
        try {
            Object dialogue = invoke(screen, "getDialogueDTO");
            Object page = invoke(dialogue, "getCurrentPageDTO");
            ResourceLocation background = (ResourceLocation) invoke(page, "getBackground");
            Theme theme = themeFor(background);
            if (theme == null) {
                return null;
            }

            @SuppressWarnings("unchecked")
            List<Component> components = (List<Component>) invoke(page, "getLines");
            List<String> lines = components.stream().map(Component::getString).toList();
            String text = String.join("\n\n", lines);

            @SuppressWarnings("unchecked")
            List<String> clientActions = (List<String>) invoke(page, "getClientActions");
            String pageId = pageIdFrom(clientActions);

            String speakerKey = (String) invoke(page, "getSpeaker");
            Component speakerName = findSpeakerName(screen, dialogue, speakerKey);

            Object input = invoke(dialogue, "getDialogueInput");
            UUID inputId = (UUID) invoke(input, "getInputId");
            String inputType = String.valueOf(invoke(input, "getInputType"));

            @SuppressWarnings("unchecked")
            List<Object> rawOptions = (List<Object>) invoke(input, "getOptions");
            List<Option> options = new ArrayList<>(rawOptions.size());
            for (Object option : rawOptions) {
                options.add(new Option(
                    (Component) invoke(option, "getText"),
                    (String) invoke(option, "getValue"),
                    (Boolean) invoke(option, "getSelectable")
                ));
            }

            return new Snapshot(
                theme,
                inputId,
                pageId,
                speakerName == null ? "" : speakerName.getString(),
                lines,
                text,
                inputType,
                List.copyOf(options)
            );
        } catch (ReflectiveOperationException | ClassCastException exception) {
            reportOnce("Could not read Cobblemon dialogue state", exception);
            return null;
        }
    }

    public static boolean sendInput(Object screen, UUID inputId, String value) {
        try {
            ClassLoader loader = screen.getClass().getClassLoader();
            Class<?> packetClass = Class.forName(INPUT_PACKET, true, loader);
            Constructor<?> constructor = packetClass.getConstructor(UUID.class, String.class);
            Object packet = constructor.newInstance(inputId, value);

            // We cannot name the parameter type at compile time, so match on name and arity.
            for (Method method : screen.getClass().getMethods()) {
                if (method.getName().equals("sendToServer") && method.getParameterCount() == 1) {
                    method.invoke(screen, packet);
                    return true;
                }
            }

            throw new NoSuchMethodException("DialogueScreen.sendToServer(packet)");
        } catch (ReflectiveOperationException exception) {
            reportOnce("Could not send Cobblemon dialogue input", exception);
            return false;
        }
    }

    private static Component findSpeakerName(Object screen, Object dialogue, String speakerKey)
        throws ReflectiveOperationException {
        if (speakerKey == null) {
            return null;
        }

        // The screen's rendered speakers win, they hold the resolved display name.
        // Fall back to the raw DTO for speakers that are not on screen.
        @SuppressWarnings("unchecked")
        Map<String, Object> renderedSpeakers = (Map<String, Object>) invoke(screen, "getSpeakers");
        Object renderedSpeaker = renderedSpeakers.get(speakerKey);
        if (renderedSpeaker != null) {
            return (Component) invoke(renderedSpeaker, "getName");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> speakers = (Map<String, Object>) invoke(dialogue, "getSpeakers");
        if (speakers == null) {
            return null;
        }

        Object speaker = speakers.get(speakerKey);
        return speaker == null ? null : (Component) invoke(speaker, "getName");
    }

    private static Theme themeFor(ResourceLocation background) {
        if (background == null || !MARKER_NAMESPACE.equals(background.getNamespace())) {
            return null;
        }
        return switch (background.getPath()) {
            case CLASSIC_MARKER_PATH -> Theme.CLASSIC;
            case HAND_MARKER_PATH -> Theme.HAND;
            default -> null;
        };
    }

    /**
     * Cobblemon 1.8 never sends the server-side page ID down to the client. Every page in
     * the datapack carries a no-op client action `v.story_page = '...'` that we parse here.
     */
    private static String pageIdFrom(List<String> clientActions) {
        String prefix = "v.story_page = '";
        for (String action : clientActions) {
            int start = action.indexOf(prefix);
            if (start < 0) {
                continue;
            }
            start += prefix.length();
            int end = action.indexOf('\'', start);
            if (end > start) {
                return action.substring(start, end);
            }
        }
        return "unknown";
    }

    private static Object invoke(Object target, String methodName)
        throws ReflectiveOperationException {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (InvocationTargetException exception) {
            // unwrap so callers see the real reflection failure
            Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            throw exception;
        }
    }

    // snapshot() runs from the render inject, so a broken lookup would log every frame.
    private static void reportOnce(String message, Exception exception) {
        if (!reportedReflectionFailure) {
            reportedReflectionFailure = true;
            System.err.println("[Cobblemon Dimensions Stories] " + message + ": " + exception);
        }
    }

    public enum Theme {
        CLASSIC,
        HAND
    }

    public record Snapshot(
        Theme theme,
        UUID inputId,
        String pageId,
        String speakerName,
        List<String> lines,
        String text,
        String inputType,
        List<Option> options
    ) {
        public String signature() {
            return inputId + "\u0000" + pageId + "\u0000" + text;
        }

        public boolean hasContinueInput() {
            return "NONE".equals(inputType) || "AUTO_CONTINUE".equals(inputType);
        }

        public boolean hasOptionInput() {
            return "OPTION".equals(inputType);
        }
    }

    public record Option(Component text, String value, boolean selectable) {
    }
}
