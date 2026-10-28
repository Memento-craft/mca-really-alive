package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.body.BrainDriven;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.ai.behavior.UseBonemeal;
import net.minecraft.world.entity.ai.behavior.WorkAtComposter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A brain villager farms with its own hands or not at all: the vanilla farmer's jobs that break,
 * plant and bonemeal crops and fill the composter without hands never start for it. Everything else
 * its vanilla or MCA brain does is left alone. (Scripted melee is stopped in {@link MobMixin}.)
 */
@Mixin(Behavior.class)
public abstract class BehaviorMixin {
    @Inject(method = "tryStart", at = @At("HEAD"), cancellable = true)
    private void reallyalive$noHandlessFarming(ServerLevel level, LivingEntity owner, long time, CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        // Nor do the crossbow attacks of MCA's guards, which load and fire without hands.
        if ((self instanceof HarvestFarmland || self instanceof UseBonemeal || self instanceof WorkAtComposter
                || self instanceof net.minecraft.world.entity.ai.behavior.CrossbowAttack)
                && BrainDriven.isDriven(owner)) {
            cir.setReturnValue(false);
        }
    }
}
