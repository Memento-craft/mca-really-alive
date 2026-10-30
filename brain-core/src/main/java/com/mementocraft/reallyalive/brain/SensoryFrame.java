package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Everything the villager senses in one tick. Filled on the server thread, read by the brain. */
public final class SensoryFrame {
    public final float[] vision = new float[SenseLayout.VISION_SIZE];
    /** Colour of what each ray hits, see {@link SenseLayout#colourIndex}. */
    public final float[] colour = new float[SenseLayout.COLOUR_SIZE];
    public final float[] feel = new float[FeelChannel.COUNT];
    public final float[] smell = new float[OlfactoryChannel.COUNT];
    /** Sounds heard this tick, by kind. */
    public final float[] sound = new float[SoundChannel.COUNT];
    /** Word features heard this tick (others' words and its own), see {@link WordCode}. */
    public final float[] words = new float[SenseLayout.WORD_FEATURES];
    /** The words heard this tick, in order, and whether each was the villager's own. */
    public final List<String> heardWords = new ArrayList<>();
    public final List<Boolean> heardOwn = new ArrayList<>();
    /**
     * Things named for the villager this tick: someone points at what it is seeing, smelling or
     * feeling, or at what it is doing, and says the word, as parents do with a child. Named words
     * only teach the links between words and concepts; they are not heard as part of a line.
     */
    public final List<String> named = new ArrayList<>();
    /** 0..1, how near someone to talk to is (a player or villager within a few blocks). */
    public float company;
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

    /**
     * Hears a word, lower case, or {@link WordCode#TURN} when a speaker finishes a line. Own is set
     * for the villager's own voice, which it hears like any other.
     */
    public void hear(String word, boolean own) {
        heardWords.add(word);
        heardOwn.add(own);
        if (!word.equals(WordCode.TURN)) {
            for (int f : WordCode.features(word)) {
                words[f] = 1f;
            }
        }
    }

    /** Someone names what the villager is seeing, smelling, feeling or doing right now. */
    public void name(String word) {
        named.add(word);
    }

    public void clear() {
        Arrays.fill(vision, 0f);
        Arrays.fill(colour, 0f);
        Arrays.fill(feel, 0f);
        Arrays.fill(smell, 0f);
        Arrays.fill(sound, 0f);
        Arrays.fill(words, 0f);
        heardWords.clear();
        heardOwn.clear();
        named.clear();
        company = 0f;
        pleasure = 0f;
        pain = 0f;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
}
