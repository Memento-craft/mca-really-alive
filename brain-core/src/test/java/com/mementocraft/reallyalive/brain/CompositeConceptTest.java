package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Page 110 of the notebook: concepts are built from other concepts, so feet, dirt and a bad smell
 * met together become "dirty stinky feet". Here, after the three keep coming together, more Concept
 * neurons answer to the whole than to any one part, compared with a brain that met them apart.
 */
class CompositeConceptTest {
    private static final int SEEDS = 4;

    @Test
    void thingsMetTogetherBecomeOneConcept() {
        int together = 0;
        int apart = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            together += composites(seed, true);
            apart += composites(seed, false);
        }
        System.out.printf("composite concept neurons: met together %d, met apart %d%n", together, apart);
        assertTrue(together > 2 * apart, together + " vs " + apart);
    }

    /** Feet (touch on the body), dirt (a patch seen low ahead) and a bad smell (smoke). */
    private static void cue(SensoryFrame f, boolean feet, boolean dirt, boolean smell) {
        f.clear();
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        if (feet) {
            f.set(FeelChannel.TOUCH_FRONT, 1f);
            f.set(FeelChannel.TOUCH_LEFT, 1f);
        }
        if (dirt) {
            for (int y = 6; y < SenseLayout.VISION_H; y++) {
                for (int x = 5; x < 11; x++) {
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = 1f;
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.9f;
                }
            }
        }
        if (smell) {
            f.set(OlfactoryChannel.SMOKE, 1f);
        }
    }

    private static float[] response(Brain b, boolean feet, boolean dirt, boolean smell) {
        SensoryFrame f = new SensoryFrame();
        IntList concept = b.members[LobeId.CONCEPT.ordinal()];
        float[] rate = new float[concept.size];
        for (int t = 0; t < 30; t++) {
            cue(f, false, false, false);
            b.step(f);
        }
        for (int t = 0; t < 100; t++) {
            cue(f, feet, dirt, smell);
            b.step(f);
            for (int k = 0; k < concept.size; k++) {
                if (b.fired[concept.get(k)]) {
                    rate[k] += 0.01f;
                }
            }
        }
        return rate;
    }

    /** Concept neurons that fire for the whole at least twice as much as for any part or nothing. */
    private static int composites(long seed, boolean together) {
        Brain b = Brain.create(Genome.defaults(), seed, 1f);
        b.setBabbleScale(0f);
        SensoryFrame f = new SensoryFrame();
        for (int t = 0; t < 4000; t++) {
            int phase = t / 40 % 4;
            if (together) {
                cue(f, phase == 0, phase == 0, phase == 0);
            } else {
                cue(f, phase == 1, phase == 2, phase == 3);
            }
            b.step(f);
        }
        float[] whole = response(b, true, true, true);
        float[] feet = response(b, true, false, false);
        float[] dirt = response(b, false, true, false);
        float[] smell = response(b, false, false, true);
        float[] nothing = response(b, false, false, false);
        int count = 0;
        for (int k = 0; k < whole.length; k++) {
            float part = Math.max(feet[k], Math.max(dirt[k], smell[k]));
            if (whole[k] > 0.08f && whole[k] > 2f * part && whole[k] > 2f * nothing[k]) {
                count++;
            }
        }
        return count;
    }
}
