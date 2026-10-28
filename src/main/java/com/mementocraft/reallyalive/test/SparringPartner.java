package com.mementocraft.reallyalive.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.UUID;

/**
 * A player to spar with in game tests, where no real player is: it walks at a casual pace toward
 * whoever it fights, faces them, and swings whenever its attack has fully recharged, as an
 * ordinary player does. Unlike NeoForge's fake player it can be hurt, and its hurt and attack
 * timers run, since a fake player's own tick does nothing.
 */
final class SparringPartner extends FakePlayer {
    /** Blocks per tick it closes in: a little under a player's walking speed. */
    static final double PACE = 0.15;

    SparringPartner(ServerLevel level, String name) {
        super(level, new GameProfile(UUID.nameUUIDFromBytes(("reallyalive:spar:" + name).getBytes()), name));
    }

    /** Puts a weapon in its hand, with the attack damage and speed holding it gives a player. */
    void arm(ItemStack weapon) {
        getInventory().selected = 0;
        getInventory().items.set(0, weapon);
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            var instance = getAttribute(attribute);
            if (instance != null) {
                instance.addOrReplacePermanentModifier(modifier);
            }
        });
    }

    /** One tick of fighting this foe: timers run, it turns to face them, closes in and swings when ready. */
    void fight(LivingEntity foe) {
        attackStrengthTicker++;
        if (invulnerableTime > 0) {
            invulnerableTime--;
        }
        if (hurtTime > 0) {
            hurtTime--;
        }
        Vec3 to = foe.getEyePosition().subtract(getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG);
        setYRot(yaw);
        setYHeadRot(yaw);
        setXRot(pitch);
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat > 2.5) {
            setPos(getX() + to.x / flat * PACE, getY(), getZ() + to.z / flat * PACE);
        }
        // Knockback it took fades as it would on the ground.
        Vec3 push = getDeltaMovement();
        if (push.lengthSqr() > 1e-4) {
            setPos(getX() + push.x, getY(), getZ() + push.z);
            setDeltaMovement(push.scale(0.5));
        }
        if (flat < 3.0 && getAttackStrengthScale(0.5f) >= 1f) {
            attack(foe);
            swing(InteractionHand.MAIN_HAND);
        }
    }

    /**
     * A new player cannot be hurt for its first three seconds, a timer its own tick counts down;
     * a fake player never ticks, so it would never be hurt at all.
     */
    void settleIn() {
        net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(
                net.minecraft.server.level.ServerPlayer.class, this, 0, "spawnInvulnerableTime");
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return false;
    }
}
