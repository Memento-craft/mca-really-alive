package com.mementocraft.reallyalive.brain;

/** Small xorshift generator whose state can be saved with the brain. */
public final class Rng {
    private long state;
    private boolean hasSpare;
    private float spare;

    public Rng(long seed) {
        this.state = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
    }

    public long state() {
        return state;
    }

    public void setState(long state) {
        this.state = state == 0 ? 0x9E3779B97F4A7C15L : state;
        hasSpare = false;
    }

    boolean hasSpare() {
        return hasSpare;
    }

    float spare() {
        return spare;
    }

    void setSpare(boolean has, float value) {
        hasSpare = has;
        spare = value;
    }

    public long nextLong() {
        long x = state;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        state = x;
        return x * 0x2545F4914F6CDD1DL;
    }

    public float nextFloat() {
        return (nextLong() >>> 40) * 0x1.0p-24f;
    }

    public int nextInt(int bound) {
        return (int) ((nextLong() >>> 33) % bound);
    }

    public float gaussian() {
        if (hasSpare) {
            hasSpare = false;
            return spare;
        }
        float u, v, s;
        do {
            u = nextFloat() * 2f - 1f;
            v = nextFloat() * 2f - 1f;
            s = u * u + v * v;
        } while (s >= 1f || s == 0f);
        float m = (float) Math.sqrt(-2.0 * Math.log(s) / s);
        spare = v * m;
        hasSpare = true;
        return u * m;
    }
}
