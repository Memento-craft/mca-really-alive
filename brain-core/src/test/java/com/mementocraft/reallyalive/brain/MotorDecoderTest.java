package com.mementocraft.reallyalive.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MotorDecoderTest {
    private static float[] rates(Object... pairs) {
        float[] r = new float[MotorChannel.COUNT];
        for (int i = 0; i < pairs.length; i += 2) {
            r[((MotorChannel) pairs[i]).ordinal()] = (Float) pairs[i + 1];
        }
        return r;
    }

    @Test
    void everyControlCanBeHeldAtOnceLikeAPlayer() {
        MotorDecoder d = new MotorDecoder();
        MotorCommand c = d.decode(rates(
                MotorChannel.FORWARD, 0.6f, MotorChannel.LEFT, 0.5f, MotorChannel.JUMP, 0.5f,
                MotorChannel.SPRINT, 0.5f, MotorChannel.YAW_RIGHT, 0.4f, MotorChannel.PITCH_UP, 0.3f));
        assertEquals(1, c.forward());
        assertEquals(1, c.strafe());
        assertTrue(c.jump());
        assertTrue(c.sprint());
        assertFalse(c.sneak());
        assertTrue(c.yawDelta() > 0f);
        assertTrue(c.pitchDelta() < 0f);
    }

    @Test
    void opposingKeysCancelLikePressingBoth() {
        MotorDecoder d = new MotorDecoder();
        MotorCommand c = d.decode(rates(MotorChannel.FORWARD, 0.6f, MotorChannel.BACK, 0.6f));
        assertEquals(0, c.forward());
    }

    @Test
    void heldKeysDoNotFlickerOnSmallDips() {
        MotorDecoder d = new MotorDecoder();
        assertTrue(d.decode(rates(MotorChannel.JUMP, 0.3f)).jump());
        assertTrue(d.decode(rates(MotorChannel.JUMP, 0.15f)).jump(), "still held above the release level");
        assertFalse(d.decode(rates(MotorChannel.JUMP, 0.05f)).jump());
        assertFalse(d.decode(rates(MotorChannel.JUMP, 0.15f)).jump(), "needs the press level to start again");
    }

    @Test
    void turningIsContinuousAndCapped() {
        MotorDecoder d = new MotorDecoder();
        float slow = d.decode(rates(MotorChannel.YAW_LEFT, 0.2f)).yawDelta();
        float fast = d.decode(rates(MotorChannel.YAW_LEFT, 0.8f)).yawDelta();
        float max = d.decode(rates(MotorChannel.YAW_LEFT, 5f)).yawDelta();
        assertTrue(slow < 0f && fast < slow, slow + " " + fast);
        assertEquals(-MotorDecoder.MAX_YAW_PER_TICK, max, 1e-4);
    }
}
