package dev.kushcraft.guide;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.recipe.RecipeBook;
import dev.kushcraft.recipe.Recipes;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The KushCraft Handbook - a written book with a clickable contents page and
 * a picture of every recipe (pictures need the resource pack; without it the
 * recipe pages list the ingredients as text).
 */
public final class Guide {

    private static final int RECIPES_PER_INDEX_PAGE = 11;

    private record Section(String name, List<String> pages) {
    }

    private Guide() {
    }

    public static ItemStack book(Player viewer) {
        boolean pictures = viewer != null && KushCraft.get().pack().hasPack(viewer);
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("KushCraft Handbook"));
        meta.author(Component.text("KushCraft"));
        List<Component> pages = new ArrayList<>();
        for (String p : pages(pictures)) {
            pages.add(Text.mm(p));
        }
        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    public static List<String> pages(boolean pictures) {
        List<RecipeBook.Entry> recipes = RecipeBook.all();
        int indexPages = (recipes.size() + RECIPES_PER_INDEX_PAGE - 1) / RECIPES_PER_INDEX_PAGE;

        List<Section> sections = new ArrayList<>();
        sections.add(new Section("Quick start", quickStart()));
        sections.add(new Section("The 4 blocks", blocks()));
        sections.add(new Section("Seeds & growing", growing()));
        sections.add(new Section("Drug Lab", lab()));
        int recipesAt = sections.size();
        sections.add(new Section("Recipes (pictures)", null));
        sections.add(new Section("Money & jobs", money()));
        sections.add(new Section("Your own strain", strain()));
        sections.add(new Section("Effects", effects()));
        sections.add(new Section("Strains", strains()));

        // page numbers: 1 = cover, 2 = contents
        int page = 3;
        Map<String, Integer> start = new LinkedHashMap<>();
        for (Section s : sections) {
            start.put(s.name(), page);
            page += s.pages() == null ? indexPages + recipes.size() : s.pages().size();
        }
        int firstRecipe = start.get("Recipes (pictures)") + indexPages;
        sections.set(recipesAt, new Section("Recipes (pictures)", recipes(recipes, firstRecipe, pictures)));

        List<String> out = new ArrayList<>();
        out.add("<dark_green><bold>   KUSHCRAFT</bold>\n<dark_gray>    Handbook\n\n"
                + "<black>Grow plants, cook in the Drug Lab, roll joints and get rich.\n\n"
                + "<dark_gray>Open the menu:\n<black>/kush <dark_gray>or <black>Shift+F\n<dark_gray>or right-click the\n"
                + "<black>KushCraft Menu<dark_gray> book.\n\n"
                + "<click:run_command:/kush><dark_green><u>> Open the menu</u></dark_green></click>");
        StringBuilder toc = new StringBuilder("<dark_green><bold>Contents</bold>\n<dark_gray>click a line\n\n");
        for (Map.Entry<String, Integer> e : start.entrySet()) {
            toc.append(link(e.getValue(), "<black>" + e.getKey())).append(" <dark_gray>").append(e.getValue()).append("\n");
        }
        out.add(toc.toString());
        for (Section s : sections) {
            out.addAll(s.pages());
        }
        return out;
    }

    /**
     * Rough number of lines a page needs (a book page shows 14): word wrap
     * at 114 px with Minecraft's default font widths.
     */
    public static int lines(String plain) {
        int lines = 0;
        for (String para : plain.split("\n", -1)) {
            int x = 0;
            int n = 1;
            for (String word : para.split(" ", -1)) {
                int w = 0;
                for (char c : word.toCharArray()) {
                    w += switch (c) {
                        case 'i', '!', '.', ',', ':', ';', '|', '\'' -> 2;
                        case 'l', '`' -> 3;
                        case 't', 'I', '[', ']' -> 4;
                        case 'f', 'k', '<', '>', '(', ')', '{', '}', '"', '*' -> 5;
                        case '@', '~' -> 7;
                        default -> c >= 0xE000 && c < 0xF000 ? 113 : 6;
                    };
                }
                if (x > 0 && x + 4 + w > 114) {
                    n++;
                    x = w;
                } else {
                    x += (x > 0 ? 4 : 0) + w;
                }
            }
            lines += n;
        }
        return lines;
    }

    private static String link(int page, String text) {
        return "<click:change_page:" + page + "><hover:show_text:'Go to page " + page + "'>" + text + "</hover></click>";
    }

    private static String run(String command, String text) {
        return "<click:run_command:" + command + "><hover:show_text:'" + command + "'><dark_green><u>" + text
                + "</u></dark_green></hover></click>";
    }

    private static List<String> quickStart() {
        return List.of("<dark_green><bold>Quick start</bold>\n\n<black>1. Break <dark_green>grass</dark_green>: seeds\n"
                + "2. Plant on farmland\n3. Harvest when grown\n4. Craft a <dark_green>Drug Lab</dark_green>\n"
                + "5. Dry, roll & cook\n6. Sell at the <gold>Market</gold>\n\n"
                + "<dark_gray>All recipes have pictures!\n" + run("/kush recipes", "> Recipe viewer"));
    }

    private static List<String> blocks() {
        return List.of("<dark_green><bold>The 4 blocks</bold>\n\n<black><bold>Drug Lab</bold>\n<dark_gray>cook, roll, dry, mix\n"
                + "<black><bold>Planter</bold>\n<dark_gray>best soil, +1 quality\n<black><bold>Grow Lamp</bold>\n"
                + "<dark_gray>grow indoors\n<black><bold>Dealer Stand</bold>\n<dark_gray>the market as a block\n\n"
                + "<dark_gray>Place like a block, punch to pick up.");
    }

    private static List<String> growing() {
        return List.of(
                "<dark_green><bold>Getting seeds</bold>\n\n<black>Break <dark_green>grass</dark_green> or ferns for"
                        + " seeds - the <dark_aqua>biome</dark_aqua> picks the strain.\n\nJungle grass: <dark_green>coca"
                        + "</dark_green>.\nRed poppies: <red>poppy</red>.\nSmall mushrooms: <gold>spores</gold>.\n\n"
                        + "Or buy seeds at the Market.",
                "<dark_green><bold>Growing</bold>\n\n<black>Right-click the <dark_aqua>top</dark_aqua> of farmland,"
                        + " grass, dirt or a <dark_green>Planter</dark_green>.\n\nNeeds <gold>light 9+</gold> or a"
                        + " <dark_purple>Grow Lamp</dark_purple>. Mushrooms like the dark.\nWater, Planters and"
                        + " Fertilizer help.\nClick a plant to check it.",
                "<dark_green><bold>Biomes</bold>\n\n<gold>Sativa</gold><black>, <dark_green>coca</dark_green>:"
                        + " warm biomes.\n<dark_purple>Indica</dark_purple><black>: cold biomes.\n<dark_green>Hybrid"
                        + "</dark_green><black>, <red>poppy</red>: mild biomes.\n\nRight climate = faster growth and more"
                        + " <gold>★</gold> quality. Cold is bad for coca and poppies.");
    }

    private static List<String> lab() {
        return List.of("<dark_green><bold>Drug Lab</bold>\n\n<black><bold>Cook</bold>: hash, brownies, tea,"
                + " cocaine, heroin, LSD, meth...\n<bold>Roll</bold>: joints & blunts\n<bold>Dry</bold>: fresh"
                + " -> dried buds\n<bold>Mix</bold>: new strains\n\n<dark_gray>Ingredients come from your"
                + " inventory.");
    }

    private static List<String> recipes(List<RecipeBook.Entry> recipes, int firstRecipe, boolean pictures) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < recipes.size(); i += RECIPES_PER_INDEX_PAGE) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Recipes</bold>\n");
            for (int j = i; j < Math.min(recipes.size(), i + RECIPES_PER_INDEX_PAGE); j++) {
                RecipeBook.Entry e = recipes.get(j);
                b.append(link(firstRecipe + j, "<black>" + e.title())).append(" <dark_gray>").append(firstRecipe + j)
                        .append("\n");
            }
            out.add(b.toString());
        }
        Map<String, RecipeBook.Picture> pics = RecipeBook.pictures();
        for (RecipeBook.Entry e : recipes) {
            StringBuilder b = new StringBuilder("<dark_green><bold>" + e.title() + "</bold>")
                    .append(e.amount() > 1 ? "<dark_gray> x" + e.amount() : "").append("\n");
            RecipeBook.Picture pic = pics.get(e.id());
            if (pictures && pic != null) {
                b.append("<white><font:kush:book>").append(pic.glyph()).append("</font></white>");
                b.append("\n".repeat(RecipeBook.pictureLines()));
            } else {
                b.append("<dark_gray>").append(e.kind().station()).append("\n\n");
            }
            if (pictures && pic != null) {
                b.append("<dark_gray>").append(e.kind().station());
            } else {
                b.append(textRecipe(e));
            }
            out.add(b.toString());
        }
        return out;
    }

    /** The recipe as text, for players without the resource pack. */
    private static String textRecipe(RecipeBook.Entry e) {
        StringBuilder b = new StringBuilder();
        if (e.kind() == RecipeBook.Kind.CRAFTING) {
            for (Recipes.Info r : Recipes.info()) {
                if (r.result() == e.result() && r.shape() != null) {
                    for (String row : r.shape()) {
                        b.append("<black>[").append(row.replace(' ', '-')).append("]\n");
                    }
                    return b.append("<dark_gray>").append(r.legend()).toString();
                }
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack in : e.grid()) {
            if (in != null) {
                counts.merge(RecipeBook.name(in), in.getAmount(), Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> c : counts.entrySet()) {
            b.append("<black>").append(c.getValue()).append("x ").append(c.getKey()).append("\n");
        }
        return b.append("<dark_gray>").append(e.notes().get(0)).toString();
    }

    private static List<String> money() {
        return List.of(
                "<dark_green><bold>Market</bold>\n\n<black>Sell your product at the <gold>Market</gold>. Selling lots of"
                        + " one thing drops its price - sell a mix!\n\nThe <gold>HOT</gold> item pays +50%.\n"
                        + "<gold>Daily Orders</gold> pay ~75% extra for big batches.\n\n" + run("/kush market", "> Market"),
                "<dark_green><bold>Exchange</bold>\n\n<black>Trade money for <dark_aqua>ores, food, wood, blocks"
                        + "</dark_aqua> and more - and sell them back.\n\nPrices are fair: rare things cost more. They"
                        + " move a little with what people buy and sell.\n\n" + run("/kush exchange", "> Exchange"),
                "<dark_green><bold>Jobs</bold>\n\n<black>You get paid for normal work:\n<dark_gray>Miner</dark_gray>"
                        + " - ores\n<dark_gray>Farmer</dark_gray> - grown crops\n<dark_gray>Woodcutter</dark_gray> - logs\n"
                        + "<dark_gray>Hunter</dark_gray> - monsters\n<dark_gray>Grower</dark_gray> - harvests\n\n"
                        + "<dark_gray>Placed blocks don't pay.\n" + run("/kush jobs", "> Jobs"),
                "<dark_green><bold>Send money</bold>\n\n<black>/kush > Send $ and click a player, or type\n"
                        + "<dark_gray>/kush pay \\<name> \\<amount>\n\n<black>Top Dealers shows the richest players.");
    }

    private static List<String> strain() {
        return List.of("<dark_green><bold>Your own strain</bold>\n\n<black>/kush > Strains > <dark_green>Mix</dark_green>"
                + " (or the Drug Lab). Click two seeds, pick up to <gold>3 effects</gold>, type and colour, then"
                + " <dark_green>Create</dark_green> and type a <bold>name</bold>.\n\nRename your strains later in"
                + " the Strains list.");
    }

    private static List<String> effects() {
        List<String> out = new ArrayList<>();
        List<EffectType> effects = List.of(EffectType.values());
        for (int i = 0; i < effects.size(); i += 3) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Effects</bold>\n");
            for (int j = i; j < Math.min(effects.size(), i + 3); j++) {
                EffectType e = effects.get(j);
                b.append("\n<black><bold>").append(e.display()).append("</bold>\n<dark_gray>").append(e.description());
            }
            out.add(b.toString());
        }
        return out;
    }

    private static List<String> strains() {
        List<String> out = new ArrayList<>();
        List<Strain> strains = new ArrayList<>(KushCraft.get().strains().all());
        for (int i = 0; i < strains.size() && i < 60; i += 5) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Strains</bold>\n");
            for (int j = i; j < Math.min(strains.size(), i + 5); j++) {
                Strain s = strains.get(j);
                b.append("\n<black><bold>").append(Text.escape(s.name())).append("</bold> <dark_gray>")
                        .append(s.type().display()).append(" ").append(s.potency()).append("%\n");
            }
            out.add(b.toString());
        }
        if (out.isEmpty()) {
            out.add("<dark_green><bold>Strains</bold>\n\n<black>No strains yet.");
        }
        return out;
    }
}
