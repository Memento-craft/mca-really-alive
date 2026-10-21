package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Learns how good the current situation is, read linearly from Concept, Emotion and Memory activity.
 * Dopamine is then the reward prediction error, as in real brains: what was felt plus how good
 * things now look, minus how good they looked a moment ago. Without it, every action taken in a bad
 * situation was punished, including the ones that would have got the villager out of it.
 */
final class Critic {
    static final float DISCOUNT = 0.95f;
    static final float LEARNING_RATE = 0.02f;

    /** weights[neuron]; indexed by global neuron id so growth only needs more room. */
    float[] weights;
    float bias;
    float lastValue;
    boolean hasLast;
    private int[] lastInputs = new int[0];
    private float[] lastRates = new float[0];
    private int lastCount;

    Critic(int capacity) {
        weights = new float[capacity];
    }

    void ensureCapacity(int capacity) {
        if (weights.length < capacity) {
            weights = Arrays.copyOf(weights, capacity);
        }
    }

    /** Returns the prediction error for this tick and learns from it. */
    float step(float reward, int[][] inputLists, int[] counts, float[] rates) {
        float value = bias;
        int total = 0;
        for (int l = 0; l < inputLists.length; l++) {
            for (int k = 0; k < counts[l]; k++) {
                int n = inputLists[l][k];
                value += weights[n] * rates[n];
                total++;
            }
        }
        float error = 0f;
        if (hasLast) {
            error = reward + DISCOUNT * value - lastValue;
            float norm = 1f;
            for (int k = 0; k < lastCount; k++) {
                norm += lastRates[k] * lastRates[k];
            }
            float step = LEARNING_RATE * error / norm;
            for (int k = 0; k < lastCount; k++) {
                weights[lastInputs[k]] += step * lastRates[k];
            }
            bias += step;
            // Learning changed the weights, so re-read this tick's value with them.
            value += step * overlap(rates);
        }
        if (lastInputs.length < total) {
            lastInputs = new int[total * 2];
            lastRates = new float[total * 2];
        }
        int k2 = 0;
        for (int l = 0; l < inputLists.length; l++) {
            for (int k = 0; k < counts[l]; k++) {
                int n = inputLists[l][k];
                lastInputs[k2] = n;
                lastRates[k2] = rates[n];
                k2++;
            }
        }
        lastCount = total;
        lastValue = value;
        hasLast = true;
        return error;
    }

    /** How much this tick's value moves per unit of weight step on last tick's inputs. */
    private float overlap(float[] rates) {
        float overlap = 1f;
        for (int k = 0; k < lastCount; k++) {
            overlap += lastRates[k] * rates[lastInputs[k]];
        }
        return overlap;
    }

    /** Saves last tick's inputs, which the next step learns from. */
    void writeLast(DataOutput out) throws IOException {
        out.writeInt(lastCount);
        for (int k = 0; k < lastCount; k++) {
            out.writeInt(lastInputs[k]);
            out.writeFloat(lastRates[k]);
        }
    }

    void readLast(DataInput in) throws IOException {
        lastCount = in.readInt();
        lastInputs = new int[Math.max(1, lastCount * 2)];
        lastRates = new float[lastInputs.length];
        for (int k = 0; k < lastCount; k++) {
            lastInputs[k] = in.readInt();
            lastRates[k] = in.readFloat();
        }
    }
}
