package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effects.EffectType;
import dev.kushcraft.items.ItemType;
import dev.kushcraft.items.Items;
import dev.kushcraft.machines.Machine;
import dev.kushcraft.strains.Breeding;
import dev.kushcraft.strains.Exotic;
import dev.kushcraft.strains.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drug Lab > Mix: click two seeds in your inventory, press MIX and see what
 * you get - random effects from the parents, maybe a mutation, a random
 * potency and rarity. Keep it (and name it) or throw it away and try again.
 * Layout matches tools/gui.py mix().
 */
public final class MixerMenu extends LabTabMenu {

    // row * 9 + column (constants, for the switch below)
    static final int SEED_A = 10;
    static final int SEED_B = 12;
    static final int RESULT = 16;
    static final int FIRST_CHANCE = 27;
    static final int MUTATION = 35;
    static final int DISCARD = 39;
    static final int MIX = 40;
    static final int KEEP = 41;

    private static final String[] SUFFIX = {"Dream", "Haze", "Kush", "Diesel", "Glue", "Cookies", "Cake", "Breath",
            "Fire", "Frost", "Punch", "Runtz", "Gelato", "Zkittlez", "Widow", "Express"};

    /** Mixing state survives closing the menu (so a rolled strain isn't lost). */
    private static final class Session {
        String a;
        String b;
        Breeding.Result result;
        String name;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private final Session s;
    private boolean rolling;

    private MixerMenu(Player player, Machine machine) {
        super(player, machine, Tab.MIX);
        this.s = SESSIONS.computeIfAbsent(player.getUniqueId(), k -> new Session());
    }

    /** Opens the mixer of a Drug Lab (needs kushcraft.strainmaker). */
    public static void openFor(Player player, Machine machine) {
        if (!player.hasPermission("kushcraft.strainmaker")) {
            player.sendActionBar(Text.mm("<red>You are not allowed to breed strains."));
            return;
        }
        new MixerMenu(player, machine).open();
    }

    private Strain strain(String id) {
        return id == null ? null : KushCraft.get().strains().get(id);
    }

    private double cost() {
        return KushCraft.get().getConfig().getDouble("strain-maker.cost", 1500);
    }

    private static ItemStack seedIcon(Strain st) {
        return Items.strainItem(ItemType.SEED_PACK, st, 3, 1);
    }

    @Override
    protected void page() {
        Strain sa = strain(s.a), sb = strain(s.b);
        set(SEED_A, sa != null ? withLine(seedIcon(sa), "<dark_gray>Click to remove")
                : Items.icon("seed_pack", "<gray>Seed A", "<dark_gray>Click a seed below."));
        set(SEED_B, sb != null ? withLine(seedIcon(sb), "<dark_gray>Click to remove")
                : Items.icon("seed_pack", "<gray>Seed B", "<dark_gray>Click a seed below."));
        if (sa != null && sb != null) {
            int i = 0;
            for (Map.Entry<EffectType, Double> e : Breeding.odds(sa, sb).entrySet()) {
                if (i >= 8) {
                    break;
                }
                set(FIRST_CHANCE + i++, Items.icon(e.getKey().icon(), e.getKey().colored(),
                        "<white>" + Math.round(e.getValue() * 100) + "% <dark_gray>chance"));
            }
            List<String> odds = new ArrayList<>();
            odds.add("<white>35% <dark_gray>new effect · <white>20% <dark_gray>new colour");
            odds.add("<white>14% <dark_gray>new two-tone pattern");
            odds.add(Exotic.RAINBOW.wrap(String.format(java.util.Locale.ROOT, "%.1f%% Mythic",
                    Breeding.mythicChance(sa, sb) * 100)));
            double exotic = Breeding.exoticChance(sa, sb);
            if (exotic > 0) {
                odds.add(Exotic.PRISM.wrap(String.format(java.util.Locale.ROOT, "%.0f%% Exotic", exotic * 100)));
            } else {
                odds.add("<dark_gray>Exotic: only from two Mythic seeds");
            }
            Strain recipe = Breeding.recipe(sa, sb, KushCraft.get().strains().all());
            if (recipe != null) {
                odds.add(Exotic.PRISM.wrap(String.format(java.util.Locale.ROOT, "%.0f%% ", Breeding.DISCOVERY * 100))
                        + recipe.colored() + " <gray>!");
            }
            set(MUTATION, Items.icon("ui_dna", "<light_purple>Mutation", odds));
        }
        if (s.result != null) {
            set(RESULT, Items.glint(resultIcon(s.result), true));
            set(KEEP, Items.icon("ui_confirm", "<green><bold>Keep it", s.result.discovered() != null
                    ? "<dark_gray>Get " + seedsGiven() + " seeds." : "<dark_gray>Name it, get " + seedsGiven() + " seeds."));
            set(DISCARD, Items.icon("ui_cancel", "<red>Throw away"));
        } else if (!rolling) {
            set(RESULT, Items.icon("ui_info", "<gray>???"));
            boolean ready = sa != null && sb != null;
            set(MIX, Items.glint(Items.icon("ui_dna", ready ? "<green><bold>MIX " + "<gold>"
                    + KushCraft.get().economy().format(cost()) : "<gray>MIX",
                    ready ? "<dark_gray>Uses one seed of each." : "<dark_gray>Pick two seeds first."), ready));
        }
    }

    private ItemStack resultIcon(Breeding.Result r) {
        List<String> lore = new ArrayList<>();
        lore.add(r.type().colored() + " <dark_gray>·</dark_gray> <white>" + r.potency() + "% THC"
                + " <dark_gray>·</dark_gray> " + r.rarity().colored() + (r.jackpot() ? " <gold>JACKPOT!" : ""));
        lore.add(r.climate().colored() + " <gray>climate" + (r.flavor().isEmpty() ? ""
                : " <dark_gray>·</dark_gray> <gray>" + Text.escape(r.flavor())));
        for (EffectType e : r.effects()) {
            lore.add(" " + e.colored() + (r.mutations().contains(e) ? " <light_purple>✦ new!" : ""));
        }
        if (r.look().pattern() != dev.kushcraft.strains.BudPattern.NONE) {
            lore.add("<color:" + Text.hex(Strain.brighten(r.look().accent())) + ">" + r.look().pattern().display() + "</color>");
        }
        if (r.discovered() != null) {
            lore.add(r.look().exotic().wrap("✦ You found " + Text.escape(r.discovered().name()) + "! ✦"));
            lore.add("<gray>Keep it for its seeds.");
        } else if (r.look().exotic() != Exotic.NONE) {
            lore.add(r.look().exotic().wrap("✦ " + r.look().exotic().display() + " look! ✦"));
        } else if (r.newLook()) {
            lore.add("<light_purple>✦ New colours!");
        }
        String name = Text.escape(r.discovered() != null ? r.discovered().name() : s.name == null ? "New strain" : s.name);
        ItemStack icon = Items.icon("bud_dried", r.look().exotic() != Exotic.NONE ? r.look().exotic().wrap(name)
                : "<white>" + name, lore);
        icon.editMeta(m -> Items.look(m, r.look(), false));
        return icon;
    }

    private static ItemStack withLine(ItemStack it, String line) {
        it.editMeta(m -> {
            List<net.kyori.adventure.text.Component> l = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
            l.add(Text.mm(""));
            l.add(Text.mm(line));
            m.lore(l);
        });
        return it;
    }

    private int seedsGiven() {
        return Math.max(1, KushCraft.get().getConfig().getInt("strain-maker.seeds-given", 3));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (rolling) {
            return;
        }
        switch (slot) {
            case SEED_A, SEED_B -> {
                if (s.result != null) {
                    keepOrDiscard();
                    return;
                }
                if (slot == SEED_A) {
                    s.a = null;
                } else {
                    s.b = null;
                }
                clickSound();
                render();
            }
            case MIX -> mix();
            case KEEP -> keep();
            case DISCARD -> {
                if (s.result != null) {
                    s.result = null;
                    s.name = null;
                    player.playSound(player.getLocation(), "minecraft:block.composter.empty", SoundCategory.MASTER, 0.8f, 1f);
                    render();
                }
            }
            default -> {
            }
        }
    }

    private void keepOrDiscard() {
        player.sendActionBar(Text.mm("<yellow>Keep or throw away the new strain first."));
        failSound();
    }

    private int countSeeds(String id) {
        return InventoryUtil.count(player, it -> Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null
                && Items.strain(it).id().equals(id));
    }

    private void removeSeed(String id) {
        InventoryUtil.remove(player, it -> Items.type(it) == ItemType.SEED_PACK && Items.strain(it) != null
                && Items.strain(it).id().equals(id), 1);
    }

    private void mix() {
        KushCraft plugin = KushCraft.get();
        Strain sa = strain(s.a), sb = strain(s.b);
        if (s.result != null) {
            keepOrDiscard();
            return;
        }
        if (sa == null || sb == null) {
            player.sendActionBar(Text.mm("<red>Click two seeds in your inventory first."));
            failSound();
            return;
        }
        int max = plugin.getConfig().getInt("strain-maker.max-per-player", 25);
        if (!player.hasPermission("kushcraft.admin") && plugin.strains().countCreatedBy(player.getUniqueId()) >= max) {
            player.sendActionBar(Text.mm("<red>You already bred " + max + " strains."));
            failSound();
            return;
        }
        if (countSeeds(s.a) < (s.a.equals(s.b) ? 2 : 1) || countSeeds(s.b) < 1) {
            player.sendActionBar(Text.mm("<red>You need one seed of each parent in your inventory."));
            failSound();
            return;
        }
        if (!plugin.economy().withdraw(player, cost())) {
            player.sendActionBar(Text.mm("<red>Mixing costs " + plugin.economy().format(cost()) + "."));
            failSound();
            return;
        }
        removeSeed(s.a);
        removeSeed(s.b);
        Breeding.Result result = Breeding.cross(sa, sb, RANDOM, plugin.strains().all());
        if (result.jackpot()) {
            plugin.awards().jackpot(player);
        }
        rolling = true;
        render();
        List<EffectType> all = List.of(EffectType.values());
        new BukkitRunnable() {
            int frame;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    finish();
                    cancel();
                    return;
                }
                if (frame++ < 14) {
                    EffectType e = all.get(ThreadLocalRandom.current().nextInt(all.size()));
                    inv.setItem(RESULT, Items.icon(e.icon(), "<light_purple>Mixing..."));
                    player.playSound(player.getLocation(), "minecraft:block.note_block.hat", SoundCategory.MASTER, 0.6f,
                            0.6f + frame * 0.08f);
                    return;
                }
                finish();
                cancel();
            }

            private void finish() {
                rolling = false;
                s.result = result;
                s.name = autoName(sa, sb);
                boolean great = result.jackpot() || result.rarity().ordinal() >= 3 || result.newLook();
                player.playSound(player.getLocation(), great ? "minecraft:ui.toast.challenge_complete"
                        : "minecraft:entity.player.levelup", SoundCategory.MASTER, 0.7f, 1.2f);
                if (player.getOpenInventory().getTopInventory() == inv) {
                    render();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void keep() {
        if (s.result == null) {
            return;
        }
        if (s.result.discovered() != null) {
            discover(s.result.discovered());
            render();
            return;
        }
        ChatInput.ask(player, "<green>Name your new strain!</green> <gray>Type a name, or <white>ok</white> to keep <white>"
                + Text.escape(s.name) + "</white>.", text -> {
            if (!text.equalsIgnoreCase("ok")) {
                String n = clean(text);
                if (n.length() < 2 || n.length() > 24) {
                    player.sendMessage(Text.msg("<red>Names must be 2-24 letters/numbers."));
                    reopen();
                    return;
                }
                s.name = n;
            }
            register();
            reopen();
        }, this::reopen);
    }

    private void register() {
        KushCraft plugin = KushCraft.get();
        Breeding.Result r = s.result;
        if (r == null) {
            return;
        }
        if (plugin.strains().nameTaken(s.name)) {
            player.sendMessage(Text.msg("<red>That name is taken - press Keep again and pick another."));
            return;
        }
        Strain st = plugin.strains().create(s.name, r, player.getUniqueId(), player.getName());
        InventoryUtil.give(player, Items.strainItem(ItemType.SEED_PACK, st, 3, seedsGiven()));
        plugin.awards().bred(player, st.rarity());
        s.result = null;
        s.name = null;
        player.playSound(player.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.7f, 1.2f);
        Bukkit.broadcast(Text.msg("<white>" + Text.escape(player.getName()) + " <gray>bred a " + st.rarity().colored()
                + " <gray>strain: " + st.colored() + " <gray>(" + st.type().colored() + "<gray>, " + st.potency() + "% THC)"));
        if (st.exotic() != Exotic.NONE) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                o.playSound(o.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.6f, 1.4f);
            }
        }
    }

    /** Crossing the two Mythic parents of a built-in Exotic strain found it: its seeds, no name needed. */
    private void discover(Strain st) {
        KushCraft plugin = KushCraft.get();
        InventoryUtil.give(player, Items.strainItem(ItemType.SEED_PACK, st, 3, seedsGiven()));
        plugin.awards().bred(player, st.rarity());
        s.result = null;
        s.name = null;
        Bukkit.broadcast(Text.msg("<white>" + Text.escape(player.getName()) + " <gray>discovered the "
                + st.rarity().colored() + " <gray>strain " + st.colored() + "<gray>!"));
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.playSound(o.getLocation(), "minecraft:ui.toast.challenge_complete", SoundCategory.MASTER, 0.6f, 1.4f);
        }
    }

    private String autoName(Strain sa, Strain sb) {
        String first = sa.name().split(" ")[0];
        String[] bw = sb.name().split(" ");
        String n = first + " " + bw[bw.length - 1];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int tries = 0;
        while ((KushCraft.get().strains().nameTaken(n) || n.equalsIgnoreCase(sa.name()) || n.equalsIgnoreCase(sb.name()))
                && tries++ < 40) {
            n = first + " " + SUFFIX[r.nextInt(SUFFIX.length)] + (tries > 16 ? " " + (tries - 15) : "");
        }
        return n;
    }

    static String clean(String text) {
        return text.replaceAll("[^A-Za-z0-9 '\\-]", "").replaceAll(" +", " ").trim();
    }

    private void reopen() {
        if (player.isOnline()) {
            open();
        }
    }

    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (rolling || Items.type(item) != ItemType.SEED_PACK || Items.strain(item) == null) {
            return;
        }
        if (s.result != null) {
            keepOrDiscard();
            return;
        }
        String id = Items.strain(item).id();
        if (s.a == null) {
            s.a = id;
        } else {
            s.b = id;
        }
        clickSound();
        render();
    }
}
