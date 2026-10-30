package com.mementocraft.reallyalive.brain;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The topic links: words of an answer that go with the words of the line it answers. Also builds
 * the shipped links when TOPICS_OUT is set (see tools/corpus/README.md).
 */
class TopicLinksTest {
    @Test
    void anAnswerAboutWhatWasSaidFitsBetterThanOneAboutSomethingElse() {
        TopicLinks topics = TopicLinks.shared();
        assertNotNull(topics, "the topic links are missing from the resources");
        Vocabulary v = EnglishBase.shared().vocabulary;
        String[][] cases = {{"can i sell my old toys?", "why do you want to sell your toys?", "the weather is nice today."},
            {"i'm so hungry.", "let's get something to eat.", "i like your new shoes."},
            {"it's raining again.", "take an umbrella.", "i got a new job."}};
        for (String[] c : cases) {
            int[] heard = ids(v, c[0]);
            int[] fitting = ids(v, c[1]);
            int[] other = ids(v, c[2]);
            float fits = topics.fit(heard, fitting, fitting.length);
            float not = topics.fit(heard, other, other.length);
            System.out.println("  " + c[0] + " -> " + c[1] + " " + fits + ", " + c[2] + " " + not);
            assertTrue(fits > not, "\"" + c[1] + "\" should fit \"" + c[0] + "\" better than \"" + c[2] + "\"");
        }
    }

    private static int[] ids(Vocabulary v, String line) {
        return WordCode.tokenize(line).stream().mapToInt(v::find).toArray();
    }

    /**
     * Builds the links from a corpus file: for each pair of lines, every telling word of the line
     * (not one of the STOP_WORDS commonest) with every telling word of its answer. A link is kept
     * where the two came together at least MIN_COUNT times and more often than chance, the
     * strongest KEEP per word, as pointwise mutual information in steps of 1/32.
     */
    @Test
    void build() throws IOException {
        String out = System.getenv("TOPICS_OUT");
        String corpus = System.getenv("CORPUS");
        if (out == null || corpus == null) {
            return;
        }
        int stopWords = Integer.parseInt(System.getenv().getOrDefault("STOP_WORDS", "150"));
        int minCount = Integer.parseInt(System.getenv().getOrDefault("MIN_COUNT", "5"));
        int keep = Integer.parseInt(System.getenv().getOrDefault("KEEP", "64"));
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
        boolean[] stop = new boolean[words];
        for (int i = 0; i < stopWords; i++) {
            stop[byFreq[i]] = true;
        }
        for (String s : new String[] {".", ",", "?", "!", WordCode.TURN}) {
            int id = v.find(s);
            if (id >= 0) {
                stop[id] = true;
            }
        }
        PairCounts together = new PairCounts(1 << 27);
        long[] asHeard = new long[words];
        long[] asAnswer = new long[words];
        long pairs = 0;
        try (BufferedReader r = Files.newBufferedReader(Path.of(corpus))) {
            String line;
            int[] prev = null;
            while ((line = r.readLine()) != null && !together.full()) {
                if (line.isBlank()) {
                    prev = null;
                    continue;
                }
                int[] cur = WordCode.tokenize(line).stream().mapToInt(v::find)
                        .filter(id -> id >= 0 && !stop[id]).distinct().toArray();
                if (prev != null && prev.length > 0 && cur.length > 0) {
                    pairs++;
                    for (int q : prev) {
                        asHeard[q]++;
                        for (int a : cur) {
                            together.add((long) q << 32 | a);
                        }
                    }
                    for (int a : cur) {
                        asAnswer[a]++;
                    }
                }
                prev = cur;
            }
        }
        List<List<long[]>> links = new ArrayList<>();
        for (int i = 0; i < words; i++) {
            links.add(new ArrayList<>());
        }
        for (int h = 0; h < together.keys.length; h++) {
            if (together.counts[h] < minCount) {
                continue;
            }
            int q = (int) (together.keys[h] >>> 32);
            int a = (int) together.keys[h];
            double pmi = Math.log((double) together.counts[h] * pairs / ((double) asHeard[q] * asAnswer[a]));
            if (pmi > 0) {
                links.get(q).add(new long[] {a, Math.min(255, Math.round(pmi * 32))});
            }
        }
        int kept = 0;
        try (OutputStream os = Files.newOutputStream(Path.of(out))) {
            DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(os)));
            o.writeInt(words);
            for (List<long[]> l : links) {
                l.sort((x, y) -> Long.compare(y[1], x[1]));
                int k = Math.min(keep, l.size());
                o.writeShort(k);
                for (long[] link : l.subList(0, k)) {
                    o.writeChar((int) link[0]);
                    o.writeByte((int) link[1]);
                }
                kept += k;
            }
            o.close();
        }
        System.out.println(pairs + " pairs of lines, " + kept + " links kept");
    }

    /** Counts of word pairs, in open addressing; stops taking new pairs once 70% full. */
    private static final class PairCounts {
        final long[] keys;
        final int[] counts;
        int used;

        PairCounts(int size) {
            keys = new long[size];
            counts = new int[size];
        }

        boolean full() {
            return used > keys.length * 0.7;
        }

        void add(long key) {
            int mask = keys.length - 1;
            int h = (int) (key * 0x9E3779B97F4A7C15L >>> 37) & mask;
            while (counts[h] != 0 && keys[h] != key) {
                h = (h + 1) & mask;
            }
            if (counts[h] == 0) {
                keys[h] = key;
                used++;
            }
            counts[h]++;
        }
    }
}
