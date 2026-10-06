package dev.kushcraft.guide;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.lab.LabRecipe;
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
        sections.add(new Section("Workers", workers()));
        int recipesAt = sections.size();
        sections.add(new Section("Recipes (pictures)", null));
        sections.add(new Section("Money & cartels", money()));
        sections.add(new Section("Breeding strains", strain()));
        // the animals page sits at the end of the effects (the contents page has room for 11 lines)
        List<String> effects = new ArrayList<>(effects());
        effects.add("<dark_green><bold>Animals</bold>\n\n<black>Right-click an animal with a joint, an edible or"
                + " any drug: its <red>eyes go red</red> and it gets high for a while.\n\n<dark_gray>Uppers give it"
                + " the zoomies, downers slow it right down.");
        sections.add(new Section("Effects & animals", effects));
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
                + "<black>KushCraft Menu<dark_gray> book.\n"
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
        return List.of("<dark_green><bold>Quick start</bold>\n\n<black>1. Buy or find seeds\n"
                + "2. Plant on farmland\n3. Harvest when grown\n4. Get a <dark_green>Drug Lab</dark_green>\n"
                + "5. Dry, roll & cook\n6. Sell at the <gold>Shop</gold>\n7. Find a <dark_green>wild plant</dark_green>\n\n"
                + "<dark_gray>The glowing <dark_aqua>Next</dark_aqua> button in the menu shows your step.\n"
                + run("/kush start", "> Getting started"));
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
                        + "</dark_green>.\nRed poppies: <red>poppy</red>.\nDead bushes: <gold>peyote</gold>.\n"
                        + "Ripe wheat: <dark_purple>ergot</dark_purple>.\nOr buy any strain in the Shop.",
                "<dark_green><bold>Wild plants</bold>\n\n<black>Plants grow by themselves out in the world:"
                        + " the biome's strain, coca in jungles, poppies on plains, peyote in deserts.\n\n"
                        + "Anyone can pick them. They wither after a few hours.",
                "<dark_green><bold>Growing</bold>\n\n<black>Right-click the <dark_aqua>top</dark_aqua> of farmland,"
                        + " grass, dirt or a <dark_green>Planter</dark_green>.\n\nNeeds <gold>light 9+</gold> or a"
                        + " <dark_purple>Grow Lamp</dark_purple>. Mushrooms like the dark.\nWater, Planters and"
                        + " Fertilizer help.\nClick a plant to check it.",
                "<dark_green><bold>Climates</bold>\n<black>Each strain loves one:\n"
                        + "<dark_gray>Tropical: jungles\nDesert: sand, savanna\nTemperate: plains\n"
                        + "Wetland: swamps, rivers\nCold: snow, taiga\nMountain: hills, y 100+\n"
                        + "<black>Right one: faster, +1 <gold>★</gold>, +1 bud. Opposite: slow (Grow Lamps help).");
    }

    private static List<String> lab() {
        return List.of("<dark_green><bold>Drug Lab</bold>\n\n<black><bold>Cook</bold>: " + LabRecipe.values().length
                + " recipes\n<bold>Roll</bold>: joints & blunts\n<bold>Dry</bold>: 5 racks, 30s\n<bold>Mix</bold>:"
                + " breed strains\n<bold>Upgrade</bold>: faster + bonus\n\n<dark_gray>Glowing = you have it all."
                + " Shift-click cooks up to 4 batches.",
                "<dark_green><bold>Step by step</bold>\n\n<black>Many drugs take 2-3 cooks:\n<dark_gray>"
                        + "coca > paste > cocaine\nopium > morphine > heroin\nmorphine > oxy\n"
                        + "ergot > extract > LSD\nDMT > ayahuasca\n<black>Red = missing (it says where to get"
                        + " it).");
    }

    private static List<String> workers() {
        int chain = KushCraft.get() == null ? 32 : KushCraft.get().workers().chainRadius();
        return List.of("<dark_green><bold>Workers</bold>\n\n<black>Shop > <dark_aqua>Gear & Workers</dark_aqua>."
                        + " Right-click the ground to put them there.\n\n<dark_green>Farmhand</dark_green>:"
                        + " harvests, replants\n<gold>Dryer</gold>: dries buds\n<dark_aqua>Cook</dark_aqua>:"
                        + " cooks or rolls\n<light_purple>Runner</light_purple>: sells it all",
                "<dark_green><bold>Work chain</bold>\n\n<black>Workers within " + chain + " blocks of each other"
                        + " pass things along by themselves:\n<dark_gray>Farmhand > Dryer > Cook > Runner\n\n"
                        + "<black>A <gold>chest</gold> next to a worker: they put their work in it and take"
                        + " supplies from it.",
                "<dark_green><bold>Your workers</bold>\n\n<black>Hire as many as you like. Right-click one for"
                        + " their satchel.\n\nThey get a <gold>wage</gold> per job (Runners keep a cut)."
                        + " <dark_gray>No money, no work!</dark_gray>\nTrain them: further, faster.\n"
                        + run("/kush workers", "> Your workers"));
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
                "<dark_green><bold>Shop</bold>\n\n<black>Seeds, gear and workers.\n\n<gold>Selling drugs is"
                        + " the only way to make money.</gold> Click product to sell it, or Sell all. The more of"
                        + " one thing you sell, the less it pays.\n" + run("/kush shop", "> Shop"),
                "<dark_green><bold>Trade</bold>\n\n<black>Spend it: lab ingredients, ores, food, wood,"
                        + " blocks, colours, decor, redstone, tools, mob drops, Nether.\n\n<dark_gray>Trade only"
                        + " sells - nothing can be sold back.\n" + run("/kush trade", "> Trade"),
                "<dark_green><bold>Cartels</bold>\n\n<black>A team. A shared <gold>bank</gold> (+5% of every"
                        + " sale), levels that pay every member more, and big <gold>shipments</gold> to fill"
                        + " together.\n" + run("/kush cartel", "> Cartel"),
                "<dark_green><bold>Market</bold>\n\n<black>Shop > <gold>Market</gold>: contracts pay extra"
                        + " for big batches. Sell a mix - flooded products pay less.\n\n<red>Dying costs you "
                        + Math.round((KushCraft.get() == null ? 0.2
                        : KushCraft.get().getConfig().getDouble("death.cash-lost", 0.2)) * 100)
                        + "%</red> of your cash, so bank on selling, not fighting.",
                "<dark_green><bold>Top Dealers</bold>\n\n<black>The players who sold the most get the titles:"
                        + "\n<dark_red>#1 Cartel Boss\n<red>#2 Kingpin\n<gold>#3 The Plug\n<dark_gray>top 5, 10, 25..."
                        + "\n\n<black>Titles pay extra on every sale.\n" + run("/kush top", "> Top"),
                "<dark_green><bold>Awards</bold>\n\n<black>" + dev.kushcraft.award.Award.values().length
                        + " achievements to collect. They pop up like"
                        + " advancements (press <dark_gray>L</dark_gray>).\n\nSend money:"
                        + "\n<dark_gray>/kush pay \\<name> \\<amount>\n" + run("/kush awards", "> Awards"));
    }

    private static List<String> strain() {
        return List.of("<dark_green><bold>Breeding</bold>\n\n<black>Drug Lab > <dark_green>Mix</dark_green>."
                        + " Click two seeds and press MIX.\n\nThe child is <bold>random</bold>: effects, potency,"
                        + " climate, colours and bud shape from the parents - or a <dark_purple>mutation</dark_purple>.",
                "<dark_green><bold>Mythic</bold>\n\n<black>Very rarely a child is <dark_purple><bold>Mythic"
                        + "</bold></dark_purple>: rainbow, galaxy, golden, crystal, neon or inferno buds that"
                        + " sparkle and sell for <gold>2.5x</gold>.\n\nA Mythic parent passes it on 1 in 5.");
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
        // 3 per page: long names wrap onto a second line
        for (int i = 0; i < strains.size() && i < 60; i += 3) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Strains</bold>\n");
            for (int j = i; j < Math.min(strains.size(), i + 3); j++) {
                Strain s = strains.get(j);
                b.append("\n<black><bold>").append(Text.escape(s.name())).append("</bold>\n<dark_gray>")
                        .append(s.rarity().display()).append(", ").append(s.potency()).append("%, ")
                        .append(s.climate().display()).append("\n");
            }
            out.add(b.toString());
        }
        if (out.isEmpty()) {
            out.add("<dark_green><bold>Strains</bold>\n\n<black>No strains yet.");
        }
        return out;
    }
}
