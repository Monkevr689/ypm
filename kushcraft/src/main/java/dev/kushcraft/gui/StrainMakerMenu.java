package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.strain.StrainType;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Strain Maker: cross two seeds and design a new strain (effects, type,
 * colour, name). Layout matches tools/gui.py strain().
 */
public final class StrainMakerMenu extends Menu {

    private static final int PARENT_A = 10;
    private static final int PARENT_B = 12;
    private static final int RESULT = 16;
    private static final int[] EFFECTS = {27, 28, 29, 30, 31, 32, 36, 37, 38, 39, 40, 41};
    private static final int TYPE = 34;
    private static final int COLOR = 35;
    private static final int NAME = 43;
    private static final int HELP = 44;
    private static final int CREATE = 49;

    static final int[] PALETTE = {0xA8E05A, 0x6FD04A, 0xD4F05A, 0xF0C850, 0xF09A3A, 0xE85A5A, 0xF07AAA, 0xA45AE8,
            0x7A5AF0, 0x6FA8F0, 0x7FE0D0, 0xF0F4F8, 0xB8B040, 0x60E8A0, 0x9A9AA8, 0x5A4A6A};
    private static final String[] SUFFIX = {"Dream", "Haze", "Kush", "Diesel", "Glue", "Cookies", "Cake", "Breath",
            "Fire", "Frost", "Punch", "Runtz", "Gelato", "Zkittlez", "Widow", "Express"};

    private String a, b;
    private final List<EffectType> effects = new ArrayList<>();
    private StrainType type = StrainType.HYBRID;
    private int color = PALETTE[0];
    private int colorIndex = -1;
    private int potency = 20;
    private String name;
    private boolean named;
    private static final int BACK = 45;

    public StrainMakerMenu(Player player) {
        super(player, 6, "strain", "Mix Strains");
    }

    private Strain strain(String id) {
        return id == null ? null : KushCraft.get().strains().get(id);
    }

    private boolean ready() {
        return strain(a) != null && strain(b) != null;
    }

    /** Fresh defaults when the parents change. */
    private void crossParents() {
        Strain sa = strain(a), sb = strain(b);
        if (sa == null || sb == null) {
            return;
        }
        effects.clear();
        for (EffectType e : sa.effects()) {
            if (effects.size() < 3 && e.selectable() && !effects.contains(e)) {
                effects.add(e);
            }
        }
        for (EffectType e : sb.effects()) {
            if (effects.size() < 3 && e.selectable() && !effects.contains(e)) {
                effects.add(e);
            }
        }
        type = sa.type() == sb.type() ? sa.type() : StrainType.HYBRID;
        color = blend(sa.color(), sb.color());
        colorIndex = -1;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        potency = Math.max(5, Math.min(35, (sa.potency() + sb.potency()) / 2 + r.nextInt(-2, 5)));
        if (!named) {
            name = autoName(sa, sb);
        }
    }

    private static int blend(int x, int y) {
        int r = (((x >> 16) & 255) + ((y >> 16) & 255)) / 2;
        int g = (((x >> 8) & 255) + ((y >> 8) & 255)) / 2;
        int bl = ((x & 255) + (y & 255)) / 2;
        return (r << 16) | (g << 8) | bl;
    }

    private String autoName(Strain sa, Strain sb) {
        String first = sa.name().split(" ")[0];
        String[] bw = sb.name().split(" ");
        String last = bw[bw.length - 1];
        String n = first + " " + last;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tries = 0;
        while ((KushCraft.get().strains().nameTaken(n) || n.equalsIgnoreCase(sa.name()) || n.equalsIgnoreCase(sb.name()))
                && tries++ < 30) {
            n = first + " " + SUFFIX[r.nextInt(SUFFIX.length)];
        }
        return n;
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        Strain sa = strain(a), sb = strain(b);
        set(PARENT_A, sa == null ? Items.icon("seed_pack", "<gray>Parent A",
                "<gray>Click a <green>Seeds</green> item in", "<gray>your inventory to add it.")
                : lore(Items.strainItem(ItemType.SEED_PACK, sa, 3, 1), "<red>Click to remove"));
        set(PARENT_B, sb == null ? Items.icon("seed_pack", "<gray>Parent B",
                "<gray>Click a second <green>Seeds</green> item.")
                : lore(Items.strainItem(ItemType.SEED_PACK, sb, 3, 1), "<red>Click to remove"));

        List<EffectType> all = EffectType.selectableValues();
        for (int i = 0; i < EFFECTS.length && i < all.size(); i++) {
            EffectType e = all.get(i);
            boolean on = effects.contains(e);
            boolean fromParent = (sa != null && sa.effects().contains(e)) || (sb != null && sb.effects().contains(e));
            ItemStack icon = Items.icon(e.icon(), (on ? "<green>✔ " : "<gray>") + e.colored(),
                    "<gray>" + e.description(),
                    fromParent ? "<dark_aqua>From a parent" : "<dark_gray>Mutation",
                    "",
                    on ? "<red>Click to remove" : "<green>Click to add <gray>(max 3)");
            set(EFFECTS[i], on ? Items.glint(icon, true) : icon);
        }
        set(TYPE, Items.icon(type.icon(), "<white>Type: " + type.colored(),
                "<gray>" + type.blurb() + ".",
                "",
                "<yellow>Click to change"));
        set(COLOR, Items.tintedIcon("bud_dried", color, "<white>Bud colour: <color:" + Text.hex(color) + ">███",
                List.of("<gray>Colour of the seeds, buds and plant.", "", "<yellow>Click: next colour",
                        "<yellow>Right-click: previous colour")));
        set(NAME, Items.icon("ui_name_tag", "<white>Name: <green>" + (name == null ? "?" : Text.escape(name)),
                "<gray>Click to type a new name in chat."));
        double cost = KushCraft.get().getConfig().getDouble("strain-maker.cost", 150);
        set(HELP, Items.icon("ui_info", "<aqua>Strain Maker",
                "<gray>1. Click two <green>Seeds</green> in your inventory",
                "<gray>2. Pick up to 3 effects",
                "<gray>3. Choose type, colour and name",
                "<gray>4. Hit <green>Create</green>!",
                "",
                "<gray>Potency comes from the parents.",
                "<gray>Both parent seeds are used up.",
                cost > 0 ? "<gray>Registration fee: <gold>" + KushCraft.get().economy().format(cost) : "<gray>Free to create."));

        if (!ready()) {
            set(RESULT, Items.icon("ui_dna", "<gray>New strain", "<gray>Add two parents first."));
            set(CREATE, Items.icon("ui_cancel", "<gray>Create", "<gray>Add two parent seeds first."));
            return;
        }
        List<String> lore = new ArrayList<>();
        lore.add(type.colored() + " <dark_gray>•</dark_gray> <gray>THC <white>~" + potency + "%");
        StringBuilder eff = new StringBuilder();
        for (EffectType e : effects) {
            if (!eff.isEmpty()) {
                eff.append("<dark_gray>, </dark_gray>");
            }
            eff.append(e.colored());
        }
        lore.add("<gray>Effects: " + (eff.isEmpty() ? "<red>none" : eff));
        lore.add("<gray>Loves " + type.climate().colored() + " <gray>biomes");
        set(RESULT, Items.tintedIcon("seed_pack", color, "<color:" + Text.hex(color) + ">" + Text.escape(name)
                + " <white>Seeds", lore));
        int seeds = KushCraft.get().getConfig().getInt("strain-maker.seeds-given", 3);
        set(CREATE, Items.glint(Items.icon("ui_confirm", "<green><bold>Create strain!",
                "<gray>You get <white>" + seeds + "x</white> seeds of",
                "<color:" + Text.hex(color) + ">" + Text.escape(name),
                cost > 0 ? "<gray>Costs <gold>" + KushCraft.get().economy().format(cost) : ""), true));
    }

    private static ItemStack lore(ItemStack it, String line) {
        it.editMeta(m -> {
            List<net.kyori.adventure.text.Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            l.add(Text.mm(""));
            l.add(Text.mm(line));
            m.lore(l);
        });
        return it;
    }

    @Override
    public void click(int slot, ClickType click) {
        if (slot == PARENT_A && a != null) {
            a = null;
            clickSound();
            render();
            return;
        }
        if (slot == PARENT_B && b != null) {
            b = null;
            clickSound();
            render();
            return;
        }
        if (!ready()) {
            if (slot == CREATE) {
                failSound();
            }
            return;
        }
        for (int i = 0; i < EFFECTS.length; i++) {
            if (EFFECTS[i] == slot) {
                List<EffectType> all = EffectType.selectableValues();
                if (i >= all.size()) {
                    return;
                }
                EffectType e = all.get(i);
                if (effects.contains(e)) {
                    effects.remove(e);
                } else if (effects.size() >= 3) {
                    player.sendActionBar(Text.mm("<red>Max 3 effects. Remove one first."));
                    failSound();
                    return;
                } else {
                    effects.add(e);
                }
                clickSound();
                render();
                return;
            }
        }
        switch (slot) {
            case TYPE -> {
                type = type.next();
                clickSound();
                render();
            }
            case COLOR -> {
                colorIndex = Math.floorMod(colorIndex + (click.isRightClick() ? -1 : 1), PALETTE.length);
                color = PALETTE[colorIndex];
                clickSound();
                render();
            }
            case NAME -> ChatInput.ask(player, "<green>What should your strain be called?", text -> {
                if (acceptName(text)) {
                    named = true;
                }
                reopen();
            }, this::reopen);
            case CREATE -> {
                if (named) {
                    create();
                    return;
                }
                // always let the player name their strain before it is saved
                ChatInput.ask(player, "<green>Name your new strain!</green> <gray>Type a name, or <white>ok</white> to keep <white>"
                        + Text.escape(name) + "</white>.", text -> {
                    if (text.equalsIgnoreCase("ok") || acceptName(text)) {
                        named = true;
                        if (!create()) {
                            reopen();
                        }
                    } else {
                        reopen();
                    }
                }, this::reopen);
            }
            default -> {
            }
        }
    }

    /** Validates a typed name and uses it. */
    private boolean acceptName(String text) {
        String n = clean(text);
        if (n.length() < 2 || n.length() > 24) {
            player.sendMessage(Text.msg("<red>Names must be 2-24 letters/numbers."));
            return false;
        }
        if (KushCraft.get().strains().nameTaken(n)) {
            player.sendMessage(Text.msg("<red>That strain already exists - pick another name."));
            return false;
        }
        name = n;
        return true;
    }

    static String clean(String text) {
        return text.replaceAll("[^A-Za-z0-9 '\\-]", "").replaceAll(" +", " ").trim();
    }

    private void reopen() {
        if (player.isOnline()) {
            open();
        }
    }

    private boolean create() {
        KushCraft plugin = KushCraft.get();
        if (!player.hasPermission("kushcraft.strainmaker")) {
            player.sendActionBar(Text.mm("<red>You are not allowed to create strains."));
            failSound();
            return false;
        }
        if (effects.isEmpty()) {
            player.sendActionBar(Text.mm("<red>Pick at least one effect."));
            failSound();
            return false;
        }
        if (plugin.strains().nameTaken(name)) {
            player.sendActionBar(Text.mm("<red>That name is taken - pick another."));
            named = false;
            failSound();
            return false;
        }
        int max = plugin.getConfig().getInt("strain-maker.max-per-player", 25);
        if (!player.hasPermission("kushcraft.admin") && plugin.strains().countCreatedBy(player.getUniqueId()) >= max) {
            player.sendActionBar(Text.mm("<red>You already created " + max + " strains."));
            failSound();
            return false;
        }
        int needA = a.equals(b) ? 2 : 1;
        if (countSeeds(a) < needA || countSeeds(b) < 1) {
            player.sendActionBar(Text.mm("<red>The parent seeds are no longer in your inventory."));
            failSound();
            return false;
        }
        double cost = plugin.getConfig().getDouble("strain-maker.cost", 150);
        if (!plugin.economy().withdraw(player, cost)) {
            player.sendActionBar(Text.mm("<red>You need " + plugin.economy().format(cost) + " to register a strain."));
            failSound();
            return false;
        }
        removeSeed(a);
        removeSeed(b);
        Strain s = plugin.strains().create(name, type, color, potency, effects, player.getUniqueId(), player.getName());
        int seeds = Math.max(1, plugin.getConfig().getInt("strain-maker.seeds-given", 3));
        InventoryUtil.give(player, Items.strainItem(ItemType.SEED_PACK, s, 3, seeds));
        player.closeInventory();
        player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.7f, 1.2f);
        Bukkit.broadcast(Text.msg("<white>" + Text.escape(player.getName()) + " <gray>bred a new strain: "
                + s.colored() + " <gray>(" + s.type().colored() + "<gray>)"));
        return true;
    }

    private int countSeeds(String id) {
        return InventoryUtil.count(player, it -> Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null
                && Items.strain(it).id().equals(id));
    }

    private void removeSeed(String id) {
        InventoryUtil.remove(player, it -> Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null
                && Items.strain(it).id().equals(id), 1);
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (Items.type(item) != ItemType.SEED_PACK || Items.strain(item) == null) {
            return;
        }
        String id = Items.strain(item).id();
        if (a == null) {
            a = id;
        } else if (b == null) {
            b = id;
        } else {
            b = id;
        }
        if (ready()) {
            crossParents();
        }
        clickSound();
        render();
    }
}
