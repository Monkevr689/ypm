package dev.ypm.client.module;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Places the block you are holding under your feet as you walk off an edge. Placement goes through the
 * normal {@code useItemOn} path, exactly as if you had right-clicked the side of the neighbouring block.
 */
public final class AutoBridge {
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Direction[] SUPPORTS = {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private int cooldown;
    private int bridgeY = Integer.MIN_VALUE;

    public void onTick(Minecraft mc) {
        YpmConfig.AutoBridge cfg = YpmConfig.INSTANCE.autoBridge;
        LocalPlayer player = mc.player;
        if (!cfg.enabled || player == null || mc.level == null || mc.gameMode == null
                || !mc.mouseHandler.isMouseGrabbed() || player.isSpectator()) {
            bridgeY = Integer.MIN_VALUE;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        InteractionHand hand = blockHand(player, cfg.useOffhand);
        if (hand == null) return;

        // The layer under your feet. With keepY, jumping doesn't raise the bridge.
        int feetY = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ()).getY();
        if (player.onGround() || !cfg.keepY || bridgeY == Integer.MIN_VALUE || feetY < bridgeY) {
            bridgeY = feetY;
        }

        BlockPos under = BlockPos.containing(player.getX(), bridgeY, player.getZ());
        if (tryPlaceAt(mc, player, hand, under, cfg)) return;

        if (cfg.predict) {
            Vec3 v = player.getDeltaMovement();
            BlockPos next = BlockPos.containing(player.getX() + v.x, bridgeY, player.getZ() + v.z);
            if (!next.equals(under)) tryPlaceAt(mc, player, hand, next, cfg);
        }
    }

    private boolean tryPlaceAt(Minecraft mc, LocalPlayer player, InteractionHand hand, BlockPos target, YpmConfig.AutoBridge cfg) {
        ClientLevel level = mc.level;
        if (!level.getBlockState(target).canBeReplaced()) return false;
        if (place(mc, player, hand, target, cfg)) return true;
        if (!cfg.diagonal) return false;

        // No neighbour to click on: build a supporting block next to the target first.
        Direction best = null;
        double bestDist = Double.MAX_VALUE;
        for (Direction dir : HORIZONTAL) {
            BlockPos side = target.relative(dir);
            if (!level.getBlockState(side).canBeReplaced() || !hasSupport(level, side)) continue;
            double dist = Vec3.atCenterOf(side).distanceToSqr(player.position());
            if (dist < bestDist) {
                bestDist = dist;
                best = dir;
            }
        }
        return best != null && place(mc, player, hand, target.relative(best), cfg);
    }

    private boolean place(Minecraft mc, LocalPlayer player, InteractionHand hand, BlockPos target, YpmConfig.AutoBridge cfg) {
        Vec3 eye = player.getEyePosition();
        for (Direction dir : SUPPORTS) {
            BlockPos neighbour = target.relative(dir);
            if (mc.level.getBlockState(neighbour).canBeReplaced()) continue;

            Direction face = dir.getOpposite();
            Vec3 hit = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (eye.distanceTo(hit) > cfg.reach) continue;

            InteractionResult result = mc.gameMode.useItemOn(player, hand, new BlockHitResult(hit, face, neighbour, false));
            if (result.consumesAction()) {
                Swing.swing(player, hand);
                cooldown = cfg.placeDelay;
                return true;
            }
        }
        return false;
    }

    private static boolean hasSupport(ClientLevel level, BlockPos pos) {
        for (Direction dir : SUPPORTS) {
            if (!level.getBlockState(pos.relative(dir)).canBeReplaced()) return true;
        }
        return false;
    }

    private static InteractionHand blockHand(LocalPlayer player, boolean allowOffhand) {
        if (player.getMainHandItem().getItem() instanceof BlockItem) return InteractionHand.MAIN_HAND;
        if (allowOffhand && player.getOffhandItem().getItem() instanceof BlockItem) return InteractionHand.OFF_HAND;
        return null;
    }
}
