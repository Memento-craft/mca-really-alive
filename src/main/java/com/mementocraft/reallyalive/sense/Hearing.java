package com.mementocraft.reallyalive.sense;

import com.mementocraft.reallyalive.brain.SensoryFrame;
import com.mementocraft.reallyalive.brain.SoundChannel;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * What can be heard in each world: game sounds sorted into the kinds the ears tell apart, and words
 * said in chat or by brain villagers. Each sound is heard once by every villager in range, louder the
 * nearer it is, much as a player hears it.
 */
public final class Hearing {
    /** How far a sound of volume 1 carries, as for players. Louder sounds carry further. */
    public static final double RANGE = 16.0;
    /** Sounds are kept this many ticks, long enough for every villager to listen once after them. */
    private static final long KEEP = 20;

    private record Heard(long seq, long time, Vec3 pos, int channel, float volume, int word) {}

    private static final Map<Level, ArrayDeque<Heard>> HEARD = new IdentityHashMap<>();
    private static long seq;

    private Hearing() {}

    /** Forgets everything, for a new server. */
    public static synchronized void reset() {
        HEARD.clear();
    }

    /** The newest sound so far: a villager that starts listening now hears only what comes after. */
    public static synchronized long now() {
        return seq;
    }

    public static synchronized void sound(Level level, Vec3 pos, SoundChannel channel, float volume) {
        add(level, pos, channel.ordinal(), volume, -1);
    }

    /** A word said at a place, by its lexicon slot. Words carry as far as a voice. */
    public static synchronized void word(Level level, Vec3 pos, int slot) {
        add(level, pos, -1, 1f, slot);
    }

    private static void add(Level level, Vec3 pos, int channel, float volume, int word) {
        long time = level.getGameTime();
        ArrayDeque<Heard> queue = HEARD.computeIfAbsent(level, l -> new ArrayDeque<>());
        while (!queue.isEmpty() && queue.peekFirst().time() < time - KEEP) {
            queue.pollFirst();
        }
        queue.addLast(new Heard(++seq, time, pos, channel, volume, word));
    }

    /**
     * Puts into the frame everything heard by this listener since {@code after}, each sound kind at
     * the loudness of its loudest sound, and returns the point to listen from next time.
     */
    public static synchronized long listen(Entity listener, SensoryFrame f, long after) {
        ArrayDeque<Heard> queue = HEARD.get(listener.level());
        if (queue == null) {
            return seq;
        }
        Vec3 ear = listener.getEyePosition();
        for (Heard h : queue) {
            if (h.seq() <= after) {
                continue;
            }
            double range = RANGE * Math.max(1f, h.volume());
            double dist = ear.distanceTo(h.pos());
            if (dist >= range) {
                continue;
            }
            if (h.word() >= 0) {
                f.hear(h.word());
                continue;
            }
            float loudness = (float) (1.0 - dist / range) * Math.min(1f, h.volume());
            if (loudness > f.sound[h.channel()]) {
                f.sound[h.channel()] = SensoryFrame.clamp01(loudness);
            }
        }
        return seq;
    }

    /** Which kind of sound a game sound is, or null for one the ears ignore (music, interface). */
    public static SoundChannel classify(Holder<SoundEvent> sound, SoundSource source) {
        if (source == SoundSource.MUSIC || source == SoundSource.RECORDS || source == SoundSource.MASTER
                || source == SoundSource.AMBIENT && !sound.value().getLocation().getPath().contains("water")) {
            return null;
        }
        ResourceLocation id = sound.value().getLocation();
        String path = id.getPath();
        if (path.contains("bell")) {
            return SoundChannel.BELL;
        }
        if (path.contains("door") || path.contains("gate")) {
            return SoundChannel.DOOR;
        }
        if (path.endsWith(".step")) {
            return SoundChannel.STEPS;
        }
        if (path.contains("water") || path.contains("swim") || path.contains("splash") || path.contains("bubble")) {
            return SoundChannel.WATER;
        }
        if (path.endsWith(".place")) {
            return SoundChannel.BUILDING;
        }
        if (path.endsWith(".break") || path.endsWith(".hit")) {
            return SoundChannel.DIGGING;
        }
        if (source == SoundSource.HOSTILE) {
            return SoundChannel.HOSTILE;
        }
        if (path.contains("villager") || id.getNamespace().equals("mca")) {
            return SoundChannel.VILLAGER_VOICE;
        }
        if (source == SoundSource.PLAYERS && path.startsWith("entity.player")) {
            return SoundChannel.PLAYER_VOICE;
        }
        if (source == SoundSource.NEUTRAL) {
            return SoundChannel.ANIMAL;
        }
        return null;
    }
}
