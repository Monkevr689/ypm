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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
 * Builds the resource pack from the textures/models inside the plugin jar
 * and sends it to every player that joins. By default ("url: auto") players
 * download the copy of this exact version that is hosted on GitHub, which
 * works on every host (Shockbyte and most game hosts only open the game
 * port). With an empty url the plugin hosts the zip itself on a port.
 */
public final class ResourcePackManager implements Listener {

    private static final UUID PACK_ID = UUID.nameUUIDFromBytes("KushCraft-pack".getBytes(StandardCharsets.UTF_8));
    private static final String PREFIX = "pack/";
    /** Every release puts KushCraft-pack-&lt;version&gt;.zip here and never changes it again. */
    public static final String HOSTED = "https://raw.githubusercontent.com/Monkevr689/ypm/claude/inspiring-keller-65lzp9/"
            + "kushcraft/release/";
    /** How often the hash of an external pack is checked again, in minutes (the file there can change). */
    private static final long RECHECK_MINUTES = 20;

    private final KushCraft plugin;
    private final File jar;
    private byte[] zip;
    private byte[] sha1;
    private String hashHex;
    /** SHA-1 of the zip found at resource-pack.url (clients reject a download whose hash differs). */
    private volatile byte[] externalSha1;
    private HttpServer server;
    private ExecutorService pool;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    /** Players who already got a second try after a failed download. */
    private final Set<UUID> retried = ConcurrentHashMap.newKeySet();

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
        String external = externalUrl();
        if (external != null) {
            plugin.getLogger().info("Resource pack: players download it from " + external);
            refreshExternal(null);
            Bukkit.getScheduler().runTaskTimer(plugin, () -> refreshExternal(null),
                    20L * 60 * RECHECK_MINUTES, 20L * 60 * RECHECK_MINUTES);
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
            plugin.getLogger().severe("Open that port, pick another 'resource-pack.port', or set 'resource-pack.url: auto'"
                    + " (players then download the pack from GitHub).");
            server = null;
        }
    }

    /**
     * The link players download the pack from, or null when the plugin hosts
     * it itself. "auto" = the copy of this version on GitHub.
     */
    public String externalUrl() {
        String url = plugin.getConfig().getString("resource-pack.url", "auto");
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.trim().equalsIgnoreCase("auto")) {
            return HOSTED + "KushCraft-pack-" + plugin.getPluginMeta().getVersion() + ".zip";
        }
        return url.trim();
    }

    public void refreshExternal() {
        refreshExternal(null);
    }

    /**
     * With an external URL the hash sent to players must be the hash of the
     * file hosted there (the client rejects a download whose hash differs),
     * so download it (async) and hash it. Done at start-up, every 20 minutes
     * (the file may have been replaced) and when a player's download failed.
     * If it can't be checked, no hash is sent: the client then loads whatever
     * is there instead of failing. then (may be null) runs on the main thread.
     */
    public void refreshExternal(Runnable then) {
        String url = externalUrl();
        if (url == null) {
            externalSha1 = null;
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            byte[] before = externalSha1;
            try {
                HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(15)).build();
                HttpResponse<byte[]> res = client.send(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(60)).header("User-Agent", "KushCraft").GET().build(),
                        HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) {
                    externalSha1 = null;
                    plugin.getLogger().warning("The resource pack link returned HTTP " + res.statusCode() + " (" + url + ")."
                            + (url.startsWith(HOSTED) ? " This KushCraft version has no hosted pack: set resource-pack.url"
                            + " to '' and open resource-pack.port, or upload plugins/KushCraft/KushCraft-pack.zip"
                            + " somewhere and paste a direct link." : " It must be a DIRECT link to the zip."));
                } else if (res.body().length < 4 || res.body()[0] != 'P' || res.body()[1] != 'K') {
                    externalSha1 = null;
                    plugin.getLogger().warning("resource-pack.url does not point to a zip file (it looks like a web page)."
                            + " Use a direct download link (Dropbox: ?dl=1, GitHub: raw link).");
                } else {
                    byte[] body = res.body();
                    byte[] sha = MessageDigest.getInstance("SHA-1").digest(body);
                    externalSha1 = sha;
                    if (before == null || !java.util.Arrays.equals(before, sha)) {
                        String hex = HexFormat.of().formatHex(sha);
                        // compare the files inside, not the zip bytes (zip tools pack the same files differently)
                        boolean same = hex.equals(hashHex) || contents(body).equals(contents(zip));
                        plugin.getLogger().info("Resource pack: " + body.length / 1024 + " KB at the link, sha1 " + hex
                                + (same ? " (same as this plugin's pack)" : " (a different pack)"));
                        if (!same) {
                            plugin.getLogger().warning("The zip at resource-pack.url is not the pack this KushCraft version"
                                    + " builds - some textures will be missing. Use 'url: auto', or upload"
                                    + " plugins/KushCraft/KushCraft-pack.zip there again.");
                        }
                    }
                }
            } catch (Exception e) {
                externalSha1 = null;
                plugin.getLogger().warning("Could not check the resource pack link from the server (" + e + ")."
                        + " Players still get it, without a hash check.");
            }
            if (then != null) {
                Bukkit.getScheduler().runTask(plugin, then);
            }
        });
    }

    /** file name -> CRC32 of every file in a zip. */
    private static java.util.Map<String, Long> contents(byte[] data) {
        java.util.Map<String, Long> out = new java.util.TreeMap<>();
        try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(data))) {
            ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = in.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                java.util.zip.CRC32 crc = new java.util.zip.CRC32();
                int n;
                while ((n = in.read(buf)) > 0) {
                    crc.update(buf, 0, n);
                }
                out.put(e.getName(), crc.getValue());
            }
        } catch (IOException ex) {
            return java.util.Map.of();
        }
        return out;
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
        String external = externalUrl();
        if (external != null) {
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
        boolean external = externalUrl() != null;
        if (server == null && !external) {
            return;
        }
        String prompt = plugin.getConfig().getString("resource-pack.prompt", "KushCraft textures");
        // external and not checked: no hash, so the client never rejects the file for a wrong one
        byte[] hash = external ? externalSha1 : sha1;
        p.setResourcePack(PACK_ID, url(p), hash, Text.mm(prompt),
                plugin.getConfig().getBoolean("resource-pack.required", false));
    }

    /** /kush pack and the admin panel: check the link again, then send it. */
    public void resend(Player p) {
        retried.remove(p.getUniqueId());
        if (externalUrl() != null) {
            refreshExternal(() -> {
                if (p.isOnline()) {
                    send(p);
                }
            });
        } else {
            send(p);
        }
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
        retried.remove(e.getPlayer().getUniqueId());
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
                p.sendMessage(Text.msg("<yellow>You said no to the KushCraft textures - drugs and plants will look like"
                        + " purple squares. <green><click:run_command:'/kush pack'><hover:show_text:'Get the textures'>"
                        + "[Click here to get them]</hover></click>"));
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                loaded.remove(p.getUniqueId());
                if (externalUrl() != null && retried.add(p.getUniqueId())) {
                    // the file at the link may have changed since it was hashed: hash it again and resend once
                    refreshExternal(() -> {
                        if (p.isOnline()) {
                            send(p);
                        }
                    });
                    return;
                }
                plugin.getLogger().warning(p.getName() + " could not load the resource pack (" + e.getStatus()
                        + ") from " + url(p) + (server != null
                        ? " - players cannot reach port " + plugin.getConfig().getInt("resource-pack.port", 8163)
                        + " on this server. Open it, or set resource-pack.url to auto (see README)."
                        : " - check that the link works in a browser."));
                p.sendMessage(Text.msg("<red>The KushCraft textures could not be downloaded. <gray>Check your internet"
                        + " and type <white>/kush pack</white> to try again. <dark_gray>(Server resource packs must be"
                        + " Enabled or Prompt in the server list - Edit server.)"));
            }
            default -> {
            }
        }
    }
}
