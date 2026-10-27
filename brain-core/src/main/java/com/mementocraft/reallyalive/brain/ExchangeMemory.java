package com.mementocraft.reallyalive.brain;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Remembered exchanges, like the hippocampus keeps episodes: lines heard in conversations and the
 * answers that followed them. Each remembered answer is linked to the words of the line it
 * answered (all but the few heard everywhere), the more strongly the rarer the word. Hearing a
 * line brings up the answers whose cues it shares most, which the frontal part then weighs next to
 * the lines it makes up itself. Shared by all brains, like the English base, and frozen.
 */
final class ExchangeMemory {
    static final String RESOURCE = "/reallyalive/exchanges.bin.gz";

    /** How strongly a line must cue (root of summed squared cue weights) to recall at full strength. */
    static final float CLEAR_CUES = 8f;
    static final int SMALL_WORDS = 120;
    static final float SMALL_WORD_CUE = Float.parseFloat(System.getProperty("reallyalive.smallCue", "0.5"));
    /** How much of a match is measured against the best match for the same line, rather than outright. */
    static final float RELATIVE = Float.parseFloat(System.getProperty("reallyalive.relative", "0.5"));
    /**
     * How much an answer counts that answered the other kind of line: a telling ("the bridge fell
     * down.") is not answered as the question "what is a bridge?" was, nor a question as a telling.
     */
    static final float OTHER_KIND = Float.parseFloat(System.getProperty("reallyalive.otherKind", "0.75"));
    /** Set on an answer line's stored length when the line it answered was a question. */
    static final int ANSWERED_QUESTION = 0x80;

    private static ExchangeMemory shared;
    private static boolean tried;

    /** Remembered answers, as word ids of the English base. */
    private final char[][] answers;
    /** Whether each answer answered a question. */
    private final boolean[] answeredQuestion;
    /** How many cues each answer has, for weighing a match by how much of its line it covers. */
    private final float[] cueNorm;
    /** For each word, the answers it is a cue for, and how telling it is. */
    private final int[][] cuedBy;
    private final float[] weight;

    private ExchangeMemory(char[][] answers, boolean[] answeredQuestion, char[][] cues, int words) {
        this.answers = answers;
        this.answeredQuestion = answeredQuestion;
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
        // The small words heard in most lines ("what", "do", "some") cue only faintly: they tell a
        // question from an answer, the rarer words what it is about.
        Integer[] byCount = new Integer[words];
        Arrays.setAll(byCount, i -> i);
        Arrays.sort(byCount, (x, y) -> count[y] - count[x]);
        for (int i = 0; i < Math.min(SMALL_WORDS, words); i++) {
            weight[byCount[i]] *= SMALL_WORD_CUE;
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
        boolean[] answeredQuestion = new boolean[n];
        char[][] cues = new char[n][];
        for (int p = 0; p < n; p++) {
            int length = d.readUnsignedByte();
            answeredQuestion[p] = (length & ANSWERED_QUESTION) != 0;
            answers[p] = readLine(d, length & ~ANSWERED_QUESTION);
            cues[p] = readLine(d, d.readUnsignedByte());
        }
        return new ExchangeMemory(answers, answeredQuestion, cues, words);
    }

    private static char[] readLine(DataInputStream d, int length) throws IOException {
        char[] line = new char[length];
        for (int i = 0; i < line.length; i++) {
            line[i] = d.readChar();
        }
        return line;
    }

    /**
     * The answers best cued by a heard line (word ids), best first, with how well each matched
     * (0..1). The line before it cues too, by the given share, so the talk keeps to its topic.
     * Words the memory has no cue for count for nothing. The heard line asks something if it ends
     * with the word ask ("?"); answers to the other kind of line count less.
     */
    int[][] recall(int[] heard, int[] before, float beforeShare, int k, float[] match, int ask) {
        boolean asking = heard.length > 0 && heard[heard.length - 1] == ask;
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
            float v = score[p] / (cueNorm[p] * heardNorm) * (answeredQuestion[p] == asking ? 1f : OTHER_KIND);
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
                // A long line shares only a part of itself with any one remembered cue, so its best
                // match is low however well it was answered; half of how well an answer matched is
                // how close it comes to the best one.
                match[i] = (1f - RELATIVE + RELATIVE / topScore[0]) * topScore[i] * clarity;
            }
        }
        return out;
    }

    int size() {
        return answers.length;
    }

    /** How telling a word is as a cue (0 for the commonest words); words it never saw count as rare. */
    float weight(int word) {
        return word < weight.length ? weight[word] : (float) Math.log(answers.length);
    }

    /**
     * One villager's own remembered exchanges, from talk heard in its world: what it answered
     * itself (so it answers the same question the same way, and keeps to who it is), what others
     * answered its lines, and exchanges it overheard. Cued as the shared memory is, with the shared
     * memory's sense of how telling each word is. The oldest make way for new ones.
     */
    static final class Own {
        static final int CAPACITY = 256;
        /** Room for what it knows about itself, kept apart and not saved: the game tells it again. */
        static final int SELF_CAPACITY = 64;
        static final float SMALL_WORD_CUE = 0.5f;
        final int[][] cues = new int[CAPACITY + SELF_CAPACITY][];
        final int[][] answers = new int[CAPACITY + SELF_CAPACITY][];
        int count;
        private int next;
        private int selfCount;
        private final int[][] selfKeys = new int[SELF_CAPACITY][];

        /** Forgets what it knew about itself, before being told again. */
        void forgetSelf() {
            selfCount = 0;
        }

        /** Knows that asked this line, the answer about itself is that one; keys must be in what is asked. */
        void knowSelf(int[] line, int[] keys, int[] answer) {
            if (selfCount < SELF_CAPACITY && line.length > 0 && answer.length > 0) {
                cues[CAPACITY + selfCount] = cuesOf(line, null);
                selfKeys[selfCount] = Arrays.stream(keys).sorted().toArray();
                answers[CAPACITY + selfCount] = answer.clone();
                selfCount++;
            }
        }

        private int[] slots() {
            int[] slots = new int[count + selfCount];
            for (int i = 0; i < count; i++) {
                slots[i] = i;
            }
            for (int i = 0; i < selfCount; i++) {
                slots[count + i] = CAPACITY + i;
            }
            return slots;
        }

        /** Remembers that this line was answered so. */
        void remember(int[] line, int[] answer, ExchangeMemory shared) {
            int[] cue = cuesOf(line, shared);
            if (cue.length == 0 || answer.length == 0) {
                return;
            }
            cues[next] = cue;
            answers[next] = answer.clone();
            next = (next + 1) % CAPACITY;
            count = Math.min(CAPACITY, count + 1);
        }

        /**
         * Keeps a line it was told whole, to answer questions about it later, even after a save
         * ("the key is under the bed ." days ago answers "where is the key?"): it is kept as an
         * exchange the line answered itself, which recall leaves alone.
         */
        void rememberTold(int[] line) {
            int[] cue = cuesOf(line, null);
            if (cue.length == 0) {
                return;
            }
            for (int i = 0; i < count; i++) {
                if (told(i) && Arrays.equals(answers[i], line)) {
                    return;
                }
            }
            cues[next] = cue;
            answers[next] = line.clone();
            next = (next + 1) % CAPACITY;
            count = Math.min(CAPACITY, count + 1);
        }

        /** Whether a place holds a line it was told rather than an exchange. */
        private boolean told(int slot) {
            return slot < CAPACITY && Arrays.equals(cues[slot], cuesOf(answers[slot], null));
        }

        /** The lines it was told and kept, the latest first. */
        List<int[]> toldLines() {
            List<int[]> lines = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                int slot = Math.floorMod(next - i, CAPACITY);
                if (told(slot)) {
                    lines.add(answers[slot]);
                }
            }
            return lines;
        }

        /**
         * Every word of the line cues its own memories, small words faintly: a line it heard
         * itself is remembered whole, so "what do you do for work?" asked again is known again.
         */
        private static int[] cuesOf(int[] line, ExchangeMemory shared) {
            return Arrays.stream(line).filter(w -> w >= 0).distinct().sorted().toArray();
        }

        private static float cueWeight(int w, ExchangeMemory shared) {
            return Math.max(SMALL_WORD_CUE, shared.weight(w));
        }

        /**
         * Whether a heard line asks what this piece of self knowledge answers: it has one of the
         * question's key words ("name", "job") and asks it of the villager ("your name", not
         * "her name"; "how old are you", not "are you okay").
         */
        private boolean askedOfIt(int slot, int[] heard) {
            int[] keys = selfKeys[slot - CAPACITY];
            boolean key = false, asked = false;
            for (int h : heard) {
                key |= Arrays.binarySearch(keys, h) >= 0;
                asked |= Arrays.binarySearch(you, h) >= 0;
            }
            return key && asked;
        }

        /** The words that ask something of the villager itself ("you", "your"), sorted ids. */
        int[] you = new int[0];
        /** The question mark's word id. */
        int ask = -1;

        /** As {@link ExchangeMemory#recall}, from this villager's own memories. */
        int[][] recall(int[] heard, int[] before, float beforeShare, int k, float[] match, ExchangeMemory shared) {
            int[] slots = Arrays.stream(slots()).filter(slot -> !told(slot)).toArray();
            int n = slots.length;
            float[] score = new float[n];
            float heardNorm = 0f;
            int[] seen = new int[heard.length + before.length];
            int distinct = 0;
            for (int pass = 0; pass < 2; pass++) {
                int[] line = pass == 0 ? heard : before;
                float share = pass == 0 ? 1f : beforeShare;
                outer:
                for (int w : line) {
                    float wt = w < 0 || share <= 0f ? 0f : cueWeight(w, shared);
                    if (wt == 0f) {
                        continue;
                    }
                    for (int i = 0; i < distinct; i++) {
                        if (seen[i] == w) {
                            continue outer;
                        }
                    }
                    seen[distinct++] = w;
                    wt = share * wt * wt;
                    heardNorm += wt * share;
                    for (int p = 0; p < n; p++) {
                        if (Arrays.binarySearch(cues[slots[p]], w) >= 0) {
                            score[p] += wt;
                        }
                    }
                }
            }
            if (heardNorm == 0f) {
                return new int[0][];
            }
            boolean asking = false;
            for (int w : heard) {
                asking |= w == ask && ask >= 0;
            }
            // Its own memories are vivid: even a vague line brings them up at full strength.
            Integer[] order = new Integer[n];
            float[] v = new float[n];
            for (int p = 0; p < n; p++) {
                order[p] = p;
                float norm = 0f;
                for (int w : cues[slots[p]]) {
                    norm += cueWeight(w, shared) * cueWeight(w, shared);
                }
                float covered = score[p] / Math.max(norm, 1e-6f);
                float heardCovered = score[p] / heardNorm;
                // What it knows about itself is brought up by its question inside a longer line;
                // other memories need the lines to match as wholes.
                double lean = slots[p] >= CAPACITY ? 0.9 : 0.5;
                v[p] = (float) (Math.pow(covered, lean) * Math.pow(heardCovered, 1 - lean));
                if (slots[p] >= CAPACITY ? !askedOfIt(slots[p], heard)
                        : asking != (Arrays.binarySearch(cues[slots[p]], ask) >= 0)) {
                    // A question is not answered as the telling it asks about was: told "my
                    // favourite colour is green." and asked "what's my favourite colour?", the
                    // answer is not "why green?" again.
                    v[p] = 0f;
                }
            }
            Arrays.sort(order, (a, b) -> Float.compare(v[b], v[a]));
            int found = 0;
            while (found < Math.min(k, n) && v[order[found]] > 0f) {
                found++;
            }
            int[][] out = new int[found][];
            for (int i = 0; i < found; i++) {
                out[i] = answers[slots[order[i]]];
                if (match != null && i < match.length) {
                    match[i] = v[order[i]];
                }
            }
            return out;
        }
    }

    @Override
    public String toString() {
        return "ExchangeMemory[" + answers.length + " answers, " + Arrays.stream(cuedBy).filter(c -> c.length > 0).count() + " cue words]";
    }
}
