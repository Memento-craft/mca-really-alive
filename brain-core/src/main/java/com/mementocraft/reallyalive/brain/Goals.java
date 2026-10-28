package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The frontal part's working memory for what the villager is after: a goal held in mind, and under
 * it the steps it still lacks. Which step a thing needs is learned, like any association: getting
 * bread just after wheat was used up links bread to wheat, getting wheat just after breaking a
 * ripe crop links wheat to breaking it. Being told ("you need wheat to make bread.") links them
 * too. With a goal held, the missing step is taken up as the next thing to get, and getting a
 * step that serves the goal is felt as a small pleasure, so what it did to get there is learned
 * even though the goal itself is still far off.
 *
 * <p>Things are named as the game names them, in words ("bread", "oak log"), and deeds as a verb
 * and a thing ("broke wheat", "placed dirt").
 */
final class Goals {
    /** Most steps held in mind under a goal, the goal included. */
    static final int DEPTH = 4;
    /** What was used up or done this long (ticks) before something was got counts as how it was got. */
    static final int WINDOW = 200;
    /** How much one experience strengthens a link, and what being told one gives it. */
    static final float LEARN = 0.3f;
    static final float TOLD = 0.6f;
    /** Links of a thing that did not come before getting it again weaken. */
    static final float UNSUPPORTED = 0.85f;
    /** A link this strong is a step worth taking. */
    static final float STEP_MIN = 0.25f;
    /** A goal held this long (ticks) without getting any closer is given up. */
    static final int GIVE_UP = 24_000;
    /** A step seen getting closer (a block cracking that is to be broken) feels this good each tick. */
    static final float CLOSER = 0.08f;
    static final int MAX_THINGS = 512;
    /** A need felt this strongly takes up, as a goal, what eased it before. */
    static final float WANT_AT = 0.5f;
    /** A need that falls this much at once was eased by what was just done. */
    static final float EASED = 0.02f;
    /** A need this much stronger than the one the goal serves takes over. */
    static final float OVERRULE = 0.3f;
    static final float URGENT = 0.75f;
    /** Deeds as done and as said: "broke" is "break". */
    static final String[][] DEEDS = {{"broke ", "break ", "some "}, {"placed ", "place ", "some "}, {"ate ", "eat ", "some "},
            {"slept in ", "sleep in ", "a "}, {"talked with ", "talk with ", ""}, {"got away from ", "get away from ", "the "},
            {"made ", "make ", "some "}};

    /** For each thing, what getting it needs, and how surely. */
    final Map<String, Map<String, Float>> needs = new HashMap<>();
    /** The goal at the bottom, the next step on top. */
    final Deque<String> stack = new ArrayDeque<>();
    /** What it carries, last it looked. */
    final Map<String, Integer> carried = new HashMap<>();
    /** Deeds done since the goal was set. */
    private final Set<String> doneSince = new HashSet<>();
    /** Things used up and deeds done lately, with when. */
    private final ArrayDeque<Object[]> recent = new ArrayDeque<>();
    long goalSince;
    long lastProgress;
    /** How strongly each need ("hunger") was felt last tick. */
    private final Map<String, Float> felt = new HashMap<>();
    /** Where each need stood when it last began to ease, or was last learned from. */
    private final Map<String, Float> easedFrom = new HashMap<>();
    /** The need the goal held now serves, or null when it was asked for or there is none. */
    String because;
    /** Whether it has looked at what it carries yet: the first look is no getting of anything. */
    private boolean looked;

    /** A thing's name as the brain keeps it: lower case words, one of a kind ("carrots" is "carrot"). */
    static String canonical(String thing) {
        String s = thing.toLowerCase(java.util.Locale.ROOT).replace('_', ' ').trim();
        for (String a : new String[] {"a ", "an ", "some ", "the ", "any ", "more "}) {
            if (s.startsWith(a)) {
                s = s.substring(a.length());
            }
        }
        if (s.endsWith("oes") && s.length() > 5) {
            return s.substring(0, s.length() - 2);
        }
        if (s.endsWith("s") && !s.endsWith("ss") && s.length() > 3) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    /** Takes up a goal, dropping whatever it was after before. */
    void want(String thing, long now) {
        because = null;
        stack.clear();
        doneSince.clear();
        stack.push(canonical(thing));
        goalSince = now;
        lastProgress = now;
    }

    String goal() {
        return stack.isEmpty() ? null : stack.peekLast();
    }

    String nextStep() {
        return stack.isEmpty() ? null : stack.peekFirst();
    }

    boolean has(String thing) {
        return carried.getOrDefault(thing, 0) > 0 || doneSince.contains(thing);
    }

    static boolean isDeed(String t) {
        for (String[] d : DEEDS) {
            if (t.startsWith(d[0])) {
                return true;
            }
        }
        return false;
    }

    /**
     * A need, felt now this strongly (0..1). When it falls at once, what was just done or used up
     * is what eases it, learned as any step is. When it is strong and nothing else is held in mind,
     * what eased it surest is taken up as the goal. Returns how much it was eased this tick.
     */
    float feel(String need, float level, long tick) {
        float was = felt.getOrDefault(need, level);
        felt.put(need, level);
        float eased = was - level;
        // Easing is learned once it adds up: a meal at once, a night's sleep a little each tick.
        float from = easedFrom.getOrDefault(need, level);
        if (level > from) {
            from = level;
        } else if (from - level > EASED) {
            gained(need);
            from = level;
        }
        easedFrom.put(need, from);
        return Math.max(0f, eased);
    }

    /**
     * With the needs felt this tick: the strongest one it knows a way to ease, if strong enough,
     * is taken up as the goal when nothing else is held in mind. A need far stronger than the one
     * the goal serves (a monster near while fetching bread) takes over.
     */
    void takeUp(long tick) {
        String best = null, way = null;
        float bestLevel = WANT_AT;
        for (Map.Entry<String, Float> e : felt.entrySet()) {
            if (e.getValue() >= bestLevel) {
                String w = missing(e.getKey());
                if (w != null) {
                    best = e.getKey();
                    way = w;
                    bestLevel = e.getValue();
                }
            }
        }
        if (best == null) {
            return;
        }
        boolean free = stack.isEmpty();
        // Something asked for gives way only to a need that is urgent.
        boolean overruled = because == null ? bestLevel >= URGENT
                : !best.equals(because) && bestLevel > felt.getOrDefault(because, 0f) + OVERRULE;
        if (free || overruled) {
            want(way, tick);
            because = best;
            progress(tick);
        }
    }

    /** How felt a need is, 0..1, as of the last tick. */
    float felt(String need) {
        return felt.getOrDefault(need, 0f);
    }

    /** Learns that getting what comes first needs what comes second. */
    void told(String thing, String need) {
        link(canonical(thing), canonical(need), TOLD);
    }

    private void link(String thing, String need, float rate) {
        if (thing.equals(need) || needs.size() >= MAX_THINGS && !needs.containsKey(thing)) {
            return;
        }
        Map<String, Float> m = needs.computeIfAbsent(thing, k -> new HashMap<>());
        float s = m.getOrDefault(need, 0f);
        m.put(need, s + rate * (1f - s));
    }

    /**
     * What it carries and did this tick. Returns how good getting closer felt: 1 for the goal
     * reached, a step's share for a step that served it, a little for a step under way, 0 for nothing.
     */
    float sense(Map<String, Integer> now, List<String> did, long tick) {
        return sense(now, did, List.of(), tick);
    }

    float sense(Map<String, Integer> now, List<String> did, List<String> doing, long tick) {
        while (!recent.isEmpty() && tick - (long) recent.peekFirst()[0] > WINDOW) {
            recent.pollFirst();
        }
        List<String> got = new ArrayList<>();
        if (now != null) {
            Map<String, Integer> counts = new HashMap<>();
            for (Map.Entry<String, Integer> e : now.entrySet()) {
                counts.merge(canonical(e.getKey()), e.getValue(), Integer::sum);
            }
            Set<String> all = new HashSet<>(counts.keySet());
            all.addAll(carried.keySet());
            for (String t : all) {
                int was = carried.getOrDefault(t, 0), is = counts.getOrDefault(t, 0);
                if (is > was && looked) {
                    got.add(t);
                } else if (is < was) {
                    recent.add(new Object[] {tick, t});
                }
            }
            carried.clear();
            carried.putAll(counts);
            looked = true;
        }
        // Deeds are what was done to get what is got now or soon after.
        // What is under way (a block cracking, a night's sleep) counts as done lately while it goes on.
        for (String d : doing) {
            recent.add(new Object[] {tick, canonical(d)});
        }
        for (String d : did) {
            String deed = canonical(d);
            got.add(deed);
            if (!stack.isEmpty()) {
                doneSince.add(deed);
            }
            recent.add(new Object[] {tick, deed});
        }
        for (String t : got) {
            gained(t);
        }
        float joy = progress(tick);
        for (String d : doing) {
            if (stack.contains(canonical(d))) {
                joy = Math.max(joy, CLOSER);
            }
        }
        return joy;
    }

    /** It got a thing: what came just before is how, and other ways it thought of weaken. */
    private void gained(String thing) {
        Set<String> before = new HashSet<>();
        for (Object[] r : recent) {
            before.add((String) r[1]);
        }
        before.remove(thing);
        Map<String, Float> m = needs.get(thing);
        if (m != null) {
            for (Map.Entry<String, Float> e : m.entrySet()) {
                if (!before.contains(e.getKey())) {
                    e.setValue(e.getValue() * UNSUPPORTED);
                }
            }
        }
        for (String b : before) {
            if (!screened(thing, b, before)) {
                link(thing, b, LEARN);
            }
        }
    }

    /**
     * Whether what came before is already how some other thing that came before was got: breaking
     * wheat got the wheat, and the wheat, not the breaking, is what the bread took.
     */
    private boolean screened(String thing, String b, Set<String> before) {
        for (String z : before) {
            if (!z.equals(b) && !z.equals(thing)) {
                Map<String, Float> m = needs.get(z);
                if (m != null && m.getOrDefault(b, 0f) >= STEP_MIN) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Works the plan: steps got are taken off (a step felt good, the goal more so), then the step
     * the top still lacks most surely is taken up next.
     */
    private float progress(long tick) {
        float joy = 0f;
        // A step got another way (bread given, not baked) makes the steps above it needless.
        int got = -1, i = 0;
        for (String step : stack) {
            if (has(step)) {
                got = i;
            }
            i++;
        }
        while (got-- > 0) {
            stack.pollFirst();
        }
        while (!stack.isEmpty() && has(stack.peekFirst())) {
            stack.pollFirst();
            joy = Math.max(joy, stack.isEmpty() ? 1f : 0.5f);
            lastProgress = tick;
            if (stack.isEmpty()) {
                doneSince.clear();
                because = null;
            }
        }
        if (!stack.isEmpty() && tick - lastProgress > GIVE_UP) {
            stack.clear();
            doneSince.clear();
            because = null;
            return joy;
        }
        while (!stack.isEmpty() && stack.size() < DEPTH) {
            String step = missing(stack.peekFirst());
            if (step == null) {
                break;
            }
            stack.push(step);
        }
        return joy;
    }

    /** The surest thing this needs that it lacks and is not already after, or null. */
    String missing(String thing) {
        Map<String, Float> m = needs.get(thing);
        if (m == null) {
            return null;
        }
        String best = null;
        float bestS = STEP_MIN;
        for (Map.Entry<String, Float> e : m.entrySet()) {
            if (e.getValue() >= bestS && !has(e.getKey()) && !stack.contains(e.getKey())) {
                best = e.getKey();
                bestS = e.getValue();
            }
        }
        return best;
    }

    /** What getting a thing needs, surest first, as far as it knows. */
    List<String> needsOf(String thing) {
        Map<String, Float> m = needs.getOrDefault(canonical(thing), Map.of());
        List<String> out = new ArrayList<>();
        m.entrySet().stream().filter(e -> e.getValue() >= STEP_MIN)
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue())).forEach(e -> out.add(e.getKey()));
        return out;
    }

    // ------------------------------------------------------------ saving

    void write(DataOutput out) throws IOException {
        out.writeInt(needs.size());
        for (Map.Entry<String, Map<String, Float>> e : needs.entrySet()) {
            out.writeUTF(e.getKey());
            out.writeInt(e.getValue().size());
            for (Map.Entry<String, Float> n : e.getValue().entrySet()) {
                out.writeUTF(n.getKey());
                out.writeFloat(n.getValue());
            }
        }
        out.writeInt(stack.size());
        for (Iterator<String> it = stack.descendingIterator(); it.hasNext(); ) {
            out.writeUTF(it.next());
        }
        out.writeLong(goalSince);
        out.writeLong(lastProgress);
    }

    void read(DataInput in) throws IOException {
        needs.clear();
        stack.clear();
        int things = in.readInt();
        for (int i = 0; i < things; i++) {
            Map<String, Float> m = new HashMap<>();
            String t = in.readUTF();
            int k = in.readInt();
            for (int j = 0; j < k; j++) {
                m.put(in.readUTF(), in.readFloat());
            }
            needs.put(t, m);
        }
        int depth = in.readInt();
        for (int i = 0; i < depth; i++) {
            stack.push(in.readUTF());
        }
        goalSince = in.readLong();
        lastProgress = in.readLong();
    }
}
