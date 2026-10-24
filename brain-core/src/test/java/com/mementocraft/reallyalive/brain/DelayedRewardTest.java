package com.mementocraft.reallyalive.brain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Credit across seconds: a cue is on for 40 ticks, and pressing use during it is rewarded 40 ticks
 * after the first press, when the cue is long gone. Placing a step and only later climbing onto it
 * has the same shape.
 */
class DelayedRewardTest {
    private static final int SEEDS = 2;
    private static final int EPISODES = 1500;
    private static final int CUE_TICKS = 40;
    private static final int DELAY = 40;
    private static final int MEASURED = 300;

    @Test
    void rewardSecondsLaterIsLearned() {
        float rewarded = 0f, control = 0f;
        for (long seed = 1; seed <= SEEDS; seed++) {
            rewarded += pressRate(seed, true) / SEEDS;
            control += pressRate(seed, false) / SEEDS;
        }
        assertTrue(rewarded > control + 0.15f, rewarded + " vs " + control);
    }

    /** The fraction of cue ticks with use pressed, over the last episodes. */
    private static float pressRate(long seed, boolean reward) {
        Brain b = Brain.create(Genome.defaults(), seed, 0f);
        b.setBabbleScale(1f);
        SensoryFrame f = new SensoryFrame();
        int pressed = 0;
        for (int e = 0; e < EPISODES; e++) {
            int pressAt = -1;
            for (int t = 0; t < CUE_TICKS + DELAY + 20; t++) {
                f.clear();
                f.set(FeelChannel.ON_GROUND, 1f);
                boolean cue = t < CUE_TICKS;
                f.set(FeelChannel.HOLDING_BLOCK, cue ? 1f : 0f);
                if (reward && pressAt >= 0 && t == pressAt + DELAY) {
                    f.pleasure = 1f;
                }
                MotorCommand m = b.step(f);
                if (cue && m.use()) {
                    if (pressAt < 0) {
                        pressAt = t;
                    }
                    if (e >= EPISODES - MEASURED) {
                        pressed++;
                    }
                }
            }
        }
        return pressed / (float) (MEASURED * CUE_TICKS);
    }
}
