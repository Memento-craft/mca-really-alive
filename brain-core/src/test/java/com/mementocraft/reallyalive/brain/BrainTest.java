package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BrainTest {
    private static SensoryFrame noisyFrame(Rng r) {
        SensoryFrame f = new SensoryFrame();
        for (int i = 0; i < f.vision.length; i++) {
            f.vision[i] = r.nextFloat() < 0.2f ? r.nextFloat() : 0f;
        }
        f.set(FeelChannel.ON_GROUND, 1f);
        f.set(FeelChannel.HEALTH, 1f);
        return f;
    }

    @Test
    void adultBrainHasAboutFiveThousandNeuronsInEveryLobe() {
        Brain b = Brain.create(Genome.defaults(), 1L, 1f);
        assertEquals(5000, b.neuronCount(), 10);
        for (LobeId l : LobeId.values()) {
            assertTrue(b.neuronCount(l) > 0, l + " is empty");
        }
        assertTrue(b.synapseCount() > 50_000, "synapses: " + b.synapseCount());
    }

    @Test
    void childStartsSmallerAndGrowsTowardAdultSize() {
        Brain b = Brain.create(Genome.defaults(), 2L, 0f);
        assertEquals(3000, b.neuronCount(), 10);
        int added = b.grow(1f, 500);
        assertEquals(500, added);
        while (b.grow(1f, 500) > 0) {
            // keep growing
        }
        assertEquals(5000, b.neuronCount(), 10);
    }

    @Test
    void activityStaysInAHealthyRangeOverTime() {
        Brain b = Brain.create(Genome.defaults(), 3L, 1f);
        Rng r = new Rng(9);
        for (int t = 0; t < 4000; t++) {
            b.step(noisyFrame(r));
        }
        for (LobeId l : LobeId.values()) {
            if (l == LobeId.OLFACTORY || l == LobeId.MOTOR) {
                continue; // no smells in this test; motor depends on babbling
            }
            float a = b.lobeActivity(l);
            assertTrue(a > 0.01f && a < 0.2f, l + " activity " + a);
        }
    }

    @Test
    void overStimulatedNeuronsDecayAndAreReborn() {
        Brain b = Brain.create(Genome.defaults(), 4L, 1f);
        // Drive one interior neuron far above its threshold so it over-fires.
        int victim = b.members[LobeId.CONCEPT.ordinal()].get(5);
        long before = b.resets();
        Rng r = new Rng(1);
        for (int t = 0; t < 6000 && b.resets() == before; t++) {
            b.extInput[victim] = 50f;
            b.step(noisyFrame(r));
        }
        assertTrue(b.resets() > before, "an over-stimulated neuron should eventually decay and reset");
    }

    @Test
    void cementedNeuronsDecaySlower() {
        Brain b = Brain.create(Genome.defaults(), 5L, 1f);
        int a = b.members[LobeId.MEMORY.ordinal()].get(0);
        int c = b.members[LobeId.MEMORY.ordinal()].get(1);
        b.cement[a] = 1f;
        b.cement[c] = 0f;
        b.slowRate[a] = 0f;
        b.slowRate[c] = 0f;
        b.health[a] = 1f;
        b.health[c] = 1f;
        Rng r = new Rng(2);
        for (int t = 0; t < 50; t++) {
            b.slowRate[a] = 0f;
            b.slowRate[c] = 0f;
            b.step(noisyFrame(r));
        }
        assertTrue(b.health[a] > b.health[c], "cemented " + b.health[a] + " vs plain " + b.health[c]);
    }

    @Test
    void saveAndLoadContinuesExactlyWhereItLeftOff() throws IOException {
        Brain a = Brain.create(Genome.defaults(), 6L, 0.5f);
        Rng r = new Rng(3);
        for (int t = 0; t < 300; t++) {
            a.step(noisyFrame(r));
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        BrainIO.write(a, new DataOutputStream(bytes));
        Brain b = BrainIO.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));

        assertEquals(a.neuronCount(), b.neuronCount());
        assertEquals(a.synapseCount(), b.synapseCount());
        Rng ra = new Rng(4);
        Rng rb = new Rng(4);
        for (int t = 0; t < 200; t++) {
            MotorCommand ca = a.step(noisyFrame(ra));
            MotorCommand cb = b.step(noisyFrame(rb));
            assertEquals(ca, cb, "diverged at tick " + t);
        }
        assertEquals(a.dopamine(), b.dopamine());
    }

    @Test
    void rewardForAKeyTeachesTheCerebellumToPressIt() {
        float rewarded = forwardShareAfterTraining(true);
        float control = forwardShareAfterTraining(false);
        assertTrue(rewarded > 0.4f && rewarded > control * 2f, "rewarded " + rewarded + ", control " + control);
    }

    private static float forwardShareAfterTraining(boolean reward) {
        Brain b = Brain.create(Genome.defaults(), 1L, 0f);
        Rng r = new Rng(5);
        MotorCommand last = MotorCommand.IDLE;
        int pressed = 0;
        for (int t = 0; t < 4000; t++) {
            SensoryFrame f = noisyFrame(r);
            f.pleasure = reward && last.forward() > 0 ? 1f : 0f;
            last = b.step(f);
            if (t >= 3000 && last.forward() > 0) {
                pressed++;
            }
        }
        return pressed / 1000f;
    }

    @Test
    void newbornsLevelTheirGazeByReflex() {
        Brain b = Brain.create(Genome.defaults(), 7L, 0f);
        b.setBabbleScale(0f);
        Rng r = new Rng(8);
        float lookingDown = 0f;
        float lookingUp = 0f;
        for (int t = 0; t < 200; t++) {
            SensoryFrame f = noisyFrame(r);
            f.set(FeelChannel.HEAD_DOWN, 0.8f);
            lookingDown += b.step(f).pitchDelta();
        }
        for (int t = 0; t < 200; t++) {
            SensoryFrame f = noisyFrame(r);
            f.set(FeelChannel.HEAD_UP, 0.8f);
            lookingUp += b.step(f).pitchDelta();
        }
        assertTrue(lookingDown < 0f, "head tipped down should make it look up: " + lookingDown);
        assertTrue(lookingUp > 0f, "head tipped up should make it look down: " + lookingUp);
    }
}
