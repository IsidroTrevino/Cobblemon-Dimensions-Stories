package com.cobblemondimensions.stories.dialogue;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure progression and input authority for one server dialogue. */
public final class DialogueCursor {
    public enum Result { REJECTED, PAGE, COMPLETED, CANCELLED, MINIGAME }

    /** The topology copied from datapack pages, without Minecraft or display dependencies. */
    public record Page(String id, List<Optional<String>> options, Optional<String> next, boolean minigame) {
        public Page {
            Objects.requireNonNull(id);
            options = List.copyOf(options);
            Objects.requireNonNull(next);
        }
    }

    private final UUID owner;
    private final List<Page> pages;
    private int pageIndex;
    private boolean active;
    private String token;

    public DialogueCursor(UUID owner, List<Page> pages) {
        this.owner = Objects.requireNonNull(owner);
        this.pages = List.copyOf(pages);
        this.active = !pages.isEmpty();
        this.token = active ? newToken() : "";
    }

    public String token() {
        return token;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public boolean isActive() {
        return active;
    }

    public Result choose(UUID player, String submittedToken, int choice) {
        if (!active || !owner.equals(player) || !token.equals(submittedToken)) {
            return Result.REJECTED;
        }
        if (choice == -2) {
            cancel();
            return Result.CANCELLED;
        }

        Page page = pages.get(pageIndex);
        Optional<String> next;
        if (page.options().isEmpty()) {
            if (choice != -1) {
                return Result.REJECTED;
            }
            next = page.next();
        } else {
            if (choice < 0 || choice >= page.options().size()) {
                return Result.REJECTED;
            }
            next = page.options().get(choice);
        }

        if (page.minigame()) {
            close();
            return Result.MINIGAME;
        }
        int target = next.isPresent() ? indexOf(next.get()) : pageIndex + 1;
        if (target < 0 || target >= pages.size()) {
            close();
            return Result.COMPLETED;
        }

        pageIndex = target;
        token = newToken();
        return Result.PAGE;
    }

    public boolean cancel() {
        if (!active) {
            return false;
        }
        close();
        return true;
    }

    private int indexOf(String id) {
        for (int index = 0; index < pages.size(); index++) {
            if (pages.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    private void close() {
        active = false;
        token = "";
    }

    private static String newToken() {
        // UUID.randomUUID uses a cryptographically strong random generator.
        return UUID.randomUUID().toString();
    }
}
