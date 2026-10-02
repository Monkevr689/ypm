package dev.ypm.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings persisted to {@code config/ypm-client.json}. Edit the file and press the reload key to apply. */
public final class YpmConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ypm-client.json");

    public static YpmConfig INSTANCE = new YpmConfig();

    public AimAssist aimAssist = new AimAssist();

    public static final class AimAssist {
        /** Whether aim assist starts enabled when the game launches. */
        public boolean enabled = false;
        /** Maximum distance (blocks) to a target. */
        public double range = 4.5;
        /** Only targets within this cone (degrees, full width) around the crosshair are considered. */
        public double fov = 70.0;
        /** How quickly the view eases toward the target; higher is snappier. */
        public double smoothing = 6.0;
        /** Hard cap on rotation speed in degrees per second. */
        public double maxSpeed = 240.0;
        /** Also correct pitch, not just yaw. */
        public boolean vertical = true;
        /** Only assist while the attack button is held. */
        public boolean requireAttackKey = true;
        /** Keep the same target while it stays valid instead of re-picking every frame. */
        public boolean stickyTarget = true;
        public boolean targetPlayers = true;
        public boolean targetHostiles = true;
        public boolean targetPassives = false;
        public boolean ignoreInvisible = true;
        public boolean ignoreTeammates = true;
        public boolean requireLineOfSight = true;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                YpmConfig loaded = GSON.fromJson(reader, YpmConfig.class);
                if (loaded != null) {
                    if (loaded.aimAssist == null) loaded.aimAssist = new AimAssist();
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                YpmClient.LOGGER.error("Failed to read {}, using defaults", PATH, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            YpmClient.LOGGER.error("Failed to write {}", PATH, e);
        }
    }
}
