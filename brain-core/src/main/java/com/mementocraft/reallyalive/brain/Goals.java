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
        Set<String> between = new HashSet<>(before);
        for (Map.Entry<String, Integer> c : carried.entrySet()) {
            if (c.getValue() > 0) {
                between.add(c.getKey()); // the wheat still in hand came between the breaking and the bread too
            }
        }
        for (String z : between) {
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
        while (!stack.isEmpty() && has(stack.peekFirst())) {
            stack.pollFirst();
            joy = Math.max(joy, stack.isEmpty() ? 1f : 0.5f);
            lastProgress = tick;
            if (stack.isEmpty()) {
                doneSince.clear();
            }
        }
        if (!stack.isEmpty() && tick - lastProgress > GIVE_UP) {
            stack.clear();
            doneSince.clear();
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
