package com.mementocraft.reallyalive.brain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * A goal teaches the steps to it. Nothing feels good in itself here: no pleasure is ever given.
 * A villager that holds the goal of placing dirt, and knows that takes breaking some first, feels
 * a step's small pleasure when its hands break a block, and so learns to press attack. One that
 * holds no goal, given the same world, does not.
 */
class GoalCreditTest {
    private static final int SEEDS = 2;
    private static final int EPISODES = 800;
    private static final int CUE_TICKS = 40;
    private static final int REST_TICKS = 20;
    private static final int MEASURED = 200;

    @Test
    void holdingAGoalTeachesTheStepToIt() {
        float goal = 0f, control = 0f;
        for (long seed = 1; seed <= SEEDS; seed++) {
            float g = pressRate(seed, true), c = pressRate(seed, false);
            System.out.printf("seed %d: attack pressed %.2f of the time with the goal, %.2f without%n", seed, g, c);
            assertTrue(g > c, "seed " + seed + ": " + g + " with the goal vs " + c);
            goal += g / SEEDS;
            control += c / SEEDS;
        }
        // A full pleasure given for the same press lifts it about 0.11 over these episodes.
        assertTrue(goal > control + 0.04f, goal + " vs " + control);
    }

    /** The fraction of cue ticks with attack pressed, over the last episodes. */
    private static float pressRate(long seed, boolean holdsGoal) {
        Brain b = Brain.create(Genome.defaults(), seed, 0f);
        b.setBabbleScale(1f);
        b.goals.told("placed dirt", "broke dirt");
        SensoryFrame f = new SensoryFrame();
        int pressed = 0;
        for (int e = 0; e < EPISODES; e++) {
            if (holdsGoal) {
                b.goals.want("placed dirt", 0);
            }
            boolean broke = false, attack = false;
            for (int t = 0; t < CUE_TICKS + REST_TICKS; t++) {
                f.clear();
                f.set(FeelChannel.ON_GROUND, 1f);
                boolean cue = t < CUE_TICKS;
                f.set(FeelChannel.HOLDING_BLOCK, cue ? 1f : 0f);
                if (attack && cue && !broke) {
                    f.did.add("broke dirt"); // the block in front of it gave way
                    broke = true;
                }
                MotorCommand m = b.step(f);
                attack = m.attack();
                if (cue && attack && e >= EPISODES - MEASURED) {
                    pressed++;
                }
            }
        }
        return pressed / (float) (MEASURED * CUE_TICKS);
    }
}
