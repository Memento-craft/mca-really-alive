package com.mementocraft.reallyalive.sense;

import com.mementocraft.reallyalive.brain.FeelChannel;
import com.mementocraft.reallyalive.brain.OlfactoryChannel;
import com.mementocraft.reallyalive.brain.SensoryFrame;
import com.mementocraft.reallyalive.brain.WordCode;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Words for what a villager is seeing, smelling, feeling or doing, said now and then while someone
 * is near, the way parents point and say "a cow!" to a child. The brain is only told the word: it
 * links it to whatever its concept lobe holds at that moment, and learns what the word means from
 * the many different moments it hears it in (see the brain's ConceptLinks). Nothing here makes a
 * villager speak.
 */
public final class Naming {
    /** Chance per tick, while someone is near, that something is named: about every three seconds. */
    private static final float CHANCE = 0.015f;
    private static final double COMPANY_RANGE = 8.0;
    private static final double SEE_RANGE = 12.0;
    private static final double REACH = 5.0;
    private static final float SMELL_ON = 0.4f;

    private Naming() {}

    public static void name(LivingEntity body, SensoryFrame f) {
        RandomSource random = body.getRandom();
        if (random.nextFloat() >= CHANCE || !someoneNear(body)) {
            return;
        }
        List<String> things = new ArrayList<>();
        LivingEntity seen = creatureInView(body);
        if (seen != null) {
            things.add(seen.getType().getDescription().getString());
        }
        String block = lookedAtBlock(body);
        if (block != null) {
            things.add(block);
        }
        feelings(body, f, things);
        smells(f, things);
        if (things.isEmpty()) {
            return;
        }
        for (String w : WordCode.tokenize(things.get(random.nextInt(things.size())))) {
            f.name(w);
        }
    }

    private static boolean someoneNear(LivingEntity body) {
        return !body.level().getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(COMPANY_RANGE),
                e -> e != body && e.isAlive() && (e instanceof Player || e instanceof AbstractVillager)).isEmpty();
    }

    /** The nearest creature in the middle of its view that it has a clear line of sight to. */
    private static LivingEntity creatureInView(LivingEntity body) {
        Vec3 eye = body.getEyePosition();
        Vec3 look = body.getLookAngle();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : body.level().getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(SEE_RANGE),
                e -> e != body && e.isAlive())) {
            Vec3 to = e.getEyePosition().subtract(eye);
            double dist = to.length();
            if (dist < 1e-3 || dist > SEE_RANGE || to.normalize().dot(look) < Mth.cos(Senses.FOV_H / 4f * Mth.DEG_TO_RAD)) {
                continue;
            }
            if (dist < bestDist && body.hasLineOfSight(e)) {
                best = e;
                bestDist = dist;
            }
        }
        return best;
    }

    /** The block or liquid in the crosshair, within arm's reach. */
    private static String lookedAtBlock(LivingEntity body) {
        HitResult hit = body.pick(REACH, 1f, true);
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        var fluid = body.level().getFluidState(b.getBlockPos());
        if (fluid.is(FluidTags.WATER)) {
            return "water";
        }
        if (fluid.is(FluidTags.LAVA)) {
            return "lava";
        }
        BlockState state = body.level().getBlockState(b.getBlockPos());
        List<String> words = WordCode.tokenize(state.getBlock().getName().getString());
        // "oak planks", "cobblestone stairs": the last word says what it is.
        return words.isEmpty() ? null : words.get(words.size() - 1);
    }

    private static void feelings(LivingEntity body, SensoryFrame f, List<String> things) {
        if (body.isInWater()) {
            things.add(body.isSwimming() || !body.onGround() ? "swimming" : "water");
        }
        if (body.onClimbable()) {
            things.add("climbing");
        }
        if (f.pain > 0.1f || body.getHealth() < body.getMaxHealth() * 0.5f) {
            things.add("hurt");
        }
        if (body.isOnFire()) {
            things.add("fire");
        }
        if (f.feel[FeelChannel.DIG_PROGRESS.ordinal()] > 0.1f) {
            things.add("digging");
        }
        if (body.fallDistance > 2f) {
            things.add("falling");
        } else if (body.isSprinting()) {
            things.add("running");
        } else if (body.onGround() && f.feel[FeelChannel.VEL_FORWARD.ordinal()] > 0.3f) {
            things.add("walking");
        }
    }

    private static void smells(SensoryFrame f, List<String> things) {
        String[] words = new String[OlfactoryChannel.COUNT];
        words[OlfactoryChannel.FOOD.ordinal()] = "food";
        words[OlfactoryChannel.SMOKE.ordinal()] = "smoke";
        words[OlfactoryChannel.FLOWER.ordinal()] = "flowers";
        words[OlfactoryChannel.WATER.ordinal()] = "water";
        for (int c = 0; c < OlfactoryChannel.COUNT; c++) {
            if (words[c] != null && f.smell[c] > SMELL_ON) {
                things.add(words[c]);
            }
        }
    }
}
