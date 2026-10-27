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

    /**
     * A longer talk, one line after another, as a player would chat: every line gets an answer
     * of its own, not the same stock line again and again.
     */
    @Test
    void villagerKeepsUpAConversation() {
        Brain b = Brain.create(Genome.defaults(), 5, 1f);
        b.setBabbleScale(1f);
        for (int t = 0; t < 500; t++) {
            b.step(frame());
        }
        String[] talk = {"hey, how's it going?", "what did you do today?", "oh nice, was it hard work?",
            "what's your favourite food?", "i've never tried that. is it sweet?", "do you have any family?",
            "that's lovely. i have to go now.", "see you tomorrow!"};
        int[] onScript = {0, 0};
        java.util.Set<String> answers = new java.util.HashSet<>();
        int answered = 0;
        for (String line : talk) {
            List<String> said = talk(b, line, onScript);
            System.out.println("  player: " + line + "\n  villager: " + String.join(" ", said));
            answered += said.size() >= 2 ? 1 : 0;
            answers.add(String.join(" ", said));
        }
        System.out.printf("answered %d of %d lines with %d different answers%n", answered, talk.length, answers.size());
        assertTrue(answered >= talk.length - 1, "answered only " + answered);
        assertTrue(answers.size() >= talk.length - 1, "repeated itself: " + answers.size() + " different answers");
    }

    /** As the game does when the world is saved and loaded again. */
    static Brain saveAndLoad(Brain b) {
        try {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            BrainIO.write(b, new java.io.DataOutputStream(bytes));
            return BrainIO.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /** Two players talking near it: the villager hears a line answered. */
    static void overhear(Brain b, String line) {
        for (String w : WordCode.tokenize(line)) {
            SensoryFrame f = frame();
            f.hear(w, false);
            f.set(SoundChannel.PLAYER_VOICE, 1f);
            b.step(f);
        }
        SensoryFrame f = frame();
        f.hear(WordCode.TURN, false);
        f.set(SoundChannel.PLAYER_VOICE, 1f);
        b.step(f);
        // Players keep talking among themselves: the villager does not get a word in.
        b.replyUrge = 0f;
    }

    /**
     * Villagers remember talk: asked the same thing again later, even after the world was saved
     * and loaded, a villager answers as it did before, and what it overheard players answer to a question it gives as its own answer.
     */
    @Test
    void villagerRemembersWhatItSaidAndWhatItOverheard() {
        int same = 0, learned = 0, seeds = 3;
        for (long seed = 1; seed <= seeds; seed++) {
            Brain b = Brain.create(Genome.defaults(), 20 + seed, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> first = talk(b, "what do you do for work?", onScript);
            for (String line : new String[] {"nice weather today.", "i'm going to the market.", "have you seen my dog?"}) {
                talk(b, line, onScript);
            }
            b = saveAndLoad(b);
            List<String> again = talk(b, "what do you do for work?", onScript);
            System.out.println("  first: " + String.join(" ", first) + "\n  again: " + String.join(" ", again));
            same += first.equals(again) ? 1 : 0;

            overhear(b, "what is the password to the vault?");
            overhear(b, "the password is pumpkin.");
            for (String line : new String[] {"how are you?", "what's for dinner?"}) {
                talk(b, line, onScript);
            }
            List<String> asked = talk(b, "what is the password to the vault?", onScript);
            System.out.println("  password: " + String.join(" ", asked));
            learned += asked.contains("pumpkin") ? 1 : 0;
        }
        System.out.printf("answered the same again %d of %d; gave the overheard answer %d of %d%n", same, seeds, learned, seeds);
        assertTrue(same >= seeds - 1, "answers changed: " + same);
        assertTrue(learned >= seeds - 1, "did not learn from what it overheard: " + learned);
    }

    /** Told who it is by the game, a villager answers questions about itself truthfully. */
    @Test
    void villagerKnowsWhoItIs() {
        String[][] villagers = {{"Ellie Rose", "farmer"}, {"Bram", "librarian"}, {"Odo", "armorer"}};
        int right = 0, asked = 0;
        for (int i = 0; i < villagers.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 40 + i, 1f);
            b.setBabbleScale(1f);
            SelfKnowledge.tell(b, villagers[i][0], villagers[i][1], false);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            for (String[] qa : new String[][] {{"hi! what's your name?", villagers[i][0].split(" ")[0].toLowerCase()},
                    {"nice to meet you. what do you do for work?", villagers[i][1]}, {"how old are you?", "grown"}}) {
                List<String> said = talk(b, qa[0], onScript);
                System.out.println("  player: " + qa[0] + "\n  " + villagers[i][0] + ": " + String.join(" ", said));
                right += said.contains(qa[1]) ? 1 : 0;
                asked++;
            }
        }
        System.out.printf("answered about itself truthfully %d of %d%n", right, asked);
        assertTrue(right >= asked - 1, "answered about itself truthfully only " + right + " of " + asked);
    }

    /**
     * Told something earlier in a talk, a villager answers a question about it with what it was
     * told: "i have a dog named rex." ... "what's my dog's name?" "you have a dog named rex.";
     * "i'm sad today." "my cat ran away." ... "why am i sad?" "because your cat ran away."
     */
    @Test
    void villagerRemembersWhatItWasTold() {
        String[][] talks = {{"hi, i'm josh.", "how are you?", "what's my name?", "josh"},
            {"i have a dog named rex.", "he likes to play fetch.", "what's my dog's name?", "rex"},
            {"my favourite colour is green.", "what's yours?", "what's my favourite colour?", "green"},
            {"i live in the big house by the river.", "it's very old.", "where do i live?", "river"},
            {"i'm going to the mine tomorrow.", "i need iron.", "where am i going tomorrow?", "mine"},
            {"i'm sad today.", "my cat ran away.", "why am i sad?", "cat"},
            {"i'm so tired.", "i worked all night at the forge.", "why am i tired?", "forge"},
            {"my sister is called anna.", "she lives in the next village.", "what's my sister called?", "anna"},
            {"i lost my sword in the cave.", "it was made of iron.", "where did i lose my sword?", "cave"},
            {"i'm building a tower out of stone.", "it will be very tall.", "what am i building?", "tower"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 500 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (int k = 0; k < 3; k++) {
                said = talk(b, talks[i][k], onScript);
                System.out.println("  player: " + talks[i][k] + "\n  villager: " + String.join(" ", said));
            }
            right += said.contains(talks[i][3]) ? 1 : 0;
        }
        System.out.printf("remembered what it was told %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "remembered what it was told only " + right + " of " + talks.length);
    }

    /**
     * Told something new about someone or something else, with other talk in between, a villager
     * answers a question about it, even asked in other words ("what was the man wearing?" after "a
     * man in a purple coat arrived.").
     */
    @Test
    void villagerWorksOutTheMostAndHowMany() {
        String[][] talks = {{"tom is older than sam.", "sam is older than ben.", "who is the oldest?", "tom"},
            {"tom is older than sam.", "sam is older than ben.", "who is the youngest?", "ben"},
            {"the mill is bigger than the church.", "the church is bigger than the well.", "what is the biggest?", "mill"},
            {"i have five apples and i eat two.", "the weather is nice today.", "how many apples do i have?", "three"},
            {"the farmer had eight chickens.", "a fox stole three of them.", "how many chickens does the farmer have?", "five"},
            {"i picked six carrots.", "then i found four more.", "how many carrots do i have?", "ten"},
            {"today is monday.", "the weather is nice today.", "what day is tomorrow?", "tuesday"},
            {"yesterday was friday.", "i had soup for lunch.", "what day is today?", "saturday"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 760 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (int k = 0; k < 3; k++) {
                said = talk(b, talks[i][k], onScript);
                System.out.println("  player: " + talks[i][k] + "\n  villager: " + String.join(" ", said));
            }
            right += said.contains(talks[i][3]) ? 1 : 0;
        }
        System.out.printf("worked out the most and how many %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "worked out only " + right + " of " + talks.length);
    }

    @Test
    void villagerKeepsTrackOfWhatChanged() {
        // The latest of what was told answers, not the first: {told, told, asked, right, wrong}.
        String[][] talks = {{"mary went to the kitchen.", "mary went to the garden.", "where is mary?", "garden", "kitchen"},
            {"the cat was on the roof.", "then it jumped onto the wall.", "where is the cat?", "wall", "roof"},
            {"tom took the key.", "tom gave the key to anna.", "who has the key?", "anna", "tom"},
            {"the door was open.", "i closed the door.", "is the door open?", "no", "yes"},
            {"john is in the barn.", "john moved to the mill.", "where is john?", "mill", "barn"},
            {"eli is in the forest.", "ana is in the barn.", "where is eli?", "forest", "barn"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 790 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (int k = 0; k < 3; k++) {
                said = talk(b, talks[i][k], onScript);
                System.out.println("  player: " + talks[i][k] + "\n  villager: " + String.join(" ", said));
            }
            right += said.contains(talks[i][3]) && !said.contains(talks[i][4]) ? 1 : 0;
        }
        System.out.printf("kept track of what changed %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "kept track of only " + right + " of " + talks.length);
    }

    @Test
    void villagerRemembersWhatItWasToldAcrossASave() throws java.io.IOException {
        String[][] told = {{"the priest keeps the church keys in a box behind the altar.", "where are the church keys?", "altar"},
            {"rosa won the archery contest at the fair.", "who won the archery contest?", "rosa"},
            {"the old well was filled with sand years ago.", "what was the old well filled with?", "sand"},
            {"marco built his new house out of birch wood.", "what is marco's house made of?", "birch"}};
        String[] other = {"the weather is nice today.", "i had soup for lunch.", "the market was busy.", "my feet hurt from walking.",
            "how are you doing?", "i love the sound of rain.", "have you eaten yet?", "it's getting late."};
        Brain b = Brain.create(Genome.defaults(), 830, 1f);
        b.setBabbleScale(1f);
        for (int t = 0; t < 300; t++) {
            b.step(frame());
        }
        int[] onScript = {0, 0};
        for (String[] fact : told) {
            talk(b, fact[0], onScript);
            for (String line : other) {
                talk(b, line, onScript);
            }
        }
        // Saved and loaded again, as when the world is left and come back to.
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        BrainIO.write(b, new java.io.DataOutputStream(bytes));
        b = BrainIO.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
        b.setBabbleScale(1f);
        for (int t = 0; t < 300; t++) {
            b.step(frame());
        }
        onScript = new int[] {0, 0};
        int right = 0;
        for (String[] fact : told) {
            List<String> said = talk(b, fact[1], onScript);
            System.out.println("  player: " + fact[1] + "\n  villager: " + String.join(" ", said));
            right += said.contains(fact[2]) ? 1 : 0;
        }
        System.out.printf("remembered what it was told across a save %d of %d%n", right, told.length);
        assertTrue(right >= told.length - 1, "remembered only " + right + " of " + told.length);
    }

    @Test
    void villagerWorksOutFamilyTies() {
        // Told one way round, asked the other, or two ties apart: {told, told or "", asked, right}.
        String[][] talks = {{"anna is ben's mother.", "", "who is anna's son?", "ben"},
            {"rosa is ivan's wife.", "", "who is rosa's husband?", "ivan"},
            {"anna is ben's mother.", "ben is tom's father.", "who is tom's grandmother?", "anna"},
            {"pia is luca's mother.", "luca is ada's brother.", "who is ada's mother?", "pia"},
            {"the priest is my uncle.", "", "who is the priest's nephew?", "you"},
            {"nina is paul's sister.", "", "is paul nina's brother?", "yes"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 840 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (int k = 0; k < 3; k++) {
                if (!talks[i][k].isEmpty()) {
                    said = talk(b, talks[i][k], onScript);
                    System.out.println("  player: " + talks[i][k] + "\n  villager: " + String.join(" ", said));
                }
            }
            right += said.contains(talks[i][3]) ? 1 : 0;
        }
        System.out.printf("worked out family ties %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "worked out only " + right + " of " + talks.length);
    }

    @Test
    void villagerWorksOutSumsAndWhatComesNext() {
        String[][] asked = {{"what is 12 plus 9?", "21"}, {"how much is eight minus three?", "five"},
            {"what is four times five?", "twenty"}, {"what is twenty divided by four?", "five"},
            {"what comes before monday?", "sunday"}, {"which month comes after june?", "july"}};
        int right = 0;
        for (int i = 0; i < asked.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 800 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            List<String> said = talk(b, asked[i][0], new int[] {0, 0});
            System.out.println("  player: " + asked[i][0] + "\n  villager: " + String.join(" ", said));
            right += said.contains(asked[i][1]) ? 1 : 0;
        }
        System.out.printf("worked out sums and what comes next %d of %d%n", right, asked.length);
        assertTrue(right >= asked.length - 1, "worked out only " + right + " of " + asked.length);
    }

    @Test
    void villagerKnowsWhichIsBiggerWithoutBeingTold() {
        // Nothing is told: the answer comes from which way round the language area finds likelier.
        String[][] asked = {{"is a horse bigger than a mouse?", "yes"}, {"is a mouse bigger than a horse?", "no"},
            {"is a whale bigger than a dog?", "yes"}, {"is a dog bigger than a whale?", "no"},
            {"is a deer bigger than a snail?", "yes"}, {"is a snail bigger than a deer?", "no"}};
        int right = 0;
        for (int i = 0; i < asked.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 780 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            List<String> said = talk(b, asked[i][0], new int[] {0, 0});
            System.out.println("  player: " + asked[i][0] + "\n  villager: " + String.join(" ", said));
            right += said.contains(asked[i][1]) && !said.contains(asked[i][1].equals("yes") ? "no" : "yes") ? 1 : 0;
        }
        System.out.printf("knew which is bigger %d of %d%n", right, asked.length);
        assertTrue(right >= asked.length - 1, "knew only " + right + " of " + asked.length);
    }

    @Test
    void villagerDeducesFromTwoThingsItWasTold() {
        // Neither line alone answers: the answer follows from the two together.
        String[][] talks = {{"tom is older than sam.", "sam is older than ben.", "is tom older than ben?", "yes"},
            {"tom is older than sam.", "sam is older than ben.", "is ben older than tom?", "no"},
            {"all the farmers are tired today.", "pedro is a farmer.", "is pedro tired?", "yes"},
            {"if it rains, the field floods.", "it rained all night.", "did the field flood?", "yes"},
            {"the key is in the red box.", "the red box is in the barn.", "is the key in the barn?", "yes"},
            {"if the baker oversleeps, there is no bread.", "the baker overslept today.", "is there bread?", "no"},
            {"my house is north of the well.", "the well is north of the church.", "is the church north of my house?", "no"},
            {"lena arrived before marco.", "marco arrived before joko.", "did lena arrive before joko?", "yes"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 740 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (int k = 0; k < 3; k++) {
                said = talk(b, talks[i][k], onScript);
                System.out.println("  player: " + talks[i][k] + "\n  villager: " + String.join(" ", said));
            }
            right += !said.isEmpty() && said.get(0).equals(talks[i][3]) ? 1 : 0;
        }
        System.out.printf("deduced from two things it was told %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "deduced only " + right + " of " + talks.length);
    }

    @Test
    void villagerAnswersYesOrNoFromWhatItWasTold() {
        String[][] talks = {{"the baker is sick today.", "is the baker sick?"},
            {"a fox stole two chickens last night.", "did the fox steal the chickens?"},
            {"my dog is called rex.", "rex is sick today.", "is my dog sick?"},
            {"the river froze last night.", "did the river freeze?"},
            {"my cat's name is tibbs.", "tibbs caught a mouse.", "did my cat catch a mouse?"},
            {"my brother broke his leg.", "did my brother break his leg?"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 720 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (String line : talks[i]) {
                said = talk(b, line, onScript);
                System.out.println("  player: " + line + "\n  villager: " + String.join(" ", said));
            }
            right += !said.isEmpty() && said.get(0).equals("yes") ? 1 : 0;
        }
        System.out.printf("answered yes or no from what it was told %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "answered yes or no only " + right + " of " + talks.length);
    }

    @Test
    void villagerUnderstandsWhatItIsToldAboutOthers() {
        String[][] talks = {{"the red key is under the bed.", "the weather is nice today.", "where is the key?", "bed"},
            {"a fox stole two chickens from the farm last night.", "i had soup for lunch.", "what did the fox steal?", "chickens"},
            {"the blacksmith is making a sword for the captain.", "the market was busy.", "who is the sword for?", "captain"},
            {"our well dried up because it hasn't rained for weeks.", "my feet hurt from walking.", "why did the well dry up?", "rained"},
            {"a strange man in a purple coat arrived at the inn.", "the weather is nice today.", "what was the man wearing?", "coat"},
            {"the twins are seven years old.", "i had soup for lunch.", "how old are the twins?", "seven"},
            {"the cows escaped through a hole in the fence.", "the market was busy.", "how did the cows escape?", "hole"},
            {"the festival starts at sunset on friday.", "my feet hurt from walking.", "when does the festival start?", "sunset"},
            {"pedro married the tailor's daughter last summer.", "the market was busy.", "who did pedro marry?", "tailor's"},
            {"the mayor's daughter is getting married in june.", "the weather is nice today.", "when is the wedding?", "june"}};
        int right = 0;
        for (int i = 0; i < talks.length; i++) {
            Brain b = Brain.create(Genome.defaults(), 700 + i, 1f);
            b.setBabbleScale(1f);
            for (int t = 0; t < 300; t++) {
                b.step(frame());
            }
            int[] onScript = {0, 0};
            List<String> said = List.of();
            for (int k = 0; k < 3; k++) {
                said = talk(b, talks[i][k], onScript);
                System.out.println("  player: " + talks[i][k] + "\n  villager: " + String.join(" ", said));
            }
            right += said.contains(talks[i][3]) ? 1 : 0;
        }
        System.out.printf("understood what it was told about others %d of %d%n", right, talks.length);
        assertTrue(right >= talks.length - 1, "understood what it was told only " + right + " of " + talks.length);
    }
}
