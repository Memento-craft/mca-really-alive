package com.mementocraft.reallyalive.brain;

/** One output pool per player control. Every pool is read every tick; none suppresses another. */
public enum MotorChannel {
    FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, YAW_LEFT, YAW_RIGHT, PITCH_UP, PITCH_DOWN,
    /** Left mouse button: hit, or dig the block in the crosshair while held. */
    ATTACK,
    /** Right mouse button: place the held block against the face in the crosshair. */
    USE;

    public static final int COUNT = values().length;
}
