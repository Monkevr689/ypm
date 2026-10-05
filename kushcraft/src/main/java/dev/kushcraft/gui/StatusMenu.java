package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.effect.EffectType;
import dev.kushcraft.item.Items;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Your high meter and active effects. */
public final class StatusMenu extends ListMenu {

    public StatusMenu(Player player) {
        super(player, "Your Status");
    }

    @Override
    protected List<ItemStack> entries() {
        List<ItemStack> out = new ArrayList<>();
        for (Map.Entry<EffectType, Integer> e : KushCraft.get().effects().active(player).entrySet()) {
            EffectType t = e.getKey();
            out.add(Items.icon(t.icon(), t.colored() + " <gray>" + Text.time(e.getValue()), "<gray>" + t.description()));
        }
        if (out.isEmpty()) {
            out.add(Items.icon("ui_info", "<gray>No effects right now", "<gray>Sober as a judge."));
        }
        int pending = KushCraft.get().effects().pending(player);
        if (pending > 0) {
            out.add(Items.icon("space_brownie", "<yellow>Kicking in soon...", "<gray>" + pending + " dose(s) on the way."));
        }
        return out;
    }

    @Override
    protected ItemStack header() {
        double limit = Math.max(1, KushCraft.get().getConfig().getDouble("effects.green-out-at", 100));
        double high = KushCraft.get().effects().high(player);
        return Items.icon("effect_euphoria", "<green>High: <white>" + (int) Math.round(Math.min(1, high / limit) * 100) + "%",
                "<gray>" + Text.bar(high / limit, 10, "green", "dark_gray"),
                "<gray>Hit 100% and you <green>green out</green>.",
                "<gray>It goes down by itself over time.");
    }

    @Override
    public void tick() {
        render();
    }
}
