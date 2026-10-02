package dev.ypm.client.module;

import dev.ypm.client.YpmConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Target rules shared by Aim Assist and Triggerbot (the "Targets" tab). */
public final class TargetFilter {
    private TargetFilter() {
    }

    /** Type, liveness, visibility and team checks; range, FOV and line of sight are up to the caller. */
    public static boolean accepts(LocalPlayer player, Entity entity, YpmConfig.Targets cfg) {
        if (!(entity instanceof LivingEntity living) || living == player) return false;
        if (!living.isAlive() || living.isDeadOrDying()) return false;
        if (cfg.ignoreInvisible && living.isInvisible()) return false;

        if (living instanceof Player other) {
            if (!cfg.players || other.isSpectator()) return false;
            return !cfg.ignoreTeammates || !player.isAlliedTo(other);
        }
        if (living instanceof Mob) {
            return living instanceof Enemy ? cfg.hostiles : cfg.passives;
        }
        return false; // armor stands and other non-mob living entities
    }

    public static boolean holdingWeapon(LocalPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.is(h -> h.is(ItemTags.SWORDS) || h.is(ItemTags.AXES));
    }
}
