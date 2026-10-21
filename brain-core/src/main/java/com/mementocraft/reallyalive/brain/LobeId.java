package com.mementocraft.reallyalive.brain;

/**
 * The lobes from Joshua's notebook (page 111). Sensory lobes hold fixed receptor neurons plus
 * growable interior neurons; MOTOR holds only the fixed output pools the cerebellum drives.
 */
public enum LobeId {
    OLFACTORY(true, 0.80f),
    OCCIPITAL(true, 0.80f),
    FEELY(true, 0.80f),
    CONCEPT(false, 0.85f),
    EMOTION(false, 0.85f),
    WERNICKE(false, 0.85f),
    // Slow leak so activity lingers: the memory lobe holds recent state.
    MEMORY(false, 0.95f),
    DECISION(false, 0.85f),
    CEREBELLUM(false, 0.75f),
    MOTOR(false, 0.70f);

    public final boolean sensory;
    /** Fraction of membrane potential kept each tick. */
    public final float leak;

    LobeId(boolean sensory, float leak) {
        this.sensory = sensory;
        this.leak = leak;
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
