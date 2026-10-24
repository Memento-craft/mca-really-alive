package com.mementocraft.reallyalive.brain;

import java.util.Arrays;

/** Everything the villager senses in one tick. Filled on the server thread, read by the brain. */
public final class SensoryFrame {
    public final float[] vision = new float[SenseLayout.VISION_SIZE];
    public final float[] feel = new float[FeelChannel.COUNT];
    public final float[] smell = new float[OlfactoryChannel.COUNT];
    /** Sounds heard this tick, by kind. */
    public final float[] sound = new float[SoundChannel.COUNT];
    /** Words heard this tick (others' and its own), by lexicon slot. */
    public final float[] words = new float[SenseLayout.VOCABULARY];
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

    public void set(SoundChannel c, float value) {
        sound[c.ordinal()] = clamp01(value);
    }

    /** Hears a word by its lexicon slot; slots outside the vocabulary are not heard. */
    public void hear(int word) {
        if (word >= 0 && word < words.length) {
            words[word] = 1f;
        }
    }

    public void clear() {
        Arrays.fill(vision, 0f);
        Arrays.fill(feel, 0f);
        Arrays.fill(smell, 0f);
        Arrays.fill(sound, 0f);
        Arrays.fill(words, 0f);
        pleasure = 0f;
        pain = 0f;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
