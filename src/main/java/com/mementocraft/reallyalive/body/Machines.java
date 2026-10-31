package com.mementocraft.reallyalive.body;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Method;

/**
 * Whether a block is a machine part that is turning: a Create shaft, cog, wheel or machine with
 * rotation in it. Create is reached by reflection and is never required; without it nothing turns.
 */
public final class Machines {
    private static final Class<?> KINETIC;
    private static final Method SPEED;

    static {
        Class<?> kinetic = null;
        Method speed = null;
        try {
            kinetic = Class.forName("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            speed = kinetic.getMethod("getSpeed");
        } catch (ReflectiveOperationException | LinkageError e) {
            kinetic = null;
        }
        KINETIC = kinetic;
        SPEED = speed;
    }

    private Machines() {}

    /** Whether a machine part could be at all: Create is installed. */
    public static boolean present() {
        return KINETIC != null;
    }

    /**
     * Whether the block at a position is part of a machine, turning or not: a Create block that
     * holds something, such as a millstone, a press or the depot under it.
     */
    public static boolean isMachine(Level level, BlockPos pos) {
        if (KINETIC == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && "create".equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(be.getBlockState().getBlock()).getNamespace());
    }

    /** Whether the block at a position is a machine that things can be put in, as a depot or a millstone. */
    public static boolean holdsThings(Level level, BlockPos pos) {
        return isMachine(level, pos) && level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null) != null;
    }

    /**
     * Whether the block at a position is a working machine rather than a part that only passes
     * rotation on (a shaft, a cogwheel): a press, a millstone, a fan.
     */
    public static boolean isWorker(Level level, BlockPos pos) {
        return isMachine(level, pos) && !level.getBlockState(pos).getBlock().getClass().getName().contains(".simpleRelays.");
    }

    /** Whether the block at a position is turned by hand, as a hand crank or a valve handle is. */
    public static boolean isCrank(Level level, BlockPos pos) {
        return isMachine(level, pos) && level.getBlockState(pos).getBlock().getClass().getName().contains(".crank.");
    }

    /** Whether the block at a position is a machine part with rotation in it. */
    public static boolean turning(Level level, BlockPos pos) {
        if (KINETIC == null || !level.isLoaded(pos)) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !KINETIC.isInstance(be)) {
            return false;
        }
        try {
            return (float) SPEED.invoke(be) != 0f;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
