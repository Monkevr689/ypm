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
                + "<black>Grow strains, cook in the lab, roll joints and sell to the dealer.\n\n"
                + "<dark_gray>No commands needed - everything is items, plants and machines.");
        p.add("<dark_green><bold>Contents</bold>\n\n<black>Getting seeds\nGrowing\nBiomes\nHarvest & drying\n"
                + "Rolling & smoking\nLab Station\nLab synthetics\nStrain Maker\nDealer Stand\nEffects\nStrains\n"
                + "Crafting recipes");
        p.add("<dark_green><bold>Getting seeds</bold>\n\n<black>Break <dark_green>grass</dark_green>, ferns or dead bushes"
                + " - sometimes you find wild seeds!\n\nThe strain depends on the <dark_aqua>biome</dark_aqua>: jungles give"
                + " Jungle Haze, taigas Northern Lights...\n\nBreak small mushrooms for <gold>spores</gold>.\n\n"
                + "Or buy seeds from a <dark_green>Dealer Stand</dark_green>.");
        p.add("<dark_green><bold>Growing</bold>\n\n<black>Right-click the top of <dark_aqua>farmland</dark_aqua>, grass,"
                + " dirt or a <dark_green>Planter Box</dark_green> with seeds.\n\nPlants need <gold>light 9+</gold>"
                + " (sun/torches) or a <dark_purple>Grow Lamp</dark_purple> nearby.\n\n<dark_aqua>Watered farmland</dark_aqua>"
                + " and Planter Boxes grow faster and better. Fertilizer helps too.");
        p.add("<dark_green><bold>Biomes</bold>\n\n<gold>Sativa</gold><black> - tall, loves <gold>warm</gold> biomes"
                + " (jungle, savanna, desert).\n\n<dark_purple>Indica</dark_purple><black> - short & bushy, loves"
                + " <dark_aqua>cold</dark_aqua> biomes (taiga, snow, mountains).\n\n<dark_green>Hybrid</dark_green><black>"
                + " - loves <dark_green>mild</dark_green> biomes (plains, forest).\n\nRight climate = faster + better quality.");
        p.add("<dark_green><bold>Harvest</bold>\n\n<black>Right-click (or punch) a plant to see how it's doing."
                + " <dark_gray>Sneak</dark_gray> for details.\n\nWhen it's fully grown, click it to harvest"
                + " <dark_green>Fresh Buds</dark_green> + seeds.\n\nHang fresh buds on a <gold>Drying Rack</gold>"
                + " (right-click it). After a few minutes you get <dark_green>Dried Buds</dark_green>.");
        p.add("<dark_green><bold>Rolling & smoking</bold>\n\n<black>At a <gold>Rolling Table</gold>: click a dried bud in"
                + " your inventory, then roll a <dark_gray>Joint</dark_gray> (bud + paper) or <dark_gray>Blunt</dark_gray>"
                + " (2 buds + wrap).\n\nRight-click a joint to take a hit.\n\nA <dark_aqua>Bong</dark_aqua> smokes buds,"
                + " hash or moon rocks straight from your inventory.");
        p.add("<dark_green><bold>Lab Station</bold>\n\n<black>Click a recipe while you carry the ingredients. Come back"
                + " when it's done.\n\n<dark_gray>Hash</dark_gray> 4 bud + ice\n<dark_gray>Moon Rock</dark_gray> bud + hash +"
                + " honey\n<dark_gray>Brownies</dark_gray> 2 bud + cocoa + 2 wheat + sugar\n<dark_gray>Shroom Tea</dark_gray>"
                + " 2 shrooms + bottle\n<dark_gray>+ synthetics</dark_gray> (next page)");
        p.add("<dark_green><bold>Lab synthetics</bold>\n\n<dark_purple>Lucid Tabs</dark_purple><black>\nsolvent + 2 shrooms"
                + " + paper\n\n<blue>Blue Crystal</blue><black>\nsolvent + catalyst + 4 lapis + 2 sugar\n\n"
                + "<light_purple>Pixie Dust</light_purple><black>\nsolvent + 4 glowstone dust + 2 sugar\n\n"
                + "<dark_gray>Solvent & Catalyst are crafted or bought.");
        p.add("<dark_green><bold>Strain Maker</bold>\n\n<black>Click two seeds in your inventory to cross them. Then pick"
                + " up to <gold>3 effects</gold>, the type, bud colour and a name.\n\nPress <dark_green>Create</dark_green>"
                + " - your strain is saved forever and you get seeds!\n\nPotency comes from the parents.");
        p.add("<dark_green><bold>Dealer Stand</bold>\n\n<black>Click items at the top to <dark_green>buy</dark_green>.\n\n"
                + "Click KushCraft items in your own inventory to <gold>sell</gold> them. Shift-click sells the stack, or use"
                + " <gold>Sell everything</gold>.\n\nStronger strains & more <gold>★</gold> = more money.");
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
