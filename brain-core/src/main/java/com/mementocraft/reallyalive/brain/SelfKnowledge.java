package com.mementocraft.reallyalive.brain;

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
    private static final String[] ASK_HARVEST = {"how much have you harvested?", "how many crops have you harvested?",
        "how much wheat have you grown?", "what do you grow?", "how is the harvest?", "what crops do you farm?"};
    private static final String[] ASK_PLAN = {"what are you doing?", "what do you want?", "what are you up to?",
        "what are you trying to do?", "what do you need?", "what's your plan?"};
    private static final String PLAN_KEYS = "doing want up trying need plan";
    private static final String HARVEST_KEYS = "harvest harvested crops crop grow grown farm wheat carrots potatoes beetroots";

    private SelfKnowledge() {}

    /**
     * Tells a brain who it is. The name may be null; work is a profession word ("farmer",
     * "librarian") or null for none.
     */
    public static void tell(Brain b, String name, String work, boolean child) {
        b.selfFullName = name;
        b.selfName = Brain.firstName(name);
        b.selfWork = work;
        b.selfChild = child;
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
            if (work != null && !work.isBlank()) {
                mine(b, "work", "i'm " + article(work) + " " + work + ".", 0f);
            }
        }
        String plan = plan(b);
        if (plan != null) {
            know(b, ASK_PLAN, PLAN_KEYS, plan);
        }
        String harvest = harvest(b);
        if (harvest != null) {
            know(b, ASK_HARVEST, HARVEST_KEYS, harvest);
            int total = b.deeds.values().stream().mapToInt(Integer::intValue).sum();
            // The more it has done, the prouder it is of it, and the more it comes to mind to tell.
            mine(b, "harvest", harvest, Math.min(1f, total / 200f));
        }
    }

    /** What it is after and what it needs first: "i want some bread . first i need some wheat .". */
    static String plan(Brain b) {
        String goal = b.goals.goal();
        if (goal == null) {
            return null;
        }
        String line = (Goals.isDeed(goal) ? "i want to " : "i want ") + thing(goal) + " .";
        String need = b.goals.because == null ? null : FELT.get(b.goals.because);
        if (need != null) {
            line = need + " . " + line;
        }
        String step = b.goals.nextStep();
        if (step != null && !step.equals(goal)) {
            line += Goals.isDeed(step) ? " first i need to " + thing(step) + " ." : " first i need " + thing(step) + " .";
        }
        return line;
    }

    /** How each need is said when it set the goal. */
    static final java.util.Map<String, String> FELT = java.util.Map.of("hunger", "i'm hungry", "tiredness", "i'm tired");

    /** A thing or deed as said: "some bread", "break some wheat", "to place some dirt". */
    private static String thing(String t) {
        for (String[] d : Goals.DEEDS) {
            if (t.startsWith(d[0])) {
                // One sleeps in a bed, but eats some bread.
                return d[1] + (d[0].equals("slept in ") ? "a " : "some ") + t.substring(d[0].length());
            }
        }
        return "some " + t;
    }

    /** What it harvested, counted, the most first: "i've harvested 212 wheat and 40 carrots.". */
    static String harvest(Brain b) {
        if (b.deeds.isEmpty()) {
            return null;
        }
        List<java.util.Map.Entry<String, Integer>> crops = new java.util.ArrayList<>(b.deeds.entrySet());
        crops.sort((x, y) -> y.getValue() - x.getValue());
        StringBuilder line = new StringBuilder("i've harvested ");
        for (int i = 0; i < Math.min(3, crops.size()); i++) {
            if (i > 0) {
                line.append(i == Math.min(3, crops.size()) - 1 ? " and " : ", ");
            }
            line.append(crops.get(i).getValue()).append(' ').append(crops.get(i).getKey());
        }
        return line.append('.').toString();
    }

    /** Something about itself it may choose to tell, kept with its memories of people. */
    private static void mine(Brain b, String key, String line, float pride) {
        if (b.selfName != null) {
            b.people.learn(b.selfName, ids(b, line), pride, key);
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
