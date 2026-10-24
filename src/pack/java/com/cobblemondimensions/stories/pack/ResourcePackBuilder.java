package com.cobblemondimensions.stories.pack;

import com.cobblemondimensions.stories.ui.art.Art;
import com.cobblemondimensions.stories.ui.art.HudFont;
import com.cobblemondimensions.stories.ui.art.Overlay;
import com.cobblemondimensions.stories.ui.art.Sprite;
import com.cobblemondimensions.stories.ui.art.TextLine;
import com.cobblemondimensions.stories.ui.art.UiAtlas;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Builds the resource pack the Stories menus need.
 *
 * <p>Everything the pack contains is described by {@link UiAtlas}: each sprite
 * becomes a PNG and a character in the {@code ui} font, and each text row becomes
 * a small font that draws letters at a fixed height. The same catalogue is what
 * the server uses to place those characters, so the two cannot drift apart. The
 * overlays also need the pack's text shader, which places and scales their glyphs
 * (see {@link Overlay}).
 *
 * <pre>
 * ResourcePackBuilder --art DIR --out DIR --zip FILE
 * </pre>
 */
public final class ResourcePackBuilder {
    private static final String NAMESPACE = "cobblemon_dimensions_stories";
    /** pack_format for Minecraft 1.21 and 1.21.1. */
    private static final int PACK_FORMAT = 34;
    /** The title text starts six pixels below the menu's top edge; a glyph shifts up from there. */
    private static final int TITLE_BASELINE = 13;
    private static final String CARRIER = "knowledge_book";
    /** Enough alpha to count as ink when the client measures a glyph, too little to be drawn. */
    private static final int MARKER_ALPHA = 16;
    private static final String SHADER = "shaders/core/rendertype_text.vsh";
    /** The pixel font for menu titles; unmarked, so the text shader leaves it alone. */
    private static final String MENU_SHEET = "text_menu";

    /** The labels the client prints under a chest's rows; the pack blanks it so it cannot show through. */
    private static final String[] LANGUAGES = (
        "en_us af_za ar_sa ast_es az_az ba_ru bar be_by be_latn bg_bg br_fr brb bs_ba ca_es cs_cz cv_cu cy_gb da_dk "
        + "de_at de_ch de_de el_gr en_au en_ca en_gb en_nz en_pt en_ud enp enws eo_uy es_ar es_cl es_ec es_es es_mx "
        + "es_uy es_ve esan et_ee eu_es fa_ir fi_fi fil_ph fo_fo fr_ca fr_ch fr_fr fra_de fur_it fy_nl ga_ie gd_gb "
        + "gl_es go_fr got_de hal_ua haw_us he_il hi_in hn_no hr_hr hu_hu hy_am id_id ig_ng io_en is_is isv it_it "
        + "ja_jp jbo_en ka_ge kk_kz kn_in ko_kr ksh kw_gb ky_kg la_la lb_lu li_li lmo lo_la lol_us lt_lt lv_lv lzh "
        + "mk_mk mn_mn ms_my mt_mt nah nds_de nl_be nl_nl nn_no no_no oc_fr ovd pl_pl pls pt_br pt_pt qcb_es qid "
        + "qya_aa ro_ro rpr ru_ru ry_ua sah_sah se_no sk_sk sl_si so_so sq_al sr_cs sr_sp sv_se sxu szl ta_in "
        + "th_th tl_ph tlh_aa tok tr_tr tt_ru tzo_mx uk_ua uz_uz val_es vec_it vi_vn vp_vl vro yi_de yo_ng zh_cn "
        + "zh_hk zh_tw zlm_arab").split(" ");

    /** Rows of the game's own glyph sheet, so the pack's text fonts can reuse its art. */
    private static final String[] ASCII_ROWS = {
        null, null, " !\"#$%&'()*+,-./", "0123456789:;<=>?", "@ABCDEFGHIJKLMNO", "PQRSTUVWXYZ[\\]^_",
        "`abcdefghijklmno", "pqrstuvwxyz{|}~\u0000", null, null, null, null, null, null, null, null,
    };
    private static final String[] ACCENTED_ROWS = {
        "ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏ",
        "ÐÑÒÓÔÕÖÙÚÛÜÝàáâã",
        "äåæçìíîïñòóôõöùú",
        "ûüýÿĀāĂăĄąĆćĈĉĊċ",
    };
    private static final String ACCENTED_ROW_15 =
        "ṠṡṪṫẀẁẂẃẄẅỲỳèéêë";
    private static final int ACCENTED_TOTAL_ROWS = 75;
    private static final String NONLATIN_ROW_0 =
        "¡‰­·₴≠¿×ØÞһðøþΑΒ";
    private static final String NONLATIN_ROW_10 =
        "өʻˌ;ĸẞß₽€ѢѣѴѵӀѲѳ";
    private static final int NONLATIN_TOTAL_ROWS = 67;

    private final Path art;
    private final Path out;
    private final Map<String, BufferedImage> sheets = new HashMap<>();

    private ResourcePackBuilder(Path art, Path out) {
        this.art = art;
        this.out = out;
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
        Path art = null;
        Path out = null;
        Path zip = null;
        for (int i = 0; i < args.length - 1; i += 2) {
            switch (args[i]) {
                case "--art" -> art = Path.of(args[i + 1]);
                case "--out" -> out = Path.of(args[i + 1]);
                case "--zip" -> zip = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("Unknown option " + args[i]);
            }
        }
        if (art == null || out == null) {
            throw new IllegalArgumentException("Usage: --art DIR --out DIR [--zip FILE]");
        }
        new ResourcePackBuilder(art, out).build();
        if (zip != null) {
            pack(out, zip);
        }
    }

    private void build() throws IOException {
        UiAtlas.load();
        if (Files.exists(out)) {
            try (Stream<Path> walk = Files.walk(out)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Path assets = out.resolve("assets").resolve(NAMESPACE);
        Files.createDirectories(assets.resolve("textures/ui"));
        Files.createDirectories(assets.resolve("textures/item"));
        Files.createDirectories(assets.resolve("font"));
        Files.createDirectories(assets.resolve("models/item"));

        write(out.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":" + PACK_FORMAT
            + ",\"description\":\"Cobblemon Dimensions Stories interface\"}}");
        writeSprites(assets);
        writeTextFonts(assets);
        writeIcons(assets);
        writeLanguage(out);
        writeShader(out);
    }

    // ------------------------------------------------------------- sprites

    private void writeSprites(Path assets) throws IOException {
        List<String> providers = new ArrayList<>();
        providers.add("{\"type\":\"space\",\"advances\":{" + spacing() + "}}");

        for (Sprite sprite : UiAtlas.sprites()) {
            BufferedImage picture = render(sprite);
            if (picture.getWidth() != sprite.width() || picture.getHeight() != sprite.height()) {
                throw new IllegalStateException("Sprite " + sprite.name() + " renders "
                    + picture.getWidth() + "x" + picture.getHeight() + " but is declared "
                    + sprite.width() + "x" + sprite.height());
            }
            if (sprite.overlay() != null) {
                providers.add(writeOverlaySprite(assets, sprite, picture));
                continue;
            }
            // A glyph can be raised by at most its own height; pad short pictures
            // with clear rows below so they can reach the top of the menu.
            int height = Math.max(sprite.height(), TITLE_BASELINE - sprite.y());
            BufferedImage padded = new BufferedImage(sprite.width(), height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = padded.createGraphics();
            g.drawImage(picture, 0, 0, null);
            g.dispose();
            // The client sizes a glyph by its last column with any ink, and the server
            // assumes the full width. A faint pixel in the corner makes the two agree;
            // it is below the text shader's cut-off, so it never shows.
            int corner = padded.getRGB(sprite.width() - 1, 0);
            if ((corner >>> 24) < MARKER_ALPHA) {
                padded.setRGB(sprite.width() - 1, 0, MARKER_ALPHA << 24);
            }
            String file = height == sprite.height() ? sprite.name() : sprite.name() + "_p" + height;
            ImageIO.write(padded, "png", assets.resolve("textures/ui/" + file + ".png").toFile());
            providers.add("{\"type\":\"bitmap\",\"file\":\"" + NAMESPACE + ":ui/" + file + ".png\","
                + "\"ascent\":" + (TITLE_BASELINE - sprite.y()) + ",\"height\":" + height
                + ",\"chars\":[" + quote(String.valueOf(sprite.glyph())) + "]}");
        }
        write(assets.resolve("font/" + UiAtlas.SPRITE_FONT + ".json"),
            "{\"providers\":[" + String.join(",", providers) + "]}");
    }

    /**
     * An overlay sprite: the picture inside a one pixel clear frame whose corners
     * hold the overlay's markers. The top right marker also makes the client measure
     * the full width. The glyph hangs below the action bar's line by its sheet row;
     * the shader moves it to its place from there.
     */
    private static String writeOverlaySprite(Path assets, Sprite sprite, BufferedImage picture) throws IOException {
        int width = sprite.width() + 2;
        int height = sprite.height() + 2;
        BufferedImage framed = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = framed.createGraphics();
        g.drawImage(picture, 1, 1, null);
        g.dispose();
        int marker = sprite.overlay().marker(false);
        framed.setRGB(0, 0, marker);
        framed.setRGB(0, height - 1, marker);
        framed.setRGB(width - 1, height - 1, marker);
        framed.setRGB(width - 1, 0, marker);
        ImageIO.write(framed, "png", assets.resolve("textures/ui/" + sprite.name() + ".png").toFile());
        int ascent = 1 - sprite.y();
        if (ascent > height) {
            throw new IllegalStateException(sprite.name() + " sits above its overlay's sheet");
        }
        return "{\"type\":\"bitmap\",\"file\":\"" + NAMESPACE + ":ui/" + sprite.name() + ".png\","
            + "\"ascent\":" + ascent + ",\"height\":" + height
            + ",\"chars\":[" + quote(String.valueOf(sprite.glyph())) + "]}";
    }

    private BufferedImage render(Sprite sprite) throws IOException {
        return render(sprite, sprite.art());
    }

    private BufferedImage render(Sprite sprite, Art source) throws IOException {
        if (source instanceof Art.Faded faded) {
            BufferedImage image = render(sprite, faded.base());
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int argb = image.getRGB(x, y);
                    int alpha = Math.round((argb >>> 24) * faded.alpha() / 255.0F);
                    image.setRGB(x, y, alpha << 24 | argb & 0xFFFFFF);
                }
            }
            return image;
        }
        BufferedImage image = new BufferedImage(source.width(), source.height(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            switch (source) {
                case Art.Plate plate -> {
                    g.setComposite(java.awt.AlphaComposite.Src);
                    g.setColor(new Color(plate.fill(), true));
                    g.fillRect(0, 0, plate.w(), plate.h());
                    if ((plate.border() >>> 24) != 0) {
                        g.setColor(new Color(plate.border(), true));
                        g.drawRect(0, 0, plate.w() - 1, plate.h() - 1);
                    }
                }
                case Art.Rects rects -> {
                    g.setComposite(java.awt.AlphaComposite.Src);
                    for (int[] rect : rects.rects()) {
                        g.setColor(new Color(rect[4], true));
                        g.fillRect(rect[0], rect[1], rect[2], rect[3]);
                    }
                }
                case Art.Faded faded -> throw new IllegalStateException("handled above");
                case Art.Composite composite -> {
                    BufferedImage base = new BufferedImage(composite.w(), composite.h(), BufferedImage.TYPE_INT_ARGB);
                    Graphics2D bg = base.createGraphics();
                    for (Art.Piece piece : composite.pieces()) {
                        if (piece.sw() <= 0 || piece.sh() <= 0) {
                            continue;
                        }
                        BufferedImage sheet = sheet(piece.sheet());
                        if (piece.sx() + piece.sw() > sheet.getWidth() || piece.sy() + piece.sh() > sheet.getHeight()) {
                            throw new IllegalStateException(sprite.name() + " reads outside " + piece.sheet());
                        }
                        BufferedImage part = sheet.getSubimage(piece.sx(), piece.sy(), piece.sw(), piece.sh());
                        bg.drawImage(part, piece.dx(), piece.dy(), null);
                    }
                    bg.dispose();
                    // nearest-neighbour: every pixel becomes a scale x scale block
                    for (int y = 0; y < image.getHeight(); y++) {
                        for (int x = 0; x < image.getWidth(); x++) {
                            image.setRGB(x, y, base.getRGB(x / composite.scale(), y / composite.scale()));
                        }
                    }
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private BufferedImage sheet(String name) throws IOException {
        BufferedImage cached = sheets.get(name);
        if (cached != null) {
            return cached;
        }
        Path file = art.resolve(name);
        if (!Files.exists(file)) {
            throw new IOException("Missing art sheet " + file);
        }
        BufferedImage loaded = ImageIO.read(file.toFile());
        BufferedImage argb = new BufferedImage(loaded.getWidth(), loaded.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = argb.createGraphics();
        g.drawImage(loaded, 0, 0, null);
        g.dispose();
        sheets.put(name, argb);
        return argb;
    }

    // ---------------------------------------------------------------- text

    /**
     * One font per text row. Menu title rows reuse the game's own glyph sheets in
     * place, with the ascent chosen so the top of the glyph cell lands on the
     * requested row. Overlay rows draw {@link HudFont} from a sheet marked for
     * their overlay.
     */
    private void writeTextFonts(Path assets) throws IOException {
        for (Overlay overlay : Overlay.values()) {
            writePixelSheet(assets, "text_" + overlay.id(), overlay.marker(true));
        }
        writePixelSheet(assets, MENU_SHEET, 0);
        for (TextLine line : UiAtlas.lines()) {
            if (line.pixel()) {
                writePixelFont(assets, line);
                continue;
            }
            int halves = line.halves();
            int ascent = TITLE_BASELINE - line.y();
            // accented glyphs sit a little higher in their taller cell than plain ones
            int accentedAscent = ascent + Math.round(1.5F * halves);
            List<String> providers = new ArrayList<>();
            providers.add("{\"type\":\"space\",\"advances\":{\" \":" + line.spaceAdvance() + "}}");
            providers.add(bitmap("minecraft:font/ascii.png", ascent, 4 * halves, rows(ASCII_ROWS, 16)));
            String[] accented = new String[ACCENTED_TOTAL_ROWS];
            System.arraycopy(ACCENTED_ROWS, 0, accented, 0, ACCENTED_ROWS.length);
            accented[15] = ACCENTED_ROW_15;
            providers.add(bitmap("minecraft:font/accented.png", Math.min(accentedAscent, 6 * halves), 6 * halves, rows(accented, 16)));
            String[] nonlatin = new String[NONLATIN_TOTAL_ROWS];
            nonlatin[0] = NONLATIN_ROW_0;
            nonlatin[10] = NONLATIN_ROW_10;
            providers.add(bitmap("minecraft:font/nonlatin_european.png", ascent, 4 * halves, rows(nonlatin, 16)));
            write(assets.resolve("font/" + line.fontName() + ".json"),
                "{\"providers\":[" + String.join(",", providers) + "]}");
        }
    }

    /**
     * {@link HudFont} on one sheet: one per overlay, marked for it, and an unmarked
     * one for menu titles. Each letter sits in a tall clear cell; markers go in its
     * left corners, where they cannot touch a letter or change how wide the client
     * measures it.
     */
    private static void writePixelSheet(Path assets, String name, int marker) throws IOException {
        List<Character> characters = HudFont.characters();
        int columns = 16;
        int rows = (characters.size() + columns - 1) / columns;
        BufferedImage sheet = new BufferedImage(columns * HudFont.SHEET_CELL_WIDTH,
            rows * HudFont.SHEET_CELL_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < characters.size(); index++) {
            List<String> cell = HudFont.cell(characters.get(index));
            int left = (index % columns) * HudFont.SHEET_CELL_WIDTH;
            int top = (index / columns) * HudFont.SHEET_CELL_HEIGHT;
            if (cell.getFirst().length() > HudFont.SHEET_CELL_WIDTH) {
                throw new IllegalStateException("Letter wider than its cell: " + characters.get(index));
            }
            for (int y = 0; y < cell.size(); y++) {
                for (int x = 0; x < cell.get(y).length(); x++) {
                    if (cell.get(y).charAt(x) == '#') {
                        sheet.setRGB(left + x, top + HudFont.SHEET_TOP_ROWS + y, 0xFFFFFFFF);
                    }
                }
            }
            if (marker != 0) {
                sheet.setRGB(left, top, marker);
                sheet.setRGB(left, top + HudFont.SHEET_CELL_HEIGHT - 1, marker);
            }
        }
        ImageIO.write(sheet, "png", assets.resolve("textures/ui/" + name + ".png").toFile());
    }

    /**
     * A pixel-font row at its size. An overlay row hangs below the action bar's line
     * so the capitals start {@code y} rows down the overlay's sheet; a menu row is
     * lifted from the title's line so they start {@code y} pixels below the top.
     */
    private static void writePixelFont(Path assets, TextLine line) throws IOException {
        List<Character> characters = HudFont.characters();
        String[] rows = new String[(characters.size() + 15) / 16];
        for (int row = 0; row < rows.length; row++) {
            StringBuilder text = new StringBuilder();
            for (int column = 0; column < 16; column++) {
                int index = row * 16 + column;
                text.append(index < characters.size() ? characters.get(index) : '\u0000');
            }
            rows[row] = text.toString();
        }
        int height = HudFont.SHEET_CELL_HEIGHT * line.eighths() / 8;
        // eight sheet rows above the capitals, at the row's size
        int ascent = line.isOverlay() ? line.eighths() - line.y() : TITLE_BASELINE - line.y() + line.eighths();
        if (ascent > height) {
            throw new IllegalStateException(line.fontName() + " sits above its overlay's sheet");
        }
        write(assets.resolve("font/" + line.fontName() + ".json"), "{\"providers\":["
            + "{\"type\":\"space\",\"advances\":{\" \":" + line.spaceAdvance() + "," + spacing() + "}},"
            + bitmap(NAMESPACE + ":ui/" + (line.isOverlay() ? "text_" + line.overlay().id() : MENU_SHEET) + ".png",
                ascent, height, rows(rows, 16)) + "]}");
    }

    /**
     * Copies the text shader into the pack after checking that the numbers it reads
     * from the markers are the ones the server and this builder use.
     */
    private static void writeShader(Path root) throws IOException {
        String source;
        try (InputStream in = ResourcePackBuilder.class.getResourceAsStream("/" + SHADER)) {
            if (in == null) {
                throw new IOException("Missing " + SHADER + " on the pack tool's classpath");
            }
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("int MARKER_ALPHA", String.valueOf(Overlay.MARKER_ALPHA));
        expected.put("int MARKER_SPRITE", String.valueOf(Overlay.MARKER_SPRITE));
        expected.put("int MARKER_TEXT", String.valueOf(Overlay.MARKER_TEXT));
        expected.put("int MARKER_BLUE", String.valueOf(Overlay.MARKER_BLUE));
        expected.put("int TEXT_CELL", String.valueOf(HudFont.SHEET_CELL_WIDTH));
        expected.put("float COLOUR_FLOOR", Overlay.COLOUR_FLOOR + ".0 / 255.0");
        expected.put("float COLOUR_RANGE", Overlay.COLOUR_RANGE + ".0 / 255.0");
        for (Overlay overlay : Overlay.values()) {
            expected.put("int " + overlay.name(), String.valueOf(overlay.id()));
        }
        expected.forEach((name, value) -> {
            if (!source.contains("const " + name + " = " + value + ";")) {
                throw new IllegalStateException(SHADER + " does not declare " + name + " = " + value);
            }
        });
        write(root.resolve("assets/minecraft/" + SHADER), source);
    }

    /** The cursor-moving characters; overlay letters carry them to sit where scaled text would. */
    private static String spacing() {
        StringBuilder spacing = new StringBuilder();
        UiAtlas.spacing().forEach((glyph, advance) -> {
            if (!spacing.isEmpty()) {
                spacing.append(',');
            }
            spacing.append(quote(String.valueOf(glyph))).append(':').append(advance);
        });
        return spacing.toString();
    }

    private static String bitmap(String file, int ascent, int height, String rows) {
        return "{\"type\":\"bitmap\",\"file\":\"" + file + "\",\"ascent\":" + ascent + ",\"height\":" + height
            + ",\"chars\":[" + rows + "]}";
    }

    /** A sheet's rows, with empty cells written as NUL so only the wanted glyphs are claimed. */
    private static String rows(String[] rows, int width) {
        List<String> encoded = new ArrayList<>();
        for (String row : rows) {
            encoded.add(quote(row == null ? "\u0000".repeat(width) : row));
        }
        return String.join(",", encoded);
    }

    // --------------------------------------------------------------- icons

    private void writeIcons(Path assets) throws IOException {
        BufferedImage blank = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(blank, "png", assets.resolve("textures/item/blank.png").toFile());
        write(assets.resolve("models/item/blank.json"),
            "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + NAMESPACE + ":item/blank\"}}");

        Path minecraft = out.resolve("assets/minecraft/models/item");
        Files.createDirectories(minecraft);
        write(minecraft.resolve(CARRIER + ".json"),
            "{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/" + CARRIER + "\"},"
                + "\"overrides\":[{\"predicate\":{\"custom_model_data\":1},\"model\":\"" + NAMESPACE + ":item/blank\"}]}");
    }

    private void writeLanguage(Path root) throws IOException {
        Path lang = root.resolve("assets/minecraft/lang");
        Files.createDirectories(lang);
        for (String code : LANGUAGES) {
            write(lang.resolve(code + ".json"), "{\"container.inventory\":\"\"}");
        }
    }

    // --------------------------------------------------------------- files

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** JSON string with every non-ASCII character escaped, so private-use glyphs survive any editor. */
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    /** Zips the pack with sorted entries and a fixed timestamp, so identical input gives an identical file. */
    private static void pack(Path root, Path zip) throws IOException {
        Files.createDirectories(zip.getParent());
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream archive = new ZipOutputStream(raw)) {
            for (Path file : files) {
                ZipEntry entry = new ZipEntry(root.relativize(file).toString().replace('\\', '/'));
                entry.setTime(978307200000L);
                archive.putNextEntry(entry);
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
        ByteArrayOutputStream hex = new ByteArrayOutputStream();
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(zip));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        for (byte b : digest) {
            hex.write(String.format("%02x", b).getBytes(StandardCharsets.US_ASCII));
        }
        Files.writeString(zip.resolveSibling(zip.getFileName() + ".sha1"), hex.toString(StandardCharsets.US_ASCII) + "\n");
    }
}
