package dev.smpsuite;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** MiniMessage helpers. */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<dark_aqua>SMP</dark_aqua> <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    /** MiniMessage text without the default italics item lore gets. */
    public static Component mm(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** MiniMessage to the old section-sign text some Bukkit methods still take. */
    public static String prompt(String s) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(mm(s));
    }

    public static List<Component> lines(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            out.add(mm(l));
        }
        return out;
    }

    public static void send(CommandSender to, String s) {
        to.sendMessage(mm(PREFIX + s));
    }

    public static String escape(String s) {
        return MM.escapeTags(s);
    }

    /** 1234.5 -> "1,234.5" (no trailing .0). */
    public static String num(double d) {
        if (Math.abs(d - Math.rint(d)) < 0.05) {
            return String.format(Locale.ROOT, "%,d", Math.round(d));
        }
        return String.format(Locale.ROOT, "%,.1f", d);
    }

    public static String pct(double d) {
        double p = d * 100;
        return (Math.abs(p - Math.rint(p)) < 0.05 ? String.format(Locale.ROOT, "%d", Math.round(p))
                : String.format(Locale.ROOT, "%.1f", p)) + "%";
    }

    /** 125 -> "2:05". */
    public static String time(long seconds) {
        seconds = Math.max(0, seconds);
        return seconds >= 60 ? (seconds / 60) + ":" + String.format(Locale.ROOT, "%02d", seconds % 60) : seconds + "s";
    }

    /** A bar of n cells, filled to frac. */
    public static String bar(double frac, int n, String on, String off) {
        int f = (int) Math.round(Math.max(0, Math.min(1, frac)) * n);
        return "<" + on + ">" + "|".repeat(f) + "</" + on + "><" + off + ">" + "|".repeat(n - f) + "</" + off + ">";
    }

    public static String title(String id) {
        String s = id.replace('_', ' ').replace('-', ' ').toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            b.append(up ? Character.toUpperCase(c) : c);
            up = c == ' ';
        }
        return b.toString();
    }
}
