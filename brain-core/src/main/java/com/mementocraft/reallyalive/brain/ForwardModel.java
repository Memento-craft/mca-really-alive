package com.mementocraft.reallyalive.brain;

/**
 * The cerebellum's prediction of what the body will feel next tick, read linearly from cerebellum
 * neuron activity. The gap between prediction and what actually happens is the error signal real
 * cerebella get from climbing fibres: it trains the prediction and feeds the villager's curiosity.
 */
final class ForwardModel {
    static final FeelChannel[] PREDICTED = {
            FeelChannel.VEL_FORWARD, FeelChannel.VEL_BACKWARD, FeelChannel.VEL_LEFT, FeelChannel.VEL_RIGHT,
            FeelChannel.VEL_UP, FeelChannel.VEL_DOWN, FeelChannel.ON_GROUND,
            FeelChannel.TOUCH_FRONT, FeelChannel.TOUCH_BACK, FeelChannel.TOUCH_LEFT, FeelChannel.TOUCH_RIGHT,
            FeelChannel.TOUCH_HEAD,
    };
    static final float LEARNING_RATE = 0.05f;

    /** weights[k][neuron]; indexed by global neuron id so growth only needs more room. */
    float[][] weights;
    final float[] bias = new float[PREDICTED.length];
    final float[] prediction = new float[PREDICTED.length];
    boolean hasPrediction;
    float surprise;

    ForwardModel(int capacity) {
        weights = new float[PREDICTED.length][capacity];
    }

    void ensureCapacity(int capacity) {
        if (weights[0].length < capacity) {
            for (int k = 0; k < weights.length; k++) {
                weights[k] = java.util.Arrays.copyOf(weights[k], capacity);
            }
        }
    }

    /** Compares last tick's prediction with what the body now feels, and learns from the gap. */
    void learn(float[] feel, int[] inputs, int inputCount, float[] rates, float inputNorm) {
        if (!hasPrediction) {
            surprise = 0f;
            return;
        }
        float total = 0f;
        float step = LEARNING_RATE / (1f + inputNorm);
        for (int k = 0; k < PREDICTED.length; k++) {
            float err = feel[PREDICTED[k].ordinal()] - prediction[k];
            total += Math.abs(err);
            float[] w = weights[k];
            for (int i = 0; i < inputCount; i++) {
                int n = inputs[i];
                w[n] += step * err * rates[n];
            }
            bias[k] += step * err;
        }
        surprise = total / PREDICTED.length;
    }

    void predict(int[] inputs, int inputCount, float[] rates) {
        for (int k = 0; k < PREDICTED.length; k++) {
            float s = bias[k];
            float[] w = weights[k];
            for (int i = 0; i < inputCount; i++) {
                int n = inputs[i];
                s += w[n] * rates[n];
            }
            prediction[k] = s;
        }
        hasPrediction = true;
    }
}
