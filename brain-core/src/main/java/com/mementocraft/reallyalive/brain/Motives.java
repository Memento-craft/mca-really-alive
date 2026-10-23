package com.mementocraft.reallyalive.brain;

/**
 * Inborn motives for using the hands, felt as small pleasures: collecting things, and changing the
 * world by digging and placing blocks. Without them, digging (15 ticks of holding still before
 * anything good happens) was never learned. The game and the test world both feel these.
 */
public final class Motives {
    /** Picking up an item. */
    public static final float COLLECT = 0.3f;
    /** Each tick a block visibly cracks under the villager's digging. */
    public static final float DIG_PER_TICK = 0.05f;
    /** Placing a block. */
    public static final float PLACE = 0.3f;

    private Motives() {}
}
