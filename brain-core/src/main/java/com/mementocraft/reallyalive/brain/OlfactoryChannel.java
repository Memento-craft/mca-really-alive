package com.mementocraft.reallyalive.brain;

/** Scent "chemicals" that drift from nearby things, strongest close by. */
public enum OlfactoryChannel {
    FOOD, HOSTILE, ANIMAL, VILLAGER, PLAYER, SMOKE, FLOWER, WATER,
    /** Scent given off by a goal placed with the test command. */
    GOAL;

    public static final int COUNT = values().length;
}
