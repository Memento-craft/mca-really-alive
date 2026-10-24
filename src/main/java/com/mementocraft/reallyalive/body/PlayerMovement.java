package com.mementocraft.reallyalive.body;

import com.mementocraft.reallyalive.brain.MotorCommand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Set;

/**
 * The rules that make a brain-driven villager move exactly as a player can: player speed, player
 * pose proportions, sprint rules, sneaking that stops at ledges, and player air control. The
 * villager keeps its own size, since MCA villagers vary in height and width. Everything
 * else (gravity, friction, step height, jumping, swimming, ladders) already comes from the living
 * entity physics that players and villagers share.
 */
public final class PlayerMovement {
    /** A player's base walking speed attribute. */
    public static final double PLAYER_WALK_SPEED = 0.1;
    /** A player's default sneaking speed multiplier. */
    public static final float PLAYER_SNEAK_SPEED = 0.3f;
    public static final float AIR_SPEED = 0.02f;
    public static final float SPRINT_AIR_SPEED = 0.025999999f;

    private static final EntityDimensions STANDING = EntityDimensions.scalable(0.6f, 1.8f).withEyeHeight(1.62f);
    private static final EntityDimensions CROUCHING = EntityDimensions.scalable(0.6f, 1.5f).withEyeHeight(1.27f);
    private static final EntityDimensions LOW = EntityDimensions.scalable(0.6f, 0.6f).withEyeHeight(0.4f);
    private static final EntityDimensions DYING = EntityDimensions.fixed(0.2f, 0.2f).withEyeHeight(1.62f);

    /** A player's hitbox for each pose. */
    private static EntityDimensions dimensions(Pose pose) {
        return switch (pose) {
            case CROUCHING -> CROUCHING;
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> LOW;
            case DYING -> DYING;
            default -> STANDING;
        };
    }

    /**
     * The entity's own standing size, resized for a pose by the same proportions a player's hitbox
     * changes (for example crouching is 1.5/1.8 of standing height).
     */
    public static EntityDimensions poseDimensions(EntityDimensions own, Pose pose) {
        if (pose == Pose.SLEEPING || pose == Pose.STANDING) {
            return own;
        }
        EntityDimensions player = dimensions(pose);
        float heightRatio = player.height() / STANDING.height();
        float widthRatio = player.width() / STANDING.width();
        float eyeRatio = player.eyeHeight() / STANDING.height();
        float height = own.height() * heightRatio;
        EntityDimensions resized = own.fixed()
                ? EntityDimensions.fixed(own.width() * widthRatio, height)
                : EntityDimensions.scalable(own.width() * widthRatio, height);
        return resized.withEyeHeight(own.height() * eyeRatio);
    }

    /**
     * Speed modifiers that come from being a villager rather than from anything a player could also
     * have: MCA's inherited speed gene. A brain-driven villager moves at a player's speed, so these
     * are left out.
     */
    private static final Set<ResourceLocation> NOT_FOR_PLAYERS = Set.of(
            ResourceLocation.fromNamespaceAndPath("mca", "trait_speed"));

    /**
     * The speed a player would have with this mob's speed effects: the mob's movement speed
     * attribute (including sprinting and potions) rescaled from its own base to a player's base.
     */
    public static float walkSpeed(LivingEntity e) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null || speed.getBaseValue() <= 0) {
            return (float) PLAYER_WALK_SPEED;
        }
        return (float) (playerModifiedValue(speed) * PLAYER_WALK_SPEED / speed.getBaseValue());
    }

    /** The attribute's value as vanilla computes it, skipping {@link #NOT_FOR_PLAYERS}. */
    private static double playerModifiedValue(AttributeInstance a) {
        Collection<AttributeModifier> modifiers = a.getModifiers();
        if (modifiers.stream().noneMatch(m -> NOT_FOR_PLAYERS.contains(m.id()))) {
            return a.getValue();
        }
        double added = a.getBaseValue();
        for (AttributeModifier m : modifiers) {
            if (m.operation() == AttributeModifier.Operation.ADD_VALUE && !NOT_FOR_PLAYERS.contains(m.id())) {
                added += m.amount();
            }
        }
        double value = added;
        for (AttributeModifier m : modifiers) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE && !NOT_FOR_PLAYERS.contains(m.id())) {
                value += added * m.amount();
            }
        }
        for (AttributeModifier m : modifiers) {
            if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL && !NOT_FOR_PLAYERS.contains(m.id())) {
                value *= 1.0 + m.amount();
            }
        }
        return a.getAttribute().value().sanitizeValue(value);
    }

    public static float sneakMultiplier(LivingEntity e) {
        AttributeInstance sneak = e.getAttribute(Attributes.SNEAKING_SPEED);
        return sneak == null ? PLAYER_SNEAK_SPEED : (float) sneak.getValue();
    }

    /**
     * Applies the look, sneak, sprint and pose parts of a command before the entity ticks. The move
     * keys and jump are applied by {@link PlayerInputMoveControl} during the entity's AI step, in the
     * same place a player's keyboard input is read.
     */
    public static void applyBeforeTick(Mob mob, MotorCommand c) {
        float yaw = Mth.wrapDegrees(mob.getYRot() + c.yawDelta());
        float pitch = Mth.clamp(mob.getXRot() + c.pitchDelta(), -90f, 90f);
        mob.setYRot(yaw);
        mob.setXRot(pitch);
        mob.setYHeadRot(yaw);

        mob.setShiftKeyDown(c.sneak());

        // Player sprint rules: only while moving forward, not sneaking, not blind, and a solid
        // bump into a wall stops it.
        boolean canSprint = c.forward() > 0 && !c.sneak() && !mob.isUsingItem()
                && !mob.hasEffect(MobEffects.BLINDNESS);
        boolean bumped = mob.horizontalCollision && !mob.minorHorizontalCollision;
        mob.setSprinting(c.sprint() && canSprint && !bumped);

        mob.updateSwimming();
        updatePose(mob);
        mob.getNavigation().stop();
    }

    /** Chooses the pose a player would be in, crouching or crawling where there is no headroom. */
    public static void updatePose(LivingEntity e) {
        if (e.isSleeping() || e.isDeadOrDying()) {
            return;
        }
        Pose desired;
        if (e.isFallFlying()) {
            desired = Pose.FALL_FLYING;
        } else if (e.isSwimming()) {
            desired = Pose.SWIMMING;
        } else if (e.isShiftKeyDown()) {
            desired = Pose.CROUCHING;
        } else {
            desired = Pose.STANDING;
        }
        if (!e.isSpectator() && !e.isPassenger() && !canFit(e, desired)) {
            desired = canFit(e, Pose.CROUCHING) ? Pose.CROUCHING : Pose.SWIMMING;
        }
        if (e.getPose() != desired) {
            e.setPose(desired);
        }
    }

    private static boolean canFit(LivingEntity e, Pose pose) {
        AABB box = e.getDimensions(pose).makeBoundingBox(e.position()).deflate(1.0E-7);
        return e.level().noCollision(e, box);
    }

    /** A player's sneak edge protection: while sneaking on the ground, never step off a ledge. */
    public static Vec3 backOffFromEdge(Entity e, Vec3 move, MoverType type) {
        float step = e.maxUpStep();
        if (move.y > 0.0 || (type != MoverType.SELF && type != MoverType.PLAYER)
                || !e.isShiftKeyDown() || !isAboveGround(e, step)) {
            return move;
        }
        double x = move.x;
        double z = move.z;
        double inc = 0.05;
        AABB box = e.getBoundingBox();
        while (x != 0.0 && e.level().noCollision(e, box.move(x, -step, 0.0))) {
            x = Math.abs(x) < inc ? 0.0 : x - Math.signum(x) * inc;
        }
        while (z != 0.0 && e.level().noCollision(e, box.move(0.0, -step, z))) {
            z = Math.abs(z) < inc ? 0.0 : z - Math.signum(z) * inc;
        }
        while (x != 0.0 && z != 0.0 && e.level().noCollision(e, box.move(x, -step, z))) {
            x = Math.abs(x) < inc ? 0.0 : x - Math.signum(x) * inc;
            z = Math.abs(z) < inc ? 0.0 : z - Math.signum(z) * inc;
        }
        return new Vec3(x, move.y, z);
    }

    private static boolean isAboveGround(Entity e, float step) {
        return e.onGround() || e.fallDistance < step
                && !e.level().noCollision(e, e.getBoundingBox().move(0.0, e.fallDistance - step, 0.0));
    }

    private PlayerMovement() {}
}
