package com.mementocraft.reallyalive.mixin;

import com.mementocraft.reallyalive.BrainManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets a brain villager know it harvested a ripe crop, however it broke it: with its own hands or
 * by the vanilla or MCA farming it does when its brain is off. Changes nothing about the breaking.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"))
    private void reallyalive$harvest(BlockPos pos, boolean drop, Entity breaker, int recursion, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (level.isClientSide() || !(breaker instanceof Villager v) || BrainManager.get() == null) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            BrainManager.get().harvested(v, BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().replace('_', ' '));
        }
    }
}
