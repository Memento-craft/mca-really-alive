package com.mementocraft.reallyalive.brain;

import java.util.Arrays;

/**
 * Inherited traits that shape a brain: how big it grows, how lobes are balanced and how fast it
 * learns. Passed from parents to children with crossover and a little mutation.
 */
public final class Genome {
    public static final int ADULT_NEURONS = 0;
    public static final int LEARNING_RATE = 1;
    public static final int CONNECTIVITY = 2;
    public static final int NOISE = 3;
    public static final int BABBLE = 4;
    public static final int DECAY_RESISTANCE = 5;
    private static final int LOBE_WEIGHTS = 6;
    public static final int SIZE = LOBE_WEIGHTS + LobeId.values().length;

    private static final float[] DEFAULTS = new float[SIZE];
    private static final float[] MIN = new float[SIZE];
    private static final float[] MAX = new float[SIZE];

    static {
        def(ADULT_NEURONS, 5000f, 2500f, 9000f);
        def(LEARNING_RATE, 0.02f, 0.002f, 0.1f);
        def(CONNECTIVITY, 1.0f, 0.5f, 2.0f);
        def(NOISE, 0.12f, 0.02f, 0.4f);
        def(BABBLE, 1.0f, 0.2f, 2.0f);
        def(DECAY_RESISTANCE, 0.9f, 0.5f, 0.99f);
        // Share of interior neurons each lobe gets as the brain grows.
        float[] lobe = new float[LobeId.values().length];
        lobe[LobeId.OLFACTORY.ordinal()] = 0.04f;
        lobe[LobeId.OCCIPITAL.ordinal()] = 0.16f;
        lobe[LobeId.FEELY.ordinal()] = 0.06f;
        // The concept system: frontal/temporal in the notebook, its frontal part a lobe of its own.
        lobe[LobeId.CONCEPT.ordinal()] = 0.19f;
        lobe[LobeId.FRONTAL.ordinal()] = 0.06f;
        lobe[LobeId.EMOTION.ordinal()] = 0.06f;
        lobe[LobeId.WERNICKE.ordinal()] = 0.06f;
        lobe[LobeId.MEMORY.ordinal()] = 0.14f;
        lobe[LobeId.CEREBELLUM.ordinal()] = 0.23f;
        lobe[LobeId.MOTOR.ordinal()] = 0f;
        for (LobeId l : LobeId.values()) {
            float v = lobe[l.ordinal()];
            def(LOBE_WEIGHTS + l.ordinal(), v, v * 0.5f, v * 2f);
        }
    }

    private static void def(int i, float d, float min, float max) {
        DEFAULTS[i] = d;
        MIN[i] = min;
        MAX[i] = max;
    }

    private final float[] genes;

    private Genome(float[] genes) {
        this.genes = genes;
        clampAll();
    }

    public static Genome defaults() {
        return new Genome(DEFAULTS.clone());
    }

    /** A villager with no known parents: defaults with natural variation. */
    public static Genome random(Rng rng) {
        Genome g = defaults();
        g.mutate(rng, 1f, 0.15f);
        return g;
    }

    public static Genome fromArray(float[] genes) {
        float[] copy = DEFAULTS.clone();
        System.arraycopy(genes, 0, copy, 0, Math.min(genes.length, SIZE));
        return new Genome(copy);
    }

    /** Each gene comes from one parent at random, then a small chance of mutation. */
    public static Genome inherit(Genome a, Genome b, Rng rng) {
        float[] child = new float[SIZE];
        for (int i = 0; i < SIZE; i++) {
            child[i] = rng.nextFloat() < 0.5f ? a.genes[i] : b.genes[i];
        }
        Genome g = new Genome(child);
        g.mutate(rng, 0.1f, 0.08f);
        return g;
    }

    private void mutate(Rng rng, float chance, float spread) {
        for (int i = 0; i < SIZE; i++) {
            if (rng.nextFloat() < chance) {
                genes[i] *= 1f + rng.gaussian() * spread;
            }
        }
        clampAll();
    }

    private void clampAll() {
        for (int i = 0; i < SIZE; i++) {
            genes[i] = Math.max(MIN[i], Math.min(MAX[i], genes[i]));
        }
    }

    public float get(int gene) {
        return genes[gene];
    }

    public float lobeWeight(LobeId lobe) {
        return genes[LOBE_WEIGHTS + lobe.ordinal()];
    }

    public float[] toArray() {
        return genes.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Genome g && Arrays.equals(genes, g.genes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(genes);
    }
}
