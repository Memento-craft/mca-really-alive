package com.mementocraft.reallyalive.sense;

import com.mementocraft.reallyalive.brain.FeelChannel;
import com.mementocraft.reallyalive.brain.OlfactoryChannel;
import com.mementocraft.reallyalive.brain.SenseLayout;
import com.mementocraft.reallyalive.brain.SensoryFrame;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Reads the world the way a player perceives it and fills a {@link SensoryFrame}: eyes that cast
 * rays inside a player's field of view, a body that feels touch, motion and balance, and a nose
 * that picks up scents drifting from nearby things. Runs on the server thread.
 */
public final class Senses {
    /** A player's default vertical field of view, and the matching horizontal span at 16:9. */
    public static final float FOV_V = 70f;
    public static final float FOV_H = 100f;
    public static final double SMELL_RANGE = 16.0;
    /** Half the angle between neighbouring eye rays, as a slope. */
    private static final double COLUMN_SPREAD = Math.tan(Math.toRadians(FOV_H / SenseLayout.VISION_W / 2.0));
    private static final double ROW_SPREAD = Math.tan(Math.toRadians(FOV_V / SenseLayout.VISION_H / 2.0));
    private static final int SCENT_SAMPLES = 24;

    private Senses() {}

    /** Casts the eye rays. Column 0 is the left edge of view, row 0 the top. */
    public static void see(LivingEntity body, double range, float[] vision) {
        Level level = body.level();
        Vec3 eye = body.getEyePosition();
        List<Entity> nearby = level.getEntities(body, body.getBoundingBox().inflate(range),
                e -> e.isAlive() && e.isPickable());
        for (int y = 0; y < SenseLayout.VISION_H; y++) {
            float pitch = Mth.clamp(body.getXRot() + ((y + 0.5f) / SenseLayout.VISION_H - 0.5f) * FOV_V, -90f, 90f);
            for (int x = 0; x < SenseLayout.VISION_W; x++) {
                float yaw = body.getYRot() + ((x + 0.5f) / SenseLayout.VISION_W - 0.5f) * FOV_H;
                Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(range));
                BlockHitResult block = clipVisible(level, eye, end, body);
                double blockDist = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);

                Entity seen = null;
                double entityDist = blockDist;
                for (Entity e : nearby) {
                    // Each ray stands for its whole share of the view, so a creature between two
                    // rays is still seen instead of flickering in and out as it moves.
                    double d0 = e.getBoundingBox().getCenter().distanceTo(eye);
                    AABB box = e.getBoundingBox().inflate(e.getPickRadius() + d0 * COLUMN_SPREAD,
                            e.getPickRadius() + d0 * ROW_SPREAD, e.getPickRadius() + d0 * COLUMN_SPREAD);
                    Optional<Vec3> hit = box.clip(eye, end);
                    if (hit.isPresent()) {
                        double d = hit.get().distanceTo(eye);
                        if (d < entityDist) {
                            entityDist = d;
                            seen = e;
                        }
                    }
                }

                float near;
                float solid = 0f;
                float hazard = 0f;
                float creature = 0f;
                if (seen != null) {
                    near = (float) (1.0 - entityDist / range);
                    creature = seen instanceof LivingEntity ? 1f : 0.5f;
                    hazard = seen instanceof Enemy ? 1f : 0f;
                } else if (block.getType() != HitResult.Type.MISS) {
                    near = (float) (1.0 - blockDist / range);
                    BlockState state = level.getBlockState(block.getBlockPos());
                    boolean liquid = !level.getFluidState(block.getBlockPos()).isEmpty() && state.getCollisionShape(level, block.getBlockPos()).isEmpty();
                    solid = liquid ? 0.4f : 1f;
                    hazard = isHazard(level, block.getBlockPos(), state) ? 1f : 0f;
                } else {
                    near = 0f;
                }
                vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = near;
                vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = solid;
                vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_HAZARD)] = hazard;
                vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_CREATURE)] = creature;
            }
        }
    }

    /**
     * The first block a ray hits that a player could see. Blocks that are never drawn (barriers,
     * light blocks) are looked through, as a player's eyes would.
     */
    private static BlockHitResult clipVisible(Level level, Vec3 from, Vec3 end, Entity body) {
        Vec3 start = from;
        Vec3 dir = end.subtract(from).normalize();
        for (int i = 0; i < 8; i++) {
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, body));
            if (hit.getType() == HitResult.Type.MISS
                    || level.getBlockState(hit.getBlockPos()).getRenderShape() != RenderShape.INVISIBLE
                    || !level.getFluidState(hit.getBlockPos()).isEmpty()) {
                return hit;
            }
            // Step just past the invisible block's face and look on.
            start = hit.getLocation().add(dir.scale(1e-3));
            while (level.getBlockState(BlockPos.containing(start)).getRenderShape() == RenderShape.INVISIBLE
                    && !level.getBlockState(BlockPos.containing(start)).isAir() && start.distanceToSqr(from) < end.distanceToSqr(from)) {
                start = start.add(dir.scale(0.25));
            }
            if (start.distanceToSqr(from) >= end.distanceToSqr(from)) {
                break;
            }
        }
        return BlockHitResult.miss(end, net.minecraft.core.Direction.getNearest(dir.x, dir.y, dir.z), BlockPos.containing(end));
    }

    private static boolean isHazard(Level level, BlockPos pos, BlockState state) {
        return level.getFluidState(pos).is(FluidTags.LAVA)
                || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    /** Touch, motion, balance and the state of the body. */
    public static void feel(LivingEntity body, SensoryFrame f) {
        f.set(FeelChannel.ON_GROUND, body.onGround() ? 1f : 0f);
        f.set(FeelChannel.IN_WATER, body.isInWater() ? 1f : 0f);
        f.set(FeelChannel.UNDER_WATER, body.isUnderWater() ? 1f : 0f);
        f.set(FeelChannel.IN_LAVA, body.isInLava() ? 1f : 0f);
        f.set(FeelChannel.ON_CLIMBABLE, body.onClimbable() ? 1f : 0f);

        float yawRad = body.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yawRad);
        double fz = Mth.cos(yawRad);
        double probe = 0.05;
        Level level = body.level();
        AABB box = body.getBoundingBox();
        f.set(FeelChannel.TOUCH_FRONT, touching(level, body, box.move(fx * probe, 0, fz * probe)));
        f.set(FeelChannel.TOUCH_BACK, touching(level, body, box.move(-fx * probe, 0, -fz * probe)));
        f.set(FeelChannel.TOUCH_LEFT, touching(level, body, box.move(fz * probe, 0, -fx * probe)));
        f.set(FeelChannel.TOUCH_RIGHT, touching(level, body, box.move(-fz * probe, 0, fx * probe)));
        f.set(FeelChannel.TOUCH_HEAD, touching(level, body, box.move(0, probe, 0)));

        Vec3 v = body.getDeltaMovement();
        float forward = (float) (v.x * fx + v.z * fz);
        float left = (float) (v.x * fz - v.z * fx);
        f.set(FeelChannel.VEL_FORWARD, forward * 4f);
        f.set(FeelChannel.VEL_BACKWARD, -forward * 4f);
        f.set(FeelChannel.VEL_LEFT, left * 4f);
        f.set(FeelChannel.VEL_RIGHT, -left * 4f);
        f.set(FeelChannel.VEL_UP, (float) v.y * 2.5f);
        f.set(FeelChannel.VEL_DOWN, (float) -v.y * 2.5f);

        f.set(FeelChannel.HEAD_UP, -body.getXRot() / 90f);
        f.set(FeelChannel.HEAD_DOWN, body.getXRot() / 90f);
        f.set(FeelChannel.FALL_DISTANCE, body.fallDistance / 10f);
        f.set(FeelChannel.HEALTH, body.getHealth() / body.getMaxHealth());
        f.set(FeelChannel.HUNGER, 0f);
        f.set(FeelChannel.SNEAKING, body.isShiftKeyDown() ? 1f : 0f);
        f.set(FeelChannel.SPRINTING, body.isSprinting() ? 1f : 0f);
        f.set(FeelChannel.SWIMMING, body.isSwimming() ? 1f : 0f);
    }

    private static float touching(Level level, Entity body, AABB box) {
        return level.noCollision(body, box) ? 0f : 1f;
    }

    /**
     * Scents from nearby creatures and items, plus a few sampled blocks per tick. The previous
     * frame's smell lingers and fades, like a scent in the air.
     */
    public static void smell(LivingEntity body, SensoryFrame f, float[] lingering, BlockPos goal) {
        float[] now = new float[OlfactoryChannel.COUNT];
        Level level = body.level();
        Vec3 nose = body.getEyePosition();
        for (Entity e : level.getEntities(body, body.getBoundingBox().inflate(SMELL_RANGE))) {
            float strength = strength(nose.distanceToSqr(e.position()));
            if (e instanceof Enemy) {
                now[OlfactoryChannel.HOSTILE.ordinal()] += strength;
            } else if (e instanceof Player) {
                now[OlfactoryChannel.PLAYER.ordinal()] += strength;
            } else if (e instanceof AbstractVillager) {
                now[OlfactoryChannel.VILLAGER.ordinal()] += strength;
            } else if (e instanceof Animal) {
                now[OlfactoryChannel.ANIMAL.ordinal()] += strength;
            } else if (e instanceof ItemEntity item && item.getItem().has(DataComponents.FOOD)) {
                now[OlfactoryChannel.FOOD.ordinal()] += strength;
            }
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        var random = body.getRandom();
        for (int i = 0; i < SCENT_SAMPLES; i++) {
            pos.set(body.getX() + random.nextInt(17) - 8, body.getY() + random.nextInt(9) - 4,
                    body.getZ() + random.nextInt(17) - 8);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            float strength = strength(nose.distanceToSqr(Vec3.atCenterOf(pos))) * 4f;
            if (state.is(BlockTags.FLOWERS)) {
                now[OlfactoryChannel.FLOWER.ordinal()] += strength;
            } else if (state.is(BlockTags.FIRE) || state.getBlock() instanceof CampfireBlock) {
                now[OlfactoryChannel.SMOKE.ordinal()] += strength;
            } else if (state.is(BlockTags.CROPS)) {
                now[OlfactoryChannel.FOOD.ordinal()] += strength;
            } else if (level.getFluidState(pos).is(FluidTags.WATER)) {
                now[OlfactoryChannel.WATER.ordinal()] += strength * 0.25f;
            }
        }
        if (goal != null) {
            now[OlfactoryChannel.GOAL.ordinal()] = 1f / (1f + (float) Math.sqrt(nose.distanceToSqr(Vec3.atCenterOf(goal))) / 4f);
        }
        for (int c = 0; c < OlfactoryChannel.COUNT; c++) {
            lingering[c] = Math.max(Math.min(1f, now[c]), lingering[c] * 0.9f);
            f.smell[c] = lingering[c];
        }
    }

    private static float strength(double distSqr) {
        return (float) (1.0 / (1.0 + distSqr / 16.0));
    }
}
