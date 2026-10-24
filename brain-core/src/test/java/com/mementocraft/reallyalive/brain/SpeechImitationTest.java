package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wernicke's area learning to talk the way children do: a newborn babbles words and hears itself,
 * and so learns which word it says makes which sound. Afterwards, hearing someone say a word makes
 * it say that word back. A brain that never hears its own voice does not learn this.
 */
class SpeechImitationTest {
    private static final int SEEDS = 2;
    private static final int BABBLE_TICKS = 20_000;
    private static final int TRIALS = 64;

    @Test
    void babblingAndHearingItselfTeachesAVillagerToRepeatWords() {
        float hearsItself = 0f;
        float deaf = 0f;
        for (long seed = 1; seed <= SEEDS; seed++) {
            hearsItself += echoRate(seed, true) / SEEDS;
            deaf += echoRate(seed, false) / SEEDS;
        }
        System.out.printf("says a heard word back: hears itself %.2f of trials, never hears itself %.2f%n", hearsItself, deaf);
        assertTrue(hearsItself > 0.15f && hearsItself > deaf + 0.1f, hearsItself + " vs " + deaf);
    }

    private static SensoryFrame frame(Rng r) {
        SensoryFrame f = new SensoryFrame();
        for (int i = 0; i < f.vision.length; i++) {
            f.vision[i] = r.nextFloat() < 0.2f ? r.nextFloat() : 0f;
        }
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        return f;
    }

    /** Share of trials in which the brain says the word it just heard someone say. */
    private static float echoRate(long seed, boolean hearsItself) {
        Brain b = Brain.create(Genome.defaults(), seed, 0f);
        Rng r = new Rng(seed + 7);
        int lastSaid = -1;
        for (int t = 0; t < BABBLE_TICKS; t++) {
            SensoryFrame f = frame(r);
            if (hearsItself && lastSaid >= 0) {
                f.hear(lastSaid);
                f.set(SoundChannel.VILLAGER_VOICE, 1f);
            }
            lastSaid = b.step(f).say();
        }
        b.setBabbleScale(0.3f);
        int echoed = 0;
        for (int trial = 0; trial < TRIALS; trial++) {
            int word = trial % 8;
            boolean said = false;
            for (int t = 0; t < 40; t++) {
                SensoryFrame f = frame(r);
                if (t < 3) {
                    f.hear(word);
                    f.set(SoundChannel.PLAYER_VOICE, 1f);
                }
                said |= b.step(f).say() == word;
            }
            if (said) {
                echoed++;
            }
        }
        return echoed / (float) TRIALS;
    }
}
