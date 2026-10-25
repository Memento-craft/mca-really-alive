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
    /**
     * The shared words this one grows from (the English every villager knows), which must not
     * grow any more; its words keep their numbers and this one's own words come after them.
     */
    private final Vocabulary shared;
    private final int sharedSize;
    private final Map<String, Integer> ids = new HashMap<>();
    private final List<String> words = new ArrayList<>();
    private final List<int[]> features = new ArrayList<>();

    public Vocabulary() {
        this(null);
    }

    private Vocabulary(Vocabulary shared) {
        this.shared = shared;
        this.sharedSize = shared == null ? 0 : shared.size();
    }

    /** The word's cell number, giving a new word the next free cell. */
    public int id(String word) {
        int found = find(word);
        if (found >= 0) {
            return found;
        }
        int id = sharedSize + words.size();
        ids.put(word, id);
        words.add(word);
        features.add(word.equals(WordCode.TURN) ? new int[0] : WordCode.features(word));
        return id;
    }

    /** The word's cell number, or -1 if it was never heard. */
    public int find(String word) {
        if (shared != null) {
            int id = shared.find(word);
            if (id >= 0) {
                return id;
            }
        }
        Integer id = ids.get(word);
        return id == null ? -1 : id;
    }

    public int size() {
        return sharedSize + words.size();
    }

    public String word(int id) {
        return id < sharedSize ? shared.word(id) : words.get(id - sharedSize);
    }

    /** How the word sounds, see {@link WordCode}; none for the end of a turn, which is silence. */
    public int[] features(int id) {
        return id < sharedSize ? shared.features(id) : features.get(id - sharedSize);
    }

    /**
     * A copy that can grow on its own while keeping every existing word's number. It shares this
     * one's words instead of copying them (there are tens of thousands), so this one must not grow
     * afterwards.
     */
    public Vocabulary copy() {
        return new Vocabulary(this);
    }

    /** Writes the words from number from onwards. */
    public void write(DataOutput out, int from) throws IOException {
        out.writeInt(size() - from);
        for (int i = from; i < size(); i++) {
            out.writeUTF(word(i));
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
