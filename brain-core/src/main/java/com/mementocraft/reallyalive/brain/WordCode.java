package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * How a word sounds to the brain: a few active features out of {@link SenseLayout#WORD_FEATURES},
 * picked by hashing the word, so any word ever heard has its own pattern without a per-world list
 * of slots, and two words rarely share more than one feature. The hearing receptors and the speech
 * pools both use these features, so learning to say one word back helps with every other word.
 */
public final class WordCode {
    /** Said at the end of an utterance, when the other speaker's turn starts. */
    public static final String TURN = "<turn>";

    /** The word's active features, sorted. */
    public static int[] features(String word) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < word.length(); i++) {
            h = (h ^ word.charAt(i)) * 0x100000001B3L;
        }
        int[] f = new int[SenseLayout.WORD_FEATURES_ACTIVE];
        int n = 0;
        while (n < f.length) {
            h += 0x9E3779B97F4A7C15L;
            long z = h;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= z >>> 31;
            int c = (int) ((z >>> 1) % SenseLayout.WORD_FEATURES);
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

    /**
     * Splits text into lower-case words, keeping . ? ! and , as words of their own, like the pauses
     * and rises a listener hears.
     */
    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder w = new StringBuilder();
        String s = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '\'' && w.length() > 0) {
                w.append(c);
                continue;
            }
            if (w.length() > 0) {
                out.add(w.toString());
                w.setLength(0);
            }
            if (c == '.' || c == '?' || c == '!' || c == ',') {
                out.add(String.valueOf(c));
            }
        }
        if (w.length() > 0) {
            out.add(w.toString());
        }
        return out;
    }

    private WordCode() {}
}
