package com.mementocraft.reallyalive.brain;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Remembered exchanges, like the hippocampus keeps episodes: lines heard in conversations and the
 * answers that followed them. Each remembered answer is linked to the telling words of the line it
 * answered (not the small words heard everywhere), the more strongly the rarer the word. Hearing a
 * line brings up the answers whose cues it shares most, which the frontal part then weighs next to
 * the lines it makes up itself. Shared by all brains, like the English base, and frozen.
 */
final class ExchangeMemory {
    static final String RESOURCE = "/reallyalive/exchanges.bin.gz";

    /** How strongly a line must cue (root of summed squared cue weights) to recall at full strength. */
    static final float CLEAR_CUES = 8f;

    private static ExchangeMemory shared;
    private static boolean tried;

    /** Remembered answers, as word ids of the English base. */
    private final char[][] answers;
    /** How many cues each answer has, for weighing a match by how much of its line it covers. */
    private final float[] cueNorm;
    /** For each word, the answers it is a cue for, and how telling it is. */
    private final int[][] cuedBy;
    private final float[] weight;

    private ExchangeMemory(char[][] answers, char[][] cues, int words) {
        this.answers = answers;
        int[] count = new int[words];
        for (char[] c : cues) {
            for (char w : c) {
                count[w]++;
            }
        }
        cuedBy = new int[words][];
        weight = new float[words];
        for (int w = 0; w < words; w++) {
            cuedBy[w] = new int[count[w]];
            weight[w] = count[w] == 0 ? 0f : (float) Math.log((double) cues.length / count[w]);
        }
        int[] at = new int[words];
        cueNorm = new float[cues.length];
        for (int p = 0; p < cues.length; p++) {
            float sum = 0f;
            for (char w : cues[p]) {
                cuedBy[w][at[w]++] = p;
                sum += weight[w] * weight[w];
            }
            cueNorm[p] = (float) Math.sqrt(Math.max(sum, 1e-6f));
        }
    }

    /** The shipped memory, loaded once, or null if the mod was built without it. */
    static synchronized ExchangeMemory shared() {
        if (!tried) {
            tried = true;
            try (InputStream in = ExchangeMemory.class.getResourceAsStream(RESOURCE)) {
                if (in != null) {
                    shared = read(in);
                }
            } catch (IOException e) {
                shared = null;
            }
        }
        return shared;
    }

    static ExchangeMemory read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new BufferedInputStream(new GZIPInputStream(in)));
        int words = d.readInt();
        int n = d.readInt();
        char[][] answers = new char[n][];
        char[][] cues = new char[n][];
        for (int p = 0; p < n; p++) {
            answers[p] = readLine(d);
            cues[p] = readLine(d);
        }
        return new ExchangeMemory(answers, cues, words);
    }

    private static char[] readLine(DataInputStream d) throws IOException {
        char[] line = new char[d.readUnsignedByte()];
        for (int i = 0; i < line.length; i++) {
            line[i] = d.readChar();
        }
        return line;
    }

    /**
     * The answers best cued by a heard line (word ids), best first, with how well each matched
     * (0..1). The line before it cues too, by the given share, so the talk keeps to its topic.
     * Words the memory has no cue for count for nothing.
     */
    int[][] recall(int[] heard, int[] before, float beforeShare, int k, float[] match) {
        float[] score = new float[answers.length];
        int[] seen = new int[heard.length + before.length];
        int distinct = 0;
        float heardNorm = 0f;
        for (int pass = 0; pass < 2; pass++) {
            int[] line = pass == 0 ? heard : before;
            float share = pass == 0 ? 1f : beforeShare;
            outer:
            for (int w : line) {
                if (share <= 0f || w < 0 || w >= weight.length || weight[w] == 0f) {
                    continue;
                }
                for (int i = 0; i < distinct; i++) {
                    if (seen[i] == w) {
                        continue outer;
                    }
                }
                seen[distinct++] = w;
                float wt = share * weight[w] * weight[w];
                heardNorm += wt * share;
                for (int p : cuedBy[w]) {
                    score[p] += wt;
                }
            }
        }
        if (heardNorm == 0f) {
            return new int[0][];
        }
        heardNorm = (float) Math.sqrt(heardNorm);
        // A line with few telling words ("what do you see?") brings memories up only faintly.
        float clarity = Math.min(1f, heardNorm / CLEAR_CUES);
        int[] top = new int[k];
        float[] topScore = new float[k];
        int found = 0;
        for (int p = 0; p < score.length; p++) {
            if (score[p] == 0f) {
                continue;
            }
            float v = score[p] / (cueNorm[p] * heardNorm);
            if (found == k && v <= topScore[found - 1]) {
                continue;
            }
            int i = found < k ? found++ : found - 1;
            while (i > 0 && topScore[i - 1] < v) {
                top[i] = top[i - 1];
                topScore[i] = topScore[i - 1];
                i--;
            }
            top[i] = p;
            topScore[i] = v;
        }
        int[][] out = new int[found][];
        for (int i = 0; i < found; i++) {
            char[] a = answers[top[i]];
            out[i] = new int[a.length];
            for (int j = 0; j < a.length; j++) {
                out[i][j] = a[j];
            }
            if (match != null && i < match.length) {
                match[i] = topScore[i] * clarity;
            }
        }
        return out;
    }

    int size() {
        return answers.length;
    }

    @Override
    public String toString() {
        return "ExchangeMemory[" + answers.length + " answers, " + Arrays.stream(cuedBy).filter(c -> c.length > 0).count() + " cue words]";
    }
}
