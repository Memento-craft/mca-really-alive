package com.mementocraft.reallyalive.brain;

import java.util.Arrays;

/**
 * How a thing looks to the brain up close: the block, creature or item the crosshair or the mouse
 * pointer rests on, and the thing in hand. Like {@link WordCode} it is a few active features out of
 * {@link SenseLayout#THING_FEATURES}, picked by hashing, so any block or item from any mod has its
 * own pattern without a list. Most features come from the thing's own name; one comes from its kind
 * (the game passes its class, such as a hoe or a crop), so things of one kind look alike, the way
 * every hoe looks like a hoe to a player.
 */
public final class ThingCode {
    private ThingCode() {}

    /** The features of a thing with this registry name and kind, sorted. */
    public static int[] features(String id, String kind) {
        int[] f = new int[SenseLayout.THING_FEATURES_ACTIVE];
        int n = 0;
        if (kind != null && !kind.isEmpty()) {
            f[n++] = feature(hash(kind), 0);
        }
        long h = hash(id);
        int salt = 1;
        while (n < f.length) {
            int c = feature(h, salt++);
            boolean seen = false;
            for (int k = 0; k < n; k++) {
                seen |= f[k] == c;
            }
            if (!seen) {
                f[n++] = c;
            }
        }
        Arrays.sort(f);
        return f;
    }

    /** Sets this thing's features in a sense array, or leaves it dark for nothing. */
    public static void fill(float[] into, String id, String kind) {
        Arrays.fill(into, 0f);
        if (id == null) {
            return;
        }
        for (int c : features(id, kind)) {
            into[c] = 1f;
        }
    }

    private static long hash(String s) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < s.length(); i++) {
            h = (h ^ s.charAt(i)) * 0x100000001B3L;
        }
        return h;
    }

    private static int feature(long h, int salt) {
        long z = h + salt * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (int) ((z >>> 1) % SenseLayout.THING_FEATURES);
    }
}
