package com.mementocraft.reallyalive.brain;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Which words go with which across a turn: having heard "toys", an answer about "dolls" or
 * "legos" fits, one about the weather does not. Learned like the other Hebbian links, by how much
 * more often a telling word of a line and a telling word of its answer came together than chance
 * (their pointwise mutual information), keeping each word's strongest links. Where the language
 * area knows how a line goes on word by word, this knows what an answer is likely to be about.
 * Shared by all brains and frozen, like the English base.
 */
final class TopicLinks {
    static final String RESOURCE = "/reallyalive/topics.bin.gz";

    private static TopicLinks shared;
    private static boolean tried;

    /** For each heard word, the answer words it goes with (sorted) and how strongly. */
    private final char[][] to;
    private final float[][] strength;

    private TopicLinks(char[][] to, float[][] strength) {
        this.to = to;
        this.strength = strength;
    }

    /** The shipped links, loaded once, or null if the mod was built without them. */
    static synchronized TopicLinks shared() {
        if (!tried) {
            tried = true;
            try (InputStream in = TopicLinks.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    shared = read(in);
                }
            } catch (IOException e) {
                shared = null;
            }
        }
        return shared;
    }

    static TopicLinks read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
        int words = d.readInt();
        char[][] to = new char[words][];
        float[][] strength = new float[words][];
        for (int q = 0; q < words; q++) {
            int k = d.readUnsignedShort();
            long[] links = new long[k];
            for (int i = 0; i < k; i++) {
                links[i] = (long) d.readChar() << 8 | d.readUnsignedByte();
            }
            Arrays.sort(links);
            to[q] = new char[k];
            strength[q] = new float[k];
            for (int i = 0; i < k; i++) {
                to[q][i] = (char) (links[i] >>> 8);
                strength[q][i] = (links[i] & 0xff) / 32f;
            }
        }
        return new TopicLinks(to, strength);
    }

    /**
     * How much the words of a line are about what the heard line was about: for each of its words,
     * its strongest link from a heard word, summed.
     */
    float fit(int[] heard, int[] line, int length) {
        float sum = 0f;
        for (int j = 0; j < length; j++) {
            float best = 0f;
            for (int q : heard) {
                if (q < 0 || q >= to.length) {
                    continue;
                }
                int at = line[j] < 65536 ? Arrays.binarySearch(to[q], (char) line[j]) : -1;
                if (at >= 0) {
                    best = Math.max(best, strength[q][at]);
                }
            }
            sum += best;
        }
        return sum;
    }

    /** How strongly one word goes with another across a turn, either way round (0 if not linked). */
    float link(int a, int b) {
        return Math.max(one(a, b), one(b, a));
    }

    /**
     * How alike two words are in what they lead to: words that bring up the same answers ("wolf"
     * and "coyote" both bring up "scary", "bite", "forest") stand for like things. The cosine of
     * their links, 0..1.
     */
    float alike(int a, int b) {
        if (a < 0 || b < 0 || a >= to.length || b >= to.length || to[a].length == 0 || to[b].length == 0) {
            return 0f;
        }
        char[] x = to[a], y = to[b];
        float dot = 0f, xx = 0f, yy = 0f;
        for (float s : strength[a]) xx += s * s;
        for (float s : strength[b]) yy += s * s;
        for (int i = 0, j = 0; i < x.length && j < y.length; ) {
            if (x[i] == y[j]) {
                dot += strength[a][i++] * strength[b][j++];
            } else if (x[i] < y[j]) {
                i++;
            } else {
                j++;
            }
        }
        return dot / (float) Math.sqrt(xx * yy);
    }

    int words() {
        return to.length;
    }

    private float one(int q, int w) {
        if (q < 0 || q >= to.length || w < 0 || w >= 65536) {
            return 0f;
        }
        int at = Arrays.binarySearch(to[q], (char) w);
        return at >= 0 ? strength[q][at] : 0f;
    }
}
