package dev.kushcraft.recipe;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kushcraft.KushCraft;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.lab.LabRecipe;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every recipe in one list - crafting table recipes and the Drug Lab's Cook,
 * Roll, Dry and Mix tabs - for the recipe viewer and the handbook. The
 * handbook pictures come from the resource pack (tools/recipe_images.py);
 * recipe_book.json maps each recipe to its picture.
 */
public final class RecipeBook {

    public enum Kind {
        CRAFTING("Crafting Table", "Crafting"),
        COOK("Drug Lab > Cook", "Drug Lab"),
        ROLL("Drug Lab > Roll", "Drug Lab"),
        DRY("Drug Lab > Dry", "Drug Lab"),
        MIX("Drug Lab > Mix", "Drug Lab");

        private final String station;
        private final String group;

        Kind(String station, String group) {
            this.station = station;
            this.group = group;
        }

        public String station() {
            return station;
        }

        /** "Crafting" or "Drug Lab" (the recipe list filter). */
        public String group() {
            return group;
        }
    }

    /** grid: 9 cells (null = empty). signature: null when not checked against the pictures. */
    public record Entry(String id, Kind kind, ItemType result, int amount, ItemStack[] grid, List<String> notes,
                        String signature) {

        public String title() {
            return result == ItemType.SEED_PACK && kind == Kind.MIX ? "Your own strain" : result.display();
        }
    }

    public record Picture(char glyph, String sig) {
    }

    private static Map<String, Picture> pictures;
    private static int pictureLines = 7;

    private RecipeBook() {
    }

    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        for (Recipes.Info r : Recipes.info()) {
            ItemStack[] grid = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                grid[i] = vanilla(r.grid()[i], 1);
            }
            out.add(new Entry("craft_" + r.result().id(), Kind.CRAFTING, r.result(), r.amount(), grid,
                    List.of(r.shape() == null ? "Any shape - just put them in." : "Same pattern as shown."),
                    r.signature()));
        }
        for (LabRecipe r : LabRecipe.values()) {
            List<ItemStack> ings = new ArrayList<>();
            StringBuilder sig = new StringBuilder("cook:kush:" + r.output().id() + "x" + r.amount() + ":" + r.seconds() + "s:");
            boolean first = true;
            for (LabRecipe.Ingredient in : r.ingredients()) {
                ings.add(in.custom() != null ? sample(in.custom(), in.amount())
                        : vanilla(in.vanilla().name().toLowerCase(Locale.ROOT), in.amount()));
                sig.append(first ? "" : ",").append(in.custom() != null ? "kush:" + in.custom().id()
                        : in.vanilla().name().toLowerCase(Locale.ROOT)).append('*').append(in.amount());
                first = false;
            }
            List<String> notes = new ArrayList<>();
            notes.add("Takes " + r.seconds() + "s. Ingredients come from your inventory.");
            if (r.strainBased()) {
                notes.add("The strain of the buds decides the effects.");
            }
            out.add(new Entry("cook_" + r.name().toLowerCase(Locale.ROOT), Kind.COOK, r.output(), r.amount(),
                    spread(ings), notes, sig.toString()));
        }
        out.add(new Entry("roll_joint", Kind.ROLL, ItemType.JOINT, 1,
                spread(List.of(sample(ItemType.BUD_DRIED, 1), sample(ItemType.ROLLING_PAPERS, 1))),
                List.of("3 hits. \"Roll all\" rolls every bud at once."), null));
        out.add(new Entry("roll_blunt", Kind.ROLL, ItemType.BLUNT, 1,
                spread(List.of(sample(ItemType.BUD_DRIED, 2), sample(ItemType.BLUNT_WRAP, 1))),
                List.of("5 strong hits."), null));
        int dry = KushCraft.get().getConfig().getInt("drying.minutes", 3);
        out.add(new Entry("dry_bud", Kind.DRY, ItemType.BUD_DRIED, 1, spread(List.of(sample(ItemType.BUD_FRESH, 1))),
                List.of("Takes " + dry + " min. Up to 64 buds at once."), null));
        double cost = KushCraft.get().getConfig().getDouble("strain-maker.cost", 150);
        int seeds = KushCraft.get().getConfig().getInt("strain-maker.seeds-given", 3);
        out.add(new Entry("mix_strain", Kind.MIX, ItemType.SEED_PACK, seeds,
                spread(List.of(sample(ItemType.SEED_PACK, 1), sample(ItemType.SEED_PACK, 1))),
                List.of("Two seeds + " + KushCraft.get().economy().format(cost) + ". The result is random.",
                        "Keep and name it, or try again."), null));
        return out;
    }

    /** Recipes that make this item. */
    public static List<Entry> making(ItemType t) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (e.result() == t) {
                out.add(e);
            }
        }
        return out;
    }

    private static ItemStack[] spread(List<ItemStack> ings) {
        int[] pos = switch (ings.size()) {
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{3, 4, 5};
            case 4 -> new int[]{1, 3, 5, 7};
            default -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        };
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < Math.min(pos.length, ings.size()); i++) {
            grid[pos[i]] = ings.get(i);
        }
        return grid;
    }

    public static ItemStack sample(ItemType t, int amount) {
        ItemStack it = t.strainBound() ? Items.strainItem(t, KushCraft.get().strains().getOrDefault(null), 3, 1)
                : Items.create(t);
        it.setAmount(Math.max(1, Math.min(99, amount)));
        return it;
    }

    /** MiniMessage name of an ingredient. */
    public static String name(ItemStack it) {
        ItemType t = Items.type(it);
        if (t != null) {
            return t.display();
        }
        if (it.hasItemMeta() && it.getItemMeta().hasItemName()) {
            return Text.escape(Text.plain(it.getItemMeta().itemName()));
        }
        return Text.titleCase(it.getType().name());
    }

    /** A vanilla ingredient for display ("planks" = any planks). */
    private static ItemStack vanilla(String name, int amount) {
        if (name == null) {
            return null;
        }
        if (name.equals("planks")) {
            ItemStack it = new ItemStack(Material.OAK_PLANKS, amount);
            it.editMeta(m -> m.itemName(Text.mm("<white>Any Planks")));
            return it;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? null : new ItemStack(m, Math.max(1, amount));
    }

    // ------------------------------------------------------------------
    // handbook pictures
    // ------------------------------------------------------------------

    /** Recipe id -> picture glyph of the kush:book font (from recipe_book.json). */
    public static Map<String, Picture> pictures() {
        if (pictures == null) {
            pictures = new HashMap<>();
            try (InputStream in = KushCraft.get().getResource("recipe_book.json")) {
                if (in != null) {
                    JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                    pictureLines = root.has("lines") ? root.get("lines").getAsInt() : 7;
                    for (JsonElement el : root.getAsJsonArray("recipes")) {
                        JsonObject o = el.getAsJsonObject();
                        String c = o.get("char").getAsString();
                        pictures.put(o.get("id").getAsString(), new Picture(c.charAt(0),
                                o.has("sig") ? o.get("sig").getAsString() : null));
                    }
                }
            } catch (Exception e) {
                KushCraft.get().getLogger().warning("Could not read recipe_book.json: " + e);
            }
        }
        return pictures;
    }

    /** How many book lines one picture covers. */
    public static int pictureLines() {
        pictures();
        return pictureLines;
    }
}
