package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a villager remembers about people: what they said about themselves ("i'm a farmer." from
 * Misu is kept as "misu is a farmer."), what others told about them, a death it saw or was told
 * of, and what it did itself. Nothing is written down anywhere else: a memory lives only in the
 * villagers who keep it, fades unless it is told again or heard again, and lasts longer the more
 * it was felt. Whether a villager passes a memory on is its own choice (see the brain's urge to
 * tell); when the last villager who keeps it dies or forgets, it is gone.
 */
final class Recollections {
    static final int CAPACITY = 192;
    /** A memory this faint is forgotten. */
    static final float FORGOTTEN = 0.05f;
    /** Ticks an unrepeated, unfelt memory takes to fade to a third of its strength: three days. */
    static final float LIFETIME = 3 * 24_000f;
    /** How much telling or hearing a memory again brings it back. */
    static final float RETOLD = 0.4f;
    /** How strongly what was felt about the dead colours every memory of them. */
    static final float MOURNED = 0.8f;

    static final class Memory {
        /** Whom it is about, by first name, lower case; the villager's own name for its own deeds. */
        String person;
        /** The memory as it would be said, word ids. */
        int[] line;
        float strength = 1f;
        /** How much it was felt (0..1): felt memories fade slower and come to mind more. */
        float feeling;
        /** How often it was told or heard again: retold memories cement, as synapses do. */
        int rehearsed;
        long lastTold = Long.MIN_VALUE / 2;
        /** For what it knows of itself: which fact this is, so a new count replaces the old one. */
        String key;
    }

    final List<Memory> memories = new ArrayList<>();
    /** People it knows have died. */
    final Set<String> dead = new HashSet<>();
    private long lastFade;

    /** Remembers a line about someone, or brings it back if it already knew it. */
    Memory learn(String person, int[] line, float feeling, String key) {
        if (person == null || line.length == 0) {
            return null;
        }
        for (Memory m : memories) {
            if (key != null ? key.equals(m.key) && person.equals(m.person) : java.util.Arrays.equals(m.line, line)) {
                m.line = line.clone();
                m.strength = Math.min(1f, m.strength + RETOLD);
                m.feeling = Math.max(m.feeling, feeling);
                if (key == null) {
                    m.rehearsed++;
                }
                return m;
            }
        }
        if (memories.size() >= CAPACITY) {
            // The faintest memory makes way; what it knows of itself always stays.
            Memory weakest = null;
            for (Memory m : memories) {
                if (m.key == null && (weakest == null || salience(m, 0) < salience(weakest, 0))) {
                    weakest = m;
                }
            }
            if (weakest == null) {
                return null;
            }
            memories.remove(weakest);
        }
        Memory m = new Memory();
        m.person = person;
        m.line = line.clone();
        m.feeling = feeling;
        m.key = key;
        memories.add(m);
        return m;
    }

    /** Memories fade with time; those felt and those retold fade slower. Its own deeds do not. */
    void fade(long now) {
        long dt = now - lastFade;
        lastFade = now;
        if (dt <= 0) {
            return;
        }
        memories.removeIf(m -> {
            if (m.key != null) {
                return false;
            }
            float life = LIFETIME * (1f + 4f * m.feeling) * (1f + m.rehearsed);
            m.strength *= (float) Math.exp(-dt / life);
            return m.strength < FORGOTTEN;
        });
    }

    boolean knows(String person) {
        for (Memory m : memories) {
            if (m.person.equals(person)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Learns that someone died: what it remembers of them is now told as the past ("misu was a
     * farmer."), and coloured by the loss. Returns whether it knew them before.
     */
    boolean died(String person, Vocabulary v) {
        boolean knew = knows(person);
        if (dead.add(person)) {
            for (Memory m : memories) {
                if (m.person.equals(person)) {
                    m.line = pastTense(m.line, v);
                    m.feeling = Math.max(m.feeling, knew ? MOURNED : 0f);
                }
            }
        }
        return knew;
    }

    /**
     * How much a memory wants to be told now: how strong and how felt it is, less so if it was
     * told a little while ago.
     */
    static float salience(Memory m, long now) {
        float stale = 1f - (float) Math.exp(-(now - m.lastTold) / 6000.0);
        return m.strength * (0.3f + m.feeling) * stale;
    }

    /**
     * The memories that answer a heard line asking about someone it remembers ("who was misu?",
     * "how much wheat did misu harvest?"), best first, with how well each fits (0..1): a memory of
     * that person at all, more for sharing what the question asks about, less the more it faded.
     */
    int[][] recall(int[] heard, Vocabulary v, String self, ExchangeMemory shared, int k, float[] match) {
        String person = null;
        boolean asking = false;
        for (int w : heard) {
            if (w < 0) {
                continue;
            }
            String word = v.word(w);
            if (person == null && !word.equals(self) && knows(word)) {
                person = word;
            }
            asking |= word.equals("?") || word.equals("tell") || word.equals("remember") || word.equals("about");
        }
        if (person == null || !asking) {
            return new int[0][];
        }
        float total = 0f;
        for (int w : heard) {
            if (w >= 0 && !v.word(w).equals(person) && shared != null) {
                total += shared.weight(w);
            }
        }
        List<Memory> about = new ArrayList<>();
        Map<Memory, Float> fits = new HashMap<>();
        for (Memory m : memories) {
            if (!m.person.equals(person)) {
                continue;
            }
            float shares = 0f;
            for (int w : heard) {
                if (w >= 0 && !v.word(w).equals(person) && shared != null && shared.weight(w) > 0f && said(m.line, v.word(w), v)) {
                    shares += shared.weight(w);
                }
            }
            float overlap = total > 0f ? shares / total : 0f;
            // "What happened to misu?" asks after a death, "who was misu?" after who they were.
            if (said(heard, "happened", v) || said(heard, "happen", v)) {
                overlap += said(m.line, "died", v) ? 0.5f : 0f;
            }
            if (said(heard, "who", v)) {
                overlap += said(m.line, "a", v) && (said(m.line, "is", v) || said(m.line, "was", v)) ? 0.5f : 0f;
            }
            overlap = Math.min(1f, overlap);
            fits.put(m, (0.6f + 0.4f * overlap) * (0.6f + 0.4f * m.strength));
            about.add(m);
        }
        about.sort((a, b) -> Float.compare(fits.get(b), fits.get(a)));
        int found = Math.min(k, about.size());
        int[][] out = new int[found][];
        for (int i = 0; i < found; i++) {
            out[i] = about.get(i).line;
            if (match != null && i < match.length) {
                match[i] = fits.get(about.get(i));
            }
        }
        return out;
    }

    /** Whether the word, or the same word with an "s", "'s", "d" or "ed" on it, is in the line. */
    static boolean said(int[] line, String a, Vocabulary v) {
        for (int w : line) {
            if (w < 0) {
                continue;
            }
            String b = v.word(w);
            if (a.equals(b) || a.equals(b + "s") || b.equals(a + "s") || a.equals(b + "'s") || b.equals(a + "'s")
                    || a.equals(b + "d") || b.equals(a + "d") || a.equals(b + "ed") || b.equals(a + "ed")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------ retelling

    /** Verbs said after "i" that take an "s" after a name ("i grow wheat" -> "misu grows wheat"). */
    private static final Set<String> VERBS = Set.of("like", "love", "want", "need", "grow", "work", "live", "make",
        "think", "know", "hate", "plant", "farm", "harvest", "keep", "feel", "help", "cook", "build", "sell",
        "trade", "hope", "play", "own", "use", "eat", "sleep", "say", "bake", "brew", "breed", "raise", "care",
        "enjoy", "prefer", "water", "mine", "craft", "fight", "guard", "hunt", "fish", "go", "do", "have", "miss");
    private static final Map<String, String> THIRD = Map.of("go", "goes", "do", "does", "have", "has", "fish", "fishes",
        "miss", "misses", "don't", "doesn't", "am", "is", "was", "was");
    private static final Map<String, String> PAST = new HashMap<>();

    static {
        String[] pairs = {"is", "was", "are", "were", "isn't", "wasn't", "has", "had", "have", "had", "does", "did",
            "doesn't", "didn't", "can", "could", "can't", "couldn't", "will", "would", "won't", "wouldn't",
            "likes", "liked", "loves", "loved", "wants", "wanted", "needs", "needed", "grows", "grew", "works", "worked",
            "lives", "lived", "makes", "made", "thinks", "thought", "knows", "knew", "hates", "hated", "plants", "planted",
            "farms", "farmed", "harvests", "harvested", "keeps", "kept", "feels", "felt", "helps", "helped",
            "cooks", "cooked", "builds", "built", "sells", "sold", "trades", "traded", "hopes", "hoped",
            "plays", "played", "owns", "owned", "uses", "used", "eats", "ate", "sleeps", "slept", "says", "said",
            "bakes", "baked", "brews", "brewed", "breeds", "bred", "raises", "raised", "cares", "cared",
            "enjoys", "enjoyed", "prefers", "preferred", "waters", "watered", "mines", "mined", "crafts", "crafted",
            "fights", "fought", "guards", "guarded", "hunts", "hunted", "fishes", "fished", "goes", "went",
            "misses", "missed"};
        for (int i = 0; i < pairs.length; i += 2) {
            PAST.put(pairs[i], pairs[i + 1]);
        }
    }

    /**
     * What a speaker said about themselves, as told about them: "i'm a farmer." from Misu is
     * "misu is a farmer.", "my wheat grows best by water." is "misu's wheat grows best by water.".
     * Returns null for a line that is not the speaker telling about themselves, or that would not
     * make sense told by someone else (a question, or a line to "you").
     */
    static int[] aboutSpeaker(int[] line, String speaker, Vocabulary v) {
        if (line.length == 0 || speaker == null) {
            return null;
        }
        boolean first = false;
        for (int w : line) {
            if (w < 0) {
                return null;
            }
            String word = v.word(w);
            if (word.equals("?") || word.startsWith("you") || word.equals("we") || word.equals("us") || word.equals("our")) {
                return null;
            }
            first |= word.equals("i") || word.startsWith("i'") || word.equals("my") || word.equals("me") || word.equals("myself");
        }
        if (!first) {
            return null;
        }
        List<String> out = new ArrayList<>();
        boolean subject = false;
        for (int w : line) {
            String word = v.word(w);
            String said = null;
            switch (word) {
                case "i" -> {
                    out.add(speaker);
                    subject = true;
                    continue;
                }
                case "i'm" -> said = speaker + " is";
                case "i've" -> said = speaker + " has";
                case "i'll" -> said = speaker + " will";
                case "i'd" -> said = speaker + " would";
                case "my", "mine" -> said = speaker + "'s";
                case "me", "myself" -> said = speaker;
                default -> {
                    if (subject && THIRD.containsKey(word)) {
                        said = THIRD.get(word);
                    } else if (subject && VERBS.contains(word)) {
                        said = word + "s";
                    }
                }
            }
            subject = false;
            if (said == null) {
                out.add(word);
            } else {
                out.addAll(List.of(said.split(" ")));
            }
        }
        return ids(out, v);
    }

    /** A line about someone told as the past, now they are dead. */
    static int[] pastTense(int[] line, Vocabulary v) {
        List<String> out = new ArrayList<>();
        for (int w : line) {
            String word = v.word(w);
            out.add(PAST.getOrDefault(word, word));
        }
        return ids(out, v);
    }

    private static int[] ids(List<String> words, Vocabulary v) {
        int[] ids = new int[words.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = v.id(words.get(i));
        }
        return ids;
    }

    // ------------------------------------------------------------ saving

    void write(DataOutput out, Vocabulary v) throws IOException {
        out.writeInt(memories.size());
        for (Memory m : memories) {
            out.writeUTF(m.person);
            out.writeShort(m.line.length);
            for (int w : m.line) {
                out.writeUTF(v.word(w));
            }
            out.writeFloat(m.strength);
            out.writeFloat(m.feeling);
            out.writeInt(m.rehearsed);
            out.writeLong(m.lastTold);
            out.writeBoolean(m.key != null);
            if (m.key != null) {
                out.writeUTF(m.key);
            }
        }
        out.writeInt(dead.size());
        for (String d : dead) {
            out.writeUTF(d);
        }
        out.writeLong(lastFade);
    }

    void read(DataInput in, Vocabulary v) throws IOException {
        memories.clear();
        dead.clear();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            Memory m = new Memory();
            m.person = in.readUTF();
            m.line = new int[in.readShort()];
            for (int j = 0; j < m.line.length; j++) {
                m.line[j] = v.id(in.readUTF());
            }
            m.strength = in.readFloat();
            m.feeling = in.readFloat();
            m.rehearsed = in.readInt();
            m.lastTold = in.readLong();
            m.key = in.readBoolean() ? in.readUTF() : null;
            memories.add(m);
        }
        int deaths = in.readInt();
        for (int i = 0; i < deaths; i++) {
            dead.add(in.readUTF());
        }
        lastFade = in.readLong();
    }
}
