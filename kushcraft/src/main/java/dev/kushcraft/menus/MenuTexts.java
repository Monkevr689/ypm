package dev.kushcraft.menus;

import dev.kushcraft.KushCraft;
import dev.kushcraft.ranks.RankLadder;
import dev.kushcraft.storage.PlayerRecord;
import dev.kushcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * plugins/KushCraft/menus.yml: every word and icon of the welcome / help
 * menu (/menu). Edit it and run /kush reload - no restart, no new jar.
 * Placeholders like {rank} are filled in for the player looking at it.
 */
public final class MenuTexts {

    /** One button or text block of a page. */
    public record Entry(int slot, Material icon, String name, List<String> lore, String link, String page) {
    }

    private final KushCraft plugin;
    private final File file;
    private YamlConfiguration y = new YamlConfiguration();

    public MenuTexts(KushCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "menus.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("menus.yml", false);
        }
        y = YamlConfiguration.loadConfiguration(file);
        var in = plugin.getResource("menus.yml");
        if (in != null) {
            // anything missing from an older menus.yml comes from the built-in one
            y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public boolean openOnFirstJoin() {
        return y.getBoolean("open-on-first-join", true);
    }

    public String title(String page) {
        return y.getString("pages." + page + ".title", y.getString("title", "Server Guide"));
    }

    /** A link from the links section (discord, vote.1, ...), or the text itself when it's already a URL. */
    public String link(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        String v = y.getString("links." + key);
        return v == null || v.isBlank() ? null : v;
    }

    public List<String> voteLinks() {
        return y.getStringList("links.vote");
    }

    /** The entries of a page (main, economy, ranks, rules, community). */
    public List<Entry> entries(String page) {
        List<Entry> out = new ArrayList<>();
        for (Map<?, ?> m : y.getMapList("pages." + page + ".items")) {
            Material mat = Material.matchMaterial(String.valueOf(m.get("icon")));
            out.add(new Entry(m.get("slot") instanceof Number n ? n.intValue() : -1,
                    mat == null || !mat.isItem() ? Material.PAPER : mat,
                    m.get("name") == null ? "" : String.valueOf(m.get("name")),
                    m.get("lore") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of(),
                    m.get("link") == null ? null : String.valueOf(m.get("link")),
                    m.get("page") == null ? null : String.valueOf(m.get("page"))));
        }
        return out;
    }

    public ConfigurationSection section(String path) {
        return y.getConfigurationSection(path);
    }

    public String text(String path, String def) {
        return y.getString(path, def);
    }

    public List<String> lines(String path) {
        return y.getStringList(path);
    }

    // ------------------------------------------------------------------
    // placeholders
    // ------------------------------------------------------------------

    /** Fills in {player}, {rank}, {next-rank}, {money}, {workers}, ... for this player. */
    public String fill(Player p, String s) {
        if (s == null || s.indexOf('{') < 0) {
            return s;
        }
        PlayerRecord r = plugin.economy().account(p.getUniqueId());
        RankLadder.Progress pr = plugin.ranks().progress(p.getUniqueId());
        RankLadder.Rank next = pr.next();
        return s.replace("{player}", Text.escape(p.getName()))
                .replace("{rank}", pr.current().colored())
                .replace("{rank-number}", String.valueOf(pr.current().number()))
                .replace("{top-rank}", String.valueOf(plugin.ranks().top()))
                .replace("{next-rank}", next == null ? "<gray>none - you're at the top" : next.colored())
                .replace("{next-cost}", next == null ? "-" : plugin.economy().format(next.cost()))
                .replace("{next-playtime}", next == null ? "-" : Text.duration(pr.playtimeLeftSeconds() * 1000L))
                .replace("{next-wait}", next == null ? "-" : Text.duration(pr.waitLeftMillis()))
                .replace("{money}", plugin.economy().format(plugin.economy().balance(p)))
                .replace("{playtime}", Text.duration(r.playtime() * 1000L))
                .replace("{workers}", String.valueOf(plugin.workers().of(p.getUniqueId()).size()))
                .replace("{worker-slots}", String.valueOf(pr.current().workers()))
                .replace("{season}", String.valueOf(plugin.season()))
                .replace("{discord}", String.valueOf(link("discord")));
    }

    /** A plain vanilla icon with a MiniMessage name and lore (placeholders filled in). */
    public ItemStack icon(Player p, Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.itemName(Text.mm(fill(p, name)));
        List<String> filled = new ArrayList<>();
        for (String l : lore) {
            filled.add(fill(p, l));
        }
        meta.lore(Text.lines(filled));
        meta.addItemFlags(ItemFlag.values());
        meta.setMaxStackSize(1);
        it.setItemMeta(meta);
        return it;
    }
}
