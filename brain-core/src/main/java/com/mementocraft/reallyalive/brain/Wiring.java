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
    public record Rule(LobeId from, LobeId to, float synapsesPerNeuron) {}

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
        local(DECISION, 6);
        local(CEREBELLUM, 4);

        // Sensory block -> Concept and -> Wernicke's.
        for (LobeId s : new LobeId[] {OLFACTORY, OCCIPITAL, FEELY}) {
            add(s, CONCEPT, 8);
            add(s, WERNICKE, 3);
        }
        // Occipital -> Memory lobe.
        add(OCCIPITAL, MEMORY, 4);
        // Concept <-> Emotion, Concept <-> Wernicke's.
        both(CONCEPT, EMOTION, 4);
        both(CONCEPT, WERNICKE, 4);
        // Concept, Wernicke's and Emotion loop through the Memory lobe and back.
        both(CONCEPT, MEMORY, 4);
        both(WERNICKE, MEMORY, 4);
        both(EMOTION, MEMORY, 4);
        // The "maybe??" row, approved: Concept -> Decision -> Cerebellum.
        add(CONCEPT, DECISION, 8);
        add(DECISION, CEREBELLUM, 10);
        // Approved addition: body sense straight to the cerebellum, for balance and reflexes.
        add(FEELY, CEREBELLUM, 6);
        // Cerebellum -> motor pools (the player's controls).
        add(CEREBELLUM, MOTOR, 10);

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
        RULES.add(new Rule(from, to, k));
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
