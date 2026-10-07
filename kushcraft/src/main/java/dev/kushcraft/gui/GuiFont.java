package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Builds inventory titles that paint a full custom background: the background
 * is a bitmap glyph of the kush:gui font, moved into place with negative-space
 * characters. Players without the resource pack get a plain title instead.
 * Must match tools/pack_meta.py.
 */
public final class GuiFont {

    private static final Key FONT = Key.key("kush", "gui");
    /**
     * Every menu background, in glyph order: the n-th name is drawn by
     * character U+E000+n. Same list as GUIS in tools/pack_meta.py
     * (tools/validate_pack.py checks that they match).
     */
    public static final List<String> GUIS = List.of(
            "shop", "drugs", "trade", "cartel", "top", "awards", "cook", "roll", "dry", "mix", "recipe", "list",
            "gear", "worker", "guide", "admin");
    private static final int GUI_WIDTH = 176;
    private static final int TITLE_X = 8;

    private GuiFont() {
    }

    /** Characters that move the cursor by -px (negative) or +px (positive). */
    public static String space(int px) {
        StringBuilder b = new StringBuilder();
        boolean negative = px < 0;
        int left = Math.abs(px);
        for (int i = 8; i >= 0; i--) {
            int v = 1 << i;
            while (left >= v) {
                b.append((char) ((negative ? 0xF801 : 0xF821) + i));
                left -= v;
            }
        }
        return b.toString();
    }

    public static Component title(Player viewer, String gui, String miniMessageTitle) {
        return title(viewer, gui, miniMessageTitle, true);
    }

    /** showTitle false: pack users see only the background (tab pages have their labels drawn in). */
    public static Component title(Player viewer, String gui, String miniMessageTitle, boolean showTitle) {
        int index = GUIS.indexOf(gui);
        if (index < 0) {
            KushCraft.get().getLogger().warning("No menu background named " + gui);
        }
        if (index < 0 || !KushCraft.get().pack().hasPack(viewer)) {
            return Text.mm("<dark_gray>" + miniMessageTitle);
        }
        String bg = space(-TITLE_X) + (char) (0xE000 + index) + space(-(GUI_WIDTH + 1 - TITLE_X));
        Component background = Component.text(bg).font(FONT).color(NamedTextColor.WHITE);
        return Component.empty().append(background).append(Text.mm(showTitle ? "<white>" + miniMessageTitle : ""));
    }
}
