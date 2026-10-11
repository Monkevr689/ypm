package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.items.Items;
import dev.kushcraft.reset.SeasonReset;
import dev.kushcraft.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/**
 * Admin &gt; Reset everyone: pick how much to wipe. The click makes a checked
 * backup, shows it in chat and asks you to type the code that comes with it,
 * exactly like /kush reset. Nothing is wiped until the code is typed.
 */
public final class AdminResetMenu extends Menu {

    private static final int ECONOMY = 10;
    private static final int KUSHCRAFT = 11;
    private static final int EVERYTHING = 12;
    private static final int BACK = 45;

    public AdminResetMenu(Player player) {
        super(player, 6, "admin", "Reset everyone", false);
    }

    @Override
    public void render() {
        inv.clear();
        backButton(BACK);
        KushCraft plugin = KushCraft.get();
        boolean allowed = player.hasPermission("kushcraft.reset");
        set(9, Items.icon("ui_admin", "<red><bold>New season", "<gray>Wipes every player's progress.",
                "<gray>A checked backup is made first."));
        SeasonReset.Scope def = plugin.reset().defaultScope();
        scope(ECONOMY, "ui_wallet", "<yellow>Money & ranks", SeasonReset.Scope.ECONOMY, def, allowed);
        scope(KUSHCRAFT, "tab_cartel", "<gold>All KushCraft progress", SeasonReset.Scope.KUSHCRAFT, def, allowed);
        scope(EVERYTHING, "ui_crown", "<red>Everything", SeasonReset.Scope.EVERYTHING, def, allowed);
        set(18, Items.icon("ui_info", "<white><bold>How it works"));
        set(19, Items.icon("ui_info", "<gray>1. Click a button above.",
                "<gray>2. The server saves, makes a backup", "<gray>   and checks it.",
                "<gray>3. You get a code in chat.", "<gray>4. Type the code to start the season."));
        set(20, Items.icon("ui_info", "<gray>Builds and chests are never touched.",
                "<gray>The transaction log is kept.", "<gray>" + plugin.players().size() + " players, "
                        + plugin.workers().all().size() + " workers right now."));
        if (!allowed) {
            set(21, Items.icon("ui_pause", "<red>You need kushcraft.reset", "<gray>Ask the server owner."));
        }
    }

    private void scope(int slot, String icon, String name, SeasonReset.Scope scope, SeasonReset.Scope def, boolean allowed) {
        set(slot, Items.icon(icon, name + (scope == def ? " <green>(default)" : ""), List.of(
                "<gray>" + scope.wipes() + ".",
                allowed ? "<yellow>Click to start" : "<red>No permission")));
    }

    @Override
    public void click(int slot, ClickType click) {
        SeasonReset.Scope scope = switch (slot) {
            case ECONOMY -> SeasonReset.Scope.ECONOMY;
            case KUSHCRAFT -> SeasonReset.Scope.KUSHCRAFT;
            case EVERYTHING -> SeasonReset.Scope.EVERYTHING;
            default -> null;
        };
        if (scope == null) {
            return;
        }
        if (!player.hasPermission("kushcraft.reset")) {
            failSound();
            player.sendActionBar(Text.mm("<red>You need kushcraft.reset."));
            return;
        }
        KushCraft plugin = KushCraft.get();
        player.closeInventory();
        if (!plugin.reset().prepare(player, scope)) {
            return;
        }
        ChatInput.ask(player, "<red><bold>Type the code from above</bold> <gray>to reset <white>" + scope.id()
                        + "<gray>. Nothing has been wiped yet.",
                text -> plugin.reset().confirm(player, text),
                () -> player.sendMessage(Text.msg("<gray>Reset cancelled. Nothing was wiped; the backup is kept.")));
    }
}
