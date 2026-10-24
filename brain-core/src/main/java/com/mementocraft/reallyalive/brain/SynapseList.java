package com.mementocraft.reallyalive.brain;

import java.util.Arrays;

/** A neuron's outgoing synapses: target neuron, weight and STDP eligibility. */
final class SynapseList {
    int size;
    int[] target;
    float[] weight;
    float[] elig;

    SynapseList(int capacity) {
        int c = Math.max(4, capacity);
        target = new int[c];
        weight = new float[c];
        elig = new float[c];
    }

    void add(int t, float w) {
        if (size == target.length) {
            int c = size * 2;
            target = Arrays.copyOf(target, c);
            weight = Arrays.copyOf(weight, c);
            elig = Arrays.copyOf(elig, c);
        }
        target[size] = t;
        weight[size] = w;
        elig[size] = 0f;
        size++;
    }

    boolean contains(int t) {
        for (int k = 0; k < size; k++) {
            if (target[k] == t) {
                return true;
            }
        }
        return false;
    }

    /** Removes synapse k by moving the last one into its place. */
    void removeAt(int k) {
        size--;
        target[k] = target[size];
        weight[k] = weight[size];
        elig[k] = elig[size];
    }

    void clear() {
        size = 0;
    }
}
