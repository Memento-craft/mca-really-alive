package com.mementocraft.reallyalive.speech;

import com.mementocraft.reallyalive.Config;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.brain.WordCode;
import com.mementocraft.reallyalive.sense.Hearing;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Words spoken aloud: a player's chat heard by villagers near them, and a brain villager's voice. */
public final class Speech {
    private Speech() {}

    /**
     * Someone says a line of chat at a place: villagers within {@link Hearing#RANGE} hear its words
     * in order, then the end of the line, together with the sound of a player's voice.
     */
    public static void playerSays(Level level, Vec3 mouth, String text) {
        playerSays(level, mouth, text, null);
    }

    /** As above, said by someone listeners know by this name. */
    public static void playerSays(Level level, Vec3 mouth, String text, String name) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        List<String> words = WordCode.tokenize(text);
        if (words.isEmpty()) {
            return;
        }
        Hearing.sound(level, mouth, SoundChannel.PLAYER_VOICE, 1f);
        for (String word : words) {
            Hearing.word(level, mouth, word, null, name);
        }
        Hearing.word(level, mouth, WordCode.TURN, null, name);
    }

    /**
     * A brain villager says one word, or {@link WordCode#TURN} to end its line. Villagers nearby,
     * itself included, hear it as it is said.
     */
    public static void villagerSays(Villager v, String word) {
        if (!(v.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 mouth = v.getEyePosition();
        Hearing.sound(level, mouth, SoundChannel.VILLAGER_VOICE, 1f);
        Hearing.word(level, mouth, word, v.getUUID(), v.hasCustomName() ? v.getCustomName().getString() : null);
    }

    /** Shows a finished line in the chat of players within {@link Hearing#RANGE}. */
    public static void showLine(Villager v, String line) {
        if (!(v.level() instanceof ServerLevel level) || !Config.SPEECH_IN_CHAT.get()) {
            return;
        }
        Component message = Component.translatable("chat.type.text", v.getDisplayName(), Component.literal(line));
        double range = Hearing.RANGE * Hearing.RANGE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(v.getEyePosition()) < range) {
                p.sendSystemMessage(message);
            }
        }
    }

    /** Words joined as written: a space between words, none before . ? ! or ,. */
    public static String join(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            boolean punctuation = w.length() == 1 && ".?!,".indexOf(w.charAt(0)) >= 0;
            if (!sb.isEmpty() && !punctuation) {
                sb.append(' ');
            }
            sb.append(w);
        }
        return sb.toString();
    }
}
