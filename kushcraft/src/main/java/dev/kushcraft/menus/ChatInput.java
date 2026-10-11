package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.util.Text;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** "Type the name in chat" prompts (used to name new strains). */
public final class ChatInput implements Listener {

    private record Prompt(Consumer<String> done, Runnable cancelled, long expires) {
    }

    private static final Map<UUID, Prompt> PROMPTS = new ConcurrentHashMap<>();

    public static void ask(Player p, String question, Consumer<String> done, Runnable cancelled) {
        PROMPTS.put(p.getUniqueId(), new Prompt(done, cancelled, System.currentTimeMillis() + 60_000));
        p.closeInventory();
        p.sendMessage(Text.msg(question));
        p.sendMessage(Text.mm("<gray>Type it in chat, or type <red>cancel</red>."));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Prompt prompt = PROMPTS.remove(e.getPlayer().getUniqueId());
        if (prompt == null) {
            return;
        }
        e.setCancelled(true);
        String text = Text.plain(e.message()).trim();
        Bukkit.getScheduler().runTask(KushCraft.get(), () -> {
            if (System.currentTimeMillis() > prompt.expires() || text.equalsIgnoreCase("cancel")) {
                prompt.cancelled().run();
            } else {
                prompt.done().accept(text);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PROMPTS.remove(e.getPlayer().getUniqueId());
    }
}
