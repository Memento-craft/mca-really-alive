package com.mementocraft.reallyalive.brain;

import java.util.Arrays;
import java.util.List;

/**
 * Who a villager is, as the game knows it (its name, its work, whether it is still a child), told
 * to it the way a child is told: asked this, the answer about yourself is that. Kept with its own
 * remembered exchanges, where it is weighed like any other line it could say.
 */
public final class SelfKnowledge {
    private static final String[] ASK_NAME = {"what is your name?", "what's your name?", "your name?",
        "what are you called?", "tell me your name."};
    private static final String[] ASK_WORK = {"what do you do for work?", "what is your job?", "what's your job?",
        "what do you do for a living?", "where do you work?", "what's your work?"};
    /** Words one of which must be in the question for each answer. */
    private static final String WORK_KEYS = "work job living profession";
    private static final String AGE_KEYS = "old age kid grown";
    private static final String[] ASK_AGE = {"how old are you?", "are you a kid?", "are you a grown up?"};

    private static final String[] ASK_TIME = {"what time is it?", "what time of day is it?", "is it day or night?"};
    private static final String[] ASK_WEATHER = {"how's the weather?", "what's the weather like?", "how is the weather today?",
        "what's the weather like outside?"};
    private static final String[] ASK_RAIN = {"is it raining?", "is it raining outside?", "is it going to rain?"};
    private static final String[] ASK_HOME = {"where do you live?", "where is your home?", "where are you from?"};
    private static final String[] ASK_HERE = {"where are we?", "where is this?", "what is this place?"};

    private SelfKnowledge() {}

    /**
     * Tells a brain what the world around it is like now, after {@link #tell}: the time of day
     * ("morning"), the weather ("sunny", "raining", "snowing" or "stormy") and the land it lives in
     * ("plains"), any of them null if not known. The game tells it again as they change; what it
     * was told of them before is forgotten.
     */
    public static void world(Brain b, String time, String weather, String land) {
        b.talk.forgetAround();
        if (time != null) {
            know(b, ASK_TIME, "time day night", "it's " + time + ".", false);
        }
        if (weather != null) {
            know(b, ASK_WEATHER, "weather", weather.equals("stormy") ? "there's a storm." : "it's " + weather + ".", false);
            know(b, ASK_RAIN, "rain raining", weather.equals("raining") || weather.equals("stormy") ? "yes, it's raining."
                    : weather.equals("snowing") ? "no, it's snowing." : "no, it's not raining.", false);
        }
        if (land != null) {
            know(b, ASK_HOME, "live home from", "i live in the " + land + ".");
            know(b, ASK_HERE, "we this place", "we're in the " + land + ".", false);
        }
    }

    /**
     * Tells a brain who it is. The name may be null; work is a profession word ("farmer",
     * "librarian") or null for none.
     */
    public static void tell(Brain b, String name, String work, boolean child) {
        tell(b, name, work, child, List.of());
    }

    /**
     * Tells a brain who it is and who its family are: each a family word and a first name
     * ({"mother", "rosa"}), so asked "who is your mother?" it says "rosa is my mother .", and
     * works out more from them ("who is your grandmother?").
     */
    public static void tell(Brain b, String name, String work, boolean child, List<String[]> family) {
        b.kin.clear();
        for (String[] tie : family) {
            int[] who = ids(b, tie[1].toLowerCase(java.util.Locale.ROOT));
            if (tie.length == 2 && who.length > 0 && Arrays.stream(who).allMatch(w -> w >= 0)) {
                b.kin.add(new Brain.Kin(tie[0], who));
            }
        }
        b.talk.forgetSelf();
        b.talk.you = java.util.Arrays.stream(ids(b, "you your yourself")).sorted().toArray();
        if (name != null && !name.isBlank()) {
            String first = name.trim().split("\\s+")[0];
            know(b, ASK_NAME, "name called", "my name is " + first + ".");
        }
        if (child) {
            know(b, ASK_WORK, WORK_KEYS, "i'm just a kid. i like to play.");
            know(b, ASK_AGE, AGE_KEYS, "i'm still a kid.");
        } else {
            know(b, ASK_WORK, WORK_KEYS, work == null || work.isBlank() ? "i don't have a job right now."
                    : "i'm " + article(work) + " " + work + ".");
            know(b, ASK_AGE, AGE_KEYS, "i'm a grown up.");
        }
    }

    /**
     * Tells a brain what it holds (an item's name, or null for nothing) and what it carries, each an
     * item's name and how many ({"bread", 3}), so asked "do you have any bread?" it can say.
     */
    public static void carrying(Brain b, String held, List<java.util.Map.Entry<String, Integer>> items) {
        b.carried.clear();
        for (java.util.Map.Entry<String, Integer> item : items) {
            int[] name = ids(b, item.getKey());
            if (name.length > 0 && item.getValue() > 0) {
                b.carried.add(new Brain.Carried(name, item.getValue()));
            }
        }
        b.held = held == null ? null : ids(b, held);
        b.held = b.held != null && b.held.length == 0 ? null : b.held;
        b.toldCarried = true;
    }

    /** Tells all brains what things can be carried at all, by name ("wheat seeds"). */
    public static void items(java.util.Collection<String> names) {
        for (String name : names) {
            List<String> words = WordCode.tokenize(name);
            if (!words.isEmpty()) {
                Brain.ITEMS.add(Brain.stem(words.get(words.size() - 1)));
            }
        }
    }

    private static void know(Brain b, String[] questions, String keys, String answer) {
        know(b, questions, keys, answer, true);
    }

    private static void know(Brain b, String[] questions, String keys, String answer, boolean ofItself) {
        int[] said = ids(b, answer);
        int[] keyIds = ids(b, keys);
        for (String q : questions) {
            b.talk.knowSelf(ids(b, q), keyIds, said, ofItself);
        }
    }

    private static int[] ids(Brain b, String line) {
        List<String> words = WordCode.tokenize(line);
        int[] ids = new int[words.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = b.language.vocabulary.id(words.get(i));
        }
        return ids;
    }

    private static String article(String word) {
        return "aeiou".indexOf(word.charAt(0)) >= 0 ? "an" : "a";
    }
}
