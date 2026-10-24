package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.dialogue.DialogueView;
import com.cobblemondimensions.stories.quest.QuestStatus;
import com.cobblemondimensions.stories.shop.ShopDialogue;
import com.cobblemondimensions.stories.shop.ShopView;
import com.cobblemondimensions.stories.ui.art.Overlay;
import com.cobblemondimensions.stories.ui.art.Sprite;
import com.cobblemondimensions.stories.ui.art.TextLine;
import com.cobblemondimensions.stories.ui.art.UiAtlas;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Checks the overlays against the rules the client and the pack's shader rely on,
 * and draws every overlay through its main states so a misplaced sprite or text
 * row fails here instead of in game.
 */
public final class OverlayRegression {
    private static int checks;

    public static void main(String[] args) {
        UiAtlas.load();
        shadowsStayBelowRealColours();
        rowsAndSpritesFitTheirFonts();
        dialogueDraws();
        journalDraws();
        shopsDraw();
        System.out.println("Overlays: " + checks + " checks passed");
    }

    /** The shader hides the action bar's quarter-bright shadow by its colour alone. */
    private static void shadowsStayBelowRealColours() {
        for (int channel = 0; channel <= 255; channel++) {
            int lifted = Overlay.encode(channel) & 0xFF;
            expect(lifted >= Overlay.COLOUR_FLOOR, "colour " + channel + " is lifted above the floor");
            expect((int) (lifted * 0.25F) < 68, "the shadow of colour " + channel + " falls below the shader's line");
        }
        expect(Overlay.encode(0xFFFFFF) == 0xFFFFFF, "white stays white, so untinted art is untouched");
    }

    /** A glyph can only be raised by as much as it is tall; the client rejects the font otherwise. */
    private static void rowsAndSpritesFitTheirFonts() {
        for (TextLine line : UiAtlas.lines()) {
            if (line.isOverlay()) {
                expect(line.eighths() - line.y() <= 3 * line.eighths(), line.fontName() + " fits its font");
                expect(line.y() >= 0, line.fontName() + " is on its sheet");
            } else if (line.pixel()) {
                expect(13 - line.y() + line.eighths() <= 3 * line.eighths(), line.fontName() + " fits its font");
            } else {
                expect(line.y() >= 13 - line.cell(), line.fontName() + " fits its font");
            }
        }
        for (Sprite sprite : UiAtlas.sprites()) {
            if (sprite.overlay() != null) {
                expect(sprite.y() >= 0, sprite.name() + " is on its sheet");
                expect(sprite.width() + 2 <= 256 && sprite.height() + 2 <= 256, sprite.name() + " fits a font page");
            }
        }
    }

    private static void dialogueDraws() {
        List<String> options = List.of("Yes, I can help.", "Tell me more first.", "Not now.", "Goodbye.");
        for (long age : new long[] {0L, 200_000_000L, 480_000_000L, 2_000_000_000L}) {
            for (int count = 0; count <= options.size(); count++) {
                DialogueView.compose(new DialogueView.Frame("Mara",
                    "Take these. When you're ready for real work, the Curator keeps a closet full of it. "
                        + "He never says what he does with it all.",
                    40, count == 0 && age < 1_000_000_000L, count > 0, options.subList(0, count), 1,
                    0L, 540_000_000L, count > 0 ? 1_000_000_000L : 0L), age);
            }
            DialogueView.compose(new DialogueView.Frame("", "Short.", 6, true, false, List.of(), 0,
                0L, 0L, 0L), age);
        }
        expect(true, "the dialogue box draws in every state");
    }

    private static void journalDraws() {
        List<JournalView.Row> rows = List.of(
            new JournalView.Row("First Steps", QuestStatus.COMPLETED),
            new JournalView.Row("A Handful of Apricorns", QuestStatus.ACTIVE),
            new JournalView.Row("The Curator's Favor", QuestStatus.AVAILABLE),
            new JournalView.Row("Relics of the Closet, Part Two of Three", QuestStatus.AVAILABLE),
            new JournalView.Row("5", QuestStatus.AVAILABLE), new JournalView.Row("6", QuestStatus.AVAILABLE),
            new JournalView.Row("7", QuestStatus.AVAILABLE), new JournalView.Row("8", QuestStatus.AVAILABLE),
            new JournalView.Row("9", QuestStatus.LOCKED));
        for (int selected = -1; selected < rows.size(); selected++) {
            for (long age : new long[] {0L, 100_000_000L, 3_000_000_000L}) {
                for (int page = -1; page < 3; page++) {
                    JournalView.compose(new JournalView.Frame(rows, page, selected,
                        "The Curator needs raw material for his next batch of Poke Balls. Bring him eight apples.",
                        "Talk to The Curator to start", "6x Emerald, 3x Great Ball", 0L, 0L), age);
                }
            }
        }
        JournalView.compose(new JournalView.Frame(List.of(), 0, -1, "", "", "", 0L, 0L), 1_000_000_000L);
        expect(true, "the journal draws with and without quests");
    }

    private static void shopsDraw() {
        ShopDialogue.Option see = new ShopDialogue.Option("See the shop", Optional.of("catalog"), false, false);
        ShopDialogue.Option bye = new ShopDialogue.Option("Say goodbye", Optional.empty(), true, false);
        ShopDialogue.Option yes = new ShopDialogue.Option("Yes", Optional.empty(), false, true);
        ShopDialogue.Page main = page("main", ShopDialogue.Mood.TALK, List.of("What catches your eye?"),
            List.of(see, bye, see, bye, see), List.of());
        ShopDialogue.Page catalog = page("catalog", ShopDialogue.Mood.TALK, List.of(), List.of(), List.of(
            new ShopDialogue.Product(ResourceLocation.parse("cobblemon:poke_ball"), "Poké Ball", 3),
            new ShopDialogue.Product(ResourceLocation.parse("cobblemon:potion"), "Potion", 4),
            new ShopDialogue.Product(ResourceLocation.parse("cobblemon:great_ball"), "Great Ball", 6),
            new ShopDialogue.Product(ResourceLocation.parse("cobblemon:revive"), "Revive", 8),
            new ShopDialogue.Product(ResourceLocation.parse("cobblemon:revive"), "Max Revive", 30)));
        ShopDialogue.Page confirm = page("confirm", ShopDialogue.Mood.TALK,
            List.of("Are you sure you want to buy", "Poké Ball", "3 Emeralds"), List.of(yes, bye), List.of());
        ShopDialogue.Page nod = page("purchase_success", ShopDialogue.Mood.YES, List.of("Purchased!"), List.of(), List.of());
        ShopDialogue.Page shake = page("purchase_failure", ShopDialogue.Mood.NO, List.of("No deal."), List.of(), List.of());
        for (ShopDialogue.Theme theme : ShopDialogue.Theme.values()) {
            for (ShopDialogue.Page page : List.of(main, catalog, confirm, nod, shake)) {
                // the Curator's first page only starts talking once the closet has opened
                for (long pageStart : new long[] {0L, 1_050_000_000L}) {
                    for (long age = 0L; age <= 2_500_000_000L; age += 50_000_000L) {
                        for (int pressed = -1; pressed < 5; pressed++) {
                            String text = String.join("\n\n", page.lines());
                            long talking = Math.max(0L, age - pageStart);
                            ShopView.compose(new ShopView.Frame(theme, page,
                                theme == ShopDialogue.Theme.HAND ? "The Curator" : "Mara", page.lines(),
                                Math.min(text.length(), (int) (talking / 30_000_000L)), talking > 1_000_000_000L,
                                pressed % 3 == 0 ? 1 : -1, pressed, age - 50_000_000L, 0L, pageStart, pageStart > 0L),
                                age);
                        }
                    }
                }
            }
        }
        expect(true, "both shops draw every kind of page");
    }

    private static ShopDialogue.Page page(String id, ShopDialogue.Mood mood, List<String> lines,
                                          List<ShopDialogue.Option> options, List<ShopDialogue.Product> catalog) {
        return new ShopDialogue.Page(id, Optional.of(id.toUpperCase()), mood, lines, options, catalog,
            Optional.of("main"), false, Optional.of("confirm"), Optional.empty(), Optional.empty());
    }

    private static void expect(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
