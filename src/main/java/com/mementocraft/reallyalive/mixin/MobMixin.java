package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.body.BrainDriven;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A brain villager hits only with its own hands. MCA's guards (and any mod's scripted attack) strike
 * through Mob.doHurtTarget without aiming or swinging; for a brain villager that never lands.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
    @Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
    private void reallyalive$onlyHandsHit(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (BrainDriven.isDriven((Mob) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
