package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GenomeTest {
    @Test
    void childGenesComeFromItsParents() {
        Rng r = new Rng(1);
        Genome a = Genome.random(r);
        Genome b = Genome.random(r);
        Genome child = Genome.inherit(a, b, r);
        float[] ca = a.toArray();
        float[] cb = b.toArray();
        float[] cc = child.toArray();
        int fromParents = 0;
        for (int i = 0; i < cc.length; i++) {
            if (cc[i] == ca[i] || cc[i] == cb[i]) {
                fromParents++;
            }
        }
        assertTrue(fromParents >= cc.length - 4, "mostly inherited, a few mutations: " + fromParents);
    }

    @Test
    void roundTripsThroughAnArray() {
        Genome g = Genome.random(new Rng(2));
        assertEquals(g, Genome.fromArray(g.toArray()));
    }
}
