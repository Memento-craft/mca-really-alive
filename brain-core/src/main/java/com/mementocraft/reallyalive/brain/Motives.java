package com.mementocraft.reallyalive.brain;

/**
 * Inborn motives, felt as small pleasures: for the hands, collecting and making things, and
 * changing the world by digging, placing and working blocks; for talk, being listened to when
 * telling someone something. Without them, digging (15 ticks of holding still before anything
 * good happens) was never learned. The game and the test world both feel these.
 */
public final class Motives {
    /** Picking up an item. */
    public static final float COLLECT = 0.3f;
    /** Each tick a block visibly cracks under the villager's digging. */
    public static final float DIG_PER_TICK = 0.05f;
    /** Placing a block. */
    public static final float PLACE = 0.3f;
    /** Making something: taking what it crafted, smelted or brewed out of the result slot. */
    public static final float MAKE = 0.5f;
    /**
     * Using a held thing turned a block into a different block: tilling dirt, planting a seed,
     * stripping a log. Opening a door or a chest changes nothing that way, and is not felt.
     */
    public static final float CHANGE = 0.3f;
    /** Someone took up what it told them: being listened to. */
    public static final float HEARD = 0.3f;
    /** And more when the one listening was a child: passing something on. */
    public static final float PASSED_ON = 0.2f;
    /** Finishing a line written down for whoever reads it later. */
    public static final float WROTE = 0.2f;

    private Motives() {}
}
