package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.body.BrainDriven;
import com.mementocraft.reallyalive.body.PlayerMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sneaking brain-driven entities stop at ledges the way players do. No effect otherwise. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "maybeBackOffFromEdge", at = @At("HEAD"), cancellable = true)
    private void reallyalive$sneakEdge(Vec3 move, MoverType type, CallbackInfoReturnable<Vec3> cir) {
        if (BrainDriven.isDriven(this)) {
            cir.setReturnValue(PlayerMovement.backOffFromEdge((Entity) (Object) this, move, type));
        }
    }
}
