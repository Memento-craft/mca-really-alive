package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The obstacle milestone: in a block world with vanilla player physics, newborn brains that feel
 * good about nearing a goal they can see learn to cross open ground, jump up steps and walls, and
 * swim across ponds to reach it. Brains with the same reflexes that feel nothing for the goal are
 * the control. Each course gives 20 seconds.
 */
class ObstacleLearningTest {
    private static final int SEEDS = 3;
    private static final int TRAIN_TICKS = 30_000;
    private static final int EVAL_TICKS = 16_000;
    /** Babbling at its adult level while tested, as in game. */
    private static final float EVAL_BABBLE = 0.3f;

    @Test
    void rewardedBrainsLearnToCrossObstacles() {
        int n = BlockWorld.Course.values().length;
        int[] tried = new int[n], doneRewarded = new int[n], triedControl = new int[n], doneControl = new int[n];
        for (long seed = 1; seed <= SEEDS; seed++) {
            evaluate(seed, true, tried, doneRewarded);
            evaluate(seed, false, triedControl, doneControl);
        }
        StringBuilder report = new StringBuilder("courses cleared, rewarded vs control:");
        for (BlockWorld.Course c : BlockWorld.Course.values()) {
            int i = c.ordinal();
            report.append(String.format("%n  %-9s %d/%d vs %d/%d", c, doneRewarded[i], tried[i], doneControl[i], triedControl[i]));
        }
        System.out.println(report);
        float rewarded = rate(doneRewarded, tried);
        float control = rate(doneControl, triedControl);
        assertTrue(rewarded > control + 0.15f, report.toString());
        int needJumpOrSwim = doneRewarded[BlockWorld.Course.STEP_UP.ordinal()]
                + doneRewarded[BlockWorld.Course.LOW_WALL.ordinal()]
                + doneRewarded[BlockWorld.Course.POND.ordinal()];
        assertTrue(needJumpOrSwim >= SEEDS * 2, report.toString());
    }

    private static float rate(int[] done, int[] tried) {
        int d = 0, t = 0;
        for (int i = 0; i < done.length; i++) {
            d += done[i];
            t += tried[i];
        }
        return d / (float) Math.max(1, t);
    }

    private static void evaluate(long seed, boolean feelings, int[] tried, int[] done) {
        Brain b = Brain.create(Genome.defaults(), seed, 0f);
        BlockWorld w = new BlockWorld(seed + 2);
        SensoryFrame f = new SensoryFrame();
        b.setBabbleScale(1f);
        for (int t = 0; t < TRAIN_TICKS; t++) {
            sense(w, f, feelings);
            w.act(b.step(f));
        }
        b.setBabbleScale(EVAL_BABBLE);
        int[] tried0 = w.tried.clone(), done0 = w.done.clone();
        for (int t = 0; t < EVAL_TICKS; t++) {
            sense(w, f, feelings);
            w.act(b.step(f));
        }
        // The course still running at the end was not finished: leave it out.
        w.tried[w.course.ordinal()]--;
        for (int i = 0; i < tried.length; i++) {
            tried[i] += w.tried[i] - tried0[i];
            done[i] += w.done[i] - done0[i];
        }
    }

    private static void sense(BlockWorld w, SensoryFrame f, boolean feelings) {
        w.sense(f);
        if (!feelings) {
            f.pleasure = 0f;
            f.pain = 0f;
        }
    }
}
