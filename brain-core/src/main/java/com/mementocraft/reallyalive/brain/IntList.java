package com.mementocraft.reallyalive.brain;

import java.util.Arrays;

/** Growable list of ints without boxing. */
final class IntList {
    int size;
    int[] data;

    IntList(int capacity) {
        data = new int[Math.max(4, capacity)];
    }

    void add(int v) {
        if (size == data.length) {
            data = Arrays.copyOf(data, size * 2);
        }
        data[size++] = v;
    }

    int get(int i) {
        return data[i];
    }
}
