package com.cobblemondimensions.stories.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

/**
 * A shopkeeper's conversation, loaded from {@code data/<namespace>/shop_dialogues/}.
 *
 * <p>These are the big talking screens the story shopkeepers open when the player
 * sneak-clicks them. The server walks the pages and settles purchases itself; the
 * resource pack draws them in the shopkeeper's own art.
 *
 * <p>Lines may use {@code {player}}, {@code {item}} and {@code {price}}, filled in
 * from the player's name and the product picked in the catalog.
 */
public record ShopDialogue(
    ResourceLocation id,
    Theme theme,
    Component speaker,
    ResourceLocation currency,
    List<Page> pages
) {
    private static final ResourceLocation PLACEHOLDER_ID =
        ResourceLocation.fromNamespaceAndPath("cobblemon_dimensions_stories", "unnamed");

    public static final Codec<ShopDialogue> BODY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Theme.CODEC.fieldOf("theme").forGetter(ShopDialogue::theme),
        ComponentSerialization.CODEC.fieldOf("speaker").forGetter(ShopDialogue::speaker),
        ResourceLocation.CODEC.optionalFieldOf("currency",
            ResourceLocation.withDefaultNamespace("emerald")).forGetter(ShopDialogue::currency),
        Page.CODEC.listOf().fieldOf("pages").forGetter(ShopDialogue::pages)
    ).apply(instance, (theme, speaker, currency, pages) ->
        new ShopDialogue(PLACEHOLDER_ID, theme, speaker, currency, pages)));

    public ShopDialogue withId(ResourceLocation dialogueId) {
        return new ShopDialogue(dialogueId, theme, speaker, currency, pages);
    }

    public Optional<Page> page(String pageId) {
        return pages.stream().filter(page -> page.id().equals(pageId)).findFirst();
    }

    /** Which shopkeeper's art the conversation is drawn in. */
    public enum Theme implements StringRepresentable {
        /** Mara's counter: the blue panel and her portrait. */
        CLASSIC("classic"),
        /** The Curator's closet: gloved hands rising out of the dark. */
        HAND("hand");

        public static final Codec<Theme> CODEC = StringRepresentable.fromEnum(Theme::values);
        private final String name;

        Theme(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** How the Curator's hands act on a page. */
    public enum Mood implements StringRepresentable {
        TALK("talk"),
        YES("yes"),
        NO("no");

        public static final Codec<Mood> CODEC = StringRepresentable.fromEnum(Mood::values);
        private final String name;

        Mood(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /**
     * One screen of the conversation. A page either waits for a click to go
     * {@code next} (or {@code close}), offers {@code options}, or shows a
     * {@code catalog} to buy from.
     */
    public record Page(
        String id,
        Optional<String> title,
        Mood mood,
        List<String> lines,
        List<Option> options,
        List<Product> catalog,
        Optional<String> next,
        boolean close,
        Optional<String> confirm,
        Optional<String> bought,
        Optional<String> refused
    ) {
        public static final Codec<Page> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Page::id),
            Codec.STRING.optionalFieldOf("title").forGetter(Page::title),
            Mood.CODEC.optionalFieldOf("mood", Mood.TALK).forGetter(Page::mood),
            Codec.STRING.listOf().optionalFieldOf("lines", List.of()).forGetter(Page::lines),
            Option.CODEC.listOf().optionalFieldOf("options", List.of()).forGetter(Page::options),
            Product.CODEC.listOf().optionalFieldOf("catalog", List.of()).forGetter(Page::catalog),
            Codec.STRING.optionalFieldOf("next").forGetter(Page::next),
            Codec.BOOL.optionalFieldOf("close", false).forGetter(Page::close),
            Codec.STRING.optionalFieldOf("confirm").forGetter(Page::confirm),
            Codec.STRING.optionalFieldOf("bought").forGetter(Page::bought),
            Codec.STRING.optionalFieldOf("refused").forGetter(Page::refused)
        ).apply(instance, Page::new));

        public boolean isCatalog() {
            return !catalog.isEmpty();
        }
    }

    /**
     * A button. It goes to another page, closes the conversation, or ({@code buy})
     * settles the product picked in the catalog.
     */
    public record Option(String text, Optional<String> go, boolean close, boolean buy) {
        public static final Codec<Option> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("text").forGetter(Option::text),
            Codec.STRING.optionalFieldOf("goto").forGetter(Option::go),
            Codec.BOOL.optionalFieldOf("close", false).forGetter(Option::close),
            Codec.BOOL.optionalFieldOf("buy", false).forGetter(Option::buy)
        ).apply(instance, Option::new));
    }

    /** Something on the shelf, priced in the dialogue's currency. */
    public record Product(ResourceLocation item, String name, int price) {
        public static final Codec<Product> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(Product::item),
            Codec.STRING.fieldOf("name").forGetter(Product::name),
            Codec.intRange(1, 64 * 36).fieldOf("price").forGetter(Product::price)
        ).apply(instance, Product::new));
    }
}
