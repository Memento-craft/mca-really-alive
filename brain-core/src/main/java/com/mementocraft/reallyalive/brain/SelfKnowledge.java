package com.mementocraft.reallyalive.brain;

import java.util.List;

/**
 * Who a villager is, as the game knows it (its name, its work, whether it is still a child), told
 * to it the way a child is told: asked this, the answer about yourself is that. Kept with its own
 * remembered exchanges, where it is weighed like any other line it could say.
 */
public final class SelfKnowledge {
    private static final String[] ASK_NAME = {"what is your name?", "what's your name?", "who are you?",
        "what are you called?", "and you are?"};
    private static final String[] ASK_WORK = {"what do you do?", "what do you do for work?", "what is your job?",
        "what's your job?", "what do you do for a living?", "where do you work?"};
    private static final String[] ASK_AGE = {"how old are you?", "are you a kid?", "are you a grown up?"};

    private SelfKnowledge() {}

    /**
     * Tells a brain who it is. The name may be null; work is a profession word ("farmer",
     * "librarian") or null for none.
     */
    public static void tell(Brain b, String name, String work, boolean child) {
        b.talk.forgetSelf();
        if (name != null && !name.isBlank()) {
            String first = name.trim().split("\\s+")[0];
            know(b, ASK_NAME, "my name is " + first + ".");
        }
        if (child) {
            know(b, ASK_WORK, "i'm just a kid. i like to play.");
            know(b, ASK_AGE, "i'm still a kid.");
        } else {
            know(b, ASK_WORK, work == null || work.isBlank() ? "i don't have a job right now."
                    : "i'm " + article(work) + " " + work + ".");
            know(b, ASK_AGE, "i'm a grown up.");
        }
    }

    private static void know(Brain b, String[] questions, String answer) {
        int[] said = ids(b, answer);
        for (String q : questions) {
            b.talk.knowSelf(ids(b, q), said);
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
