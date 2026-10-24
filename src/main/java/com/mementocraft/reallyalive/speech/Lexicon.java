package com.mementocraft.reallyalive.speech;

import com.mementocraft.reallyalive.brain.SenseLayout;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The words of one world. Each new word heard takes the next free slot, so slot k is always the same
 * word for every villager in the world. Once all {@link SenseLayout#VOCABULARY} slots are taken,
 * new words are not heard as words.
 */
public final class Lexicon extends SavedData {
    private static final String NAME = "reallyalive_lexicon";
    private final List<String> words = new ArrayList<>();
    private final Map<String, Integer> slots = new HashMap<>();

    public Lexicon() {}

    public static Lexicon get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Lexicon::new, Lexicon::load, null), NAME);
    }

    private static Lexicon load(CompoundTag tag, HolderLookup.Provider registries) {
        Lexicon lexicon = new Lexicon();
        ListTag list = tag.getList("words", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            lexicon.add(list.getString(i));
        }
        return lexicon;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (String word : words) {
            list.add(StringTag.valueOf(word));
        }
        tag.put("words", list);
        return tag;
    }

    /** Splits chat into words: runs of letters, lowercased. */
    public static List<String> tokenize(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        String lower = text.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetter(c)) {
                word.append(c);
            } else if (!word.isEmpty()) {
                out.add(word.toString());
                word.setLength(0);
            }
        }
        if (!word.isEmpty()) {
            out.add(word.toString());
        }
        return out;
    }

    /** The word's slot, giving it the next free one if it is new; -1 once the vocabulary is full. */
    public int slot(String word) {
        Integer slot = slots.get(word);
        if (slot != null) {
            return slot;
        }
        if (words.size() >= SenseLayout.VOCABULARY) {
            return -1;
        }
        setDirty();
        return add(word);
    }

    /** The word in a slot, or null if no word has taken it yet. */
    public String word(int slot) {
        return slot >= 0 && slot < words.size() ? words.get(slot) : null;
    }

    public int size() {
        return words.size();
    }

    private int add(String word) {
        int slot = words.size();
        words.add(word);
        slots.put(word, slot);
        return slot;
    }
}
