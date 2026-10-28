package com.mementocraft.reallyalive.brain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Goals held in the frontal part's working memory: what a thing needs is learned from getting it,
 * a goal brings up the steps it still lacks, and getting a step that serves the goal feels good.
 */
class GoalsTest {
    private static int tick;

    private static float look(Goals g, Map<String, Integer> carried, String... did) {
        tick += 20;
        return g.sense(new HashMap<>(carried), List.of(did), tick);
    }

    @Test
    void stepsAreLearnedFromHowThingsWereGot() {
        Goals g = new Goals();
        Map<String, Integer> bag = new HashMap<>();
        look(g, bag);
        for (int round = 0; round < 3; round++) {
            for (int i = 0; i < 3; i++) {
                look(g, bag, "broke wheat");
                bag.merge("wheat", 1, Integer::sum);
                look(g, bag);
            }
            bag.put("wheat", 0);
            bag.merge("bread", 1, Integer::sum);
            look(g, bag);
            // A while of nothing in particular.
            for (int t = 0; t < 20; t++) {
                look(g, bag);
            }
        }
        System.out.println("bread needs " + g.needsOf("bread") + ", wheat needs " + g.needsOf("wheat"));
        assertEquals("wheat", g.needsOf("bread").get(0));
        assertEquals("broke wheat", g.needsOf("wheat").get(0));

        bag.clear();
        look(g, bag);
        g.want("bread", tick);
        assertEquals(0f, look(g, bag));
        System.out.println("plan for bread, next step first: " + g.stack);
        assertEquals(List.of("broke wheat", "wheat", "bread"), List.copyOf(g.stack));

        // Doing the step feels good, and the plan moves on.
        float joy = look(g, bag, "broke wheat");
        bag.put("wheat", 3);
        joy = Math.max(joy, look(g, bag));
        assertTrue(joy > 0f, "a step that serves the goal should feel good");
        assertEquals("bread", g.nextStep());
        bag.put("wheat", 0);
        bag.put("bread", 1);
        assertEquals(1f, look(g, bag), 1e-6, "getting the goal feels best");
        assertNull(g.goal());
    }

    @Test
    void hungerTakesUpWhatEasedIt() {
        Brain b = Brain.create(Genome.defaults(), 5, 0f);
        Goals g = b.goals;
        Map<String, Integer> bag = new HashMap<>(Map.of("bread", 2));
        look(g, bag);
        g.feel("hunger", 0.6f, tick);
        g.takeUp(tick);
        assertNull(g.goal(), "it knows nothing that eases hunger yet");
        // It eats a bread: one fewer, the deed done, hunger down a quarter.
        bag.put("bread", 1);
        look(g, bag, "ate bread");
        float eased = g.feel("hunger", 0.35f, tick);
        g.takeUp(tick);
        assertEquals(0.25f, eased, 1e-6);
        System.out.println("hunger needs " + g.needsOf("hunger") + ", ate bread needs " + g.needsOf("ate bread"));
        assertEquals(List.of("ate bread"), g.needsOf("hunger"));
        assertEquals(List.of("bread"), g.needsOf("ate bread"));
        g.told("bread", "wheat");

        // Hungry again with the bread gone: it sets out to eat, and finds what it lacks for it.
        bag.put("bread", 0);
        look(g, bag);
        g.feel("hunger", 0.4f, tick);
        g.takeUp(tick);
        assertNull(g.goal(), "not hungry enough yet to set out");
        look(g, bag);
        g.feel("hunger", 0.6f, tick);
        g.takeUp(tick);
        System.out.println("hungry, it is after (next step first): " + g.stack + ", because of " + g.because);
        assertEquals(List.of("wheat", "bread", "ate bread"), List.copyOf(g.stack));
        assertEquals("hunger", g.because);
        String said = SelfKnowledge.plan(b);
        System.out.println("  villager: " + said);
        assertEquals("i'm hungry . i want to eat some bread . first i need some wheat .", said);

        // Getting the bread, then eating it, ends the goal.
        bag.put("bread", 1);
        look(g, bag);
        assertEquals("ate bread", g.nextStep());
        bag.put("bread", 0);
        assertEquals(1f, look(g, bag, "ate bread"), 1e-6);
        g.feel("hunger", 0.35f, tick);
        g.takeUp(tick);
        assertNull(g.goal());
    }

    @Test
    void theStrongestNeedWinsAndFearOverrulesWork() {
        Brain b = Brain.create(Genome.defaults(), 6, 0f);
        Goals g = b.goals;
        Map<String, Integer> bag = new HashMap<>();
        look(g, bag);
        g.told("boredom", "broke wheat");
        g.told("loneliness", "talked with someone");
        g.told("fear", "got away from zombie");
        g.feel("boredom", 0.6f, tick);
        g.feel("loneliness", 0.7f, tick);
        g.feel("fear", 0f, tick);
        g.takeUp(tick);
        assertEquals("talked with someone", g.goal(), "the lonelier it is than bored, the more it wants company");
        assertEquals("i'm lonely . i want to talk with someone .", SelfKnowledge.plan(b));
        // Loneliness eased by a chat; still bored, it goes back to work.
        look(g, bag, "talked with someone");
        g.feel("loneliness", 0.5f, tick);
        g.feel("boredom", 0.6f, tick);
        g.takeUp(tick);
        assertEquals("broke wheat", g.goal());
        assertEquals("i'm bored . i want to break some wheat .", SelfKnowledge.plan(b));
        // A zombie near: fear far stronger than boredom takes over.
        look(g, bag);
        g.feel("fear", 0.95f, tick);
        g.takeUp(tick);
        assertEquals("got away from zombie", g.goal());
        assertEquals("i'm scared . i want to get away from the zombie .", SelfKnowledge.plan(b));
    }

    @Test
    void whatIsToldIsKnownAndAskingSetsTheGoal() {
        assertNotNull(EnglishBase.shared(), "the English base is missing from the resources");
        Brain b = Brain.create(Genome.defaults(), 41, 1f);
        b.setBabbleScale(0.3f);
        SelfKnowledge.tell(b, "Misu", "farmer", false);
        for (int t = 0; t < 200; t++) {
            b.step(ConversationTest.frame());
        }
        int[] onScript = {0, 0};
        ConversationTest.talk(b, "you need wheat to make bread.", onScript);
        ConversationTest.talk(b, "you get wheat by breaking wheat.", onScript);
        System.out.println("told: bread needs " + b.goals.needsOf("bread") + ", wheat needs " + b.goals.needsOf("wheat"));
        assertEquals(List.of("wheat"), b.goals.needsOf("bread"));
        ConversationTest.talk(b, "can you make some bread?", onScript);
        // It looks in its bag: empty.
        SensoryFrame f = ConversationTest.frame();
        f.carried = new HashMap<>();
        b.step(f);
        for (int t = 0; t < 40; t++) {
            b.step(ConversationTest.frame());
        }
        System.out.println("asked for bread, it is after (next step first): " + b.goals.stack);
        assertEquals("bread", b.goals.goal());
        assertEquals("broke wheat", b.goals.nextStep());
        String answer = String.join(" ", ConversationTest.talk(b, "what are you doing?", onScript));
        System.out.println("  player: what are you doing?\n  villager: " + answer);
        assertTrue(answer.contains("bread"), "it should say what it is after: " + answer);
    }
}
