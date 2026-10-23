package com.mementocraft.reallyalive.body;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;

/**
 * Jumps only when the brain presses the jump key. Requests from other AI (such as vanilla's
 * swim-to-the-surface behaviour) are ignored, so a brain-driven villager has to learn to swim like
 * a player does.
 */
public class BrainJumpControl extends JumpControl {
    private final Mob body;
    private boolean pressed;

    public BrainJumpControl(Mob mob) {
        super(mob);
        this.body = mob;
    }

    /** The brain's jump key for this tick. */
    public void press() {
        pressed = true;
    }

    @Override
    public void jump() {}

    @Override
    public void tick() {
        body.setJumping(pressed);
        pressed = false;
    }
}
