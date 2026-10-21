package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Benchmark for the next learning milestone: a newborn brain should come to keep a goal in view
 * (and walk to it) more than a brain that babbles just as much but feels nothing for it.
 */
class MovementLearningTest {
    @Test
    @Disabled("Target for the next phase: orienting is not yet learned reliably across brains")
    void rewardedBrainsKeepTheGoalInViewMoreThanUnrewardedOnes() {
        float rewarded = 0f;
        float control = 0f;
        int seeds = 6;
        for (long seed = 1; seed <= seeds; seed++) {
            rewarded += goalInViewAfterTraining(seed, true) / seeds;
            control += goalInViewAfterTraining(seed, false) / seeds;
        }
        System.out.printf("goal in view: rewarded %.2f, control %.2f%n", rewarded, control);
        assertTrue(rewarded > control + 0.15f, "rewarded " + rewarded + ", control " + control);
    }

    private static float goalInViewAfterTraining(long seed, boolean feelings) {
        Brain b = Brain.create(Genome.defaults(), seed, 0f);
        ToyWorld w = new ToyWorld(seed + 2);
        SensoryFrame f = new SensoryFrame();
        b.setBabbleScale(1f);
        for (int t = 0; t < 40_000; t++) {
            sense(w, f, feelings);
            w.act(b.step(f));
        }
        b.setBabbleScale(0.3f);
        int inView = 0;
        for (int t = 0; t < 4000; t++) {
            sense(w, f, feelings);
            if (Math.abs(w.bearing()) < ToyWorld.FOV_H / 2f) {
                inView++;
            }
            w.act(b.step(f));
        }
        return inView / 4000f;
    }

    private static void sense(ToyWorld w, SensoryFrame f, boolean feelings) {
        w.sense(f);
        if (!feelings) {
            f.pleasure = 0f;
            f.pain = 0f;
        }
    }
}
