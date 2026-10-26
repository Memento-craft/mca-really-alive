package com.mementocraft.reallyalive.brain;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Which everyday words go with which across a turn, and which go against: "passed away" is
 * answered with "sorry", hardly ever with "great"; "thank you" with "welcome". Among the commonest
 * words, how much more or less often a word of a line and a word of its answer came together than
 * chance (their pointwise mutual information, for and against). Topic links say what an answer is
 * about; these say whether its tone fits. Shared by all brains and frozen, like the English base.
 */
final class ToneLinks {
    static final String RESOURCE = "/reallyalive/tones.bin.gz";

    private static ToneLinks shared;
    private static boolean tried;

    /** Each word's row in the table, or -1 for words not among the commonest. */
    private final int[] row;
    private final int size;
    private final byte[] table;

    private ToneLinks(int[] row, int size, byte[] table) {
        this.row = row;
        this.size = size;
        this.table = table;
    }

    /** The shipped links, loaded once, or null if the mod was built without them. */
    static synchronized ToneLinks shared() {
        if (!tried) {
            tried = true;
            try (InputStream in = ToneLinks.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    shared = read(in);
                }
            } catch (IOException e) {
                shared = null;
            }
        }
        return shared;
    }

    static ToneLinks read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
        int words = d.readInt();
        int size = d.readInt();
        int[] row = new int[words];
        Arrays.fill(row, -1);
        for (int k = 0; k < size; k++) {
            row[d.readChar()] = k;
        }
        byte[] table = new byte[size * size];
        d.readFully(table);
        return new ToneLinks(row, size, table);
    }

    /**
     * How well the tone of a line goes with the heard line: for each of its words, how much it
     * goes with the heard words on average (for or against), summed. Words of the heard line said
     * back do not count.
     */
    float fit(int[] heard, int[] line, int length) {
        int[] rows = new int[heard.length];
        int n = 0;
        for (int q : heard) {
            if (q >= 0 && q < row.length && row[q] >= 0) {
                rows[n++] = row[q] * size;
            }
        }
        if (n == 0) {
            return 0f;
        }
        float sum = 0f;
        for (int j = 0; j < length; j++) {
            int a = line[j] >= 0 && line[j] < row.length ? row[line[j]] : -1;
            if (a < 0 || contains(heard, line[j])) {
                // A word said back ("what was that?" "it was...") goes with itself in any talk,
                // and says nothing about tone.
                continue;
            }
            int s = 0;
            for (int i = 0; i < n; i++) {
                s += table[rows[i] + a];
            }
            sum += s / (16f * n);
        }
        return sum;
    }

    private static boolean contains(int[] line, int word) {
        for (int w : line) {
            if (w == word) {
                return true;
            }
        }
        return false;
    }
}
