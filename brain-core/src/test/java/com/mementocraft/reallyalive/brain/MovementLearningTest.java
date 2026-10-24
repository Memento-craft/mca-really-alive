package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The phase 1 learning milestone: newborn brains that feel good about nearing a goal they can see
 * learn to walk to it, while brains that babble just as much but feel nothing never get there.
 * Both start with the same inborn reflexes, so the difference is what was learned.
 */
class MovementLearningTest {
    private static final int SEEDS = 6;
    private static final int TRAIN_TICKS = 30_000;
    private static final int EVAL_TICKS = 3000;

    @Test
    void rewardedBrainsLearnToWalkToTheGoal() {
        float rewarded = 0f;
        float control = 0f;
        int rewardedSeedsThatGotThere = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            int r = goalsReachedAfterTraining(seed, true);
            int c = goalsReachedAfterTraining(seed, false);
            System.out.printf("seed %d: rewarded reached %d, control reached %d%n", seed, r, c);
            rewarded += r / (float) SEEDS;
            control += c / (float) SEEDS;
            if (r > 0) {
                rewardedSeedsThatGotThere++;
            }
        }
        System.out.printf("goals reached per %d ticks: rewarded %.1f, control %.1f%n", EVAL_TICKS, rewarded, control);
        assertTrue(rewarded >= control + 3f, "rewarded " + rewarded + ", control " + control);
        assertTrue(rewardedSeedsThatGotThere >= SEEDS - 1, rewardedSeedsThatGotThere + " of " + SEEDS + " rewarded brains reached a goal");
    }

    private static int goalsReachedAfterTraining(long seed, boolean feelings) {
        Brain b = Brain.create(Genome.defaults(), seed, 0f);
        ToyWorld w = new ToyWorld(seed + 2);
        SensoryFrame f = new SensoryFrame();
        b.setBabbleScale(1f);
        for (int t = 0; t < TRAIN_TICKS; t++) {
            sense(w, f, feelings);
            w.act(b.step(f));
        }
        // No babbling while it is tested: only what it learned moves it.
        b.setBabbleScale(0f);
        int before = w.reached;
        for (int t = 0; t < EVAL_TICKS; t++) {
            sense(w, f, feelings);
            w.act(b.step(f));
        }
        return w.reached - before;
    }

    private static void sense(ToyWorld w, SensoryFrame f, boolean feelings) {
        w.sense(f);
        if (!feelings) {
            f.pleasure = 0f;
            f.pain = 0f;
        }
    }
}
