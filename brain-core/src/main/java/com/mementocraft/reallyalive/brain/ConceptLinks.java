package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * The links between words and what they mean: Hebbian synapses between each word cell of
 * Wernicke's area and the meaning cells that are active when the word is heard. Meaning cells are
 * the Concept lobe's (what is seen, smelt and felt, and composed concepts), helped by the frontal
 * part of the concept system (what the villager is attending to and holding in mind), the Memory
 * lobe (what just happened) and the Emotion lobe (what it wants and fears).
 *
 * <p>The links run both ways. Hearing a word drives its meaning cells, so "zombie" brings up some
 * of what seeing one does. And while the villager talks, the language area proposes how the
 * sentence goes on and each word's links say how much of its meaning is present right now, so of
 * all the words that fit "i see a ...", the one whose meaning the concept system holds wins.
 *
 * <p>Learning is cross-situational, as with a child: a word heard many times, each time with
 * different things around, ends up linked to the cells that were active every time. It is the
 * covariance form of Hebb's rule: each link follows how far its cell sits above or below its own
 * usual activity when the word is heard, in units of how much that cell usually wavers. Cells that
 * merely flicker average out to nothing, cells that reliably come on with the word grow strong
 * positive links, and cells it reliably quiets grow negative ones. A word keeps only its strongest
 * few dozen links, the weakest making way for a newcomer.
 */
final class ConceptLinks {
    /** Links each word cell keeps. */
    static final int MAX_LINKS = 256;
    /** How many of the most changed meaning cells may join a word's links when it is heard. */
    static final int ACTIVE_CELLS = 128;
    /** Learning rate when someone names a thing; overheard words in talk teach at TALK_RATE. */
    static final float NAME_RATE = 0.1f;
    static final float TALK_RATE = 0.03f;
    /** How hard a heard word pushes the other words that fit the moment away from it. */
    static final float CONTRAST = 0.3f;
    /** How much of what is held in mind stays, per update of what is present (every second). */
    static final float HELD_KEEP = 0.93f;
    /** How strongly something else present now pushes out what is held. */
    static final float HELD_DISPLACE = 0.7f;
    /** How present a word's meaning must come up, from not being in mind, to be noticed. */
    static final float NOTICE = 0.4f;
    /** How fast each meaning cell's usual activity and wavering are tracked: about a minute. */
    static final float BASELINE_DECAY = 0.999f;
    static final float SPREAD_MIN = 1e-5f;
    /** Deviations are capped, so one cell gone wild cannot carry a word on its own. */
    static final float DEVIATION_MAX = 4f;
    /** How fast each word's usual match is tracked, per time it is weighed. */
    static final float USUAL_MATCH_DECAY = 0.98f;
    /** How strongly a heard word drives its meaning cells, and how fast that fades. */
    static final float EVOKE_GAIN = 0.5f;
    static final float EVOKE_DECAY = 0.8f;
    /** A word needs this many exposures before its links count fully. */
    static final float EXPOSURES_FULL = 6f;
    /** Links this strong on average (in units of a cell's usual wavering) count fully... */
    static final float WEIGHT_FULL = 1f;
    /** ...and links this weak, what hearing a word anywhere leaves behind, count not at all. */
    static final float WEIGHT_NONE = 0.3f;

    /** Per word id: linked meaning cells, their weights, and how many there are. */
    int[][] cells = new int[0][];
    float[][] weights = new float[0][];
    int[] size = new int[0];
    /** How often each word was learned from, and how much of its meaning is usually present. */
    float[] exposures = new float[0];
    float[] usualMatch = new float[0];
    /**
     * What the frontal part holds in mind: how present each word's meaning was a moment ago,
     * fading, and pushed out sooner by whatever else is present now. Not saved.
     */
    float[] held = new float[0];
    /**
     * The word whose meaning has just come up strongly and was not in mind before (something new
     * in front of the villager), as of the last learning update of presence; -1 if none.
     */
    int noticed = -1;

    /** Per neuron: slow activity of meaning cells, its usual level and wavering, what words evoke. */
    float[] activity = new float[0];
    float[] baseline = new float[0];
    float[] spread = new float[0];
    float[] evoked = new float[0];

    private int[] top = new int[ACTIVE_CELLS];
    private float[] topD = new float[ACTIVE_CELLS];

    void ensureNeurons(int capacity) {
        if (activity.length < capacity) {
            activity = Arrays.copyOf(activity, capacity);
            baseline = Arrays.copyOf(baseline, capacity);
            spread = Arrays.copyOf(spread, capacity);
            evoked = Arrays.copyOf(evoked, capacity);
        }
    }

    void ensureWords(int count) {
        if (size.length < count) {
            int c = Math.max(count, size.length * 2);
            cells = Arrays.copyOf(cells, c);
            weights = Arrays.copyOf(weights, c);
            size = Arrays.copyOf(size, c);
            exposures = Arrays.copyOf(exposures, c);
            usualMatch = Arrays.copyOf(usualMatch, c);
            held = Arrays.copyOf(held, c);
        }
    }

    /**
     * Follows the meaning cells' firing, each with its lobe's time constant: the frontal part holds
     * what it attends to longest, which is how a topic stays in mind after the thing is gone.
     */
    void track(int[] meaning, int count, boolean[] fired, float[] keep) {
        for (int k = 0; k < count; k++) {
            int i = meaning[k];
            float a = activity[i] = activity[i] * keep[k] + (fired[i] ? 1f - keep[k] : 0f);
            float d = a - baseline[i];
            baseline[i] += d * (1f - BASELINE_DECAY);
            spread[i] = spread[i] * BASELINE_DECAY + d * d * (1f - BASELINE_DECAY);
            evoked[i] *= EVOKE_DECAY;
        }
    }

    /** How far a meaning cell is from its usual activity, in units of its usual wavering. */
    float deviation(int i) {
        float z = (activity[i] - baseline[i]) / (float) Math.sqrt(spread[i] + SPREAD_MIN);
        return z > DEVIATION_MAX ? DEVIATION_MAX : (z < -DEVIATION_MAX ? -DEVIATION_MAX : z);
    }

    /** Learns that this word goes with what the meaning cells hold now. */
    void learn(int word, int[] meaning, int count, float rate) {
        if (word < 0) {
            return;
        }
        ensureWords(word + 1);
        int found = topCells(meaning, count);
        int[] c = cells[word];
        float[] w = weights[word];
        if (c == null) {
            c = cells[word] = new int[MAX_LINKS];
            w = weights[word] = new float[MAX_LINKS];
        }
        int n = size[word];
        // Every link moves toward how far its cell is from usual now; a link to a cell that only
        // happened to be active once fades back toward nothing with each later hearing.
        for (int k = 0; k < n; k++) {
            w[k] += rate * (deviation(c[k]) - w[k]);
        }
        link(word, found, rate);
        exposures[word] += 1f;
        // Word cells compete: every other word that fits what is here now too is pushed a little
        // away from it, so "cow" and "zombie", both creatures, come to differ by what tells them
        // apart (the smell) instead of both meaning "a creature".
        for (int other = 0; other < size.length; other++) {
            if (other == word || size[other] == 0) {
                continue;
            }
            float m = match(other);
            if (m <= 0f) {
                continue;
            }
            float step = CONTRAST * rate * m;
            for (int k = 0; k < size[other]; k++) {
                weights[other][k] -= step * deviation(cells[other][k]);
            }
            // And it learns what it is not: links against the cells most stirred now, so in front
            // of a cow, "zombie" comes to fit less than "cow" even where both are creatures.
            link(other, found, -step);
        }
    }

    /** Links a word to those of the found cells it lacks, starting at scale times their deviation. */
    private void link(int word, int found, float scale) {
        int[] c = cells[word];
        float[] w = weights[word];
        int n = size[word];
        for (int t = 0; t < found; t++) {
            int cell = top[t];
            boolean linked = false;
            for (int k = 0; k < n && !linked; k++) {
                linked = c[k] == cell;
            }
            if (linked) {
                continue;
            }
            float start = scale * topD[t];
            if (n < MAX_LINKS) {
                c[n] = cell;
                w[n++] = start;
                continue;
            }
            int weakest = 0;
            for (int k = 1; k < n; k++) {
                if (Math.abs(w[k]) < Math.abs(w[weakest])) {
                    weakest = k;
                }
            }
            if (Math.abs(start) > Math.abs(w[weakest])) {
                c[weakest] = cell;
                w[weakest] = start;
            }
        }
        size[word] = n;
    }

    /** The meaning cells furthest from their usual activity, into top and topD; returns how many. */
    private int topCells(int[] meaning, int count) {
        int found = 0;
        for (int k = 0; k < count; k++) {
            int i = meaning[k];
            float d = deviation(i);
            float m = Math.abs(d);
            if (m < 1f || found == ACTIVE_CELLS && m <= Math.abs(topD[found - 1])) {
                continue;
            }
            int p = found < ACTIVE_CELLS ? found++ : found - 1;
            while (p > 0 && Math.abs(topD[p - 1]) < m) {
                top[p] = top[p - 1];
                topD[p] = topD[p - 1];
                p--;
            }
            top[p] = i;
            topD[p] = d;
        }
        return found;
    }

    /**
     * How well what the meaning cells hold now fits the word's meaning: the correlation between its
     * links and how far each linked cell is from usual, about -1..1.
     */
    float match(int word) {
        if (word >= size.length || size[word] == 0) {
            return 0f;
        }
        int[] c = cells[word];
        float[] w = weights[word];
        float dot = 0f, ww = 0f, dd = 0f;
        for (int k = 0; k < size[word]; k++) {
            float d = deviation(c[k]);
            dot += w[k] * d;
            ww += w[k] * w[k];
            dd += d * d;
        }
        // Against unit wavering as well as the cells' own, so a few cells drifting a little never
        // look like a perfect fit.
        return ww > 0f ? dot / (float) Math.sqrt(ww * Math.max(dd, size[word])) : 0f;
    }

    /**
     * How much more than usual each word's meaning is present, for every word with links, into
     * boost (indexed by word id). A word that fits whatever is going on (a word like "see") is
     * about as present as usual and gets nothing; a word for something in front of the villager
     * gets a lot; a word for something absent goes below its usual and loses. With learn set, each
     * word's usual level is updated.
     */
    void presence(float[] boost, int words, boolean learn) {
        presence(boost, words, learn, true);
    }

    /** As {@link #presence(float[], int, boolean)}; without recall, only what is present now. */
    void presence(float[] boost, int words, boolean learn, boolean recall) {
        int limit = Math.min(words, size.length);
        float present = 0f;
        for (int wd = 0; wd < limit; wd++) {
            if (size[wd] == 0) {
                continue;
            }
            float m = match(wd);
            boost[wd] = strength(wd) * (m - usualMatch[wd]);
            present = Math.max(present, boost[wd]);
            if (learn) {
                usualMatch[wd] = usualMatch[wd] * USUAL_MATCH_DECAY + m * (1f - USUAL_MATCH_DECAY);
            }
        }
        // What was here a moment ago stays in mind after its cells have quieted (they even dip
        // below usual), until it fades or something else takes its place.
        if (learn) {
            noticed = -1;
        }
        float newest = NOTICE;
        for (int wd = 0; wd < limit; wd++) {
            if (size[wd] == 0) {
                continue;
            }
            float now = boost[wd];
            if (learn) {
                if (now > newest && held[wd] < now / 2f) {
                    newest = now;
                    noticed = wd;
                }
                float displaced = Math.max(0f, Math.min(1f, HELD_DISPLACE * (present - now)));
                held[wd] = Math.max(now, held[wd] * HELD_KEEP * (1f - displaced));
            }
            boost[wd] = recall ? Math.max(now, held[wd]) : now;
        }
    }

    /**
     * How surely a word means something, 0..1: heard often enough, and its links strong. A word
     * heard in every kind of situation ("you", "the") averages out to weak links, so even if they
     * happen to fit what is going on, it is weighed by the language area alone.
     */
    float strength(int word) {
        int n = size[word];
        float ww = 0f;
        for (int k = 0; k < n; k++) {
            ww += weights[word][k] * weights[word][k];
        }
        float rms = n > 0 ? (float) Math.sqrt(ww / n) : 0f;
        float strong = Math.max(0f, Math.min(1f, (rms - WEIGHT_NONE) / (WEIGHT_FULL - WEIGHT_NONE)));
        return Math.min(1f, exposures[word] / EXPOSURES_FULL) * strong;
    }

    /** Hearing a word brings up its meaning: its linked cells are driven, or quieted, a moment. */
    void evoke(int word) {
        if (word < 0 || word >= size.length) {
            return;
        }
        float sure = strength(word);
        for (int k = 0; k < size[word]; k++) {
            evoked[cells[word][k]] += EVOKE_GAIN * sure * weights[word][k];
        }
    }

    /** A reborn neuron starts with no meaning: links to it are dropped and its levels cleared. */
    void forget(int neuron) {
        if (neuron < activity.length) {
            activity[neuron] = 0f;
            baseline[neuron] = 0f;
            spread[neuron] = 0f;
            evoked[neuron] = 0f;
        }
        for (int wd = 0; wd < size.length; wd++) {
            int[] c = cells[wd];
            for (int k = size[wd] - 1; k >= 0; k--) {
                if (c[k] == neuron) {
                    size[wd]--;
                    c[k] = c[size[wd]];
                    weights[wd][k] = weights[wd][size[wd]];
                }
            }
        }
    }

    /** How many meaning cells the word is linked to. */
    int links(int word) {
        return word >= 0 && word < size.length ? size[word] : 0;
    }

    void write(DataOutput out, int neurons) throws IOException {
        for (int i = 0; i < neurons; i++) {
            out.writeFloat(activity[i]);
            out.writeFloat(baseline[i]);
            out.writeFloat(spread[i]);
            out.writeFloat(evoked[i]);
        }
        int words = 0;
        for (int wd = 0; wd < size.length; wd++) {
            words += exposures[wd] > 0f ? 1 : 0;
        }
        out.writeInt(words);
        for (int wd = 0; wd < size.length; wd++) {
            if (exposures[wd] == 0f) {
                continue;
            }
            out.writeInt(wd);
            out.writeFloat(exposures[wd]);
            out.writeFloat(usualMatch[wd]);
            out.writeInt(size[wd]);
            for (int k = 0; k < size[wd]; k++) {
                out.writeInt(cells[wd][k]);
                out.writeFloat(weights[wd][k]);
            }
        }
    }

    void read(DataInput in, int neurons) throws IOException {
        ensureNeurons(neurons);
        for (int i = 0; i < neurons; i++) {
            activity[i] = in.readFloat();
            baseline[i] = in.readFloat();
            spread[i] = in.readFloat();
            evoked[i] = in.readFloat();
        }
        int words = in.readInt();
        for (int j = 0; j < words; j++) {
            int wd = in.readInt();
            ensureWords(wd + 1);
            exposures[wd] = in.readFloat();
            usualMatch[wd] = in.readFloat();
            int n = in.readInt();
            cells[wd] = new int[MAX_LINKS];
            weights[wd] = new float[MAX_LINKS];
            for (int k = 0; k < n; k++) {
                cells[wd][k] = in.readInt();
                weights[wd][k] = in.readFloat();
            }
            size[wd] = n;
        }
    }
}
