package com.mementocraft.reallyalive.speech;

import com.mementocraft.reallyalive.Config;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.sense.Hearing;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Words spoken aloud: a player's chat heard by villagers near them, and a brain villager's voice. */
public final class Speech {
    private Speech() {}

    /**
     * Someone says a line of chat at a place: every word in it is heard by villagers within
     * {@link Hearing#RANGE}, each new word taking the next free lexicon slot.
     */
    public static void playerSays(Level level, Vec3 mouth, String text) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Lexicon lexicon = Lexicon.get(server.getServer());
        Hearing.sound(level, mouth, SoundChannel.PLAYER_VOICE, 1f);
        for (String word : Lexicon.tokenize(text)) {
            int slot = lexicon.slot(word);
            if (slot >= 0) {
                Hearing.word(level, mouth, slot);
            }
        }
    }

    /**
     * A brain villager says the word in a lexicon slot. Villagers nearby, itself included, hear it;
     * players nearby read it in chat. A slot no word has taken yet is babble: heard, but not shown.
     */
    public static void villagerSays(Villager v, int slot) {
        if (!(v.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 mouth = v.getEyePosition();
        Hearing.sound(level, mouth, SoundChannel.VILLAGER_VOICE, 1f);
        Hearing.word(level, mouth, slot);
        String word = Lexicon.get(level.getServer()).word(slot);
        if (word == null || !Config.SPEECH_IN_CHAT.get()) {
            return;
        }
        Component line = Component.translatable("chat.type.text", v.getDisplayName(), Component.literal(word));
        double range = Hearing.RANGE * Hearing.RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mouth) < range) {
                p.sendSystemMessage(line);
            }
        }
    }
}
