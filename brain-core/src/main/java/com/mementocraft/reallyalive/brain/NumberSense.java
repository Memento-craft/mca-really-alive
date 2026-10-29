package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The frontal part's sense of numbers told in a talk: ages, heights and other measures, counts
 * of things someone has, and prices, and what follows from them ("lisa is 8." "her brother is 3
 * years older.": lisa's brother is 11; "i have 3 cows and 4 pigs.": seven animals; "kai has 5
 * apples." "mia has twice as many.": ten). Works on the words of lines told lately, latest last,
 * and answers only what it had to work out; a count told as it is ("i have three apples .") is
 * left to plain recall.
 */
final class NumberSense {
    private static final List<String> WORDS = List.of("zero", "one", "two", "three", "four", "five", "six", "seven",
            "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen",
            "eighteen", "nineteen", "twenty");
    private static final Map<String, Integer> TENS = Map.of("thirty", 30, "forty", 40, "fifty", 50, "sixty", 60,
            "seventy", 70, "eighty", 80, "ninety", 90, "hundred", 100);
    private static final Set<String> ARTICLES = Set.of("the", "a", "an");
    /** Measures and which way their comparatives go. */
    private static final Map<String, String> MORE = Map.ofEntries(Map.entry("older", "old"), Map.entry("taller", "tall"),
            Map.entry("higher", "high"), Map.entry("longer", "long"), Map.entry("deeper", "deep"),
            Map.entry("bigger", "big"), Map.entry("heavier", "heavy"), Map.entry("wider", "wide"));
    private static final Map<String, String> LESS = Map.ofEntries(Map.entry("younger", "old"), Map.entry("shorter", "tall"),
            Map.entry("lower", "high"), Map.entry("smaller", "big"), Map.entry("lighter", "heavy"),
            Map.entry("narrower", "wide"));
    private static final Set<String> MEASURES = Set.of("old", "tall", "high", "long", "deep", "big", "heavy", "wide");
    private static final Set<String> PRONOUNS = Set.of("he", "she", "it", "they", "his", "her", "its", "their");
    private static final Set<String> HAVING = Set.of("have", "has", "had");
    /** Doings after which someone has what they did it to ("we baked 20 cookies ."). */
    private static final Set<String> GETTING = Set.of("baked", "bought", "made", "caught", "found", "picked", "collected",
            "got", "gathered", "grew", "built", "planted", "sowed", "hatched");

    /** Measures of things: thing -> measure -> value. */
    private final Map<String, Map<String, Integer>> measured = new HashMap<>();
    /** Who is more of a measure than whom, without numbers: {more, less, measure}. */
    private final List<String[]> above = new ArrayList<>();
    /** What someone has: who -> thing -> count, in the order told. */
    private final Map<String, LinkedHashMap<String, Integer>> counts = new HashMap<>();
    /** Prices: thing -> {amount, unit}. */
    private final Map<String, Object[]> prices = new HashMap<>();
    /** Which counts were worked out rather than told. */
    private final Set<String> derived = new java.util.HashSet<>();
    /** Doings that leave fewer of what was had ("shot", "ate", "sold"), by their plain forms. */
    private static final Set<String> USED = Set.of("shot", "shoot", "eat", "sell", "lose", "lost", "give", "gave", "spend",
            "spent", "use", "us", "break", "drop", "dropp", "burn", "throw", "threw");
    /** "three of them did not grow .": {who had them, thing, how many, whether not, doing}. */
    private final List<Object[]> ofThem = new ArrayList<>();
    private static final Map<String, String> PAST = Map.of("grew", "grow", "ate", "eat", "came", "come", "ran", "run",
            "broke", "break", "flew", "fly", "fell", "fall", "froze", "freeze", "sold", "sell", "won", "win");

    /** "grew" and "grow", "hatched" and "hatch": the doing without its ending. */
    private static String plainVerb(String v) {
        if (PAST.containsKey(v)) {
            return PAST.get(v);
        }
        for (String end : new String[] {"ed", "d", "s"}) {
            if (v.endsWith(end) && v.length() > end.length() + 2) {
                return v.substring(0, v.length() - end.length());
            }
        }
        return v;
    }

    /** Counts told against another's, worked out once that is known: {who, thing, times, whose}. */
    private final List<Object[]> pending = new ArrayList<>();
    private String lastSubject;
    private String lastThing;
    private String lastHaver;

    /** What follows from the lines for the question, as words to say; null if nothing had to be worked out. */
    static List<String> answer(List<List<String>> told, List<String> question) {
        NumberSense sense = new NumberSense();
        for (List<String> line : told) {
            sense.read(line);
        }
        for (int round = 0; round < 3; round++) {
            for (Object[] p : sense.pending) {
                Integer of = sense.counts.getOrDefault((String) p[3], new LinkedHashMap<>()).get((String) p[1]);
                if (of != null && !sense.counts.getOrDefault((String) p[0], new LinkedHashMap<>()).containsKey((String) p[1])) {
                    sense.count((String) p[0], (String) p[1], (int) Math.round(of * (double) p[2]));
                    sense.derived.add(p[0] + " " + p[1]);
                }
            }
        }
        return sense.ask(question);
    }

    static int number(String w) {
        int n = WORDS.indexOf(w);
        if (n >= 0) {
            return n;
        }
        if (TENS.containsKey(w)) {
            return TENS.get(w);
        }
        if (!w.isEmpty() && w.length() < 6 && w.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(w);
        }
        return -1;
    }

    static String say(int n) {
        return n >= 0 && n < WORDS.size() ? WORDS.get(n) : Integer.toString(n);
    }

    private static List<String> words(List<String> line) {
        List<String> w = new ArrayList<>(line);
        while (!w.isEmpty() && Set.of(".", "!", "?").contains(w.get(w.size() - 1))) {
            w.remove(w.size() - 1);
        }
        return w;
    }

    /** "the tower" "tower"; "her brother" "lisa's brother" after a line about lisa. */
    private String thing(List<String> words) {
        List<String> w = new ArrayList<>();
        for (String s : words) {
            if (!ARTICLES.contains(s)) {
                w.add(s);
            }
        }
        if (w.size() == 2 && Set.of("his", "her", "their").contains(w.get(0)) && lastSubject != null) {
            return lastSubject + "'s " + w.get(1);
        }
        if (w.size() == 1 && PRONOUNS.contains(w.get(0)) && lastSubject != null) {
            return lastSubject;
        }
        return String.join(" ", w);
    }

    private void read(List<String> line) {
        List<String> w = words(line);
        if (w.size() < 3 || w.get(0).equals("if") || w.get(0).equals("when")) {
            return;
        }
        int is = w.indexOf("is");
        if (is < 0) {
            is = w.indexOf("are");
        }
        int has = -1;
        for (int i = 1; i < w.size(); i++) {
            if (HAVING.contains(w.get(i)) || GETTING.contains(w.get(i)) && i + 1 < w.size() && number(w.get(i + 1)) >= 0) {
                has = i;
                break;
            }
        }
        if (is > 0 && (has < 0 || is < has)) {
            readMeasure(w, is);
        } else if (has > 0) {
            readCounts(w, has);
        } else {
            readOther(w);
        }
    }

    /** "lisa is 8 .", "the wall is 6 blocks high .", "her brother is 3 years older .", "anna is older than tom ." */
    private void readMeasure(List<String> w, int is) {
        List<String> rest = w.subList(is + 1, w.size());
        // "it is eight now ." tells the time, whoever was told of before.
        boolean time = is == 1 && w.get(0).equals("it") && !rest.isEmpty() && (hour(rest.get(0)) >= 0 || SEASONS.contains(rest.get(0)))
                && rest.contains("now");
        String who = time ? "it" : thing(w.subList(0, is));
        if (who.equals("there")) {
            // "there are 3 houses ."
            if (rest.size() >= 2 && number(rest.get(0)) >= 0) {
                count("there", rest.get(1), number(rest.get(0)));
            }
            return;
        }
        if (who.equals("it")) {
            // "it is eight now .", "it is winter now ."
            if (!rest.isEmpty() && hour(rest.get(0)) >= 0) {
                hourNow = hour(rest.get(0));
            } else if (!rest.isEmpty() && SEASONS.contains(rest.get(0))) {
                seasonNow = rest.get(0);
            }
            return;
        }
        if (rest.size() == 2 && rest.get(0).equals("at") && hour(rest.get(1)) >= 0) {
            // "the market is at noon ."
            startsAt.put(who, hour(rest.get(1)));
            return;
        }
        int n = rest.isEmpty() ? -1 : number(rest.get(0));
        String measure = null;
        int by = 0;
        String than = null;
        for (int i = 0; i < rest.size(); i++) {
            String r = rest.get(i);
            if (MORE.containsKey(r) || LESS.containsKey(r)) {
                measure = MORE.containsKey(r) ? MORE.get(r) : LESS.get(r);
                by = MORE.containsKey(r) ? 1 : -1;
                if (i + 1 < rest.size() && rest.get(i + 1).equals("than")) {
                    than = thing(rest.subList(i + 2, rest.size()));
                }
                break;
            }
            if (MEASURES.contains(r)) {
                measure = r;
                if (i > 1 && number(rest.get(i - 2)) >= 0 && !rest.get(i - 1).equals("years")) {
                    units.put(r, rest.get(i - 1));
                }
            }
        }
        if (by != 0) {
            String other = than != null ? than : lastSubject;
            Integer base = other == null ? null : value(other, measure);
            if (n >= 0 && base != null) {
                measure(who, measure, base + by * n);
                derivedMeasures.add(who + " " + measure);
            } else if (n < 0 && other != null) {
                above.add(by > 0 ? new String[] {who, other, measure} : new String[] {other, who, measure});
            }
        } else if (n >= 0 && (rest.size() == 1 || measure != null || rest.contains("years"))) {
            measure(who, measure == null ? "old" : measure, n);
        }
        lastSubject = who;
    }

    /** "i have 3 cows and 4 pigs .", "mia has twice as many .", "each house has 2 doors ." */
    private void readCounts(List<String> w, int has) {
        boolean each = w.get(0).equals("each");
        String who = thing(w.subList(each ? 1 : 0, has));
        List<String> rest = w.subList(has + 1, w.size());
        int as = rest.lastIndexOf("as");
        if (rest.size() >= 5 && Set.of("twice", "half").contains(rest.get(0)) && rest.contains("many") && as > 2
                && as + 1 < rest.size()) {
            // "tom has twice as many sheep as ben .", told before or after what ben has.
            int many = rest.indexOf("many");
            String what = many + 1 < as ? rest.get(many + 1) : lastThing;
            if (what != null) {
                pending.add(new Object[] {who, what, rest.get(0).equals("twice") ? 2.0 : 0.5, thing(rest.subList(as + 1, rest.size()))});
                lastHaver = who;
                lastThing = what;
                return;
            }
        }
        if (rest.size() >= 3 && Set.of("twice", "half").contains(rest.get(0)) && rest.contains("many")
                && lastHaver != null && lastThing != null) {
            Integer before = counts.getOrDefault(lastHaver, new LinkedHashMap<>()).get(lastThing);
            if (before != null) {
                count(who, lastThing, rest.get(0).equals("twice") ? before * 2 : before / 2);
                derived.add(who + " " + lastThing);
            }
            lastHaver = who;
            return;
        }
        for (int i = 0; i + 1 < rest.size(); i++) {
            int n = number(rest.get(i));
            if (n >= 0 && !rest.get(i + 1).equals("years")) {
                String what = rest.get(i + 1);
                if (each && counts.containsKey("there")) {
                    // "there are 3 houses ." "each house has 2 doors .": six doors.
                    Integer many = null;
                    for (Map.Entry<String, Integer> e : counts.get("there").entrySet()) {
                        if (who.startsWith(e.getKey()) || e.getKey().startsWith(who)) {
                            many = e.getValue();
                        }
                    }
                    if (many != null) {
                        count("there", what, many * n);
                        derived.add("there " + what);
                    }
                } else {
                    count(who, what, n);
                }
            }
        }
        lastHaver = who;
        lastSubject = who;
    }

    /** "we used half of them .", "a loaf costs two emeralds .", "the shop opens at nine .", "the trip takes three days ." */
    private void readOther(List<String> w) {
        int at = w.indexOf("at");
        if (at >= 2 && at == w.size() - 2 && Set.of("opens", "starts", "begins", "open", "start").contains(w.get(at - 1))
                && hour(w.get(at + 1)) >= 0) {
            startsAt.put(thing(w.subList(0, at - 1)), hour(w.get(at + 1)));
            startVerb.put(thing(w.subList(0, at - 1)), w.get(at - 1).startsWith("open") ? "opens" : "starts");
            return;
        }
        int takes = w.indexOf("takes");
        if (takes > 0 && takes + 2 < w.size() && number(w.get(takes + 1)) >= 0 && w.get(takes + 2).startsWith("day")) {
            tripDays = number(w.get(takes + 1));
            return;
        }
        int on = w.indexOf("on");
        if (on > 0 && on + 1 < w.size() && DAYS.contains(w.get(on + 1)) && Set.of("left", "set").contains(w.get(on - 1))) {
            leftOn = w.get(on + 1);
            return;
        }
        int costs = Math.max(w.indexOf("costs"), w.indexOf("cost"));
        if (costs > 0 && costs + 2 < w.size() + 1 && costs + 1 < w.size() && number(w.get(costs + 1)) >= 0) {
            String what = thing(w.subList(0, costs));
            String unit = costs + 2 < w.size() ? w.get(costs + 2) : "";
            prices.put(what, new Object[] {number(w.get(costs + 1)), unit});
            return;
        }
        if (w.size() >= 4 && number(w.get(0)) >= 0 && w.get(1).equals("of") && w.get(2).equals("them") && lastThing != null) {
            // "three of them did not grow .", "two of them hatched ."
            boolean not = w.size() >= 6 && w.get(3).equals("did") && w.get(4).equals("not") || w.get(3).equals("didn't");
            String verb = w.get(w.size() - 1);
            ofThem.add(new Object[] {lastHaver, lastThing, number(w.get(0)), not, plainVerb(verb)});
            return;
        }
        int ofIt = w.indexOf("of");
        if (ofIt >= 3 && ofIt + 1 < w.size() && w.get(ofIt + 1).equals("them") && number(w.get(ofIt - 1)) >= 0
                && USED.contains(plainVerb(w.get(ofIt - 2))) && lastHaver != null && lastThing != null) {
            // "i shot five of them .": five fewer.
            Integer before = counts.getOrDefault(lastHaver, new LinkedHashMap<>()).get(lastThing);
            if (before != null && before >= number(w.get(ofIt - 1))) {
                count(lastHaver, lastThing, before - number(w.get(ofIt - 1)));
                derived.add(lastHaver + " " + lastThing);
            }
            return;
        }
        if (w.contains("half") && w.contains("of") && lastHaver != null && lastThing != null) {
            Integer before = counts.getOrDefault(lastHaver, new LinkedHashMap<>()).get(lastThing);
            if (before != null) {
                count(lastHaver, lastThing, before - before / 2);
                derived.add(lastHaver + " " + lastThing);
            }
        }
    }

    private void count(String who, String what, int n) {
        counts.computeIfAbsent(who, k -> new LinkedHashMap<>()).put(what, n);
        lastThing = what;
    }

    private void measure(String who, String measure, int n) {
        measured.computeIfAbsent(who, k -> new HashMap<>()).put(measure, n);
    }

    private Integer value(String who, String measure) {
        Map<String, Integer> m = measured.get(who);
        return m == null ? null : m.get(measure);
    }

    /** The least a thing is known to be of a measure, from "anna is older than tom ." and tom's age. */
    private Integer atLeast(String who, String measure, int depth) {
        Integer v = value(who, measure);
        if (v != null || depth > 4) {
            return v;
        }
        for (String[] a : above) {
            if (a[0].equals(who) && a[2].equals(measure)) {
                Integer under = atLeast(a[1], measure, depth + 1);
                if (under != null) {
                    return under + 1;
                }
            }
        }
        return null;
    }

    private List<String> ask(List<String> question) {
        List<String> q = words(question);
        if (q.size() < 3) {
            return null;
        }
        String first = q.get(0), second = q.get(1);
        if (first.equals("how") && MEASURES.contains(second) && q.size() >= 4) {
            return howMeasure(q, second);
        }
        if (first.equals("how") && second.equals("many") && q.size() >= 4) {
            return howMany(q);
        }
        if (first.equals("how") && second.equals("much") && q.size() >= 5) {
            return howMuch(q);
        }
        if (first.equals("is") && q.size() == 3 && q.get(1).equals("it") && SEASONS.contains(q.get(2)) && seasonNow != null) {
            // "is it spring?" "no , it is winter ."
            return seasonNow.equals(q.get(2)) ? List.of("yes", ",", "it", "is", seasonNow, ".")
                : List.of("no", ",", "it", "is", seasonNow, ".");
        }
        if ((first.equals("is") && q.get(q.size() - 1).equals("open") || first.equals("has") && q.get(q.size() - 1).equals("started"))
                && hourNow >= 0) {
            // "is the shop open?" after "the shop opens at nine ." "it is eight now ."
            Integer from = startsAt.get(thing(q.subList(1, q.size() - 1)));
            if (from != null) {
                boolean so = hourNow >= from;
                List<String> said = new ArrayList<>(List.of(so ? "yes" : "no", ","));
                said.addAll(List.of(thing(q.subList(1, q.size() - 1)).split(" ")));
                String verb = startVerb.getOrDefault(thing(q.subList(1, q.size() - 1)), "starts");
                said.addAll(List.of(so ? (verb.equals("opens") ? "opened" : "started") : verb, "at", say(from), "."));
                said.add(1 + said.indexOf(","), "the");
                return said;
            }
        }
        if (first.equals("when") && tripDays >= 0 && leftOn != null && q.contains("arrive")) {
            // "the trip takes three days ." "we left on monday .": thursday.
            return List.of("on", DAYS.get((DAYS.indexOf(leftOn) + tripDays) % 7), ".");
        }
        if (Set.of("is", "are", "was").contains(first)) {
            return isMore(q);
        }
        if (Set.of("who", "which").contains(first) && Set.of("is", "are").contains(second) && q.size() == 3) {
            return whoMore(q.get(2));
        }
        return null;
    }

    /** "how old is lisa's brother?", "how old will ben be in two years?", "how high is the tower?" */
    private List<String> howMeasure(List<String> q, String measure) {
        int after = 0;
        List<String> who;
        if (q.get(2).equals("will") && q.indexOf("be") > 3) {
            who = q.subList(3, q.indexOf("be"));
            int in = q.indexOf("in");
            after = in > 0 && in + 1 < q.size() ? number(q.get(in + 1)) : -1;
            if (after < 0) {
                return null;
            }
        } else if (Set.of("is", "are").contains(q.get(2))) {
            who = q.subList(3, q.size());
        } else {
            return null;
        }
        String thing = thing(who);
        Integer v = value(thing, measure);
        if (v == null || after == 0 && !derivedMeasure(thing, measure)) {
            return null;
        }
        List<String> said = new ArrayList<>(who);
        said.add(after > 0 ? "will" : "is");
        if (after > 0) {
            said.add("be");
        }
        said.add(say(v + after));
        if (!measure.equals("old")) {
            if (units.containsKey(measure)) {
                said.add(units.get(measure));
            }
            said.add(measure);
        }
        said.add(".");
        return said;
    }

    /** Whether a measure was worked out from another, not told as it is. */
    private boolean derivedMeasure(String who, String measure) {
        return derivedMeasures.contains(who + " " + measure);
    }

    private final Set<String> derivedMeasures = new java.util.HashSet<>();
    /** The unit a measure was told in ("blocks" high). */
    private final Map<String, String> units = new HashMap<>();
    /** The hour or season it is now, when told ("it is eight now ."). */
    private int hourNow = -1;
    private String seasonNow;
    /** When things start: thing -> hour ("the shop opens at nine ."). */
    private final Map<String, Integer> startsAt = new HashMap<>();
    private final Map<String, String> startVerb = new HashMap<>();
    /** How long a trip takes in days, and the day it began. */
    private int tripDays = -1;
    private String leftOn;
    private static final List<String> DAYS = List.of("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday");
    private static final Set<String> SEASONS = Set.of("spring", "summer", "autumn", "fall", "winter");

    private static int hour(String w) {
        return w.equals("noon") || w.equals("midday") ? 12 : w.equals("midnight") ? 0 : number(w);
    }

    /** "how many animals do i have?", "how many apples does mia have?", "how many doors are there?", "how many eggs are left?" */
    private List<String> howMany(List<String> q) {
        if (q.size() >= 6 && Set.of("left", "now").contains(q.get(q.size() - 1)) && HAVING.contains(q.get(q.size() - 2))) {
            // "how many arrows do i have left?"
            q = q.subList(0, q.size() - 1);
        }
        String what = q.get(2);
        String who;
        if (q.size() == 4 && !HAVING.contains(q.get(3))) {
            // "how many seeds grew?" after "i planted ten seeds ." "three of them did not grow ."
            for (Object[] o : ofThem) {
                Integer all = counts.getOrDefault((String) o[0], new LinkedHashMap<>()).get((String) o[1]);
                if (o[1].equals(what) && o[4].equals(plainVerb(q.get(3))) && all != null) {
                    int n = (boolean) o[3] ? all - (int) o[2] : (int) o[2];
                    return List.of(say(n), what, q.get(3), ".");
                }
            }
        }
        if (q.size() == 7 && Set.of("do").contains(q.get(3)) && Set.of("they", "we", "you").contains(q.get(4))
                && HAVING.contains(q.get(5)) && q.get(6).equals("together")) {
            // "how many sheep do they have together?": all the counts of it told.
            int all = 0, many = 0;
            for (LinkedHashMap<String, Integer> c : counts.values()) {
                if (c.containsKey(what)) {
                    all += c.get(what);
                    many++;
                }
            }
            return many < 2 ? null : List.of(q.get(4).equals("we") ? "we" : q.get(4).equals("you") ? "we" : "they", "have", say(all), what,
                    "together", ".");
        }
        if (q.size() >= 5 && Set.of("do", "does").contains(q.get(3)) && HAVING.contains(q.get(q.size() - 1))) {
            who = thing(q.subList(4, q.size() - 1));
        } else if (q.size() == 5 && q.get(3).equals("are") && Set.of("there", "left").contains(q.get(4))) {
            who = q.get(4).equals("there") ? "there" : null;
        } else {
            return null;
        }
        if (who == null) {
            // "how many eggs are left?": whoever had them.
            for (Map.Entry<String, LinkedHashMap<String, Integer>> e : counts.entrySet()) {
                if (e.getValue().containsKey(what) && derived.contains(e.getKey() + " " + what)) {
                    return List.of("there", "are", say(e.getValue().get(what)), what, "left", ".");
                }
            }
            return null;
        }
        Map<String, Integer> has = counts.get(who);
        if (has == null) {
            return null;
        }
        Integer n = has.get(what);
        if (n == null && has.size() >= 2) {
            // "siblings": the brothers and the sister; "animals": the cows and the pigs.
            n = has.values().stream().mapToInt(Integer::intValue).sum();
        } else if (n == null || !derived.contains(who + " " + what)) {
            return null;
        }
        List<String> said = new ArrayList<>();
        if (who.equals("there")) {
            said.addAll(List.of("there", "are"));
        } else {
            said.addAll(who.equals("i") ? List.of("you") : who.equals("we") ? List.of("we") : List.of(who.split(" ")));
            said.add(Set.of("i", "we", "you", "they").contains(who) ? "have" : "has");
        }
        said.add(say(n));
        said.add(what);
        said.add(".");
        return said;
    }

    /** "how much do three loaves cost?" */
    private List<String> howMuch(List<String> q) {
        int cost = q.indexOf("cost");
        if (cost < 4 || !Set.of("do", "does").contains(q.get(2))) {
            return null;
        }
        int n = number(q.get(3));
        if (n < 0 || cost != 5) {
            return null;
        }
        String what = q.get(4);
        for (Map.Entry<String, Object[]> e : prices.entrySet()) {
            String sold = e.getKey();
            if (what.startsWith(sold.substring(0, Math.max(1, sold.length() - 1)))) {
                int each = (Integer) e.getValue()[0];
                String unit = (String) e.getValue()[1];
                List<String> said = new ArrayList<>(List.of(q.get(3), what, "cost", say(n * each)));
                if (!unit.isEmpty()) {
                    said.add(unit);
                }
                said.add(".");
                return said;
            }
        }
        return null;
    }

    /** "is anna older than 5?", "is tom older than sam?" */
    private List<String> isMore(List<String> q) {
        int than = q.indexOf("than");
        if (than < 3 || than >= q.size() - 1) {
            return null;
        }
        String word = q.get(than - 1);
        if (!MORE.containsKey(word) && !LESS.containsKey(word)) {
            return null;
        }
        String measure = MORE.containsKey(word) ? MORE.get(word) : LESS.get(word);
        boolean more = MORE.containsKey(word);
        String who = thing(q.subList(1, than - 1));
        List<String> otherWords = q.subList(than + 1, q.size());
        int n = otherWords.size() == 1 ? number(otherWords.get(0)) : -1;
        Integer mine = value(who, measure);
        Integer theirs = n >= 0 ? Integer.valueOf(n) : value(thing(otherWords), measure);
        if (theirs == null) {
            return null;
        }
        Boolean so = null;
        if (mine != null) {
            so = more ? mine > theirs : mine < theirs;
        } else if (more) {
            Integer least = atLeast(who, measure, 0);
            if (least != null && least > theirs) {
                so = true;
            }
        }
        if (so == null) {
            return null;
        }
        List<String> said = new ArrayList<>(List.of(so ? "yes" : "no", ","));
        said.addAll(List.of(who.split(" ")));
        said.add("is");
        if (mine != null) {
            said.add(say(mine));
            if (!measure.equals("old")) {
                said.add(measure);
            }
            if (n < 0) {
                // "no , tom is seven and sam is nine ."
                said.add("and");
                said.addAll(otherWords);
                said.addAll(List.of("is", say(theirs)));
            }
        } else {
            said.addAll(List.of(word, "than"));
            said.addAll(otherWords);
        }
        said.add(".");
        return said;
    }

    /** "who is older?" of the two whose ages were told. */
    private List<String> whoMore(String word) {
        if (!MORE.containsKey(word) && !LESS.containsKey(word)) {
            return null;
        }
        String measure = MORE.containsKey(word) ? MORE.get(word) : LESS.get(word);
        boolean more = MORE.containsKey(word);
        String best = null;
        Integer bestValue = null;
        int known = 0;
        for (Map.Entry<String, Map<String, Integer>> e : measured.entrySet()) {
            Integer v = e.getValue().get(measure);
            if (v == null) {
                continue;
            }
            known++;
            if (bestValue == null || (more ? v > bestValue : v < bestValue)) {
                best = e.getKey();
                bestValue = v;
            }
        }
        if (known < 2) {
            return null;
        }
        List<String> said = new ArrayList<>(List.of(best.split(" ")));
        said.addAll(List.of("is", word, "."));
        return said;
    }
}
