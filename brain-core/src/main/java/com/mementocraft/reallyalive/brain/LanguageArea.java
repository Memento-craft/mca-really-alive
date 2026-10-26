package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wernicke's word-sequence area: it listens to words one at a time and learns, by Hebbian
 * co-activity alone, which word tends to come next.
 *
 * <p>Two kinds of cell take part. Context cells each stand for a piece of what was just heard: the
 * last word, the last two, three or four words in order, and each word of the other speaker's
 * last utterance (held in short-term memory), on its own at the start of a reply and paired with
 * the word just said after that. Each piece
 * lights up two context cells picked by hashing it, so the area needs no list of all possible
 * contexts. Word cells stand for single words. When a word is heard, every synapse from a context
 * cell that was active a moment before onto that word's cell grows by one. A context cell only has
 * room for a few dozen synapses; when it is full, a new word takes the place of its weakest one,
 * which is the competition that keeps the common continuations and lets rare ones go. Expecting the
 * next word is the summed, normalised drive from the active context cells onto each word cell.
 * This is the same next-word game a large language model is trained on, but learned with local
 * counts instead of back-propagation.
 *
 * <p>A brain's area can sit on top of a shared, frozen base (the English every villager grows up
 * with), adding what it hears in its own world in a small sheet of its own.
 */
public final class LanguageArea {
    static final int MAX_SYNAPSES = Integer.getInteger("reallyalive.maxSynapses", 48);
    static final int CELLS_PER_FEATURE = 2;
    static final int HISTORY = 5;
    static final int MEMORY_WORDS = 16;
    /**
     * How much a brain trusts what it heard in its own world next to the shared base: a little,
     * since one line heard once would otherwise outweigh millions of words.
     */
    static final float OWN_WEIGHT = 0.5f;

    /** Feature kinds, with how strongly each one's guess counts. */
    static final int F_UNI = 0, F_BI = 1, F_TRI = 2, F_QUAD = 3, F_HEARD_START = 4, F_HEARD_BI = 5, F_QUINT = 6,
            F_EARLIER_BI = 7;
    static float[] featureWeight = {0.05f, 1f, 4f, 16f, 1f, 1f, 16f, 0.5f};

    /** Features that come from another line than the one being said (the line answered, or the one before). */
    static boolean fromHeard(int kind) {
        return kind == F_HEARD_START || kind == F_HEARD_BI || kind == F_EARLIER_BI;
    }

    /**
     * A sheet of context cells and their synapses onto word cells. A frozen sheet (the shared base)
     * keeps every synapse in a few flat arrays, which takes a fraction of the memory and never
     * changes.
     */
    public static final class Sheet {
        final int cellMask;
        final int[] total;
        // Growing sheet.
        int[][] synWord;
        int[][] synCount;
        int[] synSize;
        // Frozen sheet: synapses of cell c are at start[c] .. start[c+1].
        int[] start;
        int[] flatWord;
        char[] flatCount;

        /** A growing sheet of 2^cellBits context cells. */
        public Sheet(int cellBits) {
            int cells = 1 << cellBits;
            cellMask = cells - 1;
            synWord = new int[cells][];
            synCount = new int[cells][];
            synSize = new int[cells];
            total = new int[cells];
        }

        private Sheet(int cellBits, boolean frozen) {
            cellMask = (1 << cellBits) - 1;
            total = new int[cellMask + 1];
            start = new int[cellMask + 2];
        }

        int size(int cell) {
            return start != null ? start[cell + 1] - start[cell] : synSize[cell];
        }

        int wordAt(int cell, int k) {
            return start != null ? flatWord[start[cell] + k] : synWord[cell][k];
        }

        int countAt(int cell, int k) {
            return start != null ? flatCount[start[cell] + k] : synCount[cell][k];
        }

        /**
         * A frozen copy holding only synapses grown at least minCount times: one-off pairings are
         * mostly noise, and dropping them roughly halves the size.
         */
        public Sheet frozen(int minCount) {
            return frozen(minCount, MAX_SYNAPSES);
        }

        /** As {@link #frozen(int)}, keeping at most the keep strongest synapses of each cell. */
        public Sheet frozen(int minCount, int keep) {
            Sheet f = new Sheet(Integer.numberOfTrailingZeros(cellMask + 1), true);
            IntList words = new IntList(1 << 20);
            IntList counts = new IntList(1 << 20);
            int[] order = new int[MAX_SYNAPSES];
            for (int cell = 0; cell <= cellMask; cell++) {
                f.start[cell] = words.size;
                f.total[cell] = total[cell];
                int n = 0;
                for (int k = 0; k < size(cell); k++) {
                    if (countAt(cell, k) < minCount) {
                        continue;
                    }
                    // Strongest first.
                    int p = n++;
                    while (p > 0 && countAt(cell, order[p - 1]) < countAt(cell, k)) {
                        order[p] = order[p - 1];
                        p--;
                    }
                    order[p] = k;
                }
                n = Math.min(n, keep);
                for (int i = 0; i < n; i++) {
                    words.add(wordAt(cell, order[i]));
                    counts.add(Math.min(Character.MAX_VALUE, countAt(cell, order[i])));
                }
            }
            f.start[cellMask + 1] = words.size;
            f.flatWord = Arrays.copyOf(words.data, words.size);
            f.flatCount = new char[counts.size];
            for (int i = 0; i < counts.size; i++) {
                f.flatCount[i] = (char) counts.data[i];
            }
            return f;
        }

        void strengthen(int cell, int word) {
            total[cell]++;
            int n = synSize[cell];
            int[] words = synWord[cell];
            if (words == null) {
                words = synWord[cell] = new int[4];
                synCount[cell] = new int[4];
            }
            int[] counts = synCount[cell];
            int weakest = -1;
            for (int k = 0; k < n; k++) {
                if (words[k] == word) {
                    counts[k]++;
                    return;
                }
                if (weakest < 0 || counts[k] < counts[weakest]) {
                    weakest = k;
                }
            }
            if (n < MAX_SYNAPSES) {
                if (n == words.length) {
                    words = synWord[cell] = Arrays.copyOf(words, Math.min(MAX_SYNAPSES, n * 2));
                    counts = synCount[cell] = Arrays.copyOf(counts, words.length);
                }
                words[n] = word;
                counts[n] = 1;
                synSize[cell] = n + 1;
            } else {
                // Full: the newcomer takes the weakest synapse's place and inherits its strength,
                // so a word that keeps coming back can climb, and one heard once soon makes way.
                words[weakest] = word;
                counts[weakest]++;
            }
        }

        int countOf(int cell, int word) {
            int n = size(cell);
            for (int k = 0; k < n; k++) {
                if (wordAt(cell, k) == word) {
                    return countAt(cell, k);
                }
            }
            return 0;
        }

        /**
         * Adds this sheet's drive from one piece of context (cells a and b) onto the word cells. A
         * synapse only counts as much as both cells agree on, so a cell shared with some unrelated
         * context by chance adds nothing.
         */
        void drive(long hashA, long hashB, float weight, float[] score) {
            int a = (int) hashA & cellMask;
            int b = (int) hashB & cellMask;
            int n = size(a);
            if (n == 0 || size(b) == 0) {
                return;
            }
            float t = Math.min(total[a], total[b]);
            // A context seen only once or twice is a weak guide.
            float w = weight * t / (t + 2f) / t;
            for (int k = 0; k < n; k++) {
                int word = wordAt(a, k);
                int c = Math.min(countAt(a, k), countOf(b, word));
                if (c > 0 && word < score.length) {
                    score[word] += w * c;
                }
            }
        }

        /** Cells in use and synapses in total. */
        public long[] usage() {
            long cells = 0, syn = 0;
            for (int cell = 0; cell <= cellMask; cell++) {
                int n = size(cell);
                cells += n > 0 ? 1 : 0;
                syn += n;
            }
            return new long[] {cells, syn};
        }

        public void write(DataOutput out) throws IOException {
            out.writeInt(Integer.numberOfTrailingZeros(cellMask + 1));
            out.writeBoolean(start != null);
            out.writeInt((int) usage()[0]);
            int last = 0;
            for (int cell = 0; cell <= cellMask; cell++) {
                int n = size(cell);
                if (n == 0) {
                    continue;
                }
                writeVarInt(out, cell - last);
                last = cell;
                writeVarInt(out, total[cell]);
                writeVarInt(out, n);
                for (int k = 0; k < n; k++) {
                    writeVarInt(out, wordAt(cell, k));
                    writeVarInt(out, countAt(cell, k));
                }
            }
        }

        public static Sheet read(DataInput in) throws IOException {
            int bits = in.readInt();
            boolean frozen = in.readBoolean();
            int used = in.readInt();
            Sheet s = frozen ? new Sheet(bits, true) : new Sheet(bits);
            IntList words = new IntList(frozen ? 1 << 20 : 4);
            IntList counts = new IntList(frozen ? 1 << 20 : 4);
            int cell = 0;
            int next = 0;
            for (int i = 0; i < used; i++) {
                cell += readVarInt(in);
                s.total[cell] = readVarInt(in);
                int n = readVarInt(in);
                if (frozen) {
                    for (int c = next; c <= cell; c++) {
                        s.start[c] = words.size;
                    }
                    next = cell + 1;
                    for (int k = 0; k < n; k++) {
                        words.add(readVarInt(in));
                        counts.add(readVarInt(in));
                    }
                } else {
                    s.synSize[cell] = n;
                    s.synWord[cell] = new int[Math.max(n, 4)];
                    s.synCount[cell] = new int[Math.max(n, 4)];
                    for (int k = 0; k < n; k++) {
                        s.synWord[cell][k] = readVarInt(in);
                        s.synCount[cell][k] = readVarInt(in);
                    }
                }
            }
            if (frozen) {
                for (int c = next; c <= s.cellMask + 1; c++) {
                    s.start[c] = words.size;
                }
                s.flatWord = Arrays.copyOf(words.data, words.size);
                s.flatCount = new char[counts.size];
                for (int i = 0; i < counts.size; i++) {
                    s.flatCount[i] = (char) counts.data[i];
                }
            }
            return s;
        }
    }

    final Sheet own;
    final Sheet base;
    public final Vocabulary vocabulary;

    private final int[] history = new int[HISTORY];
    private int historyLength;
    private final List<Integer> utterance = new ArrayList<>();
    private int[] heard = new int[0];
    /** The line heard before that one. */
    private int[] earlier = new int[0];
    /** The remembered line was this brain's own. */
    private boolean heardOwn;
    private byte[] activeKinds = new byte[256];
    private long[] activeHashes = new long[256];
    private float[] activeWeights = new float[256];
    private int activeCount;
    private float[] score = new float[1024];
    private float[] peek = new float[1024];

    /** An area that learns everything itself, in a sheet of 2^cellBits context cells. */
    public LanguageArea(int cellBits) {
        this(new Sheet(cellBits), null, new Vocabulary());
    }

    /**
     * An area on top of a frozen base sheet (or none), whose word cells are numbered as in the
     * vocabulary. Its own sheet learns from what this brain hears.
     */
    public LanguageArea(Sheet own, Sheet base, Vocabulary vocabulary) {
        this.own = own;
        this.base = base;
        this.vocabulary = vocabulary;
    }

    /** Forgets what was just said, as when a conversation ends. Learned synapses stay. */
    public void reset() {
        historyLength = 0;
        utterance.clear();
        heard = new int[0];
        earlier = new int[0];
        heardOwn = false;
        activeCount = 0;
    }

    /** Where the area is in a conversation, to come back to after thinking a line through. */
    public final class Place {
        final int[] history = LanguageArea.this.history.clone();
        final int historyLength = LanguageArea.this.historyLength;
        final List<Integer> utterance = new ArrayList<>(LanguageArea.this.utterance);
        final int[] heard = LanguageArea.this.heard;
        final int[] earlier = LanguageArea.this.earlier;
        final boolean heardOwn = LanguageArea.this.heardOwn;
    }

    public Place place() {
        return new Place();
    }

    public void restore(Place p) {
        System.arraycopy(p.history, 0, history, 0, HISTORY);
        historyLength = p.historyLength;
        utterance.clear();
        utterance.addAll(p.utterance);
        heard = p.heard;
        earlier = p.earlier;
        heardOwn = p.heardOwn;
        activate();
    }

    /**
     * Gets ready to start a line of its own with nobody having spoken: a fresh turn, as if the
     * last line had just ended.
     */
    public void startLine() {
        reset();
        hear(WordCode.TURN, false);
        // Nobody spoke, itself included, so the first words of the line are learned.
        heardOwn = false;
    }

    /** The words of the last line heard (word ids), and whether it was this brain's own. */
    int[] heardLine() {
        return heard;
    }

    int[] earlierLine() {
        return earlier;
    }

    boolean heardOwnLine() {
        return heardOwn;
    }

    /** True while nothing has been heard since the last reset or turn change. */
    public boolean atTurnStart() {
        return utterance.isEmpty();
    }

    /** Hears one word. With learn set, the synapses from the context it followed grow. */
    public void hear(String word, boolean learn) {
        int id = vocabulary.id(word);
        // The first word someone says after this brain's own line is not learned at all: what
        // follows its own words across the turn is not an answer to them.
        if (learn && !(heardOwn && utterance.isEmpty())) {
            for (int i = 0; i < activeCount; i++) {
                // What someone says after this brain's own line is not learned as an answer to
                // that line: a player asking the same thing again and again would otherwise teach
                // it to answer with the question.
                if (heardOwn && fromHeard(activeKinds[i])) {
                    continue;
                }
                own.strengthen((int) activeHashes[i] & own.cellMask, id);
            }
        }
        System.arraycopy(history, 0, history, 1, HISTORY - 1);
        history[0] = id;
        historyLength = Math.min(HISTORY, historyLength + 1);
        if (word.equals(WordCode.TURN)) {
            int from = Math.max(0, utterance.size() - MEMORY_WORDS);
            earlier = heard;
            heard = utterance.subList(from, utterance.size()).stream().mapToInt(Integer::intValue).toArray();
            heardOwn = !learn;
            utterance.clear();
        } else {
            utterance.add(id);
        }
        activate();
    }

    /** Forgets the words of a line that was never ended, so they are not taken as part of the next one. */
    void dropUnended() {
        utterance.clear();
    }

    /** The word this area most expects next, or null when it expects nothing. */
    public String expect() {
        int best = bestWord(-1);
        return best < 0 ? null : vocabulary.word(best);
    }

    /** The most expected word other than skip, or -1. */
    public int bestWord(int skip) {
        float[] s = scores();
        int best = -1;
        float bestScore = 0f;
        for (int w = 0; w < vocabulary.size(); w++) {
            if (w != skip && s[w] > bestScore) {
                bestScore = s[w];
                best = w;
            }
        }
        return best;
    }

    /** Drive onto each word cell from the active context cells, indexed by vocabulary id. */
    public float[] scores() {
        if (score.length < vocabulary.size()) {
            score = new float[vocabulary.size() * 2];
        }
        Arrays.fill(score, 0, vocabulary.size(), 0f);
        for (int i = 0; i + 1 < activeCount; i += CELLS_PER_FEATURE) {
            if (base != null) {
                base.drive(activeHashes[i], activeHashes[i + 1], activeWeights[i], score);
                own.drive(activeHashes[i], activeHashes[i + 1], activeWeights[i] * OWN_WEIGHT, score);
            } else {
                own.drive(activeHashes[i], activeHashes[i + 1], activeWeights[i], score);
            }
        }
        return score;
    }

    /**
     * What the area would expect after one more word, without hearing it: the drive onto each word
     * cell, into a buffer of its own that the next call overwrites. Used to plan a word ahead.
     */
    public float[] scoresAfter(int word) {
        int[] savedHistory = history.clone();
        int savedLength = historyLength;
        int savedCount = activeCount;
        long[] savedHashes = Arrays.copyOf(activeHashes, activeCount);
        float[] savedWeights = Arrays.copyOf(activeWeights, activeCount);
        byte[] savedKinds = Arrays.copyOf(activeKinds, activeCount);
        float[] savedScore = score;
        System.arraycopy(history, 0, history, 1, HISTORY - 1);
        history[0] = word;
        historyLength = Math.min(HISTORY, historyLength + 1);
        utterance.add(word);
        activate();
        score = peek.length < vocabulary.size() ? new float[vocabulary.size() * 2] : peek;
        peek = scores();
        utterance.remove(utterance.size() - 1);
        System.arraycopy(savedHistory, 0, history, 0, HISTORY);
        historyLength = savedLength;
        activeCount = savedCount;
        System.arraycopy(savedHashes, 0, activeHashes, 0, savedCount);
        System.arraycopy(savedWeights, 0, activeWeights, 0, savedCount);
        System.arraycopy(savedKinds, 0, activeKinds, 0, savedCount);
        score = savedScore;
        return peek;
    }

    /** Lights the context cells for what has just been heard. */
    private void activate() {
        activeCount = 0;
        long h = 0;
        for (int order = 0; order < historyLength; order++) {
            h = mix(h * 31 + history[order] + 1);
            feature(order < 4 ? order : F_QUINT, h);
        }
        if (heard.length == 0 && utterance.isEmpty() && historyLength > 0) {
            // Nothing was said to it: it is opening the conversation ("look, a zombie!").
            feature(F_HEARD_START, 0x0BE5EED5L);
        }
        for (int q : heard) {
            if (utterance.isEmpty()) {
                feature(F_HEARD_START, mix(((long) q << 8) + 0x51ED27L));
            }
            if (historyLength > 0) {
                feature(F_HEARD_BI, mix(((long) q << 24) + history[0] + 0x3C6EF3L));
            }
        }
        // The line before that one too, so the talk keeps to its topic over a few turns.
        if (historyLength > 0) {
            for (int q : earlier) {
                feature(F_EARLIER_BI, mix(((long) q << 24) + history[0] + 0x7F4A7C15L));
            }
        }
    }

    private void feature(int kind, long h) {
        float w = featureWeight[kind];
        for (int c = 0; c < CELLS_PER_FEATURE; c++) {
            h = mix(h + kind * 0x9E3779B97F4A7C15L + c);
            if (activeCount == activeHashes.length) {
                activeHashes = Arrays.copyOf(activeHashes, activeCount * 2);
                activeWeights = Arrays.copyOf(activeWeights, activeCount * 2);
                activeKinds = Arrays.copyOf(activeKinds, activeCount * 2);
            }
            activeKinds[activeCount] = (byte) kind;
            activeHashes[activeCount] = h;
            activeWeights[activeCount++] = w;
        }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static void writeVarInt(DataOutput out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    static int readVarInt(DataInput in) throws IOException {
        int v = 0;
        for (int shift = 0; ; shift += 7) {
            int b = in.readUnsignedByte();
            v |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return v;
            }
        }
    }
}
