package com.cobblemondimensions.stories.ui;

import com.cobblemondimensions.stories.dialogue.DialogueView;
import com.cobblemondimensions.stories.quest.QuestStatus;
import com.cobblemondimensions.stories.shop.ShopDialogue;
import com.cobblemondimensions.stories.shop.ShopView;
import com.cobblemondimensions.stories.ui.art.Overlay;
import com.cobblemondimensions.stories.ui.art.QuestArt;
import com.cobblemondimensions.stories.ui.art.ShopArt;
import com.cobblemondimensions.stories.ui.art.UiAtlas;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the interfaces the way a client with the resource pack would, without
 * starting the game. Menus are drawn as a chest menu's title over the chest's
 * background; the dialogue box is laid out as action bar text and then moved and
 * scaled as the pack's text shader does. Useful for checking a layout on several
 * window sizes at once.
 *
 * <pre>
 * OverlayPreview PACK_DIR BACKGROUND.png CHEST_BACKGROUND.png OUT_DIR
 * </pre>
 */
public final class OverlayPreview {
    private final Path pack;
    private final BufferedImage chest;
    private final Map<String, List<JsonObject>> fonts = new HashMap<>();
    private final Map<String, BufferedImage> images = new HashMap<>();

    private OverlayPreview(Path pack, BufferedImage chest) {
        this.pack = pack;
        this.chest = chest;
    }

    /** Something to draw: action bar text, or a menu title with the menu's chest rows. */
    private record Scene(Component text, int menuRows) {
        boolean menu() {
            return menuRows > 0;
        }
    }

    public static void main(String[] args) throws IOException {
        UiAtlas.load();
        OverlayPreview preview = new OverlayPreview(Path.of(args[0]), ImageIO.read(Path.of(args[2]).toFile()));
        BufferedImage world = ImageIO.read(Path.of(args[1]).toFile());
        Path out = Path.of(args[3]);
        Files.createDirectories(out);

        // window width, height and GUI scale: the user's window at 2, the same window at
        // auto (4), the default small window, and a 1440p screen at 3
        int[][] windows = {{1919, 1009, 2}, {1919, 1009, 4}, {854, 480, 2}, {2560, 1369, 3}};
        for (int[] window : windows) {
            for (Map.Entry<String, Scene> scene : scenes().entrySet()) {
                BufferedImage image = preview.render(scene.getValue(), world, window[0], window[1], window[2]);
                ImageIO.write(image, "png", out.resolve(scene.getKey() + "_" + window[0] + "x" + window[1]
                    + "_gui" + window[2] + ".png").toFile());
            }
        }
        System.out.println("Previews written to " + out);
    }

    private static Map<String, Scene> scenes() {
        Map<String, Scene> scenes = new LinkedHashMap<>();
        long late = 5_000_000_000L;
        scenes.put("dialogue", new Scene(DialogueView.compose(new DialogueView.Frame("Mara",
            "Take these. When you're ready for real work, the Curator keeps a closet full of it.",
            60, false, false, List.of(), 0, 0L, 600_000_000L, 0L), late), 0));
        scenes.put("dialogue_choices", new Scene(DialogueView.compose(new DialogueView.Frame("Mara",
            "Will you help me keep watch tonight?", 40, true, true,
            List.of("Of course.", "What's in it for me?", "Tell me more first.", "Not tonight."), 1,
            0L, 600_000_000L, 1_000_000_000L), late), 0));

        List<JournalView.Row> rows = List.of(
            new JournalView.Row("First Steps", QuestStatus.COMPLETED),
            new JournalView.Row("A Handful of Apricorns", QuestStatus.ACTIVE),
            new JournalView.Row("The Curator's Favor", QuestStatus.AVAILABLE),
            new JournalView.Row("Relics of the Closet", QuestStatus.AVAILABLE));
        scenes.put("journal", new Scene(JournalView.compose(new JournalView.Frame(rows, 0, 1,
            "The Curator needs raw material for his next batch of Poke Balls. Bring him eight apples from the "
                + "orchard and he will make it worth your while.\n\nBring 8 apples to the Curator. (3/8)",
            "Bring it back to The Curator", "6x Emerald, 3x Great Ball", 0L, 0L), late), QuestArt.MENU_ROWS));
        scenes.put("journal_new", new Scene(JournalView.compose(new JournalView.Frame(rows, 0, 2,
            "Something stirs in the closet. The Curator wants a word.", "Talk to The Curator to start",
            "A gift from the back room", 0L, 0L), late), QuestArt.MENU_ROWS));

        ShopDialogue.Page main = new ShopDialogue.Page("main", Optional.of("CURIO CLOSET"), ShopDialogue.Mood.TALK,
            List.of("A pair of gloved hands unfolds from the dark. What catches your eye?"),
            List.of(option("See the shop"), option("Talk"), option("Say goodbye")), List.of(),
            Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty());
        scenes.put("curator", new Scene(ShopView.compose(new ShopView.Frame(ShopDialogue.Theme.HAND, main,
            "The Curator", main.lines(), 999, true, -1, 1, late - 50_000_000L, 0L, 1_050_000_000L, true), late),
            ShopArt.MENU_ROWS));
        ShopDialogue.Page catalog = new ShopDialogue.Page("catalog", Optional.of("BROWSE THE SHELVES"),
            ShopDialogue.Mood.TALK, List.of(), List.of(), List.of(
                new ShopDialogue.Product(ResourceLocation.parse("cobblemon:poke_ball"), "Poké Ball", 3),
                new ShopDialogue.Product(ResourceLocation.parse("cobblemon:potion"), "Potion", 4),
                new ShopDialogue.Product(ResourceLocation.parse("cobblemon:great_ball"), "Great Ball", 6),
                new ShopDialogue.Product(ResourceLocation.parse("cobblemon:revive"), "Revive", 8)),
            Optional.of("main"), false, Optional.of("confirm"), Optional.empty(), Optional.empty());
        scenes.put("curator_catalog", new Scene(ShopView.compose(new ShopView.Frame(ShopDialogue.Theme.HAND, catalog,
            "The Curator", List.of(), 0, true, 0, -1, 0L, 0L, 0L, false), late), ShopArt.MENU_ROWS));
        ShopDialogue.Page counter = new ShopDialogue.Page("main", Optional.empty(), ShopDialogue.Mood.TALK,
            List.of("Welcome, AshKetchum! What can I do for you?"),
            List.of(option("Buy"), option("Talk"), option("Say goodbye")), List.of(),
            Optional.empty(), false, Optional.empty(), Optional.empty(), Optional.empty());
        scenes.put("mara", new Scene(ShopView.compose(new ShopView.Frame(ShopDialogue.Theme.CLASSIC, counter, "Mara",
            counter.lines(), 999, true, -1, -1, 0L, 0L, 0L, true), late), ShopArt.MENU_ROWS));
        com.cobblemondimensions.stories.minigame.PatternPreviewScenes.scenes()
            .forEach((name, text) -> scenes.put(name, new Scene(text, 0)));
        return scenes;
    }

    private static ShopDialogue.Option option(String text) {
        return new ShopDialogue.Option(text, Optional.of("main"), false, false);
    }

    // ------------------------------------------------------------------ layout

    private record Glyph(BufferedImage image, int cellX, int cellY, int cellW, int cellH, float scale, int ascent,
                         float advance) {
    }

    private record Quad(Glyph glyph, float x, float y, int rgb) {
    }

    private BufferedImage render(Scene scene, BufferedImage world, int windowW, int windowH, int guiScale) {
        int guiW = (int) Math.ceil(windowW / (double) guiScale);
        int guiH = (int) Math.ceil(windowH / (double) guiScale);
        BufferedImage canvas = new BufferedImage(windowW, windowH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(world, 0, 0, windowW, windowH, null);
        g.dispose();

        List<Object[]> runs = new ArrayList<>();
        scene.text().visit((style, content) -> {
            runs.add(new Object[] {style, content});
            return Optional.empty();
        }, Style.EMPTY);

        if (scene.menu()) {
            // a menu dims the world, draws the chest, then its title from (8, 6)
            blendAll(canvas, 0xC8101010);
            int height = 114 + scene.menuRows() * 18;
            int left = (guiW - 176) / 2;
            int top = (guiH - height) / 2;
            int split = scene.menuRows() * 18 + 17;
            blitChest(canvas, guiScale, left, top, 0, 0, 176, split);
            blitChest(canvas, guiScale, left, top + split, 0, 126, 176, 96);
            for (Quad quad : layout(runs, left + 8, top + 6, false)) {
                draw(canvas, quad, quad.x(), quad.y(), 1, guiScale, quad.rgb());
            }
            return canvas;
        }

        // the action bar centres on the total advance, on the line 68 up, with a shadow pass first
        float width = 0;
        for (Object[] run : runs) {
            for (int c : ((String) run[1]).codePoints().toArray()) {
                width += advance(glyph(((Style) run[0]).getFont(), c));
            }
        }
        float left = guiW / 2 + (int) -Math.ceil(width) / 2;
        List<Quad> quads = new ArrayList<>(layout(runs, left, guiH - 72, true));
        quads.addAll(layout(runs, left, guiH - 72, false));
        for (Quad quad : quads) {
            shaded(canvas, quad, guiW, guiH, guiScale);
        }
        return canvas;
    }

    private List<Quad> layout(List<Object[]> runs, float left, float textY, boolean shadow) {
        List<Quad> quads = new ArrayList<>();
        float pen = 0;
        float offset = shadow ? 1 : 0;
        for (Object[] run : runs) {
            Style style = (Style) run[0];
            int rgb = style.getColor() == null ? 0x404040 : style.getColor().getValue();
            if (shadow) {
                rgb = (int) ((rgb >> 16 & 0xFF) * 0.25F) << 16 | (int) ((rgb >> 8 & 0xFF) * 0.25F) << 8
                    | (int) ((rgb & 0xFF) * 0.25F);
            }
            for (int c : ((String) run[1]).codePoints().toArray()) {
                Object glyph = glyph(style.getFont(), c);
                if (glyph instanceof Glyph bitmap) {
                    quads.add(new Quad(bitmap, left + pen + offset, textY + 7 - bitmap.ascent() + offset, rgb));
                }
                pen += advance(glyph);
            }
        }
        return quads;
    }

    private static float advance(Object glyph) {
        return glyph instanceof Glyph bitmap ? bitmap.advance() : (Float) glyph;
    }

    // ----------------------------------------------------------------- drawing

    /** A glyph through the pack's text shader: marked dialogue glyphs move and scale, shadows vanish. */
    private void shaded(BufferedImage canvas, Quad quad, int guiW, int guiH, int guiScale) {
        Glyph glyph = quad.glyph();
        int overlay = Math.max(marker(glyph, Overlay.MARKER_SPRITE), marker(glyph, Overlay.MARKER_TEXT));
        if (overlay != Overlay.DIALOGUE.id() && overlay != Overlay.PATTERN.id()) {
            draw(canvas, quad, quad.x(), quad.y(), 1, guiScale, quad.rgb());
            return;
        }
        float red = (quad.rgb() >> 16 & 0xFF) / 255F;
        float green = (quad.rgb() >> 8 & 0xFF) / 255F;
        float blue = (quad.rgb() & 0xFF) / 255F;
        if (Math.max(red, Math.max(green, blue)) < 68 / 255F) {
            return;
        }
        int rgb = Math.round(decode(red) * 255) << 16 | Math.round(decode(green) * 255) << 8 | Math.round(decode(blue) * 255);
        float drawnX = guiW / 2 - 128;
        float drawnY = guiH - 65;
        float scale;
        float originY;
        if (overlay == Overlay.DIALOGUE.id()) {
            scale = Math.max(1, Math.min(3, (float) Math.floor(guiW * 0.5 / 197.0)));
            originY = guiH - 40 - 145 * scale;
        } else {
            scale = Math.max(1, Math.min(6, (float) Math.min(Math.floor(guiW * 11.0 / 3520.0), Math.floor(guiH * 4.0 / 490.0))));
            originY = (float) Math.floor((guiH * 9.0 - 20 * 56 * scale) / 20.0);
        }
        float originX = (float) Math.floor((guiW - 256 * scale) / 2);
        draw(canvas, quad, originX + (quad.x() - drawnX) * scale, originY + (quad.y() - drawnY) * scale, scale, guiScale, rgb);
    }

    /** Draws a glyph quad with its top left at GUI position (x, y), {@code scale} times its size. */
    private static void draw(BufferedImage canvas, Quad quad, float x, float y, float scale, int guiScale, int rgb) {
        Glyph glyph = quad.glyph();
        float x1 = x + glyph.cellW() * glyph.scale() * scale;
        float y1 = y + glyph.cellH() * glyph.scale() * scale;
        int px0 = Math.round(x * guiScale);
        int px1 = Math.round(x1 * guiScale);
        int py0 = Math.round(y * guiScale);
        int py1 = Math.round(y1 * guiScale);
        float red = (rgb >> 16 & 0xFF) / 255F;
        float green = (rgb >> 8 & 0xFF) / 255F;
        float blue = (rgb & 0xFF) / 255F;
        for (int py = Math.max(0, py0); py < Math.min(canvas.getHeight(), py1); py++) {
            for (int px = Math.max(0, px0); px < Math.min(canvas.getWidth(), px1); px++) {
                float u = (px + 0.5F - px0) / (px1 - px0);
                float v = (py + 0.5F - py0) / (py1 - py0);
                int tx = glyph.cellX() + Math.min(glyph.cellW() - 1, (int) (u * glyph.cellW()));
                int ty = glyph.cellY() + Math.min(glyph.cellH() - 1, (int) (v * glyph.cellH()));
                int texel = glyph.image().getRGB(tx, ty);
                float alpha = (texel >>> 24) / 255F;
                if (alpha < 0.1F) {
                    continue;
                }
                canvas.setRGB(px, py, blend(canvas.getRGB(px, py), (texel >> 16 & 0xFF) / 255F * red,
                    (texel >> 8 & 0xFF) / 255F * green, (texel & 0xFF) / 255F * blue, alpha));
            }
        }
    }

    private void blitChest(BufferedImage canvas, int guiScale, int x, int y, int u, int v, int w, int h) {
        for (int py = y * guiScale; py < (y + h) * guiScale; py++) {
            for (int px = x * guiScale; px < (x + w) * guiScale; px++) {
                if (px < 0 || py < 0 || px >= canvas.getWidth() || py >= canvas.getHeight()) {
                    continue;
                }
                int texel = chest.getRGB(u + (px / guiScale - x), v + (py / guiScale - y));
                float alpha = (texel >>> 24) / 255F;
                if (alpha > 0) {
                    canvas.setRGB(px, py, blend(canvas.getRGB(px, py), (texel >> 16 & 0xFF) / 255F,
                        (texel >> 8 & 0xFF) / 255F, (texel & 0xFF) / 255F, alpha));
                }
            }
        }
    }

    private static void blendAll(BufferedImage canvas, int argb) {
        float alpha = (argb >>> 24) / 255F;
        for (int y = 0; y < canvas.getHeight(); y++) {
            for (int x = 0; x < canvas.getWidth(); x++) {
                canvas.setRGB(x, y, blend(canvas.getRGB(x, y), (argb >> 16 & 0xFF) / 255F, (argb >> 8 & 0xFF) / 255F,
                    (argb & 0xFF) / 255F, alpha));
            }
        }
    }

    private static int blend(int under, float r, float g, float b, float alpha) {
        int nr = Math.round(r * 255 * alpha + (under >> 16 & 0xFF) * (1 - alpha));
        int ng = Math.round(g * 255 * alpha + (under >> 8 & 0xFF) * (1 - alpha));
        int nb = Math.round(b * 255 * alpha + (under & 0xFF) * (1 - alpha));
        return nr << 16 | ng << 8 | nb;
    }

    private static float decode(float channel) {
        return Math.max(0, Math.min(1, (channel - Overlay.COLOUR_FLOOR / 255F) / (Overlay.COLOUR_RANGE / 255F)));
    }

    private static int marker(Glyph glyph, int kind) {
        int pixel = glyph.image().getRGB(glyph.cellX(), glyph.cellY());
        boolean marked = (pixel >>> 24) == Overlay.MARKER_ALPHA && (pixel >> 8 & 0xFF) == kind
            && (pixel & 0xFF) == Overlay.MARKER_BLUE;
        return marked ? pixel >> 16 & 0xFF : 0;
    }

    // ------------------------------------------------------------------- fonts

    /** A bitmap glyph, or the advance of a space. */
    private Object glyph(ResourceLocation font, int codePoint) {
        for (JsonObject provider : providers(font)) {
            String type = provider.get("type").getAsString();
            if (type.equals("space")) {
                JsonElement advance = provider.getAsJsonObject("advances").get(new String(Character.toChars(codePoint)));
                if (advance != null) {
                    return advance.getAsFloat();
                }
            } else if (type.equals("bitmap")) {
                JsonArray rows = provider.getAsJsonArray("chars");
                for (int row = 0; row < rows.size(); row++) {
                    int[] chars = rows.get(row).getAsString().codePoints().toArray();
                    for (int column = 0; column < chars.length; column++) {
                        if (chars[column] == codePoint) {
                            return bitmap(provider, rows.size(), chars.length, row, column);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("No glyph U+" + Integer.toHexString(codePoint) + " in " + font);
    }

    private Glyph bitmap(JsonObject provider, int rows, int columns, int row, int column) {
        BufferedImage image = image(provider.get("file").getAsString());
        int cellW = image.getWidth() / columns;
        int cellH = image.getHeight() / rows;
        float scale = provider.get("height").getAsInt() / (float) cellH;
        int actual = 0;
        for (int x = cellW - 1; x >= 0 && actual == 0; x--) {
            for (int y = 0; y < cellH; y++) {
                if ((image.getRGB(column * cellW + x, row * cellH + y) >>> 24) != 0) {
                    actual = x + 1;
                    break;
                }
            }
        }
        return new Glyph(image, column * cellW, row * cellH, cellW, cellH, scale,
            provider.get("ascent").getAsInt(), (int) (0.5F + actual * scale) + 1);
    }

    private List<JsonObject> providers(ResourceLocation font) {
        return fonts.computeIfAbsent(font.toString(), key -> {
            Path file = pack.resolve("assets").resolve(font.getNamespace()).resolve("font").resolve(font.getPath() + ".json");
            try {
                JsonArray array = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("providers");
                List<JsonObject> list = new ArrayList<>();
                array.forEach(element -> list.add(element.getAsJsonObject()));
                return list;
            } catch (IOException exception) {
                throw new IllegalStateException("Missing font " + file, exception);
            }
        });
    }

    private BufferedImage image(String location) {
        return images.computeIfAbsent(location, key -> {
            ResourceLocation id = ResourceLocation.parse(location);
            Path file = pack.resolve("assets").resolve(id.getNamespace()).resolve("textures").resolve(id.getPath());
            try {
                return ImageIO.read(file.toFile());
            } catch (IOException exception) {
                throw new IllegalStateException("Missing texture " + file, exception);
            }
        });
    }
}
