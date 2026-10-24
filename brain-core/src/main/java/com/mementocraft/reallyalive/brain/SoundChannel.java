package com.mementocraft.reallyalive.brain;

/** Kinds of sound the ears tell apart, each 0..1 by loudness. Heard words come separately. */
public enum SoundChannel {
    STEPS, VILLAGER_VOICE, PLAYER_VOICE, HOSTILE, ANIMAL, DIGGING, BUILDING, DOOR, WATER, BELL;

    public static final int COUNT = values().length;
}
