package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.body.BrainDriven;
import com.mementocraft.reallyalive.body.PlayerMovement;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gives brain-driven entities player pose sizes and a player's air control. No effect otherwise. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements BrainDriven {
    @Unique
    private boolean reallyalive$driven;

    @Override
    public boolean reallyalive$isDriven() {
        return reallyalive$driven;
    }

    @Override
    public void reallyalive$setDriven(boolean driven) {
        reallyalive$driven = driven;
    }

    @Inject(method = "getFlyingSpeed", at = @At("HEAD"), cancellable = true)
    private void reallyalive$playerAirSpeed(CallbackInfoReturnable<Float> cir) {
        if (reallyalive$driven) {
            LivingEntity self = (LivingEntity) (Object) this;
            cir.setReturnValue(self.isSprinting() ? PlayerMovement.SPRINT_AIR_SPEED : PlayerMovement.AIR_SPEED);
        }
    }

    /** Keeps the villager's own (MCA) size, shrunk for crouching or swimming the way a player is. */
    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void reallyalive$poseSize(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (reallyalive$driven) {
            cir.setReturnValue(PlayerMovement.poseDimensions(cir.getReturnValue(), pose));
        }
    }
}
