package dev.kushcraft.gui;

import dev.kushcraft.KushCraft;
import dev.kushcraft.util.Text;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Builds inventory titles that paint a full custom background: the background
 * is a bitmap glyph of the kush:gui font, moved into place with negative-space
 * characters. Players without the resource pack get a plain title instead.
 * Must match tools/pack_meta.py.
 */
public final class GuiFont {

    private static final Key FONT = Key.key("kush", "gui");
    private static final Map<String, Character> GLYPHS = Map.of(
            "lab", '', "strain", '', "roller", '', "dealer", '');
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
        Character glyph = GLYPHS.get(gui);
        if (glyph == null || !KushCraft.get().pack().hasPack(viewer)) {
            return Text.mm("<dark_gray>" + miniMessageTitle);
        }
        String bg = space(-TITLE_X) + glyph + space(-(GUI_WIDTH + 1 - TITLE_X));
        Component background = Component.text(bg).font(FONT).color(NamedTextColor.WHITE);
        return Component.empty().append(background).append(Text.mm("<white>" + miniMessageTitle));
    }
}
