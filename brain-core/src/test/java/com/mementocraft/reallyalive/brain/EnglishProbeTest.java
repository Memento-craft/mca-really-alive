package com.mementocraft.reallyalive.brain;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Trains Wernicke's sequence layer on English conversations and measures next-word prediction
 * on held-out ones. Runs only when CORPUS (one or more files, comma separated) and EVAL point at
 * corpus files.
 */
class EnglishProbeTest {
    static List<List<String>> load(String path, int max) throws IOException {
        List<List<String>> dialogues = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(path))) {
            if (line.isBlank()) {
                if (!cur.isEmpty()) {
                    dialogues.add(cur);
                    cur = new ArrayList<>();
                    if (dialogues.size() >= max) {
                        break;
                    }
                }
                continue;
            }
            cur.addAll(WordCode.tokenize(line));
            cur.add(WordCode.TURN);
        }
        return dialogues;
    }

    static String reply(LanguageArea area, String prompt) {
        area.reset();
        for (String w : WordCode.tokenize(prompt)) {
            area.hear(w, false);
        }
        area.hear(WordCode.TURN, false);
        StringBuilder sb = new StringBuilder();
        int last = -1;
        for (int i = 0; i < 24; i++) {
            int id = area.bestWord(last);
            if (id < 0 || area.vocabulary.word(id).equals(WordCode.TURN)) {
                break;
            }
            String w = area.vocabulary.word(id);
            sb.append(w).append(' ');
            area.hear(w, false);
            last = id;
        }
        return sb.toString().trim();
    }

    static final String[] PROMPTS = {"hello.", "how are you?", "what is your name?", "where is the market?",
        "i'm hungry.", "watch out! zombies!", "thank you so much.", "what do you do for work?",
        "can you help me with fixing the fence?", "why did the chicken cross the road?", "what colour is the sky?",
        "i feel sad today.", "do you have any bread?", "good night.", "will you marry me?", "it's getting dark.",
        "what do bees make?", "i got the job!", "my head hurts.", "are you angry with me?",
        "how much for the apples?", "i'm sorry i was late.", "do you want to play hide and seek?",
        "what's for dinner?", "is it far?", "can you swim?", "tell me a joke.", "where do you live?"};

    @Test
    void probe() throws IOException {
        String corpus = System.getenv("CORPUS");
        String eval = System.getenv("EVAL");
        if (corpus == null || eval == null) {
            return;
        }
        int bits = Integer.parseInt(System.getenv().getOrDefault("BITS", "23"));
        String fw = System.getenv("FW");
        if (fw != null) {
            String[] parts = fw.split(",");
            for (int i = 0; i < parts.length; i++) {
                LanguageArea.featureWeight[i] = Float.parseFloat(parts[i]);
            }
        }
        List<List<String>> test = load(eval, 2000);
        LanguageArea area = new LanguageArea(bits);
        Map<String, Map<String, Integer>> bigram = new HashMap<>();
        long start = System.currentTimeMillis();
        // CORPUS may name several files; their conversations are interleaved so each is spread
        // over the whole run (a full context cell keeps what keeps coming back, so order matters).
        String[] files = corpus.split(",");
        BufferedReader[] in = new BufferedReader[files.length];
        double[] size = new double[files.length];
        double[] read = new double[files.length];
        for (int i = 0; i < files.length; i++) {
            in[i] = Files.newBufferedReader(Path.of(files[i]));
            size[i] = Files.size(Path.of(files[i]));
        }
        long words = 0, dialogues = 0;
        while (true) {
            int pick = -1;
            for (int i = 0; i < files.length; i++) {
                if (in[i] != null && (pick < 0 || read[i] / size[i] < read[pick] / size[pick])) {
                    pick = i;
                }
            }
            if (pick < 0) {
                break;
            }
            List<String> d = new ArrayList<>();
            String line;
            while ((line = in[pick].readLine()) != null) {
                read[pick] += line.length() + 1;
                if (line.isBlank()) {
                    if (!d.isEmpty()) {
                        break;
                    }
                    continue;
                }
                d.addAll(WordCode.tokenize(line));
                d.add(WordCode.TURN);
            }
            if (line == null) {
                in[pick].close();
                in[pick] = null;
            }
            // A conversation opens at a turn with nothing heard, so how lines open is learned too.
            area.startLine();
            String prev = null;
            for (String w : d) {
                area.hear(w, true);
                if (prev != null && dialogues < 200_000) {
                    bigram.computeIfAbsent(prev, k -> new HashMap<>()).merge(w, 1, Integer::sum);
                }
                prev = w;
                words++;
            }
            if (++dialogues % 500_000 == 0) {
                System.out.printf("trained %d dialogues, %d words, vocab %d, %.0fs%n", dialogues, words,
                        area.vocabulary.size(), (System.currentTimeMillis() - start) / 1000.0);
            }
        }
        long[] use = area.own.usage();
        System.out.printf("trained %d dialogues, %d words; cells used %d, synapses %d; %s%n", dialogues, words, use[0], use[1],
                evaluate(area, test, bigram));
        String out = System.getenv("MODEL_OUT");
        if (out != null) {
            int min = Integer.parseInt(System.getenv().getOrDefault("MIN_COUNT", "12"));
            int keep = Integer.parseInt(System.getenv().getOrDefault("KEEP", "6"));
            try (var o = Files.newOutputStream(Path.of(out))) {
                EnglishBase.write(area, min, keep, o);
            }
            area = null;
            EnglishBase base;
            try (var i = Files.newInputStream(Path.of(out))) {
                base = EnglishBase.read(i);
            }
            long[] bu = base.sheet.usage();
            System.out.printf("frozen model: %d bytes, %d cells, %d synapses%n", Files.size(Path.of(out)), bu[0], bu[1]);
            area = new LanguageArea(new LanguageArea.Sheet(EnglishBase.OWN_CELL_BITS), base.sheet, base.vocabulary.copy());
            System.out.println("reloaded: " + evaluate(area, test, bigram));
        }
        for (String p : PROMPTS) {
            System.out.println("  > " + p + "\n    " + reply(area, p));
        }
    }

    static String evaluate(LanguageArea area, List<List<String>> test, Map<String, Map<String, Integer>> bigram) {
        int n = 0, hit = 0, biHit = 0, turnN = 0, turnHit = 0, turnBi = 0;
        for (List<String> d : test) {
            area.reset();
            String prev = null;
            for (String w : d) {
                if (prev != null) {
                    String guess = area.expect();
                    n++;
                    boolean ok = w.equals(guess);
                    if (ok) {
                        hit++;
                    }
                    Map<String, Integer> next = bigram.get(prev);
                    boolean bok = next != null
                            && next.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey().equals(w);
                    if (bok) {
                        biHit++;
                    }
                    if (prev.equals(WordCode.TURN)) {
                        turnN++;
                        turnHit += ok ? 1 : 0;
                        turnBi += bok ? 1 : 0;
                    }
                }
                area.hear(w, false);
                prev = w;
            }
        }
        // Whole replies: answer the first line of each held-out talk and compare with the real answer.
        int exact = 0, replies = 0;
        float overlap = 0f;
        for (List<String> d : test.subList(0, Math.min(500, test.size()))) {
            int t = d.indexOf(WordCode.TURN);
            int t2 = t < 0 ? -1 : d.subList(t + 1, d.size()).indexOf(WordCode.TURN);
            if (t2 < 0) {
                continue;
            }
            List<String> real = d.subList(t + 1, t + 1 + t2);
            String said = reply(area, String.join(" ", d.subList(0, t)));
            List<String> mine = List.of(said.split(" "));
            replies++;
            if (mine.equals(real)) {
                exact++;
            }
            long common = mine.stream().filter(real::contains).count();
            overlap += 2f * common / (mine.size() + real.size());
        }
        return String.format("next word %.1f%% (bigram counts %.1f%%), first word of reply %.1f%% (bigram %.1f%%), "
                        + "whole replies exact %.1f%% word overlap %.2f",
                100f * hit / n, 100f * biHit / n, 100f * turnHit / Math.max(1, turnN), 100f * turnBi / Math.max(1, turnN),
                100f * exact / replies, overlap / replies);
    }
}
