package com.mementocraft.reallyalive.brain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mementocraft.reallyalive.brain.LobeId.*;

/**
 * Which lobes connect to which, following the notebook diagram (page 111). Each rule gives how many
 * synapses a neuron in the source lobe makes into the target lobe on average; the exact partners are
 * chosen when the neuron is born or reborn.
 */
public final class Wiring {
    /**
     * One kind of connection. The target can be limited to a band of the target lobe's sheet (its y
     * range), so one lobe can hold separate areas. A diffuse rule ignores position and links
     * anywhere in the band, which is how a memory binds an action, a person and a place together.
     */
    public record Rule(LobeId from, LobeId to, float synapsesPerNeuron, Band band, boolean diffuse) {
        /** True when a neuron at sheet height y in the target lobe can receive this rule. */
        public boolean accepts(float y) {
            return y >= band.y0 && y < band.y1 || band.y1 >= 1f && y >= 1f;
        }
    }

    /**
     * Areas of a lobe's sheet. The Concept lobe's come from the memory layout (page 111): concepts
     * of actions, of people and of places. The Motor lobe holds the controls and the voice.
     */
    public enum Band {
        ALL(0f, 1f), ACTION(0f, 1f / 3f), PEOPLE(1f / 3f, 2f / 3f), PLACES(2f / 3f, 1f),
        /** The Motor lobe's two parts: the player's controls, and the voice. */
        KEYS(0f, 0.5f), VOICE(0.5f, 1f);

        public final float y0;
        public final float y1;

        Band(float y0, float y1) {
            this.y0 = y0;
            this.y1 = y1;
        }

        /** Maps a 0..1 sheet height into this band, keeping the map's order. */
        public float into(float y) {
            return y0 + y * (y1 - y0);
        }

        /** Maps a height inside this band back to 0..1. */
        public float outOf(float y) {
            return (y - y0) / (y1 - y0);
        }

        public float share() {
            return y1 - y0;
        }
    }

    private static final List<Rule> RULES = new ArrayList<>();
    private static final List<List<Rule>> OUT = new ArrayList<>();
    private static final List<List<Rule>> IN = new ArrayList<>();

    static {
        // Within each lobe: local recurrent circuits. Receptors feed their lobe's interior this way.
        local(OLFACTORY, 6);
        local(OCCIPITAL, 6);
        local(FEELY, 6);
        local(CONCEPT, 8);
        local(EMOTION, 6);
        local(WERNICKE, 6);
        local(MEMORY, 10);
        local(CEREBELLUM, 4);

        // Sensory block -> Concept and -> Wernicke's. Inside Concept, what the body feels and does
        // becomes concepts of actions, what is seen and smelt becomes concepts of people and places.
        add(FEELY, CONCEPT, 6, Band.ACTION);
        add(FEELY, CONCEPT, 2, Band.PLACES);
        add(OCCIPITAL, CONCEPT, 4, Band.PEOPLE);
        add(OCCIPITAL, CONCEPT, 4, Band.PLACES);
        add(OLFACTORY, CONCEPT, 4, Band.PEOPLE);
        add(OLFACTORY, CONCEPT, 4, Band.PLACES);
        for (LobeId s : new LobeId[] {OLFACTORY, OCCIPITAL, FEELY}) {
            add(s, WERNICKE, 3);
        }
        // Occipital -> Memory lobe.
        add(OCCIPITAL, MEMORY, 4);
        // Concept <-> Emotion, Concept <-> Wernicke's.
        both(CONCEPT, EMOTION, 4);
        both(CONCEPT, WERNICKE, 4);
        // The memory layout: each memory links concepts of an action, the people and the place
        // (diffuse, across all three areas) and is recalled through them. Memories chain to other
        // memories through the lobe's own recurrent wiring. Wernicke's and Emotion loop through
        // the Memory lobe and back.
        diffuse(CONCEPT, MEMORY, 4);
        diffuse(MEMORY, CONCEPT, 4);
        both(WERNICKE, MEMORY, 4);
        both(EMOTION, MEMORY, 4);
        // Skills: what the cerebellum is doing is remembered too.
        add(CEREBELLUM, MEMORY, 2);
        // Concept and Feely drive the cerebellum (Joshua, 2026-10-21; the "maybe??" Decision lobe
        // on page 111 was only brainstorming).
        add(CONCEPT, CEREBELLUM, 10);
        add(FEELY, CEREBELLUM, 6);
        // Cerebellum -> motor pools (the player's controls).
        add(CEREBELLUM, MOTOR, 10, Band.KEYS);
        // Wernicke's area hears words (its own receptors) and drives the voice: saying a word.
        add(WERNICKE, MOTOR, 6, Band.VOICE);

        for (int i = 0; i < LobeId.values().length; i++) {
            OUT.add(new ArrayList<>());
            IN.add(new ArrayList<>());
        }
        for (Rule r : RULES) {
            OUT.get(r.from.ordinal()).add(r);
            IN.get(r.to.ordinal()).add(r);
        }
    }

    private static void local(LobeId l, float k) {
        add(l, l, k);
    }

    private static void both(LobeId a, LobeId b, float k) {
        add(a, b, k);
        add(b, a, k);
    }

    private static void add(LobeId from, LobeId to, float k) {
        add(from, to, k, Band.ALL);
    }

    private static void add(LobeId from, LobeId to, float k, Band band) {
        RULES.add(new Rule(from, to, k, band, false));
    }

    private static void diffuse(LobeId from, LobeId to, float k) {
        RULES.add(new Rule(from, to, k, Band.ALL, true));
    }

    public static List<Rule> rules() {
        return Collections.unmodifiableList(RULES);
    }

    public static List<Rule> outgoing(LobeId from) {
        return OUT.get(from.ordinal());
    }

    public static List<Rule> incoming(LobeId to) {
        return IN.get(to.ordinal());
    }

    private Wiring() {}
}
