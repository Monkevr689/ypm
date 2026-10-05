package dev.kushcraft.guide;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.recipe.Recipes;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/** The Grower's Handbook - a written book that explains everything in game. */
public final class Guide {

    private Guide() {
    }

    public static ItemStack book() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("Grower's Handbook"));
        meta.author(Component.text("KushCraft"));
        List<Component> pages = new ArrayList<>();
        for (String p : pages()) {
            pages.add(Text.mm(p));
        }
        meta.pages(pages);
        book.setItemMeta(meta);
        return book;
    }

    private static List<String> pages() {
        List<String> p = new ArrayList<>();
        p.add("<dark_green><bold>   KUSHCRAFT</bold>\n<dark_gray>  Grower's Handbook\n\n"
                + "<black>Grow plants, cook in the Drug Lab, roll joints and get rich at the Market.\n\n"
                + "<dark_gray>Type <black>/kush</black> any time for the menu: market, catalog, strains, orders.");
        p.add("<dark_green><bold>The 4 blocks</bold>\n\n<black><bold>Drug Lab</bold>\n<dark_gray>cook, roll, dry, mix strains\n"
                + "<black><bold>Planter</bold>\n<dark_gray>best soil, +1 quality\n<black><bold>Grow Lamp</bold>\n"
                + "<dark_gray>grow indoors\n<black><bold>Dealer Stand</bold>\n<dark_gray>the market as a block\n\n"
                + "<dark_gray>Recipes: /kush > Catalog");
        p.add("<dark_green><bold>Getting seeds</bold>\n\n<black>Break <dark_green>grass</dark_green> or ferns for cannabis"
                + " seeds - the <dark_aqua>biome</dark_aqua> decides the strain.\n\nJungle grass can drop"
                + " <dark_green>coca seeds</dark_green>, red poppies drop <red>poppy seeds</red>, small mushrooms drop"
                + " <gold>spores</gold>.\n\nOr buy them at the Market.");
        p.add("<dark_green><bold>Growing</bold>\n\n<black>Right-click the <dark_aqua>top</dark_aqua> of farmland, grass,"
                + " dirt or a <dark_green>Planter</dark_green>.\n\nNeeds <gold>light 9+</gold> or a"
                + " <dark_purple>Grow Lamp</dark_purple>. Mushrooms like the dark.\n\nWatered farmland, Planters and"
                + " Fertilizer = faster + better quality.\nClick a plant to check it.");
        p.add("<dark_green><bold>Biomes</bold>\n\n<gold>Sativa</gold><black>, <dark_green>coca</dark_green>:"
                + " warm biomes.\n<dark_purple>Indica</dark_purple><black>: cold biomes.\n<dark_green>Hybrid</dark_green>"
                + "<black>, <red>poppy</red>: mild biomes.\n\nRight climate = faster growth and more"
                + " <gold>\u2605</gold> quality. Cold is bad for coca and poppies.");
        p.add("<dark_green><bold>Drug Lab</bold>\n\n<black><bold>Cook</bold>: hash, moon rocks, brownies, tea,"
                + " cocaine, heroin, LSD, meth...\n<bold>Roll</bold>: joints & blunts\n<bold>Dry</bold>: fresh buds"
                + " -> dried buds\n<bold>Mix</bold>: make a strain\n\n<dark_gray>Ingredients come from your inventory.");
        p.add("<dark_green><bold>Hard drugs</bold>\n\n<black><bold>Cocaine</bold>\n<dark_gray>8 coca leaves + solvent"
                + " + sugar\n<black><bold>Heroin</bold>\n<dark_gray>6 poppy pods + solvent + catalyst\n<black><bold>LSD"
                + "</bold>\n<dark_gray>solvent + 2 shrooms + paper\n<black><bold>Meth</bold>\n<dark_gray>solvent +"
                + " catalyst + 4 lapis + 2 sugar");
        p.add("<dark_green><bold>Your own strain</bold>\n\n<black>/kush > Strains > <dark_green>Mix</dark_green> (or the"
                + " Drug Lab). Click two seeds, pick up to <gold>3 effects</gold>, type and colour, then"
                + " <dark_green>Create</dark_green> and type a <bold>name</bold>.\n\nYou can rename your strains"
                + " later in the Strains list.");
        p.add("<dark_green><bold>Money</bold>\n\n<black>Sell at the <dark_green>Market</dark_green>. Selling lots of one"
                + " thing drops its price - sell a mix!\n\nThe <gold>HOT</gold> item pays +50%.\n\n"
                + "<gold>Daily Orders</gold> pay ~75% extra for big batches.\n\nBetter <gold>\u2605</gold> = more money.");
        // effects (4 per page)
        List<EffectType> effects = List.of(EffectType.values());
        for (int i = 0; i < effects.size(); i += 4) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Effects</bold>\n");
            for (int j = i; j < Math.min(effects.size(), i + 4); j++) {
                EffectType e = effects.get(j);
                b.append("\n<black><bold>").append(e.display()).append("</bold>\n<dark_gray>").append(e.description()).append("\n");
            }
            p.add(b.toString());
        }
        // strains (5 per page)
        List<Strain> strains = new ArrayList<>(KushCraft.get().strains().all());
        for (int i = 0; i < strains.size() && i < 60; i += 5) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Strains</bold>\n");
            for (int j = i; j < Math.min(strains.size(), i + 5); j++) {
                Strain s = strains.get(j);
                b.append("\n<black><bold>").append(Text.escape(s.name())).append("</bold> <dark_gray>")
                        .append(s.type().display()).append(" ").append(s.potency()).append("%\n");
            }
            p.add(b.toString());
        }
        // crafting recipes (2 per page)
        List<Recipes.Info> info = Recipes.info();
        for (int i = 0; i < info.size(); i += 2) {
            StringBuilder b = new StringBuilder("<dark_green><bold>Crafting</bold>\n");
            for (int j = i; j < Math.min(info.size(), i + 2); j++) {
                Recipes.Info r = info.get(j);
                b.append("\n<black><bold>").append(r.result().display()).append("</bold>")
                        .append(r.amount() > 1 ? " x" + r.amount() : "").append("\n");
                if (r.shape() != null) {
                    for (String row : r.shape()) {
                        b.append("<dark_gray>[").append(row.replace(' ', '-')).append("]\n");
                    }
                }
                b.append("<dark_gray>").append(r.legend()).append("\n");
            }
            p.add(b.toString());
        }
        return p;
    }
}
