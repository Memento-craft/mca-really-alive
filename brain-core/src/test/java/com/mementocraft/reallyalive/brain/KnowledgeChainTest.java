package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Knowledge passed on from one generation to the next, only because villagers choose to tell it: a
 * farmer tells a friend what he harvested, the farmer dies, the friend tells a child about him, and
 * once the friend is gone too the child still answers who the farmer was. Nothing is written down
 * anywhere but in the brains.
 */
class KnowledgeChainTest {
    /** Villagers standing together, each hearing what the others say, as the game's hearing does. */
    static final class Village {
        final List<Brain> brains = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final List<Boolean> children = new ArrayList<>();
        final List<String> said = new ArrayList<>();
        final List<String> lines = new ArrayList<>();
        private final List<StringBuilder> line = new ArrayList<>();

        void add(Brain b, String name, boolean child) {
            brains.add(b);
            names.add(name);
            children.add(child);
            said.add(null);
            line.add(new StringBuilder());
        }

        /** Runs the village for some ticks; returns the lines said, as "name: line". */
        List<String> run(int ticks) {
            List<String> out = new ArrayList<>();
            for (int t = 0; t < ticks; t++) {
                for (int i = 0; i < brains.size(); i++) {
                    SensoryFrame f = ConversationTest.frame();
                    f.company = brains.size() > 1 ? 1f : 0f;
                    for (int j = 0; j < brains.size(); j++) {
                        if (j != i && children.get(j)) {
                            f.youngCompany = 1f;
                        }
                    }
                    for (int j = 0; j < brains.size(); j++) {
                        if (said.get(j) != null) {
                            f.hear(said.get(j), i == j, names.get(j));
                            f.set(SoundChannel.VILLAGER_VOICE, 1f);
                        }
                    }
                    String w = brains.get(i).word(brains.get(i).step(f).say());
                    said.set(i, w);
                    if (w != null) {
                        if (w.equals(WordCode.TURN)) {
                            if (line.get(i).length() > 0) {
                                out.add(names.get(i) + ": " + line.get(i));
                            }
                            line.get(i).setLength(0);
                        } else {
                            line.get(i).append(w).append(' ');
                        }
                    }
                }
            }
            lines.addAll(out);
            return out;
        }

        void remove(Brain b) {
            int i = brains.indexOf(b);
            brains.remove(i);
            names.remove(i);
            children.remove(i);
            said.remove(i);
            line.remove(i);
        }
    }

    static Brain villager(long seed, String name, String work, boolean child) {
        Brain b = Brain.create(Genome.defaults(), seed, child ? 0.3f : 1f);
        b.setBabbleScale(0.3f);
        SelfKnowledge.tell(b, name, work, child);
        return b;
    }

    static String memories(Brain b, String person) {
        StringBuilder sb = new StringBuilder();
        for (Recollections.Memory m : b.people.memories) {
            if (m.person.equals(person)) {
                sb.append(String.format("    [%.2f] ", m.strength));
                for (int w : m.line) {
                    sb.append(b.word(w)).append(' ');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void aFarmersHarvestOutlivesHimAndTheOneWhoToldIt() {
        assertNotNull(EnglishBase.shared(), "the English base is missing from the resources");
        Brain misu = villager(11, "Misu", "farmer", false);
        for (int i = 0; i < 150; i++) {
            misu.deeds.merge("wheat", 1, Integer::sum);
        }
        SelfKnowledge.tell(misu, "Misu", "farmer", false);
        Brain tomas = villager(12, "Tomas", "mason", false);
        // Keen tellers, so the test needn't wait days of game time.
        misu.tellRate = tomas.tellRate = 0.3f;

        Village village = new Village();
        village.add(misu, "Misu", false);
        village.add(tomas, "Tomas", false);
        village.run(300);
        for (int round = 0; round < 12 && !tomas.people.knows("misu"); round++) {
            village.run(1500);
        }
        System.out.println(String.join("\n", village.lines));
        System.out.println("  tomas remembers of misu:\n" + memories(tomas, "misu"));
        assertTrue(tomas.people.knows("misu"), "the farmer never told his friend anything about himself");

        // Misu dies in front of Tomas.
        village.remove(misu);
        SensoryFrame f = ConversationTest.frame();
        f.died.add("Misu");
        tomas.step(f);
        assertTrue(tomas.grief > 0.5f, "losing someone it knew should hurt");
        System.out.println("  after the death:\n" + memories(tomas, "misu"));

        // Tomas spends time with a child.
        Brain lia = villager(13, "Lia", null, true);
        village.add(lia, "Lia", true);
        village.lines.clear();
        for (int round = 0; round < 30 && !knowsHarvest(lia); round++) {
            village.run(1500);
        }
        System.out.println(String.join("\n", village.lines));
        System.out.println("  lia remembers of misu:\n" + memories(lia, "misu"));
        assertTrue(lia.people.knows("misu"), "the friend never told the child about the dead farmer");
        assertTrue(knowsHarvest(lia), "the friend never told the child what the farmer harvested");

        // Tomas is gone too; Lia is asked.
        village.remove(tomas);
        int[] onScript = {0, 0};
        String who = String.join(" ", ConversationTest.talk(lia, "what happened to misu?", onScript));
        System.out.println("  player: what happened to misu?\n  lia: " + who);
        String much = String.join(" ", ConversationTest.talk(lia, "how much wheat did misu harvest?", onScript));
        System.out.println("  player: how much wheat did misu harvest?\n  lia: " + much);
        assertTrue(who.contains("died"), "the child should remember the death: " + who);
        assertTrue(much.contains("150"), "the child should know the farmer's count: " + much);
    }

    static boolean knowsHarvest(Brain b) {
        for (Recollections.Memory m : b.people.memories) {
            if (m.person.equals("misu") && java.util.Arrays.stream(m.line).anyMatch(w -> "150".equals(b.word(w)))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void tellingIsLearnedFromBeingListenedTo() {
        assertNotNull(EnglishBase.shared(), "the English base is missing from the resources");
        int[] told = new int[2];
        float[] value = new float[2];
        for (int heard = 0; heard < 2; heard++) {
            Brain b = villager(21, "Misu", "farmer", false);
            for (int i = 0; i < 300; i++) {
                b.deeds.merge("wheat", 1, Integer::sum);
            }
            SelfKnowledge.tell(b, "Misu", "farmer", false);
            b.tellRate = 0.3f;
            List<String> reply = new ArrayList<>();
            String echo = null;
            int lastReply = -10_000;
            for (int t = 0; t < 15_000; t++) {
                SensoryFrame f = ConversationTest.frame();
                f.company = 1f;
                if (echo != null) {
                    f.hear(echo, true);
                    f.set(SoundChannel.VILLAGER_VOICE, 1f);
                }
                if (!reply.isEmpty()) {
                    f.hear(reply.remove(0), false, "Steve");
                    f.set(SoundChannel.PLAYER_VOICE, 1f);
                }
                echo = b.word(b.step(f).say());
                if (WordCode.TURN.equals(echo)) {
                    told[heard]++;
                    // Someone answers it, but not its answers to that answer, or they'd talk forever.
                    if (heard == 1 && t - lastReply > 1000) {
                        lastReply = t;
                        reply.addAll(WordCode.tokenize("oh really? tell me more."));
                        reply.add(WordCode.TURN);
                    }
                }
            }
            value[heard] = b.tellValue;
        }
        System.out.printf("ignored: told %d lines, worth %.2f; listened to: told %d lines, worth %.2f%n",
                told[0], value[0], told[1], value[1]);
        assertTrue(value[1] > value[0] + 0.2f, "being listened to should make telling worth more");
    }

    @Test
    void retoldMemoriesLastAndUnrepeatedOnesFade() {
        Vocabulary v = new Vocabulary();
        Recollections r = new Recollections();
        int[] line = {v.id("misu"), v.id("was"), v.id("a"), v.id("farmer"), v.id(".")};
        int[] other = {v.id("misu"), v.id("liked"), v.id("bread"), v.id(".")};
        r.learn("misu", line, 0f, null);
        r.learn("misu", other, 0f, null);
        for (long t = 0; t <= 10 * 24_000L; t += 1200) {
            r.fade(t);
            if (t % 48_000 == 0) {
                r.learn("misu", line, 0f, null);
            }
        }
        assertEquals(1, r.memories.size(), "the line never told again should be forgotten in ten days");
        assertArrayEquals(line, r.memories.get(0).line);
    }

    @Test
    void whatSomeoneSaysOfThemselvesIsToldAboutThem() {
        Vocabulary v = new Vocabulary();
        String[][] cases = {
            {"i'm a farmer.", "misu is a farmer ."},
            {"i've harvested 150 wheat.", "misu has harvested 150 wheat ."},
            {"i grow wheat by the river.", "misu grows wheat by the river ."},
            {"my bread is the best.", "misu's bread is the best ."},
        };
        for (String[] c : cases) {
            int[] line = WordCode.tokenize(c[0]).stream().mapToInt(v::id).toArray();
            int[] about = Recollections.aboutSpeaker(line, "misu", v);
            StringBuilder sb = new StringBuilder();
            for (int w : about) {
                sb.append(sb.isEmpty() ? "" : " ").append(v.word(w));
            }
            assertEquals(c[1], sb.toString());
            int[] past = Recollections.pastTense(about, v);
            System.out.println("  " + c[0] + " -> " + sb + " -> " + String.join(" ",
                    java.util.Arrays.stream(past).mapToObj(v::word).toList()));
        }
        assertNull(Recollections.aboutSpeaker(WordCode.tokenize("are you a farmer?").stream().mapToInt(v::id).toArray(), "misu", v));
    }

    /**
     * Alone with a book and quill, a villager may write down what it remembers; a child who later
     * reads the page knows it too, though the writer never spoke to her.
     */
    @Test
    void whatIsWrittenDownIsKnownToWhoeverReadsIt() {
        assertNotNull(EnglishBase.shared(), "the English base is missing from the resources");
        Brain tomas = villager(31, "Tomas", "mason", false);
        SensoryFrame seen = ConversationTest.frame();
        seen.died.add("Misu");
        tomas.step(seen);
        tomas.tellRate = 0.3f;
        List<String> page = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String wrote = null;
        int aloud = 0;
        for (int t = 0; t < 6000 && page.isEmpty(); t++) {
            SensoryFrame f = ConversationTest.frame();
            f.canWrite = true;
            if (wrote != null) {
                f.hear(wrote, true);
            }
            MotorCommand keys = tomas.step(f);
            wrote = tomas.word(keys.say());
            if (wrote == null) {
                continue;
            }
            if (!keys.use()) {
                aloud++;
                wrote = null;
            } else if (wrote.equals(WordCode.TURN)) {
                page.add(line.toString().trim());
                line.setLength(0);
            } else {
                line.append(wrote).append(' ');
            }
        }
        System.out.println("  tomas wrote: " + page + " (words said aloud meanwhile: " + aloud + ")");
        assertFalse(page.isEmpty(), "alone with a book, it never wrote anything down");

        Brain lia = villager(32, "Lia", null, true);
        for (int t = 0; t < 200; t++) {
            lia.step(ConversationTest.frame());
        }
        for (String written : page) {
            List<String> words = new ArrayList<>(WordCode.tokenize(written));
            words.add(WordCode.TURN);
            for (String w : words) {
                for (int k = 0; k < 3; k++) {
                    SensoryFrame f = ConversationTest.frame();
                    if (k == 0) {
                        f.read(w, "Tomas");
                    }
                    lia.step(f);
                }
            }
        }
        System.out.println("  lia read of misu:\n" + memories(lia, "misu"));
        assertTrue(lia.people.knows("misu"), "reading the page taught her nothing");
        int[] onScript = {0, 0};
        String answer = String.join(" ", ConversationTest.talk(lia, "what happened to misu?", onScript));
        System.out.println("  player: what happened to misu?\n  lia: " + answer);
        assertTrue(answer.contains("died"), "she should answer from what she read: " + answer);
    }
}
