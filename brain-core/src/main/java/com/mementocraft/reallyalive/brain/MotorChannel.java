package com.mementocraft.reallyalive.brain;

/** One output pool per player control. Every pool is read every tick; none suppresses another. */
public enum MotorChannel {
    FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, YAW_LEFT, YAW_RIGHT, PITCH_UP, PITCH_DOWN;

    public static final int COUNT = values().length;
}
