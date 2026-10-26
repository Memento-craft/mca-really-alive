package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Apprenticeship: a child that grows up watching someone dig, seeing the digger's hands work,
 * digs far more once it is on its own than a child that never watched. Nothing is copied into
 * it: watching only stirs its own motor pools, so it tries what it sees, and what works (the
 * block cracks, the dirt is picked up) is learned the way all its movements are.
 */
class ApprenticeshipTest {
    private static final int SEEDS = 3;
    private static final int WATCH_TICKS = 30_000;
    private static final int ALONE_TICKS = 16_000;
    private static final float ALONE_BABBLE = 0.3f;

    @Test
    void aChildThatWatchedADiggerDigsOnItsOwn() {
        BlockWorld.Course[] saved = BlockWorld.courses;
        BlockWorld.courses = new BlockWorld.Course[] {BlockWorld.Course.DIG_THROUGH, BlockWorld.Course.FLAT};
        try {
            int[] apprentice = new int[2], control = new int[2];
            for (long seed = 1; seed <= SEEDS; seed++) {
                int[] a = run(seed, true), c = run(seed, false);
                System.out.printf("seed %d: apprentice dug %d while watching, %d alone; control %d, %d%n",
                        seed, a[0], a[1], c[0], c[1]);
                for (int i = 0; i < 2; i++) {
                    apprentice[i] += a[i];
                    control[i] += c[i];
                }
            }
            System.out.printf("dug alone afterwards: apprentices %d, controls %d%n", apprentice[1], control[1]);
            assertTrue(apprentice[1] > 2 * control[1] + 3,
                    "apprentices dug " + apprentice[1] + " on their own, controls " + control[1]);
        } finally {
            BlockWorld.courses = saved;
        }
    }

    /** Dirt dug while watching (or not), then alone. */
    private static int[] run(long seed, boolean watching) {
        Brain b = Brain.create(Genome.defaults(), seed, 0.3f);
        BlockWorld w = new BlockWorld(seed + 7);
        SensoryFrame f = new SensoryFrame();
        b.setBabbleScale(1f);
        for (int t = 0; t < WATCH_TICKS; t++) {
            w.sense(f);
            if (watching) {
                // Someone beside it digs: their hand works the block most of the time.
                f.watched[MotorChannel.ATTACK.ordinal()] = (t / 40) % 4 == 3 ? 0f : 1f;
            }
            w.act(b.step(f));
        }
        int dugWatching = w.dug;
        b.setBabbleScale(ALONE_BABBLE);
        int pressed = 0;
        for (int t = 0; t < ALONE_TICKS; t++) {
            w.sense(f);
            MotorCommand c = b.step(f);
            pressed += c.attack() ? 1 : 0;
            w.act(c);
        }
        System.out.printf("  %s seed %d: attack held %.1f%% of the time alone%n", watching ? "apprentice" : "control",
                seed, 100f * pressed / ALONE_TICKS);
        return new int[] {dugWatching, w.dug - dugWatching};
    }
}
