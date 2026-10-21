package com.mementocraft.reallyalive.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Mob.class)
public interface MobAccessor {
    @Mutable
    @Accessor("moveControl")
    void reallyalive$setMoveControl(MoveControl control);

    @Mutable
    @Accessor("lookControl")
    void reallyalive$setLookControl(LookControl control);
}
