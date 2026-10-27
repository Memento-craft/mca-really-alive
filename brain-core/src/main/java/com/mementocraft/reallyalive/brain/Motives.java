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
     * Seeing something new form in a result slot, as planks appear once a log is in the grid:
     * the sign that making is under way, before anything is taken.
     */
    public static final float FORM = 0.2f;
    /**
     * Opening a kind of screen not seen for a while: its novelty, the pull a child feels to look
     * into a box. Fades as that kind of screen grows familiar and comes back as it is forgotten,
     * so a villager keeps peeking into its inventory now and then instead of learning that
     * screens are never worth opening.
     */
    public static final float NEW_SCREEN = 0.3f;
    /**
     * A click on a screen that moved something: picking a stack up, putting it down, splitting it.
     * Felt the less the more it has lately been doing so, like a toy that wears off, so the hands
     * try things out on a screen without shuffling there forever.
     */
    public static final float MOVE_THINGS = 0.1f;
    /**
     * Using a held thing turned a block into a different block: tilling dirt, planting a seed,
     * stripping a log. Opening a door or a chest changes nothing that way, and is not felt.
     */
    public static final float CHANGE = 0.3f;
    /** Someone took up what it told them: being listened to. */
    public static final float HEARD = 0.3f;
    /** And more when the one listening was a child: passing something on. */
    public static final float PASSED_ON = 0.2f;
    /** Per tick, doing what it sees someone else do: the pull to imitate that apprenticeship rests on. */
    public static final float IMITATE = Float.parseFloat(System.getProperty("reallyalive.imitate", "0.2"));
    /** Getting what it set out to get; a step that serves it is felt at half this. */
    public static final float GOAL = Float.parseFloat(System.getProperty("reallyalive.goal", "1.0"));
    /** Finishing a line written down for whoever reads it later. */
    public static final float WROTE = 0.2f;
    /**
     * Setting a machine going: a block it placed or worked with its hands, still and dead when it
     * did so, starts turning soon after, as a shaft does once it joins a turning wheel. The joy of
     * seeing something it built come alive, felt the less the more it has lately been doing so.
     */
    public static final float SET_GOING = 1.0f;
    /**
     * Putting something where a remembered picture of a recipe showed it: the grid looks a little
     * more like the page in the book. Felt once for each place filled on a screen, so a recipe
     * needing two things in two places is not all or nothing.
     */
    public static final float MATCH_PICTURE = 0.15f;

    private Motives() {}
}
