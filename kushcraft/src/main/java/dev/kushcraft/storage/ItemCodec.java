package dev.kushcraft.storage;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Items to bytes for the database (satchels, lab output). Uses Paper's own
 * item format, so items keep every component and survive version upgrades.
 * Encoding runs on the database thread on copies made on the server thread.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    /** Copies of every slot (server thread): safe to encode on another thread afterwards. */
    public static ItemStack[] copy(ItemStack[] slots) {
        ItemStack[] out = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            ItemStack it = slots[i];
            out[i] = it == null || it.getType().isAir() || it.getAmount() <= 0 ? null : it.clone();
        }
        return out;
    }

    /** Slots to bytes: [count] then [slot, length, Paper item bytes] for every filled slot. */
    public static byte[] encode(ItemStack[] slots) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            int n = 0;
            for (ItemStack it : slots) {
                if (it != null) {
                    n++;
                }
            }
            out.writeShort(n);
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] == null) {
                    continue;
                }
                byte[] b = slots[i].serializeAsBytes();
                out.writeShort(i);
                out.writeInt(b.length);
                out.write(b);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Bytes back to slots (an unreadable item is skipped, never crashes the load). */
    public static ItemStack[] decode(byte[] data, int size) {
        ItemStack[] out = new ItemStack[size];
        if (data == null || data.length < 2) {
            return out;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int n = in.readUnsignedShort();
            for (int k = 0; k < n; k++) {
                int slot = in.readUnsignedShort();
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                try {
                    ItemStack it = ItemStack.deserializeBytes(b);
                    if (slot < size) {
                        out[slot] = it;
                    }
                } catch (RuntimeException ignored) {
                    // an item this version can't read
                }
            }
        } catch (IOException e) {
            // truncated: keep what was read
        }
        return out;
    }

    public static byte[] encode(ItemStack it) {
        return it == null ? null : it.serializeAsBytes();
    }

    public static ItemStack decode(byte[] b) {
        if (b == null || b.length == 0) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(b);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
