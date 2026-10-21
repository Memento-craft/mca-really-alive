package com.mementocraft.reallyalive.brain;

import java.util.Arrays;

/** Everything the villager senses in one tick. Filled on the server thread, read by the brain. */
public final class SensoryFrame {
    public final float[] vision = new float[SenseLayout.VISION_SIZE];
    public final float[] feel = new float[FeelChannel.COUNT];
    public final float[] smell = new float[OlfactoryChannel.COUNT];
    /** 0..1, from things that feel good (reaching a goal, eating). */
    public float pleasure;
    /** 0..1, from damage. */
    public float pain;

    public void set(FeelChannel c, float value) {
        feel[c.ordinal()] = clamp01(value);
    }

    public void set(OlfactoryChannel c, float value) {
        smell[c.ordinal()] = clamp01(value);
    }

    public void clear() {
        Arrays.fill(vision, 0f);
        Arrays.fill(feel, 0f);
        Arrays.fill(smell, 0f);
        pleasure = 0f;
        pain = 0f;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
