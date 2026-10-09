package dev.smpsuite.team;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** A team (party): a name, a short tag, a colour, a leader and members. */
public final class Team {

    final UUID id;
    String name;
    String tag;
    NamedTextColor color;
    UUID leader;
    final Set<UUID> members = new LinkedHashSet<>();
    boolean shareXp = true;
    /** Anyone may /party join without an invite. */
    boolean open;
    long created;

    Team(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String tag() {
        return tag;
    }

    public NamedTextColor color() {
        return color;
    }

    public UUID leader() {
        return leader;
    }

    public Set<UUID> members() {
        return members;
    }

    public boolean shareXp() {
        return shareXp;
    }

    public boolean open() {
        return open;
    }

    /** MiniMessage colour name, e.g. "aqua". */
    public String colorName() {
        String n = NamedTextColor.NAMES.key(color);
        return n == null ? "white" : n.toLowerCase(Locale.ROOT);
    }

    public String colored() {
        return "<" + colorName() + ">" + net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().escapeTags(name)
                + "</" + colorName() + ">";
    }

    public String coloredTag() {
        return "<" + colorName() + ">[" + tag + "]</" + colorName() + ">";
    }

    /** Scoreboard team name (short and unique). */
    String board() {
        return "smp_" + id.toString().substring(0, 8);
    }
}
