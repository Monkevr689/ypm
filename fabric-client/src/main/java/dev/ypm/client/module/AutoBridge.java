package dev.ypm.client.module;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
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
    private boolean holdingSneak;

    public void onTick(Minecraft mc) {
        YpmConfig.AutoBridge cfg = YpmConfig.INSTANCE.autoBridge;
        LocalPlayer player = mc.player;
        if (!cfg.enabled || player == null || mc.level == null || mc.gameMode == null
                || !mc.mouseHandler.isMouseGrabbed() || player.isSpectator() || player.getAbilities().flying) {
            reset(mc);
            return;
        }

        boolean aiming = !cfg.requireLookDown || player.getXRot() >= cfg.minPitch || player.isShiftKeyDown();
        InteractionHand hand = blockHand(player, cfg);
        if (hand == null || !aiming) {
            reset(mc);
            return;
        }

        // The layer under your feet. With keepY, jumping doesn't raise the bridge.
        int feetY = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ()).getY();
        if (player.onGround() || !cfg.keepY || bridgeY == Integer.MIN_VALUE || feetY < bridgeY) {
            bridgeY = feetY;
        }

        Vec3 v = player.getDeltaMovement();
        BlockPos under = BlockPos.containing(player.getX(), bridgeY, player.getZ());
        BlockPos next = BlockPos.containing(player.getX() + v.x, bridgeY, player.getZ() + v.z);
        updateSneak(mc, cfg, player, next);

        if (cooldown > 0) {
            cooldown--;
            return;
        }
        if (tryPlaceAt(mc, player, hand, under, cfg)) return;
        if (cfg.predict && !next.equals(under)) tryPlaceAt(mc, player, hand, next, cfg);
    }

    /** Holds sneak while standing on the edge of a drop, and lets go once there's ground ahead again. */
    private void updateSneak(Minecraft mc, YpmConfig.AutoBridge cfg, LocalPlayer player, BlockPos next) {
        boolean atEdge = cfg.sneakAtEdge && player.onGround() && mc.level.getBlockState(next).canBeReplaced();
        if (atEdge != holdingSneak) {
            mc.options.keyShift.setDown(atEdge);
            holdingSneak = atEdge;
        }
    }

    private void reset(Minecraft mc) {
        bridgeY = Integer.MIN_VALUE;
        if (holdingSneak) {
            mc.options.keyShift.setDown(false);
            holdingSneak = false;
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
            if (!isClickable(mc.level, neighbour)) continue;

            Direction face = dir.getOpposite();
            Vec3 hit = Vec3.atCenterOf(neighbour).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            if (eye.distanceTo(hit) > cfg.reach) continue;

            InteractionResult result = mc.gameMode.useItemOn(player, hand, new BlockHitResult(hit, face, neighbour, false));
            if (result.consumesAction()) {
                if (cfg.swing) Swing.swing(player, hand);
                cooldown = cfg.placeDelay;
                return true;
            }
        }
        return false;
    }

    /**
     * A neighbour we can safely click to place against: something solid that won't open a menu or toggle
     * (chests, doors, crafting tables...) instead of placing our block.
     */
    private static boolean isClickable(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.canBeReplaced() || level.getBlockEntity(pos) != null) return false;
        Block block = state.getBlock();
        return !(block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof BedBlock
                || block instanceof AnvilBlock || block instanceof CraftingTableBlock || block instanceof NoteBlock);
    }

    private static boolean hasSupport(ClientLevel level, BlockPos pos) {
        for (Direction dir : SUPPORTS) {
            if (isClickable(level, pos.relative(dir))) return true;
        }
        return false;
    }

    private static InteractionHand blockHand(LocalPlayer player, YpmConfig.AutoBridge cfg) {
        if (isBlock(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (cfg.useOffhand && isBlock(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        if (cfg.autoSwitch) {
            Inventory inv = player.getInventory();
            for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
                if (isBlock(inv.getItem(slot))) {
                    inv.setSelectedSlot(slot);
                    return InteractionHand.MAIN_HAND;
                }
            }
        }
        return null;
    }

    private static boolean isBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem;
    }
}
