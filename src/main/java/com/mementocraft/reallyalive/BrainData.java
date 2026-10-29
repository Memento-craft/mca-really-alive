package com.mementocraft.reallyalive;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Small per-villager record saved with the entity: whether its brain is switched on, its genes,
 * how hungry and tired its body is, and how bored and lonely it is.
 * The brain itself (thousands of neurons) is kept in its own file, so chunk data stays small.
 */
public final class BrainData {
    public static final Codec<BrainData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", false).forGetter(d -> d.enabled),
            Codec.FLOAT.listOf().optionalFieldOf("genome", List.of()).forGetter(d -> d.genome),
            Codec.FLOAT.optionalFieldOf("hunger", 0f).forGetter(d -> d.hunger),
            Codec.FLOAT.optionalFieldOf("tiredness", 0f).forGetter(d -> d.tiredness),
            Codec.FLOAT.optionalFieldOf("boredom", 0f).forGetter(d -> d.boredom),
            Codec.FLOAT.optionalFieldOf("loneliness", 0f).forGetter(d -> d.loneliness)
    ).apply(i, BrainData::new));

    private boolean enabled;
    private List<Float> genome;
    private float hunger;
    private float tiredness;
    private float boredom;
    private float loneliness;

    public BrainData() {
        this(false, List.of(), 0f, 0f, 0f, 0f);
    }

    private BrainData(boolean enabled, List<Float> genome, float hunger, float tiredness, float boredom, float loneliness) {
        this.enabled = enabled;
        this.genome = genome;
        this.hunger = hunger;
        this.tiredness = tiredness;
        this.boredom = boredom;
        this.loneliness = loneliness;
    }

    public float boredom() {
        return boredom;
    }

    public float loneliness() {
        return loneliness;
    }

    public float hunger() {
        return hunger;
    }

    public float tiredness() {
        return tiredness;
    }

    public void setBody(float hunger, float tiredness, float boredom, float loneliness) {
        this.hunger = hunger;
        this.tiredness = tiredness;
        this.boredom = boredom;
        this.loneliness = loneliness;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public float[] genome() {
        float[] g = new float[genome.size()];
        for (int i = 0; i < g.length; i++) {
            g[i] = genome.get(i);
        }
        return g;
    }

    public boolean hasGenome() {
        return !genome.isEmpty();
    }

    public void setGenome(float[] genes) {
        List<Float> list = new ArrayList<>(genes.length);
        for (float g : genes) {
            list.add(g);
        }
        this.genome = List.copyOf(list);
    }
}
