package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Villagers talk about what is in front of them. A brain lives through scenes (a zombie, a cow,
 * water, a ladder, fire, flowers, bread, being hungry, being hurt) while someone now and then
 * names what is there, as parents do. Then, with nobody naming anything, it is asked what it sees
 * or how it feels: the language area frames the answer ("i see a ...") and the links between the
 * concept system and its words pick the word. A brain of the same seed that never heard anything
 * named answers from the language area alone.
 */
class GroundedSpeechTest {
    record Scene(String word, String question, java.util.function.Consumer<SensoryFrame> setup) {}

    static void creature(SensoryFrame f, boolean hostile, float r, float g, float b) {
        for (int x = 6; x < 10; x++) {
            for (int y = 2; y < 7; y++) {
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_CREATURE)] = 1f;
                // The game marks a hostile creature as a hazard, as it does lava.
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_HAZARD)] = hostile ? 1f : 0f;
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.6f;
            }
        }
        paint(f, 6, 10, 2, 7, r, g, b);
    }

    static void ahead(SensoryFrame f, int channel, float r, float g, float b) {
        for (int x = 5; x < 11; x++) {
            for (int y = 0; y < 8; y++) {
                f.vision[SenseLayout.visionIndex(x, y, channel)] = 1f;
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.7f;
            }
        }
        paint(f, 5, 11, 0, 8, r, g, b);
    }

    static void paint(SensoryFrame f, int x0, int x1, int y0, int y1, float r, float g, float b) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                f.colour[SenseLayout.colourIndex(x, y, 0)] = r;
                f.colour[SenseLayout.colourIndex(x, y, 1)] = g;
                f.colour[SenseLayout.colourIndex(x, y, 2)] = b;
            }
        }
    }

    /** Times the villager lives through every scene before it is asked about them. */
    static final int ROUNDS = Integer.getInteger("rounds", 10);

    static final String SEE = "what do you see?|what can you see?|what are you looking at?";
    static final String SMELL = "what do you smell?|what's that smell?|can you smell that?";
    static final String FEEL = "how do you feel?|how are you feeling?|are you okay?";

    static final Scene[] SCENES = {
        new Scene("zombie", SEE, f -> {
            creature(f, true, 0.2f, 0.55f, 0.5f);
            f.set(OlfactoryChannel.HOSTILE, 0.9f);
            f.set(SoundChannel.HOSTILE, 0.6f);
        }),
        new Scene("cow", SEE, f -> {
            creature(f, false, 0.4f, 0.28f, 0.18f);
            f.set(OlfactoryChannel.ANIMAL, 0.9f);
            f.set(SoundChannel.ANIMAL, 0.6f);
        }),
        new Scene("ladder", SEE, f -> {
            ahead(f, SenseLayout.CH_CLIMBABLE, 0.55f, 0.4f, 0.2f);
            ahead(f, SenseLayout.CH_SOLID, 0.55f, 0.4f, 0.2f);
        }),
        new Scene("fire", SEE, f -> {
            ahead(f, SenseLayout.CH_HAZARD, 1f, 0.5f, 0.1f);
            f.set(OlfactoryChannel.SMOKE, 0.9f);
        }),
        new Scene("water", SEE, f -> {
            f.set(FeelChannel.IN_WATER, 1f);
            paint(f, 0, SenseLayout.VISION_W, 0, SenseLayout.VISION_H, 0.2f, 0.3f, 0.8f);
            f.set(OlfactoryChannel.WATER, 0.9f);
            f.set(SoundChannel.WATER, 0.7f);
        }),
        new Scene("flowers", SMELL, f -> f.set(OlfactoryChannel.FLOWER, 0.9f)),
        new Scene("bread", SMELL, f -> f.set(OlfactoryChannel.FOOD, 0.9f)),
        new Scene("hungry", FEEL, f -> f.set(FeelChannel.HUNGER, 1f)),
        new Scene("hurt", FEEL, f -> {
            f.set(FeelChannel.HEALTH, 0.3f);
            f.pain = 0.5f;
        }),
    };

    static SensoryFrame base() {
        SensoryFrame f = new SensoryFrame();
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        for (int x = 0; x < SenseLayout.VISION_W; x++) {
            for (int y = 7; y < SenseLayout.VISION_H; y++) {
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] = 1f;
            }
        }
        // Sky above, grass below.
        paint(f, 0, SenseLayout.VISION_W, 0, 7, 0.5f, 0.7f, 1f);
        paint(f, 0, SenseLayout.VISION_W, 7, SenseLayout.VISION_H, 0.35f, 0.6f, 0.2f);
        return f;
    }

    static Supplier<SensoryFrame> in(Scene s) {
        return () -> {
            SensoryFrame f = base();
            s.setup().accept(f);
            return f;
        };
    }

    /** Lives through every scene in turn; with naming on, someone says its word now and then. */
    static void liveThrough(Brain b, int rounds, boolean naming, Rng rng) {
        for (int r = 0; r < rounds; r++) {
            for (int si = 0; si < SCENES.length; si++) {
                Scene s = SCENES[(si + r * 4) % SCENES.length];
                Supplier<SensoryFrame> frame = in(s);
                for (int t = 0; t < 150; t++) {
                    SensoryFrame f = frame.get();
                    if (naming && t > 30 && rng.nextFloat() < 0.04f) {
                        f.name(s.word());
                    }
                    b.step(f);
                }
                for (int t = 0; t < 30; t++) {
                    b.step(base());
                }
            }
        }
    }

    /** Asks a question in a scene and lets the villager answer; returns the words it said. */
    static List<String> ask(Brain b, Supplier<SensoryFrame> scene, String question) {
        List<String> heard = new ArrayList<>(WordCode.tokenize(question));
        heard.add(WordCode.TURN);
        for (String w : heard) {
            SensoryFrame f = scene.get();
            f.hear(w, false);
            f.set(SoundChannel.PLAYER_VOICE, 1f);
            b.step(f);
        }
        List<String> said = new ArrayList<>();
        String echo = null;
        for (int t = 0; t < 400; t++) {
            SensoryFrame f = scene.get();
            if (echo != null) {
                f.hear(echo, true);
                f.set(SoundChannel.VILLAGER_VOICE, 1f);
            }
            echo = b.word(b.step(f).say());
            if (echo == null) {
                continue;
            }
            if (echo.equals(WordCode.TURN)) {
                SensoryFrame end = scene.get();
                end.hear(echo, true);
                b.step(end);
                break;
            }
            said.add(echo);
        }
        return said;
    }

    static boolean mentions(List<String> said, String word) {
        String stem = word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
        for (String w : said) {
            if (w.equals(word) || w.equals(stem) || w.equals(stem + "s")) {
                return true;
            }
        }
        return false;
    }

    /** Right answers, and answers naming another scene's thing, over every scene asked twice. */
    static int[] quiz(Brain b, boolean print) {
        int right = 0, wrong = 0, seen = 0;
        for (int trial = 0; trial < 2; trial++) {
            for (Scene s : SCENES) {
                Supplier<SensoryFrame> frame = in(s);
                for (int t = 0; t < 100; t++) {
                    b.step(frame.get());
                }
                String[] phrasings = s.question().split("\\|");
                String question = phrasings[(trial + seen++) % phrasings.length];
                List<String> said = ask(b, frame, question);
                boolean ok = mentions(said, s.word());
                right += ok ? 1 : 0;
                for (Scene o : SCENES) {
                    if (o != s && mentions(said, o.word())) {
                        wrong++;
                        break;
                    }
                }
                if (print) {
                    System.out.printf("    [%s] %s -> %s%s%n", s.word(), question, String.join(" ", said), ok ? "  (right)" : "");
                }
                for (int t = 0; t < 40; t++) {
                    b.step(base());
                }
            }
        }
        return new int[] {right, wrong};
    }

    @Test
    void villagersNameWhatTheySeeAndFeel() {
        assertNotNull(EnglishBase.shared(), "the English base is missing from the resources");
        int taughtRight = 0, plainRight = 0, taughtWrong = 0, asked = 0;
        for (long seed = 1; seed <= 3; seed++) {
            Brain taught = Brain.create(Genome.defaults(), seed, 1f);
            Brain plain = Brain.create(Genome.defaults(), seed, 1f);
            liveThrough(taught, ROUNDS, true, new Rng(seed * 101));
            liveThrough(plain, ROUNDS, false, new Rng(seed * 101));
            System.out.println("  seed " + seed + ", taught:");
            int[] t = quiz(taught, true);
            System.out.println("  seed " + seed + ", never taught:");
            int[] p = quiz(plain, seed == 1);
            taughtRight += t[0];
            taughtWrong += t[1];
            plainRight += p[0];
            asked += 2 * SCENES.length;
        }
        System.out.printf("named the right thing: taught %d of %d (wrong thing %d), never taught %d of %d%n",
                taughtRight, asked, taughtWrong, plainRight, asked);
        assertTrue(taughtRight >= asked / 2, "taught villagers named the right thing only " + taughtRight + " of " + asked);
        assertTrue(taughtRight >= plainRight + asked / 4, "naming taught little: " + taughtRight + " vs " + plainRight);
        assertTrue(taughtWrong < taughtRight / 2, "too many wrong things named: " + taughtWrong);
    }

    /**
     * What was just in front of it stays in mind for a few seconds: the creature or thing walks out
     * of view, two seconds pass, and asked what it saw, the villager still names it, though by
     * then the cells that saw it have quieted.
     */
    @Test
    void villagersRememberWhatTheyJustSaw() {
        int right = 0, asked = 0;
        String[] questions = {"what did you see?", "what was that?"};
        for (long seed = 1; seed <= 2; seed++) {
            Brain b = Brain.create(Genome.defaults(), seed, 1f);
            liveThrough(b, ROUNDS, true, new Rng(seed * 101));
            for (int trial = 0; trial < 2; trial++) {
                for (Scene s : SCENES) {
                    if (!s.question().equals(SEE)) {
                        continue;
                    }
                    Supplier<SensoryFrame> frame = in(s);
                    for (int t = 0; t < 100; t++) {
                        b.step(frame.get());
                    }
                    for (int t = 0; t < 40; t++) {
                        b.step(base());
                    }
                    List<String> said = ask(b, GroundedSpeechTest::base, questions[trial]);
                    boolean ok = mentions(said, s.word());
                    right += ok ? 1 : 0;
                    asked++;
                    System.out.printf("    [%s, 2 s later] %s -> %s%s%n", s.word(), questions[trial], String.join(" ", said), ok ? "  (right)" : "");
                    for (int t = 0; t < 60; t++) {
                        b.step(base());
                    }
                }
            }
        }
        System.out.printf("named what it saw 2 s before: %d of %d%n", right, asked);
        assertTrue(right >= asked / 2, "remembered only " + right + " of " + asked);
    }

    /**
     * Nobody asks anything: with someone standing by, a villager that comes upon something speaks
     * up about it ("look, a zombie!"). It thinks the line through first and keeps quiet when the
     * line would not say what it noticed, so it does not chatter about nothing; alone, it says
     * nothing at all.
     */
    @Test
    void villagersSpeakUpAboutWhatTheyNotice() {
        int right = 0, lines = 0, scenes = 0, alone = 0;
        for (long seed = 1; seed <= 2; seed++) {
            Brain b = Brain.create(Genome.defaults(), seed, 1f);
            liveThrough(b, ROUNDS, true, new Rng(seed * 101));
            for (int trial = 0; trial < 2; trial++) {
                for (Scene s : SCENES) {
                    if (!s.question().equals(SEE)) {
                        continue;
                    }
                    boolean company = trial == 0;
                    Supplier<SensoryFrame> frame = in(s);
                    watch(b, GroundedSpeechTest::base, 100, company);
                    List<List<String>> said = watch(b, frame, 200, company);
                    scenes += company ? 1 : 0;
                    boolean ok = false;
                    for (List<String> line : said) {
                        if (!company) {
                            alone++;
                            continue;
                        }
                        lines++;
                        ok |= mentions(line, s.word());
                    }
                    right += ok ? 1 : 0;
                    System.out.printf("    [%s%s] %s%s%n", s.word(), company ? "" : ", alone", said, ok ? "  (right)" : "");
                }
            }
        }
        System.out.printf("spoke up about what came into view: %d of %d, in %d lines; alone %d lines%n", right, scenes, lines, alone);
        assertTrue(right >= scenes / 2, "spoke up about only " + right + " of " + scenes);
        assertTrue(lines <= right + scenes / 2, "too many lines about nothing: " + lines);
        assertEquals(0, alone, "talked with nobody around");
    }

    /** Lives through a scene, with or without someone near, hearing its own voice; returns its lines. */
    static List<List<String>> watch(Brain b, Supplier<SensoryFrame> scene, int ticks, boolean company) {
        List<List<String>> lines = new ArrayList<>();
        List<String> line = new ArrayList<>();
        String echo = null;
        for (int t = 0; t < ticks; t++) {
            SensoryFrame f = scene.get();
            f.company = company ? 1f : 0f;
            if (echo != null) {
                f.hear(echo, true);
                f.set(SoundChannel.VILLAGER_VOICE, 1f);
            }
            boolean speaking = b.replyUrge >= Brain.URGE_MIN;
            echo = b.word(b.step(f).say());
            if (echo == null || !speaking) {
                // A word tried out while babbling is not a line.
                continue;
            }
            if (echo.equals(WordCode.TURN)) {
                lines.add(line);
                line = new ArrayList<>();
            } else {
                line.add(echo);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line);
        }
        return lines;
    }

    /**
     * The links run the other way too: hearing a word brings up its meaning in the concept system,
     * so right after someone says "zombie" in an empty field, "zombie" is the word whose meaning
     * came up the most (measured as the rise, since some meanings, like "zombie", sit higher than
     * others in an empty field anyway).
     */
    @Test
    void hearingAWordBringsUpWhatItMeans() {
        Brain b = Brain.create(Genome.defaults(), 7, 1f);
        liveThrough(b, ROUNDS, true, new Rng(707));
        int hits = 0;
        for (Scene s : SCENES) {
            for (int t = 0; t < 60; t++) {
                b.step(base());
            }
            float[] before = boosts(b);
            SensoryFrame f = base();
            f.hear(s.word(), false);
            b.step(f);
            for (int t = 0; t < 4; t++) {
                b.step(base());
            }
            String top = mostRisen(b, before);
            System.out.printf("    heard \"%s\": meaning that came up most \"%s\"%n", s.word(), top);
            hits += s.word().equals(top) ? 1 : 0;
        }
        System.out.printf("hearing a word brought up its own meaning first for %d of %d words%n", hits, SCENES.length);
        assertTrue(hits >= SCENES.length / 2, "only " + hits);
    }

    /** Of the scene words, the one whose meaning came up the most since before. */
    static String mostRisen(Brain b, float[] before) {
        String top = null;
        float best = Float.NEGATIVE_INFINITY;
        float[] now = boosts(b);
        for (Scene s : SCENES) {
            int id = b.language.vocabulary.find(s.word());
            if (id >= 0 && id < before.length && now[id] - before[id] > best) {
                best = now[id] - before[id];
                top = s.word();
            }
        }
        return top;
    }

    /** Of the scene words, the one whose meaning the concept system holds most beyond usual. */
    static String mostPresent(Brain b) {
        String top = null;
        float best = -1f;
        float[] boost = boosts(b);
        for (Scene s : SCENES) {
            int id = b.language.vocabulary.find(s.word());
            if (id >= 0 && boost[id] > best) {
                best = boost[id];
                top = s.word();
            }
        }
        return top;
    }

    /** How much more than usual each word's meaning is present right now, leaving out recall. */
    static float[] boosts(Brain b) {
        float[] boost = new float[b.language.vocabulary.size()];
        b.links.presence(boost, boost.length, false, false);
        return boost;
    }
}
