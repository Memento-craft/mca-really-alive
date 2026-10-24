package com.mementocraft.reallyalive.brain;

/**
 * The lobes from Joshua's notebook (page 111). Sensory lobes hold fixed receptor neurons plus
 * growable interior neurons; MOTOR holds only the fixed output pools the cerebellum drives.
 */
public enum LobeId {
    OLFACTORY(true, 0.80f, 1f),
    OCCIPITAL(true, 0.80f, 1f),
    FEELY(true, 0.80f, 1f),
    CONCEPT(false, 0.85f, 1f),
    EMOTION(false, 0.85f, 1f),
    WERNICKE(false, 0.85f, 1f),
    // Slow leak so activity lingers, and fast learning so a moment is stored as it happens: the
    // memory lobe holds recent state and binds it into memories.
    MEMORY(false, 0.95f, 4f),
    CEREBELLUM(false, 0.75f, 1f),
    MOTOR(false, 0.70f, 1f);


    public final boolean sensory;
    /** Fraction of membrane potential kept each tick. */
    public final float leak;
    /** How fast synapses into or out of this lobe learn, relative to the rest of the brain. */
    public final float plasticity;

    LobeId(boolean sensory, float leak, float plasticity) {
        this.sensory = sensory;
        this.leak = leak;
        this.plasticity = plasticity;
    }

    /** Lobes that gain neurons as the villager grows. */
    public boolean growable() {
        return this != MOTOR;
    }

    private static final LobeId[] VALUES = values();

    public static LobeId of(int ordinal) {
        return VALUES[ordinal];
    }
}
