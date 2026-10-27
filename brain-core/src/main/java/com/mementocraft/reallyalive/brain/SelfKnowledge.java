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

    private SelfKnowledge() {}

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

    private static void know(Brain b, String[] questions, String keys, String answer) {
        int[] said = ids(b, answer);
        int[] keyIds = ids(b, keys);
        for (String q : questions) {
            b.talk.knowSelf(ids(b, q), keyIds, said);
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
