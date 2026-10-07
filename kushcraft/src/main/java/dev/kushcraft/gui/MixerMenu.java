package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.strain.Breeding;
import dev.kushcraft.strain.Exotic;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drug Lab > Mix. Your seeds lie on a tray (rows 2-3): click one for parent A,
 * the next for parent B (right-click: always B). Press MIX and see what you
 * get - effects from the parents, maybe a mutation, a new colour, a random
 * potency and rarity; the odds are on the card at the end of the bench.
 * Keep it (it gets a name by itself - Rename to pick your own) or throw it
 * away and try again. A Cook can do this for you (job: Mix strains).
 * Layout matches tools/gui.py mix_page().
 */
public final class MixerMenu extends LabTabMenu {

    // row * 9 + column
    static final int SEED_A = 9;
    static final int SEED_B = 11;
    static final int MIX = 13;
    static final int RESULT = 15;
    static final int CHANCES = 17;
    static final int FIRST_SEED = 18;
    static final int SEEDS = 18;
    static final int PREV = 36;
    static final int INFO = 37;
    static final int DISCARD = 39;
    static final int KEEP = 40;
    static final int RENAME = 41;
    static final int NEXT = 44;

    /** Mixing state survives closing the menu (so a rolled strain isn't lost). */
    private static final class Session {
        String a;
        String b;
        Breeding.Result result;
        String name;
        int page;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Random RANDOM = new Random();

    private final Session s;
    private boolean rolling;
    /** The strains on the tray right now (this page). */
    private List<Strain> shown = List.of();

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

    private int maxStrains() {
        return KushCraft.get().getConfig().getInt("strain-maker.max-per-player", 25);
    }

    /** Every strain you have seeds of (and how many), the rarest first. */
    private Map<Strain, Integer> seeds() {
        Map<String, Integer> count = new HashMap<>();
        Map<String, Strain> byId = new HashMap<>();
        for (ItemStack it : player.getInventory().getStorageContents()) {
            Strain st = Items.type(it) == ItemType.SEED_PACK ? Items.strain(it) : null;
            if (st != null) {
                count.merge(st.id(), it.getAmount(), Integer::sum);
                byId.putIfAbsent(st.id(), st);
            }
        }
        List<Strain> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparingInt((Strain st) -> -st.rarity().ordinal()).thenComparingInt(st -> -st.potency())
                .thenComparing(Strain::name));
        Map<Strain, Integer> out = new LinkedHashMap<>();
        for (Strain st : list) {
            out.put(st, count.get(st.id()));
        }
        return out;
    }

    private int pages(int n) {
        return Math.max(1, (n + SEEDS - 1) / SEEDS);
    }

    @Override
    protected void page() {
        Strain sa = strain(s.a), sb = strain(s.b);
        Map<Strain, Integer> mine = seeds();
        set(SEED_A, sa != null ? parentIcon(sa, mine.getOrDefault(sa, 0), "A")
                : Items.icon("seed_pack", "<gray>Parent A", "<dark_gray>Click seeds on the tray below."));
        set(SEED_B, sb != null ? parentIcon(sb, mine.getOrDefault(sb, 0), "B")
                : Items.icon("seed_pack", "<gray>Parent B", "<dark_gray>Click a second strain below."));
        // the seed tray
        List<Strain> all = new ArrayList<>(mine.keySet());
        s.page = Math.min(s.page, pages(all.size()) - 1);
        shown = all.subList(Math.min(all.size(), s.page * SEEDS), Math.min(all.size(), (s.page + 1) * SEEDS));
        for (int i = 0; i < shown.size(); i++) {
            Strain st = shown.get(i);
            boolean picked = st.id().equals(s.a) || st.id().equals(s.b);
            set(FIRST_SEED + i, Items.glint(trayIcon(st, mine.get(st), picked), picked));
        }
        if (all.isEmpty()) {
            set(FIRST_SEED + 4, Items.icon("ui_info", "<gray>No seeds in your inventory",
                    "<gray>Buy some in the Shop, or harvest", "<gray>your plants for their seeds."));
        }
        if (s.page > 0) {
            set(PREV, Items.icon("ui_back", "<gray>Previous seeds", "<dark_gray>Page " + s.page + "/" + pages(all.size())));
        }
        if (s.page < pages(all.size()) - 1) {
            set(NEXT, Items.icon("ui_arrow", "<gray>More seeds", "<dark_gray>Page " + (s.page + 2) + "/" + pages(all.size())));
        }
        if (sa != null && sb != null) {
            set(CHANCES, chancesIcon(sa, sb));
        } else {
            set(CHANCES, Items.icon("ui_dna", "<light_purple>Chances", "<dark_gray>Pick two strains to see",
                    "<dark_gray>what the mix can give."));
        }
        KushCraft plugin = KushCraft.get();
        int bred = plugin.strains().countCreatedBy(player.getUniqueId());
        set(INFO, Items.icon("ui_guide", "<aqua>Mixing",
                "<gray>Two strains make a new one: effects",
                "<gray>from the parents, sometimes a new one,",
                "<gray>new colours and a random rarity.",
                "<white>" + plugin.economy().format(cost()) + " <gray>a mix, uses one seed of each.",
                "<gray>You bred <white>" + bred + "/" + maxStrains() + "<gray> strains.",
                "<light_purple>Tip: <gray>a Cook can mix for you",
                "<gray>(their menu > job > Mix strains)."));
        if (s.result != null) {
            set(RESULT, Items.glint(resultIcon(s.result), true));
            set(KEEP, Items.glint(Items.icon("ui_confirm", "<green><bold>Keep it", s.result.discovered() != null
                    ? "<gray>Get " + seedsGiven() + " seeds of it."
                    : "<gray>As <white>" + Text.escape(s.name) + "<gray>, with " + seedsGiven() + " seeds."), true));
            if (s.result.discovered() == null) {
                set(RENAME, Items.icon("ui_rename", "<white>Rename", "<gray>Now: <white>" + Text.escape(s.name),
                        "<dark_gray>Type a new name in chat."));
            }
            set(DISCARD, Items.icon("ui_cancel", "<red>Throw away", "<dark_gray>Then mix again."));
        } else if (!rolling) {
            set(RESULT, Items.icon("ui_info", "<gray>???", "<dark_gray>The new strain shows up here."));
            boolean ready = sa != null && sb != null;
            set(MIX, Items.glint(Items.icon("ui_dna", ready ? "<green><bold>MIX</bold> <gold>"
                            + plugin.economy().format(cost()) : "<gray>MIX",
                    ready ? "<gray>Uses one seed of each parent." : "<dark_gray>Pick two strains first."), ready));
        }
    }

    private ItemStack parentIcon(Strain st, int have, String which) {
        List<String> lore = new ArrayList<>();
        lore.add(st.type().colored() + " <dark_gray>·</dark_gray> <white>" + st.potency() + "% THC <dark_gray>·</dark_gray> "
                + st.rarity().colored());
        for (EffectType e : st.effects()) {
            lore.add(" " + e.colored());
        }
        lore.add(have > 0 ? "<gray>" + have + " seeds in your inventory" : "<red>No seeds of it left!");
        lore.add("<dark_gray>Parent " + which + " · click to take it off");
        ItemStack it = Items.strainItem(ItemType.SEED_PACK, st, 3, 1);
        it.editMeta(m -> {
            m.itemName(Text.mm(st.colored()));
            m.lore(Text.lines(lore));
        });
        return it;
    }

    private ItemStack trayIcon(Strain st, int have, boolean picked) {
        List<String> lore = new ArrayList<>();
        lore.add(st.type().colored() + " <dark_gray>·</dark_gray> <white>" + st.potency() + "% THC <dark_gray>·</dark_gray> "
                + st.rarity().colored());
        List<String> effects = new ArrayList<>();
        for (EffectType e : st.effects()) {
            effects.add(e.colored());
        }
        if (!effects.isEmpty()) {
            lore.add(String.join("<dark_gray>, ", effects));
        }
        lore.add(picked ? "<green>Picked" : "<dark_gray>Click: parent A (or B) · Right-click: B");
        ItemStack it = Items.strainItem(ItemType.SEED_PACK, st, 3, Math.max(1, Math.min(64, have)));
        it.editMeta(m -> {
            m.itemName(Text.mm(st.colored()));
            m.lore(Text.lines(lore));
        });
        return it;
    }

    private ItemStack chancesIcon(Strain sa, Strain sb) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Effects it can get:");
        int i = 0;
        for (Map.Entry<EffectType, Double> e : Breeding.odds(sa, sb).entrySet()) {
            if (i++ >= 6) {
                break;
            }
            lore.add(" " + e.getKey().colored() + " <white>" + Math.round(e.getValue() * 100) + "%");
        }
        lore.add("<white>35% <gray>a new effect · <white>20% <gray>new colours");
        lore.add("<white>14% <gray>a two-tone pattern");
        lore.add(Exotic.RAINBOW.wrap(String.format(java.util.Locale.ROOT, "%.1f%% Mythic",
                Breeding.mythicChance(sa, sb) * 100)));
        if (sa.rarity() != dev.kushcraft.strain.Rarity.LEGENDARY || sb.rarity() != dev.kushcraft.strain.Rarity.LEGENDARY) {
            lore.add("<dark_gray>Two Legendary parents: " + Math.round(Breeding.MYTHIC * 100 + Breeding.LEGENDARY_PARENT * 200)
                    + "% Mythic");
        }
        double exotic = Breeding.exoticChance(sa, sb);
        lore.add(exotic > 0 ? Exotic.PRISM.wrap(String.format(java.util.Locale.ROOT, "%.0f%% Exotic", exotic * 100))
                : "<dark_gray>Exotic: only from two Mythic parents");
        Strain recipe = Breeding.recipe(sa, sb, KushCraft.get().strains().all());
        if (recipe != null) {
            lore.add(Exotic.PRISM.wrap(String.format(java.util.Locale.ROOT, "%.0f%% ", Breeding.DISCOVERY * 100))
                    + recipe.colored() + " <gray>!");
        }
        return Items.icon("ui_dna", "<light_purple>Chances", lore);
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
        if (r.look().pattern() != dev.kushcraft.strain.BudPattern.NONE) {
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

    private int seedsGiven() {
        return Math.max(1, KushCraft.get().getConfig().getInt("strain-maker.seeds-given", 3));
    }

    @Override
    protected void clickPage(int slot, ClickType click) {
        if (rolling) {
            return;
        }
        int tray = slot - FIRST_SEED;
        if (tray >= 0 && tray < SEEDS) {
            if (tray < shown.size()) {
                pick(shown.get(tray).id(), click.isRightClick());
            }
            return;
        }
        switch (slot) {
            case SEED_A, SEED_B -> {
                if (s.result != null) {
                    keepOrDiscard();
                    return;
                }
                if (slot == SEED_A) {
                    s.a = s.b;
                    s.b = null;
                } else {
                    s.b = null;
                }
                clickSound();
                render();
            }
            case PREV, NEXT -> {
                int pages = pages(seeds().size());
                int next = Math.max(0, Math.min(pages - 1, s.page + (slot == NEXT ? 1 : -1)));
                if (next != s.page) {
                    s.page = next;
                    clickSound();
                    render();
                }
            }
            case MIX -> mix();
            case KEEP -> keep();
            case RENAME -> rename();
            case DISCARD -> {
                if (s.result != null) {
                    s.result = null;
                    s.name = null;
                    player.playSound(player.getLocation(), "minecraft:block.composter.empty", SoundCategory.MASTER, 0.8f, 1f);
                    render();
                }
            }
            case INFO -> player.openBook(dev.kushcraft.guide.Guide.book(player));
            default -> {
            }
        }
    }

    /** A strain from the tray (or your inventory) becomes parent A, else B. */
    private void pick(String id, boolean asB) {
        if (s.result != null) {
            keepOrDiscard();
            return;
        }
        if (!asB && s.a == null) {
            s.a = id;
        } else if (!asB && id.equals(s.a)) {
            s.a = s.b;
            s.b = null;
        } else if (id.equals(s.b)) {
            s.b = null;
        } else {
            s.b = id;
        }
        clickSound();
        render();
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
            player.sendActionBar(Text.mm("<red>Pick two strains on the seed tray first."));
            failSound();
            return;
        }
        if (!player.hasPermission("kushcraft.admin") && plugin.strains().countCreatedBy(player.getUniqueId()) >= maxStrains()) {
            player.sendActionBar(Text.mm("<red>You already bred " + maxStrains() + " strains."));
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
                s.name = Breeding.childName(sa, sb, plugin.strains()::nameTaken);
                boolean great = result.jackpot() || result.rarity().ordinal() >= 3 || result.newLook();
                player.playSound(player.getLocation(), great ? "minecraft:ui.toast.challenge_complete"
                        : "minecraft:entity.player.levelup", SoundCategory.MASTER, 0.7f, 1.2f);
                if (player.getOpenInventory().getTopInventory() == inv) {
                    render();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /** Keeps the new strain under its name (no typing needed), or the found Exotic one. */
    private void keep() {
        if (s.result == null) {
            return;
        }
        if (s.result.discovered() != null) {
            discover(s.result.discovered());
            render();
            return;
        }
        if (KushCraft.get().strains().nameTaken(s.name)) {
            s.name = Breeding.childName(strainOr(s.a), strainOr(s.b), KushCraft.get().strains()::nameTaken);
        }
        register();
        render();
    }

    private Strain strainOr(String id) {
        Strain st = strain(id);
        return st != null ? st : KushCraft.get().strains().getOrDefault(id);
    }

    private void rename() {
        if (s.result == null || s.result.discovered() != null) {
            return;
        }
        ChatInput.ask(player, "<green>Name your new strain!</green> <gray>(2-24 letters) Now: <white>"
                + Text.escape(s.name), text -> {
            String n = clean(text);
            if (n.length() < 2 || n.length() > 24) {
                player.sendMessage(Text.msg("<red>Names must be 2-24 letters/numbers."));
            } else if (KushCraft.get().strains().nameTaken(n)) {
                player.sendMessage(Text.msg("<red>That name is taken."));
            } else {
                s.name = n;
            }
            reopen();
        }, this::reopen);
    }

    private void register() {
        KushCraft plugin = KushCraft.get();
        Breeding.Result r = s.result;
        if (r == null) {
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

    static String clean(String text) {
        return text.replaceAll("[^A-Za-z0-9 '\\-]", "").replaceAll(" +", " ").trim();
    }

    private void reopen() {
        if (player.isOnline()) {
            open();
        }
    }

    /** Clicking seeds in your own inventory picks them too. */
    @Override
    public void clickOwn(int slot, ItemStack item, ClickType click) {
        if (rolling || Items.type(item) != ItemType.SEED_PACK || Items.strain(item) == null) {
            return;
        }
        pick(Items.strain(item).id(), click.isRightClick());
    }
}
