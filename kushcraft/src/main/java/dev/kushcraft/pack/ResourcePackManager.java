package dev.kushcraft.pack;

import com.sun.net.httpserver.HttpServer;
import dev.kushcraft.KushCraft;
import dev.kushcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the resource pack from the textures/models inside the plugin jar,
 * hosts it with a tiny web server and sends it to every player that joins.
 */
public final class ResourcePackManager implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("KushCraft-pack".getBytes(StandardCharsets.UTF_8));
    private static final String PREFIX = "pack/";

    private final KushCraft plugin;
    private final File jar;
    private byte[] zip;
    private byte[] sha1;
    private String hashHex;
    private HttpServer server;
    private ExecutorService pool;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();

    public ResourcePackManager(KushCraft plugin, File jar) {
        this.plugin = plugin;
        this.jar = jar;
    }

    public void start() {
        try {
            build();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not build the resource pack", e);
            return;
        }
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            return;
        }
        String external = plugin.getConfig().getString("resource-pack.url", "");
        if (external != null && !external.isBlank()) {
            plugin.getLogger().info("Resource pack: players download it from " + external);
            return;
        }
        int port = plugin.getConfig().getInt("resource-pack.port", 8163);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 16);
            pool = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "KushCraft-pack-http");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(pool);
            server.createContext("/", ex -> {
                try (ex) {
                    String path = ex.getRequestURI().getPath();
                    if (!path.endsWith(".zip")) {
                        byte[] msg = "KushCraft resource pack server".getBytes(StandardCharsets.UTF_8);
                        ex.sendResponseHeaders(404, msg.length);
                        try (OutputStream os = ex.getResponseBody()) {
                            os.write(msg);
                        }
                        return;
                    }
                    byte[] data = zip;
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.getResponseHeaders().add("Cache-Control", "no-cache");
                    if ("HEAD".equalsIgnoreCase(ex.getRequestMethod())) {
                        ex.sendResponseHeaders(200, -1);
                        return;
                    }
                    ex.sendResponseHeaders(200, data.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(data);
                    }
                } catch (IOException ignored) {
                    // client went away
                }
            });
            server.start();
            plugin.getLogger().info("Resource pack hosted on port " + port + " (" + zip.length / 1024 + " KB, sha1 " + hashHex + ")");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not start the resource pack web server on port " + port + ": " + e.getMessage());
            plugin.getLogger().severe("Open that port, pick another 'resource-pack.port', or upload plugins/KushCraft/"
                    + "KushCraft-pack.zip somewhere and set 'resource-pack.url'.");
            server = null;
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
    }

    /** Zips everything under pack/ in the plugin jar (sorted, fixed timestamps = stable hash). */
    private void build() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            for (JarEntry e : Collections.list(jf.entries())) {
                if (!e.isDirectory() && e.getName().startsWith(PREFIX)) {
                    names.add(e.getName());
                }
            }
            Collections.sort(names);
            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                for (String name : names) {
                    ZipEntry ze = new ZipEntry(name.substring(PREFIX.length()));
                    ze.setTime(315532800000L); // 1980-01-01, keeps the hash stable
                    out.putNextEntry(ze);
                    try (InputStream in = jf.getInputStream(jf.getEntry(name))) {
                        in.transferTo(out);
                    }
                    out.closeEntry();
                }
            }
        }
        zip = bytes.toByteArray();
        sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
        hashHex = HexFormat.of().formatHex(sha1);
        File outFile = new File(plugin.getDataFolder(), "KushCraft-pack.zip");
        Files.write(outFile.toPath(), zip);
        plugin.getLogger().info("Built resource pack with " + names.size() + " files -> " + outFile.getName());
    }

    public String url(Player p) {
        String external = plugin.getConfig().getString("resource-pack.url", "");
        if (external != null && !external.isBlank()) {
            return external;
        }
        String host = plugin.getConfig().getString("resource-pack.host", "");
        if (host == null || host.isBlank()) {
            InetSocketAddress vh = p.getConnection().getVirtualHost();
            host = vh != null ? vh.getHostString() : "";
        }
        if (host == null || host.isBlank()) {
            host = Bukkit.getIp();
        }
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        int port = plugin.getConfig().getInt("resource-pack.port", 8163);
        if (host.contains(":") && !host.startsWith("[")) {
            host = "[" + host + "]"; // IPv6
        }
        return "http://" + host + ":" + port + "/KushCraft-" + hashHex + ".zip";
    }

    public void send(Player p) {
        if (zip == null || !plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            return;
        }
        boolean external = !plugin.getConfig().getString("resource-pack.url", "").isBlank();
        if (server == null && !external) {
            return;
        }
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "KushCraft textures");
        p.setResourcePack(PACK_ID, url(p), sha1, Text.mm(prompt),
                plugin.getConfig().getBoolean("resource-pack.required", false));
    }

    /** True once the client told us the pack loaded (custom GUI backgrounds are only used then). */
    public boolean hasPack(Player p) {
        return loaded.contains(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                send(p);
            }
        }, 30L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!PACK_ID.equals(e.getID())) {
            return;
        }
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> loaded.add(p.getUniqueId());
            case DECLINED -> {
                loaded.remove(p.getUniqueId());
                p.sendMessage(Text.msg("<yellow>You declined the KushCraft pack - items will look like paper."
                        + " <gray>Use <white>/kush pack</white> to get it."));
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                loaded.remove(p.getUniqueId());
                plugin.getLogger().warning(p.getName() + " could not load the resource pack (" + e.getStatus()
                        + ") from " + url(p) + " - check resource-pack.port / host in config.yml");
            }
            default -> {
            }
        }
    }
}
