package dev.kushcraft.listener;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.Dose;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.gui.RollerMenu;
import dev.kushcraft.catalog.Catalog;
import dev.kushcraft.item.ItemType;
import dev.kushcraft.item.Items;
import dev.kushcraft.machine.Machine;
import dev.kushcraft.plant.Plant;
import dev.kushcraft.strain.Strain;
import dev.kushcraft.util.BlockKey;
import dev.kushcraft.util.InventoryUtil;
import dev.kushcraft.util.Protection;
import dev.kushcraft.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Right/left clicks: machines, planting seeds, smoking, eating, the guide book. */
public final class InteractListener implements Listener {

    private final KushCraft plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public InteractListener(KushCraft plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("deprecation") // BlockType#isInteractable: no replacement yet, still works
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        Action a = e.getAction();

        // no trampling farmland under plants
        if (a == Action.PHYSICAL && b != null && b.getType() == Material.FARMLAND
                && plugin.plants().at(BlockKey.of(b).up()) != null) {
            e.setCancelled(true);
            return;
        }
        if (e.getHand() != EquipmentSlot.HAND) {
            if (a == Action.RIGHT_CLICK_BLOCK && plugin.machines().at(b) != null) {
                e.setCancelled(true);
            }
            return;
        }
        ItemStack item = e.getItem();
        ItemType type = Items.type(item);

        // ---- machines ----------------------------------------------------
        Machine m = plugin.machines().at(b);
        if (m != null) {
            if (a == Action.LEFT_CLICK_BLOCK) {
                e.setCancelled(true);
                if (Protection.canBuild(p, b)) {
                    plugin.machines().breakMachine(m, p);
                } else {
                    p.sendActionBar(Text.mm("<red>You can't break that here."));
                }
                return;
            }
            if (a == Action.RIGHT_CLICK_BLOCK) {
                if (p.isSneaking() && item != null && item.getType().isBlock() && (type == null || type.machine() != null)) {
                    return; // sneak-place blocks / machines against the machine like vanilla
                }
                e.setCancelled(true);
                if (!p.hasPermission("kushcraft.use")) {
                    return;
                }
                if (m.type() == dev.kushcraft.machine.MachineType.PLANTER_BOX && kindFor(type) != null) {
                    plant(p, item, type, b);
                    return;
                }
                openMachine(p, m);
                return;
            }
        }

        if (type == null || !p.hasPermission("kushcraft.use")) {
            return;
        }

        // ---- planting ----------------------------------------------------
        if (kindFor(type) != null) {
            if (a == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
                if (e.getBlockFace() != BlockFace.UP) {
                    p.sendActionBar(Text.mm("<yellow>Click the <white>top</white> of the soil to plant."));
                    return;
                }
                plant(p, item, type, b);
            }
            return;
        }

        // ---- hiring a worker -----------------------------------------------
        if (dev.kushcraft.worker.WorkerType.of(type) != null) {
            if (a == Action.RIGHT_CLICK_BLOCK) {
                e.setCancelled(true);
                plugin.workers().hire(p, item, b, e.getBlockFace());
            } else if (a == Action.RIGHT_CLICK_AIR) {
                p.sendActionBar(Text.mm("<yellow>Right-click the ground where they should work."));
            }
            return;
        }

        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (type.machine() != null) {
            return; // placed as a block
        }
        if (a == Action.RIGHT_CLICK_BLOCK && b != null && b.getType().asBlockType() != null
                && b.getType().asBlockType().isInteractable() && !p.isSneaking()) {
            return; // let doors, chests... work
        }
        if (use(p, item, type)) {
            e.setUseItemInHand(Event.Result.DENY);
            e.setUseInteractedBlock(Event.Result.DENY);
        }
    }

    private void openMachine(Player p, Machine m) {
        switch (m.type()) {
            case LAB_STATION -> new dev.kushcraft.gui.LabMenu(p, m).open();
            case STRAIN_MAKER -> dev.kushcraft.gui.MixerMenu.openFor(p, m);
            case ROLLING_TABLE -> new RollerMenu(p, m).open();
            case DEALER -> new dev.kushcraft.gui.ShopMenu(p, true).open();
            case DRYING_RACK -> plugin.machines().useRack(p, m);
            case GROW_LAMP -> p.sendActionBar(Text.mm("<light_purple>Grow Lamp <gray>- lights up and speeds up plants within "
                    + plugin.getConfig().getInt("growth.lamp-radius", 6) + " blocks."));
            case PLANTER_BOX -> p.sendActionBar(Text.mm("<green>Planter Box <gray>- plant seeds or spores on top."));
        }
    }

    private static Plant.Kind kindFor(ItemType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case SEED_PACK -> Plant.Kind.CANNABIS;
            case MUSHROOM_SPORES -> Plant.Kind.MUSHROOM;
            case COCA_SEEDS -> Plant.Kind.COCA;
            case POPPY_SEEDS -> Plant.Kind.POPPY;
            case PEYOTE_SEEDS -> Plant.Kind.PEYOTE;
            default -> null;
        };
    }

    private void plant(Player p, ItemStack item, ItemType type, Block soil) {
        Plant.Kind kind = kindFor(type);
        Strain s = kind == Plant.Kind.CANNABIS ? Items.strain(item) : null;
        if (kind == Plant.Kind.CANNABIS && s == null) {
            p.sendActionBar(Text.mm("<red>These seeds are from a strain that no longer exists."));
            return;
        }
        if (plugin.plants().plant(p, soil, kind, s)) {
            if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                item.setAmount(item.getAmount() - 1);
            }
            p.swingMainHand();
        }
    }

    private boolean onCooldown(Player p, long ms) {
        long now = System.currentTimeMillis();
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < ms) {
            return true;
        }
        cooldown.put(p.getUniqueId(), now);
        return false;
    }

    /** Uses a consumable / tool. Returns true when the click was handled. */
    private boolean use(Player p, ItemStack item, ItemType type) {
        switch (type) {
            case GROWER_GUIDE -> {
                dev.kushcraft.gui.TabMenu.openMain(p);
                return true;
            }
            case JOINT, BLUNT, VAPE_PEN -> {
                if (!onCooldown(p, 900)) {
                    smoke(p, item, type);
                }
                return true;
            }
            case WAX -> {
                if (!onCooldown(p, 1500)) {
                    dab(p, item);
                }
                return true;
            }
            case BONG -> {
                if (!onCooldown(p, 1500)) {
                    bong(p);
                }
                return true;
            }
            case SPACE_BROWNIE, GUMMIES -> {
                if (!onCooldown(p, 800)) {
                    consume(p, item, type);
                }
                return true;
            }
            case BUD_DRIED, HASH, MOON_ROCK -> {
                p.sendActionBar(Text.mm("<gray>Smoke it with a <aqua>Bong</aqua>"
                        + (type == ItemType.BUD_DRIED ? ", or roll / cook it in a <green>Drug Lab</green>." : ".")));
                return true;
            }
            case BUD_FRESH -> {
                p.sendActionBar(Text.mm("<gray>Fresh buds need drying first - <green>Drug Lab</green> > Dry."));
                return true;
            }
            case COCA_LEAVES, POPPY_POD -> {
                p.sendActionBar(Text.mm("<gray>Cook it in a <green>Drug Lab</green> > Cook."));
                return true;
            }
            case FERTILIZER -> {
                p.sendActionBar(Text.mm("<gray>Right-click a growing plant with it."));
                return true;
            }
            default -> {
                if (Catalog.dose(type) != null) {
                    if (!onCooldown(p, 800)) {
                        consume(p, item, type);
                    }
                    return true;
                }
                return false;
            }
        }
    }

    // ------------------------------------------------------------------

    private void smoke(Player p, ItemStack item, ItemType type) {
        Strain s = Items.strain(item);
        if (s == null) {
            p.sendActionBar(Text.mm("<red>This strain no longer exists."));
            return;
        }
        int q = Items.quality(item);
        int max = Items.maxHits(type);
        int hits = Items.hits(item);
        if (hits <= 0) {
            hits = max;
        }
        Dose d = switch (type) {
            case JOINT -> Dose.strain(s, q, 40, 8);
            case VAPE_PEN -> Dose.strain(s, q, 35, 7);
            default -> Dose.strain(s, q, 55, 11);
        };
        plugin.effects().apply(p, d);
        plugin.awards().used(p, type);
        if (type == ItemType.VAPE_PEN) {
            vapeFx(p);
        } else {
            smokeFx(p, 1.0);
        }
        int left = hits - 1;
        if (item.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(left > 0 ? Items.strainItem(type, s, q, 1, left) : null);
        } else {
            item.setAmount(item.getAmount() - 1);
            if (left > 0) {
                InventoryUtil.give(p, Items.strainItem(type, s, q, 1, left));
            }
        }
        if (left <= 0) {
            p.sendActionBar(Text.mm("<gray>That was the last hit."));
        }
    }

    private void bong(Player p) {
        ItemStack off = p.getInventory().getItemInOffHand();
        ItemType ot = Items.type(off);
        ItemStack load = (ot == ItemType.BUD_DRIED || ot == ItemType.HASH || ot == ItemType.MOON_ROCK) ? off : null;
        if (load == null) {
            load = InventoryUtil.first(p, it -> {
                ItemType t = Items.type(it);
                return t == ItemType.BUD_DRIED || t == ItemType.HASH || t == ItemType.MOON_ROCK;
            });
        }
        if (load == null) {
            p.sendActionBar(Text.mm("<red>Nothing to smoke! <gray>You need Dried Bud, Hash or a Moon Rock."));
            return;
        }
        ItemType t = Items.type(load);
        Strain s = Items.strain(load);
        if (s == null) {
            return;
        }
        int q = Items.quality(load);
        Dose d = switch (t) {
            case HASH -> Dose.strain(s, q, 130, 24);
            case MOON_ROCK -> Dose.strain(s, q, 220, 36);
            default -> Dose.strain(s, q, 80, 16);
        };
        load.setAmount(load.getAmount() - 1);
        p.getWorld().playSound(p.getLocation(), "minecraft:block.bubble_column.upwards_inside", SoundCategory.PLAYERS, 1f, 1.2f);
        plugin.effects().apply(p, d);
        plugin.awards().used(p, t);
        smokeFx(p, t == ItemType.MOON_ROCK ? 2.2 : 1.7);
        p.sendActionBar(Text.mm("<gray>You rip the bong: " + s.colored() + " <dark_gray>" + t.display()));
    }

    /** Wax: one very strong dab. */
    private void dab(Player p, ItemStack item) {
        Strain s = Items.strain(item);
        if (s == null) {
            return;
        }
        Dose d = Dose.strain(s, Items.quality(item), 200, 34);
        item.setAmount(item.getAmount() - 1);
        p.getWorld().playSound(p.getLocation(), "minecraft:block.lava.pop", SoundCategory.PLAYERS, 1f, 1.4f);
        plugin.effects().apply(p, d);
        plugin.awards().used(p, ItemType.WAX);
        smokeFx(p, 2.0);
        p.sendActionBar(Text.mm("<gray>You take a dab of " + s.colored() + " <gray>wax."));
    }

    private void vapeFx(Player p) {
        Location eye = p.getEyeLocation();
        Location mouth = eye.clone().add(eye.getDirection().multiply(0.45)).add(0, -0.15, 0);
        p.getWorld().spawnParticle(Particle.CLOUD, mouth, 8, 0.1, 0.06, 0.1, 0.01);
        p.getWorld().playSound(p.getLocation(), "minecraft:block.fire.extinguish", SoundCategory.PLAYERS, 0.15f, 2f);
    }

    private void smokeFx(Player p, double amount) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().multiply(0.45);
        Location mouth = eye.clone().add(dir).add(0, -0.15, 0);
        int count = (int) (6 * amount);
        p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, mouth, count, 0.08, 0.05, 0.08, 0.02);
        p.getWorld().spawnParticle(Particle.SMOKE, mouth, count * 2, 0.1, 0.1, 0.1, 0.02);
        p.getWorld().playSound(p.getLocation(), "minecraft:block.fire.extinguish", SoundCategory.PLAYERS, 0.25f, 1.8f);
        if (ThreadLocalRandom.current().nextDouble() < 0.18 * amount) {
            p.getWorld().playSound(p.getLocation(), "minecraft:entity.panda.sneeze", SoundCategory.PLAYERS, 0.7f, 0.6f);
            p.sendActionBar(Text.mm("<gray>*cough cough*"));
        }
    }

    private void consume(Player p, ItemStack item, ItemType type) {
        Dose d;
        String sound = "minecraft:entity.generic.eat";
        int food = 0;
        if (type == ItemType.SPACE_BROWNIE || type == ItemType.GUMMIES) {
            Strain s = Items.strain(item);
            if (s == null) {
                return;
            }
            d = type == ItemType.SPACE_BROWNIE
                    ? Dose.strain(s, Items.quality(item), 300, 30).add(EffectType.MUNCHIES, 120)
                    .delay(15, "<green>The brownie kicks in...")
                    : Dose.strain(s, Items.quality(item), 420, 26).delay(25, "<green>The gummies kick in...");
            food = type == ItemType.SPACE_BROWNIE ? 5 : 1;
            p.sendActionBar(Text.mm("<gray>Tasty. <dark_gray>Doesn't feel like anything... yet."));
        } else {
            d = Catalog.dose(type);
            if (d == null) {
                return;
            }
            Particle.DustOptions dust = null;
            switch (type) {
                case MAGIC_MUSHROOM, PEYOTE_BUTTON -> food = 1;
                case SHROOM_TEA, LEAN -> {
                    sound = "minecraft:entity.generic.drink";
                    food = 2;
                }
                case LUCID_TAB, MESCALINE, ECSTASY -> sound = "minecraft:block.amethyst_block.chime";
                case BLUE_CRYSTAL -> {
                    sound = "minecraft:entity.sniffer.sniffing";
                    dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(0x72D6FF), 1f);
                }
                case COCAINE, KETAMINE, ANGEL_DUST -> {
                    sound = "minecraft:entity.sniffer.sniffing";
                    dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(type == ItemType.ANGEL_DUST ? 0xD8B880
                            : 0xFFFFFF), 1f);
                }
                case CRACK, DMT -> {
                    sound = "minecraft:block.fire.extinguish";
                    smokeFx(p, 1.4);
                }
                case HEROIN, OPIUM -> {
                    sound = "minecraft:block.beacon.deactivate";
                    p.sendActionBar(Text.mm("<gray>A heavy warmth washes over you..."));
                }
                case PIXIE_DUST -> {
                    sound = "minecraft:block.amethyst_block.resonate";
                    p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0.5);
                }
                default -> {
                }
            }
            if (dust != null) {
                p.getWorld().spawnParticle(Particle.DUST, p.getEyeLocation(), 12, 0.15, 0.1, 0.15, 0, dust);
            }
            // too high already? psychedelics can go wrong
            double limit = Math.max(1, plugin.getConfig().getDouble("effects.green-out-at", 100));
            if (Catalog.psychedelic(type) && plugin.effects().high(p) > limit * 0.6
                    && ThreadLocalRandom.current().nextDouble() < 0.5) {
                d.add(EffectType.BAD_TRIP, 45);
                p.sendMessage(Text.msg("<dark_red>Uh oh... this one is going wrong."));
            }
        }
        item.setAmount(item.getAmount() - 1);
        if (type == ItemType.SHROOM_TEA || type == ItemType.LEAN) {
            InventoryUtil.give(p, new ItemStack(Material.GLASS_BOTTLE));
        }
        if (food > 0) {
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + food));
            p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + food * 0.6f));
        }
        p.getWorld().playSound(p.getLocation(), sound, SoundCategory.PLAYERS, 0.9f, 1f);
        plugin.effects().apply(p, d);
        plugin.awards().used(p, type);
    }
}
