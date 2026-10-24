package com.mementocraft.reallyalive.body;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;

/** Leaves the head where the brain pointed it; requests from other AI to look at things are ignored. */
public class BrainLookControl extends LookControl {
    public BrainLookControl(Mob mob) {
        super(mob);
    }

    @Override
    public void tick() {
        mob.setYHeadRot(mob.getYRot());
    }
}
