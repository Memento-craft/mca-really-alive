package com.mementocraft.reallyalive.brain;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The words a brain has heard, each with its own word cell number. */
public final class Vocabulary {
    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> words = new ArrayList<>();
    private final List<int[]> features = new ArrayList<>();

    /** The word's cell number, giving a new word the next free cell. */
    public int id(String word) {
        Integer id = ids.get(word);
        if (id == null) {
            id = words.size();
            ids.put(word, id);
            words.add(word);
            features.add(word.equals(WordCode.TURN) ? new int[0] : WordCode.features(word));
        }
        return id;
    }

    /** The word's cell number, or -1 if it was never heard. */
    public int find(String word) {
        Integer id = ids.get(word);
        return id == null ? -1 : id;
    }

    public int size() {
        return words.size();
    }

    public String word(int id) {
        return words.get(id);
    }

    /** How the word sounds, see {@link WordCode}; none for the end of a turn, which is silence. */
    public int[] features(int id) {
        return features.get(id);
    }

    /** A copy that can grow on its own while keeping every existing word's number. */
    public Vocabulary copy() {
        Vocabulary v = new Vocabulary();
        for (String w : words) {
            v.id(w);
        }
        return v;
    }

    /** Writes the words from number from onwards. */
    public void write(DataOutput out, int from) throws IOException {
        out.writeInt(words.size() - from);
        for (int i = from; i < words.size(); i++) {
            out.writeUTF(words.get(i));
        }
    }

    /** Reads words written by write and adds them in order. */
    public void readInto(DataInput in) throws IOException {
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            id(in.readUTF());
        }
    }
}
