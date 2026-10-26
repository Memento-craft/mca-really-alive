package com.mementocraft.reallyalive.brain;

/**
 * Turns motor pool firing rates into player controls. Opposing keys are push-pull pairs; every
 * control is decided independently, so any combination a player could hold is possible.
 * Hysteresis keeps a key held steadily instead of flickering on noisy rates.
 */
public final class MotorDecoder {
    public static final float KEY_ON = 0.22f;
    public static final float KEY_OFF = 0.10f;
    public static final float LOOK_DEADZONE = 0.04f;
    /** Fastest a villager can turn, in degrees per tick; about a brisk mouse flick. */
    public static final float MAX_YAW_PER_TICK = 24f;
    public static final float MAX_PITCH_PER_TICK = 16f;

    private int forward;
    private int strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;
    private boolean attack;
    private boolean use;
    private boolean inventory;
    private boolean hotbar;

    public MotorCommand decode(float[] poolRates) {
        forward = axis(forward, rate(poolRates, MotorChannel.FORWARD) - rate(poolRates, MotorChannel.BACK));
        strafe = axis(strafe, rate(poolRates, MotorChannel.LEFT) - rate(poolRates, MotorChannel.RIGHT));
        jump = button(jump, rate(poolRates, MotorChannel.JUMP));
        sneak = button(sneak, rate(poolRates, MotorChannel.SNEAK));
        sprint = button(sprint, rate(poolRates, MotorChannel.SPRINT));
        attack = button(attack, rate(poolRates, MotorChannel.ATTACK));
        use = button(use, rate(poolRates, MotorChannel.USE));
        inventory = button(inventory, rate(poolRates, MotorChannel.INVENTORY));
        hotbar = button(hotbar, rate(poolRates, MotorChannel.HOTBAR));
        float yaw = look(rate(poolRates, MotorChannel.YAW_RIGHT) - rate(poolRates, MotorChannel.YAW_LEFT));
        float pitch = look(rate(poolRates, MotorChannel.PITCH_DOWN) - rate(poolRates, MotorChannel.PITCH_UP));
        return new MotorCommand(forward, strafe, jump, sneak, sprint,
                yaw * MAX_YAW_PER_TICK, pitch * MAX_PITCH_PER_TICK, attack, use, inventory, hotbar);
    }

    private static float rate(float[] rates, MotorChannel c) {
        return rates[c.ordinal()];
    }

    private static int axis(int held, float diff) {
        if (held != 0 && diff * held > KEY_OFF) {
            return held;
        }
        if (diff > KEY_ON) {
            return 1;
        }
        if (diff < -KEY_ON) {
            return -1;
        }
        return 0;
    }

    private static boolean button(boolean held, float rate) {
        return held ? rate > KEY_OFF : rate > KEY_ON;
    }

    private static float look(float diff) {
        if (Math.abs(diff) < LOOK_DEADZONE) {
            return 0f;
        }
        return Math.max(-1f, Math.min(1f, diff));
    }

    int[] state() {
        return new int[] {forward, strafe, jump ? 1 : 0, sneak ? 1 : 0, sprint ? 1 : 0, attack ? 1 : 0, use ? 1 : 0,
                inventory ? 1 : 0, hotbar ? 1 : 0};
    }

    /** How many numbers {@link #state()} holds; brains saved before version 8 hold 7. */
    static final int STATE_SIZE = 9;
    static final int STATE_SIZE_V7 = 7;

    void setState(int[] s) {
        forward = s[0];
        strafe = s[1];
        jump = s[2] != 0;
        sneak = s[3] != 0;
        sprint = s[4] != 0;
        attack = s[5] != 0;
        use = s[6] != 0;
        inventory = s.length > 7 && s[7] != 0;
        hotbar = s.length > 8 && s[8] != 0;
    }
}
