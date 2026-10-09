package dev.smpsuite.voice;

import dev.smpsuite.SMPSuite;
import dev.smpsuite.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Talks to Simple Voice Chat (the "voicechat" plugin) through its public API,
 * by reflection, so SMPSuite needs nothing extra to build or run. Proximity
 * voice is Simple Voice Chat's own; this adds a private voice group for each
 * team and the public channels from the config. Without Simple Voice Chat
 * every method here does nothing.
 */
public final class VoiceBridge {

    private final SMPSuite plugin;
    private ClassLoader svc;
    private Object api;
    private String problem = "Simple Voice Chat is not installed.";
    private final Map<UUID, Object> teamGroups = new HashMap<>();
    private final Map<String, Object> channels = new LinkedHashMap<>();

    public VoiceBridge(SMPSuite plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("voice.enabled", true);
    }

    /** True once Simple Voice Chat's server is running and SMPSuite is hooked in. */
    public boolean ready() {
        return api != null;
    }

    /** Why voice isn't ready (for /vc). */
    public String problem() {
        return problem;
    }

    public void hook() {
        if (!enabled()) {
            problem = "Voice chat groups are turned off in SMPSuite's config.";
            return;
        }
        Plugin vc = Bukkit.getPluginManager().getPlugin("voicechat");
        if (vc == null || !vc.isEnabled()) {
            plugin.getLogger().info("Voice chat: Simple Voice Chat isn't installed - team voice groups are off.");
            return;
        }
        svc = vc.getClass().getClassLoader();
        try {
            Class<?> serviceClass = cls("de.maxhenkel.voicechat.api.BukkitVoicechatService");
            Object service = Bukkit.getServicesManager().load(serviceClass);
            if (service == null) {
                problem = "Simple Voice Chat is installed but its API service isn't there.";
                plugin.getLogger().warning("Voice chat: " + problem);
                return;
            }
            Class<?> pluginClass = cls("de.maxhenkel.voicechat.api.VoicechatPlugin");
            InvocationHandler handler = (proxy, m, args) -> switch (m.getName()) {
                case "getPluginId" -> "smpsuite";
                case "initialize" -> {
                    if (args != null && args.length == 1 && has(args[0], "getConnectionOf")) {
                        started(args[0]);
                    }
                    yield null;
                }
                case "registerEvents" -> {
                    registerEvents(args[0]);
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "SMPSuite voice bridge";
                default -> null;
            };
            Object proxy = Proxy.newProxyInstance(svc, new Class<?>[]{pluginClass}, handler);
            serviceClass.getMethod("registerPlugin", pluginClass).invoke(service, proxy);
            problem = "Simple Voice Chat hasn't started its voice server yet.";
            plugin.getLogger().info("Voice chat: hooked into Simple Voice Chat.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            problem = "Couldn't hook into Simple Voice Chat (" + e.getClass().getSimpleName() + ").";
            plugin.getLogger().warning("Voice chat: " + problem + " " + e.getMessage());
        }
    }

    private Class<?> cls(String name) throws ClassNotFoundException {
        return Class.forName(name, true, svc);
    }

    private static boolean has(Object o, String method) {
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(method)) {
                return true;
            }
        }
        return false;
    }

    private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                m.setAccessible(true);
                return m.invoke(target, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    private void registerEvents(Object registration) {
        try {
            Class<?> started = cls("de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent");
            Consumer<Object> onStart = ev -> {
                try {
                    started(call(ev, "getVoicechat"));
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().warning("Voice chat: " + e.getMessage());
                }
            };
            for (Method m : registration.getClass().getMethods()) {
                if (m.getName().equals("registerEvent") && m.getParameterCount() == 2) {
                    m.setAccessible(true);
                    m.invoke(registration, started, onStart);
                    return;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Voice chat: couldn't listen for the voice server start: " + e.getMessage());
        }
    }

    /** Simple Voice Chat's server is up: make the channels and team groups. */
    private void started(Object serverApi) {
        if (api == serverApi) {
            return;
        }
        api = serverApi;
        problem = "";
        Bukkit.getScheduler().runTask(plugin, () -> {
            channels.clear();
            teamGroups.clear();
            for (String name : plugin.getConfig().getStringList("voice.channels")) {
                Object g = group(name, null, false);
                if (g != null) {
                    channels.put(name, g);
                }
            }
            for (Team t : plugin.teams().all()) {
                teamChanged(t);
            }
            plugin.getLogger().info("Voice chat: " + channels.size() + " channels and " + teamGroups.size()
                    + " team groups ready.");
        });
    }

    /** Makes a persistent group (team groups: hidden with a random password, only placed into). */
    private Object group(String name, String password, boolean hidden) {
        try {
            Object b = call(api, "groupBuilder");
            call(b, "setName", name);
            if (password != null) {
                call(b, "setPassword", password);
            }
            call(b, "setPersistent", true);
            try {
                call(b, "setHidden", hidden);
            } catch (NoSuchMethodException ignored) {
                // older Simple Voice Chat: groups can't be hidden
            }
            return call(b, "build");
        } catch (ReflectiveOperationException | RuntimeException e) {
            plugin.getLogger().warning("Voice chat: couldn't make the group " + name + ": " + e.getMessage());
            return null;
        }
    }

    private void removeGroup(Object group) {
        try {
            call(api, "removeGroup", call(group, "getId"));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older Simple Voice Chat: empty groups go away by themselves
        }
    }

    private Object connection(Player p) {
        try {
            return call(api, "getConnectionOf", p.getUniqueId());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** Has the player got the voice chat mod running? */
    public boolean connected(Player p) {
        if (api == null) {
            return false;
        }
        Object c = connection(p);
        try {
            return c != null && Boolean.TRUE.equals(call(c, "isInstalled"));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return c != null;
        }
    }

    /** Puts a player in a group (null = back to proximity chat). */
    private String place(Player p, Object group) {
        if (api == null) {
            return problem;
        }
        Object c = connection(p);
        if (c == null) {
            return "You need the Simple Voice Chat mod to use voice chat.";
        }
        try {
            call(c, "setGroup", group);
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "Voice chat didn't accept that (" + e.getClass().getSimpleName() + ").";
        }
    }

    public List<String> channelNames() {
        return new ArrayList<>(channels.keySet());
    }

    public String joinChannel(Player p, String name) {
        for (var e : channels.entrySet()) {
            if (e.getKey().equalsIgnoreCase(name)) {
                return place(p, e.getValue());
            }
        }
        return api == null ? problem : "There's no channel called " + name + ". Channels: " + String.join(", ", channels.keySet());
    }

    public String joinTeam(Player p) {
        Team t = plugin.teams().of(p);
        if (t == null) {
            return "You're not in a team.";
        }
        if (!plugin.getConfig().getBoolean("voice.team-groups", true)) {
            return "Team voice groups are turned off.";
        }
        Object g = teamGroups.get(t.id());
        if (g == null && api != null) {
            teamChanged(t);
            g = teamGroups.get(t.id());
        }
        return g == null ? (api == null ? problem : "Couldn't make your team's voice group.") : place(p, g);
    }

    public String leave(Player p) {
        return place(p, null);
    }

    /** A team was made, renamed or changed members: its voice group follows. */
    public void teamChanged(Team t) {
        if (api == null || !plugin.getConfig().getBoolean("voice.team-groups", true)) {
            return;
        }
        Object old = teamGroups.get(t.id());
        String want = "Team " + t.name();
        try {
            if (old != null && want.equals(call(old, "getName"))) {
                return;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // make a new one
        }
        Object g = group(want, UUID.randomUUID().toString().substring(0, 12), true);
        if (g == null) {
            return;
        }
        teamGroups.put(t.id(), g);
        if (old != null) {
            // members talking in the old group move along
            for (Player m : plugin.teams().online(t)) {
                Object c = connection(m);
                try {
                    Object in = c == null ? null : call(c, "getGroup");
                    if (in != null && call(in, "getId").equals(call(old, "getId"))) {
                        call(c, "setGroup", g);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // leave them be
                }
            }
            removeGroup(old);
        }
    }

    public void teamDisbanded(Team t) {
        Object g = teamGroups.remove(t.id());
        if (g == null || api == null) {
            return;
        }
        for (Player m : Bukkit.getOnlinePlayers()) {
            Object c = connection(m);
            try {
                Object in = c == null ? null : call(c, "getGroup");
                if (in != null && call(in, "getId").equals(call(g, "getId"))) {
                    call(c, "setGroup", (Object) null);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // nothing to do
            }
        }
        removeGroup(g);
    }

    /** A player left their team: out of its voice group. */
    public void left(UUID player) {
        Player p = Bukkit.getPlayer(player);
        if (p == null || api == null) {
            return;
        }
        Object c = connection(p);
        try {
            Object in = c == null ? null : call(c, "getGroup");
            if (in != null && teamGroups.containsValue(in)) {
                call(c, "setGroup", (Object) null);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // nothing to do
        }
    }
}
