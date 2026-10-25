package com.mementocraft.reallyalive.brain;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The remembered exchanges: hearing a line brings up answers people gave to lines like it. Also
 * builds the shipped memory when EXCHANGES_OUT is set (see tools/corpus/README.md).
 */
class ExchangeMemoryTest {
    @Test
    void aHeardLineBringsUpAnswersAboutTheSameThing() {
        ExchangeMemory memory = ExchangeMemory.shared();
        assertNotNull(memory, "the remembered exchanges are missing from the resources");
        Vocabulary v = EnglishBase.shared().vocabulary;
        String[][] cases = {{"can i go to the park?", "park", "parks", "swings", "play", "fun", "there"},
            {"would you like some bread?", "bread", "thank", "please", "eat", "hungry", "yes"},
            {"watch out! zombies!", "zombie", "zombies", "run", "sword", "hide", "guards", "behind"}};
        for (String[] c : cases) {
            int[] heard = WordCode.tokenize(c[0]).stream().mapToInt(v::find).toArray();
            float[] match = new float[8];
            int[][] answers = memory.recall(heard, new int[0], 0f, 8, match);
            assertTrue(answers.length > 0, "nothing recalled for " + c[0]);
            int fitting = 0;
            for (int[] a : answers) {
                StringBuilder line = new StringBuilder();
                boolean fits = false;
                for (int w : a) {
                    line.append(v.word(w)).append(' ');
                    for (int k = 1; k < c.length; k++) {
                        fits |= v.word(w).equals(c[k]);
                    }
                }
                fitting += fits ? 1 : 0;
                System.out.println("  " + c[0] + " -> " + line.toString().trim());
            }
            assertTrue(fitting >= 2, "recalled answers don't fit " + c[0]);
        }
    }

    /**
     * Builds the memory from corpus files (comma separated, each with a stride: every n-th pair of
     * lines is kept, as file:n). The answer's line is cued by the telling words of the line it
     * answered; the commonest words and names (capitalised mid-sentence in SODA) are no cues, and
     * answers naming someone are left out, since villagers don't know those people.
     */
    @Test
    void build() throws IOException {
        String out = System.getenv("EXCHANGES_OUT");
        String corpus = System.getenv("CORPUS");
        if (out == null || corpus == null) {
            return;
        }
        Vocabulary v = EnglishBase.shared().vocabulary;
        String[] files = corpus.split(",");
        Map<String, Integer> freq = new HashMap<>();
        Map<String, int[]> caps = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(Path.of(files[0].split(":")[0]))) {
            String line;
            for (int lines = 0; (line = r.readLine()) != null && lines < 2_000_000; lines++) {
                boolean start = true;
                for (String t : line.split("[^A-Za-z']+")) {
                    if (t.isEmpty()) {
                        continue;
                    }
                    String low = t.toLowerCase();
                    freq.merge(low, 1, Integer::sum);
                    if (!start) {
                        int[] c = caps.computeIfAbsent(low, k -> new int[2]);
                        c[1]++;
                        c[0] += Character.isUpperCase(t.charAt(0)) ? 1 : 0;
                    }
                    start = false;
                }
            }
        }
        List<String> byFreq = new ArrayList<>(freq.keySet());
        byFreq.sort((x, y) -> freq.get(y) - freq.get(x));
        Set<String> stop = new HashSet<>(byFreq.subList(0, Integer.parseInt(System.getenv().getOrDefault("STOP_WORDS", "120"))));
        stop.addAll(List.of(".", ",", "?", "!", WordCode.TURN));
        Set<String> names = new HashSet<>();
        caps.forEach((w, c) -> {
            if (c[1] >= 10 && c[0] >= 0.8f * c[1] && !w.equals("i") && !w.startsWith("i'")) {
                names.add(w);
            }
        });
        List<char[]> answers = new ArrayList<>();
        List<char[]> cues = new ArrayList<>();
        for (String spec : files) {
            String[] fs = spec.split(":");
            int stride = fs.length > 1 ? Integer.parseInt(fs[1]) : 1;
            long pairNo = 0;
            List<String> prev = null;
            try (BufferedReader r = Files.newBufferedReader(Path.of(fs[0]))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (line.isBlank()) {
                        prev = null;
                        continue;
                    }
                    List<String> cur = WordCode.tokenize(line);
                    if (prev != null && pairNo++ % stride == 0 && cur.size() >= 2 && cur.size() <= 24) {
                        char[] answer = new char[cur.size()];
                        boolean ok = true;
                        for (int i = 0; i < answer.length && ok; i++) {
                            int id = v.find(cur.get(i));
                            ok = id >= 0 && id < 65536 && !names.contains(cur.get(i));
                            answer[i] = (char) id;
                        }
                        Set<Integer> cue = new LinkedHashSet<>();
                        for (String w : prev) {
                            int id = v.find(w);
                            if (id >= 0 && !stop.contains(w) && !names.contains(w)) {
                                cue.add(id);
                            }
                        }
                        if (ok && !cue.isEmpty() && cue.size() < 256) {
                            char[] c = new char[cue.size()];
                            int k = 0;
                            for (int id : cue) {
                                c[k++] = (char) id;
                            }
                            answers.add(answer);
                            cues.add(c);
                        }
                    }
                    prev = cur;
                }
            }
        }
        try (OutputStream os = Files.newOutputStream(Path.of(out))) {
            DataOutputStream o = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(os)));
            o.writeInt(v.size());
            o.writeInt(answers.size());
            for (int p = 0; p < answers.size(); p++) {
                for (char[] l : new char[][] {answers.get(p), cues.get(p)}) {
                    o.writeByte(l.length);
                    for (char c : l) {
                        o.writeChar(c);
                    }
                }
            }
            o.close();
        }
        System.out.println("remembered " + answers.size() + " exchanges, " + names.size() + " names left out");
    }
}
