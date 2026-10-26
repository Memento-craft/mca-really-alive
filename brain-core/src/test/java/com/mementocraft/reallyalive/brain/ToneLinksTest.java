package com.mementocraft.reallyalive.brain;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tone links: whether the everyday words of an answer go with those of the line it answers.
 * Also builds the shipped links when TONES_OUT is set (see tools/corpus/README.md).
 */
class ToneLinksTest {
    @Test
    void anAnswerInTheRightToneFitsBetter() {
        ToneLinks tones = ToneLinks.shared();
        assertNotNull(tones, "the tone links are missing from the resources");
        Vocabulary v = EnglishBase.shared().vocabulary;
        String[][] cases = {{"my grandma passed away last week.", "i'm so sorry to hear that.", "that's great! i'm glad you're enjoying it."},
            {"i got the job!", "congratulations! that's amazing!", "i'm so sorry to hear that."},
            {"thank you so much for your help.", "you're welcome, anytime.", "what are you talking about?"},
            {"i'm sorry, i didn't mean to ignore you.", "it's okay, i understand.", "that's great news!"}};
        for (String[] c : cases) {
            int[] heard = ids(v, c[0]);
            int[] fitting = ids(v, c[1]);
            int[] other = ids(v, c[2]);
            float fits = tones.fit(heard, fitting, fitting.length);
            float not = tones.fit(heard, other, other.length);
            System.out.println("  " + c[0] + " -> " + c[1] + " " + fits + ", " + c[2] + " " + not);
            assertTrue(fits > not, "\"" + c[1] + "\" should fit \"" + c[0] + "\" better than \"" + c[2] + "\"");
        }
    }

    private static int[] ids(Vocabulary v, String line) {
        return WordCode.tokenize(line).stream().mapToInt(v::find).toArray();
    }

    /**
     * Builds the links from a corpus file: among the WORDS commonest words, for each pair of lines,
     * every word of the line with every word of its answer. A pair counts only where chance alone
     * would bring it together at least MIN_EXPECTED times; its link is the pointwise mutual
     * information, in steps of 1/16, for or against.
     */
    @Test
    void build() throws IOException {
        String out = System.getenv("TONES_OUT");
        String corpus = System.getenv("CORPUS");
        if (out == null || corpus == null) {
            return;
        }
        int size = Integer.parseInt(System.getenv().getOrDefault("WORDS", "3000"));
        double minExpected = Double.parseDouble(System.getenv().getOrDefault("MIN_EXPECTED", "20"));
        Vocabulary v = EnglishBase.shared().vocabulary;
        int words = v.size();
        long[] freq = new long[words];
        try (BufferedReader r = Files.newBufferedReader(Path.of(corpus))) {
            String line;
            for (int lines = 0; (line = r.readLine()) != null && lines < 3_000_000; lines++) {
                for (String w : WordCode.tokenize(line)) {
                    int id = v.find(w);
                    if (id >= 0) {
                        freq[id]++;
                    }
                }
            }
        }
        Integer[] byFreq = new Integer[words];
        Arrays.setAll(byFreq, i -> i);
        Arrays.sort(byFreq, (x, y) -> Long.compare(freq[y], freq[x]));
        Set<String> punctuation = Set.of(".", ",", "?", "!", WordCode.TURN);
        int[] row = new int[words];
        Arrays.fill(row, -1);
        int[] word = new int[size];
        int k = 0;
        for (int i = 0; i < words && k < size; i++) {
            if (!punctuation.contains(v.word(byFreq[i]))) {
                row[byFreq[i]] = k;
                word[k++] = byFreq[i];
            }
        }
        size = k;
        int[][] together = new int[size][size];
        long[] asHeard = new long[size];
        long[] asAnswer = new long[size];
        long pairs = 0;
        try (BufferedReader r = Files.newBufferedReader(Path.of(corpus))) {
            String line;
            int[] prev = null;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    prev = null;
                    continue;
                }
                int[] cur = WordCode.tokenize(line).stream().mapToInt(v::find)
                        .filter(id -> id >= 0 && row[id] >= 0).map(id -> row[id]).distinct().toArray();
                if (prev != null) {
                    pairs++;
                    for (int q : prev) {
                        asHeard[q]++;
                        for (int a : cur) {
                            together[q][a]++;
                        }
                    }
                    for (int a : cur) {
                        asAnswer[a]++;
                    }
                }
                prev = cur;
            }
        }
        try (OutputStream os = Files.newOutputStream(Path.of(out))) {
            DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(os)));
            o.writeInt(words);
            o.writeInt(size);
            for (int i = 0; i < size; i++) {
                o.writeChar(word[i]);
            }
            for (int q = 0; q < size; q++) {
                for (int a = 0; a < size; a++) {
                    double expected = (double) asHeard[q] * asAnswer[a] / pairs;
                    double pmi = expected >= minExpected ? Math.log((together[q][a] + 1) / expected) : 0;
                    o.writeByte((int) Math.max(-127, Math.min(127, Math.round(pmi * 16))));
                }
            }
            o.close();
        }
        System.out.println(pairs + " pairs of lines, " + size + " words");
    }
}
