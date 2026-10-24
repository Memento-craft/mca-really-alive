package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The memory layout at work: a villager that keeps smelling the same person at the same place
 * comes to bring that place to mind (in the Concept lobe's places area) from the smell alone.
 */
class MemoryRecallTest {
    private static final int SEEDS = 4;
    private static final int TRAIN_TICKS = 3000;

    @Test
    void aPersonBringsToMindThePlaceTheyAreAlwaysAt() {
        float pairedWithLeft = 0f;
        float pairedWithRight = 0f;
        for (long seed = 1; seed <= SEEDS; seed++) {
            pairedWithLeft += recallBias(seed, 0) / SEEDS;
            pairedWithRight += recallBias(seed, 1) / SEEDS;
        }
        System.out.printf("smell alone recalls the left place by %.3f when paired with it, %.3f when paired with the right%n",
                pairedWithLeft, pairedWithRight);
        assertTrue(pairedWithLeft - pairedWithRight > 0.05f, pairedWithLeft + " vs " + pairedWithRight);
    }

    /** A villager's smell, and a place: a wall filling the left (0) or right (1) half of view. */
    private static void scene(SensoryFrame f, boolean person, int place) {
        f.clear();
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        if (person) {
            f.set(OlfactoryChannel.VILLAGER, 1f);
        }
        if (place < 0) {
            return;
        }
        for (int y = 0; y < SenseLayout.VISION_H; y++) {
            for (int x = 0; x < SenseLayout.VISION_W; x++) {
                if ((x < SenseLayout.VISION_W / 2) == (place == 0)) {
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = 1f;
                    f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.8f;
                }
            }
        }
    }

    /** Firing of each Concept neuron in the places area while a scene is shown. */
    private static float[] placesResponse(Brain b, boolean person, int place) {
        SensoryFrame f = new SensoryFrame();
        IntList concept = b.members[LobeId.CONCEPT.ordinal()];
        float[] rate = new float[concept.size];
        for (int t = 0; t < 30; t++) {
            scene(f, false, -1);
            b.step(f);
        }
        for (int t = 0; t < 100; t++) {
            scene(f, person, place);
            b.step(f);
            for (int k = 0; k < concept.size; k++) {
                int i = concept.get(k);
                if (b.fired[i] && Wiring.Band.PLACES.y0 <= b.posY[i]) {
                    rate[k] += 0.01f;
                }
            }
        }
        return rate;
    }

    /** How much more the smell alone looks like the left place than the right one. */
    private static float recallBias(long seed, int pairedPlace) {
        Brain b = Brain.create(Genome.defaults(), seed, 1f);
        b.setBabbleScale(0f);
        SensoryFrame f = new SensoryFrame();
        for (int t = 0; t < TRAIN_TICKS; t++) {
            int phase = t / 50 % 3;
            if (phase == 0) {
                scene(f, true, pairedPlace);
            } else if (phase == 1) {
                scene(f, false, 1 - pairedPlace);
            } else {
                scene(f, false, -1);
            }
            b.step(f);
        }
        float[] left = placesResponse(b, false, 0);
        float[] right = placesResponse(b, false, 1);
        float[] smell = placesResponse(b, true, -1);
        return correlation(smell, left) - correlation(smell, right);
    }

    private static float correlation(float[] a, float[] b) {
        double ma = 0, mb = 0;
        for (int i = 0; i < a.length; i++) {
            ma += a[i];
            mb += b[i];
        }
        ma /= a.length;
        mb /= b.length;
        double ab = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) {
            ab += (a[i] - ma) * (b[i] - mb);
            aa += (a[i] - ma) * (a[i] - ma);
            bb += (b[i] - mb) * (b[i] - mb);
        }
        return (float) (ab / Math.sqrt(aa * bb + 1e-12));
    }
}
