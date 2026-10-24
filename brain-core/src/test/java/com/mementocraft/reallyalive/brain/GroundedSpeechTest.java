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

    static void creature(SensoryFrame f) {
        for (int x = 6; x < 10; x++) {
            for (int y = 2; y < 7; y++) {
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_CREATURE)] = 1f;
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.6f;
            }
        }
    }

    static void ahead(SensoryFrame f, int channel) {
        for (int x = 5; x < 11; x++) {
            for (int y = 0; y < 8; y++) {
                f.vision[SenseLayout.visionIndex(x, y, channel)] = 1f;
                f.vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_NEAR)] = 0.7f;
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
            creature(f);
            f.set(OlfactoryChannel.HOSTILE, 0.9f);
            f.set(SoundChannel.HOSTILE, 0.6f);
        }),
        new Scene("cow", SEE, f -> {
            creature(f);
            f.set(OlfactoryChannel.ANIMAL, 0.9f);
            f.set(SoundChannel.ANIMAL, 0.6f);
        }),
        new Scene("ladder", SEE, f -> {
            ahead(f, SenseLayout.CH_CLIMBABLE);
            ahead(f, SenseLayout.CH_SOLID);
        }),
        new Scene("fire", SEE, f -> {
            ahead(f, SenseLayout.CH_HAZARD);
            f.set(OlfactoryChannel.SMOKE, 0.9f);
        }),
        new Scene("water", SEE, f -> {
            f.set(FeelChannel.IN_WATER, 1f);
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
     * The links run the other way too: hearing a word brings up its meaning in the concept system,
     * so right after someone says "zombie" in an empty field, "zombie" is the word whose meaning is
     * most present.
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
            SensoryFrame f = base();
            f.hear(s.word(), false);
            b.step(f);
            for (int t = 0; t < 4; t++) {
                b.step(base());
            }
            String top = mostPresent(b);
            System.out.printf("    heard \"%s\": most present meaning \"%s\"%n", s.word(), top);
            hits += s.word().equals(top) ? 1 : 0;
        }
        System.out.printf("hearing a word brought up its own meaning first for %d of %d words%n", hits, SCENES.length);
        assertTrue(hits >= SCENES.length / 2, "only " + hits);
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

    static float[] boosts(Brain b) {
        float[] boost = new float[b.language.vocabulary.size()];
        b.links.presence(boost, boost.length, false);
        return boost;
    }
}
