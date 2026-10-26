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
}
