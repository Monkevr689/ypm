package dev.ypm.client.module;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;

/** Plays the normal arm swing after a placement, like a manual right-click does. */
final class Swing {
    private Swing() {
    }

    static void swing(LocalPlayer player, InteractionHand hand) {
        player.swing(hand, SwingAnimation.DEFAULT, false);
    }
}
