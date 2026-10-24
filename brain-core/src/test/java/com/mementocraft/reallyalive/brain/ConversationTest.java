package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A player talks to a villager: after each line the villager answers in English, word by word,
 * through its spiking voice, driven by what Wernicke's language area expects next, and it stops
 * at the end of its line. Its own words are fed back to it as heard, as the game does.
 */
class ConversationTest {
    static final String[] LINES = {"hello.", "how are you?", "what do you do for work?", "i'm hungry.",
        "thank you so much.", "watch out! zombies!", "what do bees make?", "good night."};

    static SensoryFrame frame() {
        SensoryFrame f = new SensoryFrame();
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        return f;
    }

    /** Says a line to the brain, then lets it talk until it ends its turn; returns what it said. */
    static List<String> talk(Brain b, String line, int[] onScript) {
        List<String> heard = new ArrayList<>(WordCode.tokenize(line));
        heard.add(WordCode.TURN);
        for (String w : heard) {
            SensoryFrame f = frame();
            f.hear(w, false);
            f.set(SoundChannel.PLAYER_VOICE, 1f);
            b.step(f);
        }
        List<String> said = new ArrayList<>();
        String echo = null;
        for (int t = 0; t < 400; t++) {
            SensoryFrame f = frame();
            if (echo != null) {
                f.hear(echo, true);
                f.set(SoundChannel.VILLAGER_VOICE, 1f);
            }
            String expected = b.expected >= 0 ? b.word(b.expected) : null;
            echo = b.word(b.step(f).say());
            if (echo == null) {
                continue;
            }
            if (echo.equals(WordCode.TURN)) {
                SensoryFrame end = frame();
                end.hear(echo, true);
                b.step(end);
                break;
            }
            said.add(echo);
            onScript[0] += echo.equals(expected) ? 1 : 0;
            onScript[1]++;
        }
        return said;
    }

    @Test
    void villagerAnswersInEnglishAndEndsItsTurn() {
        assertNotNull(EnglishBase.shared(), "the English base is missing from the resources");
        Brain b = Brain.create(Genome.defaults(), 3, 1f);
        b.setBabbleScale(1f);
        // Settle in quietly first.
        for (int t = 0; t < 500; t++) {
            b.step(frame());
        }
        int[] onScript = {0, 0};
        int answered = 0;
        for (String line : LINES) {
            List<String> said = talk(b, line, onScript);
            System.out.println("  player: " + line + "\n  villager: " + String.join(" ", said));
            answered += said.size() >= 2 ? 1 : 0;
        }
        float fidelity = onScript[0] / (float) Math.max(1, onScript[1]);
        System.out.printf("answered %d of %d lines; %.0f%% of spoken words were the word the language area expected%n",
                answered, LINES.length, 100 * fidelity);
        assertTrue(answered >= LINES.length - 1, "answered only " + answered);
        assertTrue(fidelity > 0.8f, "voice strayed from the language area: " + fidelity);
    }
}
