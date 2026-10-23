package com.mementocraft.reallyalive.body;

import com.mementocraft.reallyalive.brain.MotorCommand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Replaces a villager's move control while a brain drives it. Instead of walking toward a target it
 * presses the brain's keys, setting the same forward and sideways inputs a player's keyboard sets.
 * Path-finding requests from other AI are ignored.
 */
public class PlayerInputMoveControl extends MoveControl {
    private MotorCommand command = MotorCommand.IDLE;

    public PlayerInputMoveControl(Mob mob) {
        super(mob);
    }

    public void setCommand(MotorCommand command) {
        this.command = command;
    }

    public Mob mob() {
        return mob;
    }

    public MotorCommand command() {
        return command;
    }

    @Override
    public void tick() {
        float forward = command.forward();
        float strafe = command.strafe();
        if (mob.isShiftKeyDown()) {
            float m = PlayerMovement.sneakMultiplier(mob);
            forward *= m;
            strafe *= m;
        }
        mob.setSpeed(PlayerMovement.walkSpeed(mob));
        mob.setZza(forward);
        mob.setXxa(strafe);
        if (command.jump() && mob.getJumpControl() instanceof BrainJumpControl jump) {
            jump.press();
        }
        operation = Operation.WAIT;
    }
}
