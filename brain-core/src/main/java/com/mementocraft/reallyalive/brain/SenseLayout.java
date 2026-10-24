package com.mementocraft.reallyalive.brain;

/** Sizes of the sensory input arrays the game side fills each tick. */
public final class SenseLayout {
    /** Rays across, inside a player's horizontal field of view. */
    public static final int VISION_W = 16;
    /** Rays down, inside a player's vertical field of view. */
    public static final int VISION_H = 10;
    /** Per ray: nearness, solid surface, hazard, creature, climbable (ladders, vines). */
    public static final int VISION_CHANNELS = 5;
    public static final int VISION_SIZE = VISION_W * VISION_H * VISION_CHANNELS;

    public static final int CH_NEAR = 0;
    public static final int CH_SOLID = 1;
    public static final int CH_HAZARD = 2;
    public static final int CH_CREATURE = 3;
    public static final int CH_CLIMBABLE = 4;

    /** Feely receptors: body channels plus an efference copy of each motor pool's activity. */
    public static final int FEELY_RECEPTORS = FeelChannel.COUNT + MotorChannel.COUNT;

    /** Innate emotion receptors: pleasure, pain, curiosity. */
    public static final int EMOTION_RECEPTORS = 3;
    public static final int EMO_PLEASURE = 0;
    public static final int EMO_PAIN = 1;
    public static final int EMO_CURIOSITY = 2;

    /**
     * Words a villager can hear and say. The game keeps a lexicon per world that gives each new
     * word it hears the next free slot, so slot k is always the same word in that world.
     */
    public static final int VOCABULARY = 32;
    /** Neurons per speech pool, one pool per word. */
    public static final int SPEECH_POOL_SIZE = 6;

    /** Neurons per motor pool. Rates are read as the fraction of the pool firing. */
    public static final int MOTOR_POOL_SIZE = 12;

    public static int visionIndex(int x, int y, int channel) {
        return (y * VISION_W + x) * VISION_CHANNELS + channel;
    }

    private SenseLayout() {}
}
