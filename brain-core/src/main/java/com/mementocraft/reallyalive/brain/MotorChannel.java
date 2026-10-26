package com.mementocraft.reallyalive.brain;

/** One output pool per player control. Every pool is read every tick; none suppresses another. */
public enum MotorChannel {
    FORWARD, BACK, LEFT, RIGHT, JUMP, SNEAK, SPRINT, YAW_LEFT, YAW_RIGHT, PITCH_UP, PITCH_DOWN,
    /** Left mouse button: hit, or dig the block in the crosshair while held. */
    ATTACK,
    /** Right mouse button: place the held block against the face in the crosshair. */
    USE,
    // Added after the first brains were saved; saved brains get these pools on load.
    /** The inventory key (E): opens its own inventory, or closes whatever screen is open. */
    INVENTORY,
    /** The mouse wheel: the next thing it carries comes into its hand. */
    HOTBAR;

    public static final int COUNT = values().length;
    /** Pools a brain saved before brain file version 8 has. */
    static final int COUNT_V7 = 13;
}
