package com.mementocraft.reallyalive.brain;

/**
 * Inborn motives for using the hands, felt as small pleasures: collecting and making things, and
 * changing the world by digging, placing and working blocks. Without them, digging (15 ticks of holding still before
 * anything good happens) was never learned. The game and the test world both feel these.
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
     * Seeing something new form in a result slot, as planks appear once a log is in the grid:
     * the sign that making is under way, before anything is taken.
     */
    public static final float FORM = 0.2f;
    /**
     * Using a held thing turned a block into a different block: tilling dirt, planting a seed,
     * stripping a log. Opening a door or a chest changes nothing that way, and is not felt.
     */
    public static final float CHANGE = 0.3f;

    private Motives() {}
}
