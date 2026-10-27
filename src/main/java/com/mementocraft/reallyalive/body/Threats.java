package com.mementocraft.reallyalive.body;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Who a brain villager counts as a threat, and the blows it has felt. Monsters are threats from
 * birth, as a child fears a growling dog; anyone else becomes one by hurting it (a player, another
 * villager, a wolf), and stays one for a while after. This is what makes landing a blow feel good,
 * marks a creature as dangerous to the eyes, and lets the strike reflex fire: a villager that never
 * gets hurt by a person never wants to hit one.
 */
public final class Threats {
    /** Ticks someone who hurt it stays a threat: half a Minecraft day. */
    public static final int GRUDGE_TICKS = 12_000;
    /** Ticks a blow is felt as coming from a side, as a player's screen tips for half a second. */
    public static final int HURT_FELT_TICKS = 10;
    /** Ticks a threat it hurt counts as its own to defeat, should it die. */
    public static final int CREDIT_TICKS = 200;

    private final Map<UUID, Long> grudges = new HashMap<>();
    private final Map<UUID, Long> hurtByMe = new HashMap<>();
    private Vec3 blowFrom;
    private long blowAt = Long.MIN_VALUE / 2;

    /** True when this creature is a threat to it now. */
    public boolean isThreat(Entity e, long now) {
        if (!(e instanceof LivingEntity living) || !living.isAlive()) {
            return false;
        }
        if (e instanceof Enemy) {
            return true;
        }
        Long since = grudges.get(e.getUUID());
        return since != null && now - since < GRUDGE_TICKS;
    }

    /** It was hurt, by this creature (or null), from this place (or null). */
    public void hurtBy(Entity attacker, Vec3 from, long now) {
        if (attacker != null) {
            grudges.put(attacker.getUUID(), now);
        }
        if (from != null) {
            blowFrom = from;
            blowAt = now;
        }
    }

    /** It hurt this threat. */
    public void hit(Entity victim, long now) {
        hurtByMe.put(victim.getUUID(), now);
    }

    /**
     * A creature died: true when it was a threat this villager hurt lately, a fight won. Forgets
     * it either way.
     */
    public boolean died(Entity victim, long now) {
        grudges.remove(victim.getUUID());
        Long at = hurtByMe.remove(victim.getUUID());
        return at != null && now - at < CREDIT_TICKS;
    }

    /**
     * How strongly the last blow is felt from each side (front, back, left, right) for a body
     * facing this way, fading over {@link #HURT_FELT_TICKS}.
     */
    public void blowFelt(LivingEntity body, long now, float[] sides) {
        java.util.Arrays.fill(sides, 0f);
        long age = now - blowAt;
        if (blowFrom == null || age < 0 || age >= HURT_FELT_TICKS) {
            return;
        }
        float strength = 1f - age / (float) HURT_FELT_TICKS;
        Vec3 to = blowFrom.subtract(body.position());
        float bearing = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - body.getYRot());
        float a = Math.abs(bearing);
        if (a <= 45f) {
            sides[0] = strength;
        } else if (a >= 135f) {
            sides[1] = strength;
        } else if (bearing < 0f) {
            sides[2] = strength;
        } else {
            sides[3] = strength;
        }
    }

    /** Lets go of grudges and credit long past, so the maps do not grow forever. */
    public void forgetOld(long now) {
        for (Iterator<Map.Entry<UUID, Long>> it = grudges.entrySet().iterator(); it.hasNext(); ) {
            if (now - it.next().getValue() >= GRUDGE_TICKS) {
                it.remove();
            }
        }
        hurtByMe.values().removeIf(at -> now - at >= CREDIT_TICKS);
    }
}
