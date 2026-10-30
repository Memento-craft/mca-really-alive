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

    /**
     * The colour of what each ray hits, red, green and blue, 0..1. The game fills it per ray; the
     * brain sees it at half the rays' resolution, as an eye sees colour less sharply than light.
     */
    public static final int COLOUR_CHANNELS = 3;
    public static final int COLOUR_SIZE = VISION_W * VISION_H * COLOUR_CHANNELS;
    /** Colour receptor cells across and down, each covering 2 by 2 rays. */
    public static final int COLOUR_CELLS_W = VISION_W / 2;
    public static final int COLOUR_CELLS_H = VISION_H / 2;
    public static final int COLOUR_RECEPTORS = COLOUR_CELLS_W * COLOUR_CELLS_H * COLOUR_CHANNELS;

    /** Feely receptors: body channels plus an efference copy of each motor pool's activity. */
    public static final int FEELY_RECEPTORS = FeelChannel.COUNT + MotorChannel.COUNT;

    /** Innate emotion receptors: pleasure, pain, curiosity. */
    public static final int EMOTION_RECEPTORS = 3;
    public static final int EMO_PLEASURE = 0;
    public static final int EMO_PAIN = 1;
    public static final int EMO_CURIOSITY = 2;

    /**
     * Features a heard or spoken word is made of (see {@link WordCode}): one hearing receptor group
     * and one speech pool per feature, a few of them active for each word, so the ears and the voice
     * handle any word without a fixed list.
     */
    public static final int WORD_FEATURES = 32;
    public static final int WORD_FEATURES_ACTIVE = 4;
    /** Neurons per speech pool, one pool per word feature. */
    public static final int SPEECH_POOL_SIZE = 6;

    /** Neurons per motor pool. Rates are read as the fraction of the pool firing. */
    public static final int MOTOR_POOL_SIZE = 12;

    public static int visionIndex(int x, int y, int channel) {
        return (y * VISION_W + x) * VISION_CHANNELS + channel;
    }

    public static int colourIndex(int x, int y, int channel) {
        return (y * VISION_W + x) * COLOUR_CHANNELS + channel;
    }

    private SenseLayout() {}
}
