package dev.ypm.client.module;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Gently rotates the camera toward the nearest valid target inside a cone around the crosshair.
 * Rotation is applied through {@link LocalPlayer#turn}, the same path mouse movement takes.
 */
public final class AimAssist {
    /** {@link Entity#turn} multiplies its input by this factor. */
    private static final double TURN_SCALE = 0.15;

    private LivingEntity target;
    private long lastFrameNanos;

    public void onFrame() {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0 : Math.min((now - lastFrameNanos) / 1.0e9, 0.1);
        lastFrameNanos = now;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        YpmConfig.AimAssist cfg = YpmConfig.INSTANCE.aimAssist;

        if (!cfg.enabled || dt <= 0 || player == null || mc.level == null || !mc.mouseHandler.isMouseGrabbed() || mc.isPaused()
                || player.isSpectator() || (cfg.requireAttackKey && !mc.options.keyAttack.isDown())) {
            target = null;
            return;
        }

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 eye = player.getEyePosition(partialTick);

        if (!cfg.stickyTarget || !isValid(player, target, eye, partialTick, cfg)) {
            target = findTarget(mc, player, eye, partialTick, cfg);
        }
        if (target == null) return;

        Vec3 aim = aimPoint(player, target, eye, partialTick, cfg.vertical);
        float[] wanted = rotationTo(eye, aim);

        float yawDiff = Mth.wrapDegrees(wanted[0] - player.getYRot());
        float pitchDiff = cfg.vertical ? wanted[1] - player.getXRot() : 0f;

        // Exponential ease toward the target, capped by a max angular speed.
        double ease = 1.0 - Math.exp(-cfg.smoothing * dt);
        double cap = cfg.maxSpeed * dt;
        double yawStep = Mth.clamp(yawDiff * ease, -cap, cap);
        double pitchStep = Mth.clamp(pitchDiff * ease, -cap, cap);

        if (Math.abs(yawStep) < 1.0e-4 && Math.abs(pitchStep) < 1.0e-4) return;
        player.turn(yawStep / TURN_SCALE, pitchStep / TURN_SCALE);
    }

    private LivingEntity findTarget(Minecraft mc, LocalPlayer player, Vec3 eye, float partialTick, YpmConfig.AimAssist cfg) {
        LivingEntity best = null;
        double bestAngle = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !isValid(player, living, eye, partialTick, cfg)) continue;
            double angle = angleTo(player, eye, center(living, partialTick));
            if (angle < bestAngle) {
                bestAngle = angle;
                best = living;
            }
        }
        return best;
    }

    private boolean isValid(LocalPlayer player, LivingEntity entity, Vec3 eye, float partialTick, YpmConfig.AimAssist cfg) {
        if (entity == null || entity == player || !entity.isAlive() || entity.isDeadOrDying()) return false;
        if (cfg.ignoreInvisible && entity.isInvisible()) return false;

        if (entity instanceof Player other) {
            if (!cfg.targetPlayers || other.isSpectator()) return false;
            if (cfg.ignoreTeammates && player.isAlliedTo(other)) return false;
        } else if (entity instanceof Mob) {
            boolean hostile = entity instanceof Enemy;
            if (hostile ? !cfg.targetHostiles : !cfg.targetPassives) return false;
        } else {
            return false; // armor stands and other non-mob living entities
        }

        Vec3 center = center(entity, partialTick);
        if (closestPoint(box(entity, partialTick), eye).distanceTo(eye) > cfg.range) return false;
        if (angleTo(player, eye, center) > cfg.fov / 2.0) return false;
        return !cfg.requireLineOfSight || player.hasLineOfSight(entity);
    }

    /**
     * Aim at the target's horizontal center, but keep the current aim height when it already falls on the
     * hitbox. This avoids yanking pitch toward the middle of the body when the crosshair is already on target.
     */
    private static Vec3 aimPoint(LocalPlayer player, LivingEntity target, Vec3 eye, float partialTick, boolean vertical) {
        AABB box = box(target, partialTick);
        Vec3 center = box.getCenter();
        double margin = Math.min(0.15, box.getYsize() * 0.2);
        double y = center.y;
        if (vertical) {
            double horizontal = Math.hypot(center.x - eye.x, center.z - eye.z);
            Vec3 look = player.getViewVector(partialTick);
            double lookHorizontal = Math.hypot(look.x, look.z);
            if (lookHorizontal > 1.0e-3) {
                double rayY = eye.y + look.y * (horizontal / lookHorizontal);
                y = Mth.clamp(rayY, box.minY + margin, box.maxY - margin);
            }
        }
        return new Vec3(center.x, y, center.z);
    }

    private static float[] rotationTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(dy, Math.hypot(dx, dz)) * Mth.RAD_TO_DEG);
        return new float[]{Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90f, 90f)};
    }

    private static double angleTo(LocalPlayer player, Vec3 eye, Vec3 point) {
        float[] rot = rotationTo(eye, point);
        double yaw = Mth.wrapDegrees(rot[0] - player.getYRot());
        double pitch = rot[1] - player.getXRot();
        return Math.hypot(yaw, pitch);
    }

    private static AABB box(Entity entity, float partialTick) {
        Vec3 offset = entity.getPosition(partialTick).subtract(entity.position());
        return entity.getBoundingBox().move(offset);
    }

    private static Vec3 center(Entity entity, float partialTick) {
        return box(entity, partialTick).getCenter();
    }

    private static Vec3 closestPoint(AABB box, Vec3 p) {
        return new Vec3(Mth.clamp(p.x, box.minX, box.maxX), Mth.clamp(p.y, box.minY, box.maxY), Mth.clamp(p.z, box.minZ, box.maxZ));
    }
}
