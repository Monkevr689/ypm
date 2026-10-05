package dev.kushcraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** MiniMessage helpers. Everything shown to players goes through here. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_green>[<green>Kush</green><dark_green>]</dark_green> ";

    private Text() {
    }

    /** Parses MiniMessage and removes the default italic used by item names/lore. */
    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component msg(String s) {
        return MM.deserialize(PREFIX + s);
    }

    public static List<Component> lines(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            out.add(mm(l));
        }
        return out;
    }

    public static String escape(String s) {
        return MM.escapeTags(s);
    }

    public static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    public static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    public static String stars(int quality) {
        int q = Math.max(1, Math.min(5, quality));
        return "<gold>" + "★".repeat(q) + "</gold><dark_gray>" + "☆".repeat(5 - q) + "</dark_gray>";
    }

    public static String time(int seconds) {
        seconds = Math.max(0, seconds);
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    public static String number(double v) {
        if (v == Math.rint(v)) {
            return String.format(Locale.ROOT, "%,d", (long) v);
        }
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    public static String titleCase(String id) {
        StringBuilder b = new StringBuilder();
        for (String part : id.toLowerCase(Locale.ROOT).split("[_ ]+")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!b.isEmpty()) {
                b.append(' ');
            }
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    /** Simple progress bar made of coloured squares. */
    public static String bar(double fraction, int width, String on, String off) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * width);
        return "<" + on + ">" + "■".repeat(filled) + "</" + on + "><" + off + ">" + "■".repeat(width - filled)
                + "</" + off + ">";
    }
}
