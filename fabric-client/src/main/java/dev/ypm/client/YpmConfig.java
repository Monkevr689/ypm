package dev.ypm.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Settings persisted to {@code config/ypm-client.json}. Edited in-game through the YPM screen (Right Ctrl). */
public final class YpmConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ypm-client.json");

    public static YpmConfig INSTANCE = new YpmConfig();

    public Targets targets = new Targets();
    public AimAssist aimAssist = new AimAssist();
    public Triggerbot triggerbot = new Triggerbot();
    public AutoBridge autoBridge = new AutoBridge();
    public Speed speed = new Speed();
    public AutoSprint autoSprint = new AutoSprint();
    public Hud hud = new Hud();

    public enum Priority { ANGLE, DISTANCE, HEALTH }

    public enum Corner { TOP_LEFT, TOP_RIGHT }

    /** Which entities Aim Assist and Triggerbot may act on. */
    public static final class Targets {
        public boolean players = true;
        public boolean hostiles = true;
        public boolean passives = false;
        public boolean ignoreInvisible = true;
        public boolean ignoreTeammates = true;
        public boolean requireLineOfSight = true;
        /** How Aim Assist picks between several valid targets. */
        public Priority priority = Priority.ANGLE;
    }

    public static final class AimAssist {
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
        /** Stop adjusting while the crosshair is already on the target. */
        public boolean stopOnTarget = true;
        /** Only assist while holding a sword or axe. */
        public boolean weaponOnly = false;
    }

    public static final class Triggerbot {
        public boolean enabled = false;
        /** Attack once the attack cooldown is at least this full (0..1). */
        public double minCooldown = 0.95;
        public boolean weaponOnly = true;
        /** Don't attack while using an item (eating, blocking, drawing a bow). */
        public boolean pauseWhileUsing = true;
    }

    public static final class AutoBridge {
        public boolean enabled = false;
        /** Ticks to wait between placements (0 = every tick). */
        public int placeDelay = 1;
        /** Maximum distance from the eyes to the face being clicked. */
        public double reach = 4.5;
        /** Only bridge while looking down at least {@link #minPitch} degrees or sneaking, so normal walking with blocks in hand isn't affected. */
        public boolean requireLookDown = true;
        public double minPitch = 40.0;
        /** Keep placing at the height you started bridging from, even while jumping. */
        public boolean keepY = true;
        /** Place under where you will be next tick as well as where you are now. */
        public boolean predict = true;
        /** Also place diagonally when the block under you has no direct neighbour to click on. */
        public boolean diagonal = true;
        /** Use blocks from the off hand when the main hand isn't holding any. */
        public boolean useOffhand = true;
        /** Switch to a hotbar slot with blocks when you aren't holding any. */
        public boolean autoSwitch = false;
        /** Hold sneak while standing at an edge so you can't walk off it. */
        public boolean sneakAtEdge = false;
        /** Play the arm swing on each placement. */
        public boolean swing = true;
    }

    public static final class Speed {
        public boolean enabled = false;
        /** Multiplier applied to your ground movement speed. */
        public double multiplier = 1.3;
        public boolean notWhileSneaking = true;
    }

    public static final class AutoSprint {
        public boolean enabled = false;
    }

    public static final class Hud {
        public boolean enabled = true;
        /** List of enabled modules. */
        public boolean moduleList = true;
        /** Name and health of the current aim/trigger target. */
        public boolean targetInfo = true;
        public Corner corner = Corner.TOP_RIGHT;
    }

    public static void load() {
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH)) {
                YpmConfig loaded = GSON.fromJson(reader, YpmConfig.class);
                if (loaded != null) INSTANCE = loaded;
            } catch (Exception e) {
                YpmClient.LOGGER.error("Failed to read {}, using defaults", PATH, e);
            }
        }
        INSTANCE.sanitize();
        save();
    }

    /** Fills sections missing from an older file and clamps hand-edited values into their valid ranges. */
    private void sanitize() {
        if (targets == null) targets = new Targets();
        if (aimAssist == null) aimAssist = new AimAssist();
        if (triggerbot == null) triggerbot = new Triggerbot();
        if (autoBridge == null) autoBridge = new AutoBridge();
        if (speed == null) speed = new Speed();
        if (autoSprint == null) autoSprint = new AutoSprint();
        if (hud == null) hud = new Hud();
        if (targets.priority == null) targets.priority = Priority.ANGLE;
        if (hud.corner == null) hud.corner = Corner.TOP_RIGHT;

        aimAssist.range = Mth.clamp(aimAssist.range, 1, 8);
        aimAssist.fov = Mth.clamp(aimAssist.fov, 10, 360);
        aimAssist.smoothing = Mth.clamp(aimAssist.smoothing, 1, 20);
        aimAssist.maxSpeed = Mth.clamp(aimAssist.maxSpeed, 30, 720);
        triggerbot.minCooldown = Mth.clamp(triggerbot.minCooldown, 0.5, 1);
        autoBridge.placeDelay = Mth.clamp(autoBridge.placeDelay, 0, 10);
        autoBridge.reach = Mth.clamp(autoBridge.reach, 2, 6);
        autoBridge.minPitch = Mth.clamp(autoBridge.minPitch, 0, 90);
        speed.multiplier = Mth.clamp(speed.multiplier, 1, 3);
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
