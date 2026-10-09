package dev.smpsuite.pack;

import dev.smpsuite.Msg;
import dev.smpsuite.SMPSuite;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Sends the small gem texture pack to players, next to any other pack (it is
 * added, never replacing KushCraft's or the server's). url: auto = this
 * version's zip on GitHub. Without it gems look like the vanilla items they
 * are made of - nothing breaks.
 */
public final class PackSender implements Listener {

    public static final UUID PACK_ID = UUID.fromString("5b3c1d2e-8f41-4a6b-9c0d-2e7f6a1b3c4d");
    public static final String HOSTED = "https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/"
            + "smpsuite/release/SMPSuite-pack-";

    private final SMPSuite plugin;
    private byte[] sha1;
    private String url;

    public PackSender(SMPSuite plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("resource-pack.enabled", true) && sha1 != null && url != null;
    }

    public String url() {
        return url;
    }

    public String sha1Hex() {
        return sha1 == null ? "" : HexFormat.of().formatHex(sha1);
    }

    public void start() {
        try (InputStream in = plugin.getResource("SMPSuite-pack.zip")) {
            if (in == null) {
                plugin.getLogger().warning("Resource pack: this jar has no pack inside - gems use vanilla looks.");
                return;
            }
            sha1 = MessageDigest.getInstance("SHA-1").digest(in.readAllBytes());
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().warning("Resource pack: " + e.getMessage());
            return;
        }
        String u = plugin.getConfig().getString("resource-pack.url", "auto");
        url = u == null || u.isBlank() || u.equalsIgnoreCase("auto")
                ? HOSTED + plugin.getPluginMeta().getVersion() + ".zip" : u.trim();
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            return;
        }
        plugin.getLogger().info("Resource pack: players download the gem textures from " + url);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    /** Downloads the pack once and compares it with the one in this jar. */
    private void check() {
        try {
            HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<byte[]> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "SMPSuite").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (r.statusCode() != 200) {
                plugin.getLogger().warning("Resource pack: the link answered " + r.statusCode()
                        + " - players won't get the gem textures. Set resource-pack.url to a direct link.");
                return;
            }
            String remote = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(r.body()));
            if (remote.equals(sha1Hex())) {
                plugin.getLogger().info("Resource pack: " + r.body().length / 1024 + " KB at the link, sha1 " + remote
                        + " (same as this plugin's pack)");
            } else {
                plugin.getLogger().warning("Resource pack: the file at the link is a different pack (sha1 " + remote
                        + ", this plugin's is " + sha1Hex() + ").");
            }
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | RuntimeException e) {
            plugin.getLogger().warning("Resource pack: couldn't check the link (" + e.getMessage() + ").");
        }
    }

    public void send(Player p) {
        if (enabled()) {
            p.addResourcePack(PACK_ID, url, sha1, Msg.prompt(plugin.getConfig().getString("resource-pack.prompt", "")),
                    false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // a moment later, after other plugins' packs (which might reset the list)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                send(p);
            }
        }, 40L);
    }
}
