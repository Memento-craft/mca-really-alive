package com.mementocraft.reallyalive.brain;

/**
 * Inborn motives for using the hands, felt as small pleasures: collecting and making things, and
 * changing the world by digging, placing and working blocks, and standing up to threats. Without them, digging (15 ticks of holding still before
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

    /**
     * Landing a blow on a threat (a monster, or someone who hurt it), times how hard the blow
     * landed against a fully recharged one: a hasty swing that does a fifth of the damage feels a
     * fifth as good, so waiting for the attack to recharge is worth learning. Hitting someone who
     * is no threat is not felt at all.
     */
    public static final float HIT = 0.4f;
    /** A threat it hurt lately dies: the fight is won. */
    public static final float DEFEAT = 1.0f;
    /**
     * Per block it moves toward a threat it can see: the pull to close in on a foe, as a child
     * squares up to a bully. Only its own steps count, not the foe walking up to it, so standing
     * still is not rewarded. Without it villagers never walked up to a skeleton shooting at them
     * and lost every bout.
     */
    public static final float CLOSE_IN = 0.3f;
    /** A blow caught on a raised shield: the relief of a parry. */
    public static final float BLOCKED = 0.3f;

    private Motives() {}
}
