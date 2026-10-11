package dev.kushcraft.cartels;

import dev.kushcraft.items.ItemType;
import dev.kushcraft.util.Text;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** A group of players who share a bank, level up together and fill big shipments. */
public final class Cartel {

    /** A big order for the whole cartel. Members deliver bit by bit; the bank gets the reward. */
    public record Shipment(ItemType type, int amount, int delivered, double reward, long expires) {

        public int left() {
            return Math.max(0, amount - delivered);
        }

        public boolean done() {
            return delivered >= amount;
        }
    }

    /** Banner colours a boss can pick from. */
    public static final int[] COLORS = {0xE83A3A, 0xF0882A, 0xF8D23A, 0x7AD83A, 0x2EA04A, 0x2AC8B0, 0x3AB8F0,
            0x3A6AE8, 0x8A4AE8, 0xD84AD8, 0xF07AB0, 0xF4F4F4, 0x4A4A52};

    final String id;
    String name;
    int color;
    UUID leader;
    final Set<UUID> members = new LinkedHashSet<>();
    double bank;
    int level = 1;
    double sales;
    int shipments;
    Shipment shipment;
    long nextShipment;
    long created;

    Cartel(String id, String name, int color, UUID leader) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.leader = leader;
        members.add(leader);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    /** Banner colour. */
    public int color() {
        return color;
    }

    /** The name in the cartel's colour (MiniMessage). */
    public String colored() {
        return "<color:" + Text.hex(color == 0x4A4A52 ? 0x8A8A9A : color) + ">" + Text.escape(name) + "</color>";
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID id) {
        return leader.equals(id);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(members);
    }

    public double bank() {
        return bank;
    }

    /** 1 = Crew ... the highest level in config cartel.levels. */
    public int level() {
        return level;
    }

    /** Product the members sold while in the cartel (decides the cartel leaderboard). */
    public double sales() {
        return sales;
    }

    public int shipmentsDone() {
        return shipments;
    }

    public Shipment shipment() {
        return shipment;
    }

    public long nextShipment() {
        return nextShipment;
    }
}
