package com.mementocraft.reallyalive;

import com.mementocraft.reallyalive.body.BrainDriven;
import com.mementocraft.reallyalive.body.BrainJumpControl;
import com.mementocraft.reallyalive.body.BrainLookControl;
import com.mementocraft.reallyalive.body.Hands;
import com.mementocraft.reallyalive.body.PlayerInputMoveControl;
import com.mementocraft.reallyalive.body.PlayerMovement;
import com.mementocraft.reallyalive.brain.Brain;
import com.mementocraft.reallyalive.brain.FeelChannel;
import com.mementocraft.reallyalive.brain.BrainIO;
import com.mementocraft.reallyalive.brain.Genome;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.OlfactoryChannel;
import com.mementocraft.reallyalive.brain.Rng;
import com.mementocraft.reallyalive.brain.SensoryFrame;
import com.mementocraft.reallyalive.brain.WordCode;
import com.mementocraft.reallyalive.mixin.MobAccessor;
import com.mementocraft.reallyalive.sense.Hearing;
import com.mementocraft.reallyalive.sense.Naming;
import com.mementocraft.reallyalive.sense.ScreenSight;
import com.mementocraft.reallyalive.sense.Senses;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.mementocraft.reallyalive.speech.Speech;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Owns every running brain. Each tick, on the server thread, it reads a villager's senses and hands
 * them to a worker thread; the resulting keys are applied on the next tick (about a human reaction
 * time). Brains are saved to {@code <world>/reallyalive/brains/<uuid>.brain}.
 */
public final class BrainManager {
    private static final Logger LOG = LogUtils.getLogger();
    private static BrainManager instance;
    /** A line not ended after this many silent ticks was babble, and is dropped unseen. */
    private static final int UNFINISHED_LINE_TICKS = 60;

    private final MinecraftServer server;
    private final Path dir;
    private final ExecutorService workers;
    private final ExecutorService io = Executors.newSingleThreadExecutor(daemon("reallyalive-io"));
    private final Map<UUID, Handle> handles = new HashMap<>();
    /**
     * When true every brain step finishes before the next tick uses its keys, so a brain never
     * misses a tick even when the server runs faster than real time (game tests, /tick sprint).
     */
    private boolean lockstep;

    /** A running brain and the villager body it drives. */
    public static final class Handle {
        final UUID id;
        final Brain brain;
        final PlayerInputMoveControl move;
        final Hands hands;
        final MoveControl originalMove;
        final LookControl originalLook;
        final JumpControl originalJump;
        final SensoryFrame frame = new SensoryFrame();
        final float[] vision = new float[frame.vision.length];
        final float[] colour = new float[frame.colour.length];
        /** The eyes last saw a screen, so the world must be looked at again as soon as it closes. */
        boolean sawScreen;
        /** Someone to talk to is near, checked now and then. */
        boolean company;
        final float[] scent = new float[OlfactoryChannel.COUNT];
        CompletableFuture<MotorCommand> pending;
        MotorCommand command = MotorCommand.IDLE;
        MotorCommand held;
        float lastHealth = -1f;
        BlockPos goal;
        double lastGoalDist = -1;
        float lastGoalFacing = -2f;
        long age;
        /** The last sound heard, so each is heard once. */
        long heard;
        /** The command whose word was said last, so a word is said once per brain step. */
        MotorCommand said;
        /** Words of the line it is saying, shown in chat when it ends the line. */
        final List<String> line = new ArrayList<>();
        long lastWordAge;
        /** The word said this tick, or null. */
        String spoken;
        /** The last line shown in chat, and how many have been. */
        String lastLine;
        int lines;

        Handle(UUID id, Brain brain, PlayerInputMoveControl move, MoveControl originalMove, LookControl originalLook,
               JumpControl originalJump) {
            this.id = id;
            this.brain = brain;
            this.move = move;
            this.originalMove = originalMove;
            this.originalLook = originalLook;
            this.originalJump = originalJump;
            this.hands = new Hands((Villager) move.mob());
        }

        public Brain brain() {
            return brain;
        }

        public Hands hands() {
            return hands;
        }

        public MotorCommand command() {
            return command;
        }

        /**
         * Presses these keys instead of the brain's, like someone else holding the keyboard. The brain
         * keeps running and feels the result. {@code null} gives the keys back to the brain.
         */
        public void hold(MotorCommand keys) {
            this.held = keys;
        }

        /** What it sensed on the last tick. */
        public SensoryFrame frame() {
            return frame;
        }

        /** The word said on the last tick, or null. */
        public String spoken() {
            return spoken;
        }

        /** The last line it said in chat, or null. */
        public String lastLine() {
            return lastLine;
        }

        /** How many lines it has said in chat. */
        public int lines() {
            return lines;
        }

        /** The pleasure felt on the last tick. */
        public float pleasure() {
            return frame.pleasure;
        }

        public BlockPos goal() {
            return goal;
        }

        public void setGoal(BlockPos goal) {
            this.goal = goal;
            this.lastGoalDist = -1;
            this.lastGoalFacing = -2f;
        }

        /** Waits for a brain step still running, so the brain can be read or saved safely. */
        void settle() {
            if (pending != null) {
                command = pending.join();
                pending = null;
            }
        }
    }

    private BrainManager(MinecraftServer server) {
        this.server = server;
        this.dir = server.getWorldPath(LevelResource.ROOT).resolve("reallyalive").resolve("brains");
        this.workers = Executors.newFixedThreadPool(Config.THREADS.get(), daemon("reallyalive-brain"));
    }

    public static void start(MinecraftServer server) {
        instance = new BrainManager(server);
    }

    public static BrainManager get() {
        return instance;
    }

    public static void stop() {
        if (instance != null) {
            instance.saveAll();
            instance.workers.shutdown();
            instance.io.shutdown();
            instance = null;
        }
    }

    public Handle handle(Villager v) {
        return handles.get(v.getUUID());
    }

    public void setLockstep(boolean lockstep) {
        this.lockstep = lockstep;
    }

    public int activeCount() {
        return handles.size();
    }

    /** Switches a villager's brain on, loading it from disk or growing a new one. */
    public Handle attach(Villager v) {
        Handle existing = handles.get(v.getUUID());
        if (existing != null) {
            return existing;
        }
        if (handles.size() >= Config.MAX_BRAINS.get()) {
            return null;
        }
        BrainData data = v.getData(Attachments.BRAIN);
        if (!data.hasGenome()) {
            data.setGenome(Genome.random(new Rng(v.getUUID().getLeastSignificantBits())).toArray());
        }
        data.setEnabled(true);
        Brain brain = load(v.getUUID());
        if (brain == null) {
            brain = Brain.create(Genome.fromArray(data.genome()), v.getUUID().getMostSignificantBits(), maturity(v));
        }

        Naming.whoItIs(v, brain);

        PlayerInputMoveControl move = new PlayerInputMoveControl(v);
        Handle h = new Handle(v.getUUID(), brain, move, v.getMoveControl(), v.getLookControl(), v.getJumpControl());
        MobAccessor body = (MobAccessor) v;
        body.reallyalive$setMoveControl(move);
        body.reallyalive$setLookControl(new BrainLookControl(v));
        body.reallyalive$setJumpControl(new BrainJumpControl(v));
        ((BrainDriven) v).reallyalive$setDriven(true);
        v.refreshDimensions();
        h.heard = Hearing.now();
        handles.put(v.getUUID(), h);
        return h;
    }

    /** Hands the body back to its normal AI. The brain is saved first. */
    public void detach(Villager v, boolean keepEnabled) {
        Handle h = handles.remove(v.getUUID());
        if (h == null) {
            return;
        }
        save(h);
        h.hands.release();
        MobAccessor body = (MobAccessor) v;
        body.reallyalive$setMoveControl(h.originalMove);
        body.reallyalive$setLookControl(h.originalLook);
        body.reallyalive$setJumpControl(h.originalJump);
        ((BrainDriven) v).reallyalive$setDriven(false);
        v.setShiftKeyDown(false);
        v.setSprinting(false);
        v.refreshDimensions();
        if (!keepEnabled) {
            v.getData(Attachments.BRAIN).setEnabled(false);
        }
    }

    /** The villager died: its brain dies with it. */
    public void forget(Villager v) {
        Handle h = handles.remove(v.getUUID());
        if (h != null) {
            h.settle();
            h.hands.release();
        }
        Path file = file(v.getUUID());
        io.execute(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                LOG.warn("Could not delete brain {}", file, e);
            }
        });
    }

    /** Before the villager ticks: take the brain's latest keys and put them on the body. */
    public void beforeTick(Villager v) {
        Handle h = handles.get(v.getUUID());
        if (h == null) {
            return;
        }
        if (h.pending != null && (lockstep || h.pending.isDone())) {
            h.command = h.pending.join();
            h.pending = null;
        }
        MotorCommand keys = h.held != null ? h.held : h.command;
        // With a screen open the mouse moves the pointer and the keys do nothing to the body.
        MotorCommand bodyKeys = h.hands.screenOpen() ? MotorCommand.IDLE : keys;
        PlayerMovement.applyBeforeTick(v, bodyKeys);
        h.move.setCommand(bodyKeys);
        h.hands.tick(keys);
        speak(v, h, keys);
    }

    /**
     * Says the word the keys ask for, once per brain step, while the brain is not stepping. Words
     * gather into a line that shows in chat when the villager ends it; babble is never ended, and is
     * dropped after a while of silence.
     */
    private static void speak(Villager v, Handle h, MotorCommand keys) {
        h.spoken = null;
        if (keys.say() >= 0 && keys != h.said && h.pending == null) {
            h.said = keys;
            String word = h.brain.word(keys.say());
            if (word != null) {
                h.spoken = word;
                Speech.villagerSays(v, word);
                if (word.equals(WordCode.TURN)) {
                    if (!h.line.isEmpty()) {
                        h.lastLine = Speech.join(h.line);
                        h.lines++;
                        Speech.showLine(v, h.lastLine);
                        h.line.clear();
                    }
                } else {
                    h.line.add(word);
                    h.lastWordAge = h.age;
                }
            }
        }
        if (!h.line.isEmpty() && h.age - h.lastWordAge > UNFINISHED_LINE_TICKS) {
            h.line.clear();
        }
    }

    /** After the villager ticks: sense the result and start the next brain step. */
    public void afterTick(Villager v) {
        Handle h = handles.get(v.getUUID());
        if (h == null || h.pending != null || !v.isAlive()) {
            return;
        }
        h.age++;
        SensoryFrame f = h.frame;
        f.clear();
        if (h.hands.screenOpen()) {
            ScreenSight.see(h.hands.screen(), h.hands.pointerX(), h.hands.pointerY(), h.vision, h.colour);
            h.sawScreen = true;
        } else if (h.sawScreen || h.age % Config.VISION_INTERVAL.get() == 0) {
            Senses.see(v, Config.VISION_RANGE.get(), h.vision, h.colour);
            h.sawScreen = false;
        }
        System.arraycopy(h.vision, 0, f.vision, 0, h.vision.length);
        System.arraycopy(h.colour, 0, f.colour, 0, h.colour.length);
        if (h.age % 10 == 0) {
            h.company = Naming.someoneNear(v);
        }
        f.company = h.company ? 1f : 0f;
        Senses.feel(v, f);
        f.set(FeelChannel.HOLDING_BLOCK, h.hands.holdingBlock() ? 1f : 0f);
        f.set(FeelChannel.TARGET_IN_REACH, h.hands.targetInReach() ? 1f : 0f);
        f.set(FeelChannel.DIG_PROGRESS, h.hands.digProgress());
        f.set(FeelChannel.USING_ITEM, h.hands.usingProgress());
        if (h.hands.screenOpen()) {
            // The screen is felt the more the longer nothing is done on it, which drives the urge to
            // leave it. The head does not move while a screen is open, so its tilt is not felt as
            // something to correct; what is under the pointer is in focus, and a stack on the
            // pointer is held.
            f.set(FeelChannel.SCREEN_OPEN, h.hands.screenBoredom());
            f.set(FeelChannel.HEAD_UP, 0f);
            f.set(FeelChannel.HEAD_DOWN, 0f);
            ItemStack onPointer = h.hands.onPointer();
            f.set(FeelChannel.POINTER_HOLDING, onPointer.isEmpty() ? 0f : 1f);
            Slot slot = h.hands.pointedSlot();
            ScreenSight.item(slot == null ? ItemStack.EMPTY : slot.getItem(), f.focus);
            ScreenSight.item(onPointer.isEmpty() ? v.getMainHandItem() : onPointer, f.held);
        } else {
            ScreenSight.target(h.hands.target(), v.level(), f.focus);
            ScreenSight.item(v.getMainHandItem(), f.held);
        }
        Senses.smell(v, f, h.scent, h.goal);
        h.heard = Hearing.listen(v, f, h.heard);
        // Someone else ending a line starts the villager's answer; words it babbled before are
        // not part of that answer.
        for (int i = 0; i < f.heardWords.size(); i++) {
            if (!f.heardOwn.get(i) && f.heardWords.get(i).equals(WordCode.TURN)) {
                h.line.clear();
            }
        }
        feelings(v, h, f);
        if (Config.NAMING.get()) {
            Naming.name(v, f);
        }
        if (h.age % 200 == 0) {
            h.brain.grow(maturity(v), 5);
        }
        if (h.age % 1200 == 0) {
            // Its name or work may have changed since it was last told.
            Naming.whoItIs(v, h.brain);
        }
        Brain brain = h.brain;
        h.pending = CompletableFuture.supplyAsync(() -> brain.step(f), workers);
    }

    /**
     * Pain from damage and burning; pleasure from spotting, reaching, nearing and turning to face the
     * goal it can see, and the inborn motives of the hands: collecting, digging and placing.
     */
    private static void feelings(Villager v, Handle h, SensoryFrame f) {
        float health = v.getHealth();
        float pain = 0f;
        if (h.lastHealth >= 0f && health < h.lastHealth) {
            pain += (h.lastHealth - health) / 4f;
        }
        if (v.isOnFire() || v.isInLava()) {
            pain += 0.5f;
        }
        h.lastHealth = health;

        float pleasure = 0f;
        if (h.goal != null) {
            Vec3 target = Vec3.atBottomCenterOf(h.goal);
            double dist = v.position().distanceTo(target);
            if (h.lastGoalDist >= 0) {
                pleasure += (float) Math.max(0, (h.lastGoalDist - dist) * 4.0);
            }
            h.lastGoalDist = dist;

            Vec3 to = target.subtract(v.getEyePosition());
            float bearing = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - v.getYRot());
            float facing = Mth.cos(bearing * Mth.DEG_TO_RAD);
            boolean inView = Math.abs(bearing) < Senses.FOV_H / 2f;
            if (inView && h.lastGoalFacing > -2f) {
                pleasure += Math.max(0f, (facing - h.lastGoalFacing) * 10f);
            } else if (inView) {
                pleasure += 0.3f; // spotted it
            }
            h.lastGoalFacing = inView ? facing : -2f;

            if (dist < 1.5) {
                pleasure = 1f;
                h.goal = null;
            }
        }
        pleasure += h.hands.motivePleasure();
        f.pain = Math.min(1f, pain);
        f.pleasure = Math.min(1f, pleasure);
    }

    /** 0 for a newborn, 1 for an adult. Vanilla babies count up from -24000 ticks of age. */
    static float maturity(Villager v) {
        if (!v.isBaby()) {
            return 1f;
        }
        return Mth.clamp(1f + v.getAge() / 24000f, 0f, 1f) * 0.7f;
    }

    // ---------------------------------------------------------------- saving

    public void saveAll() {
        for (Handle h : handles.values()) {
            save(h);
        }
    }

    public void saveAll(Collection<? extends Villager> villagers) {
        for (Villager v : villagers) {
            Handle h = handles.get(v.getUUID());
            if (h != null) {
                save(h);
            }
        }
    }

    private void save(Handle h) {
        h.settle();
        byte[] bytes;
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(1 << 20);
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(buffer))) {
                BrainIO.write(h.brain, out);
            }
            bytes = buffer.toByteArray();
        } catch (IOException e) {
            LOG.error("Could not serialise brain {}", h.id, e);
            return;
        }
        Path file = file(h.id);
        io.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
                Files.write(tmp, bytes);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOG.error("Could not save brain {}", file, e);
            }
        });
    }

    private Brain load(UUID id) {
        Path file = file(id);
        if (!Files.exists(file)) {
            return null;
        }
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(raw)))) {
            return BrainIO.read(in);
        } catch (IOException e) {
            LOG.error("Could not load brain {}, growing a new one", file, e);
            return null;
        }
    }

    private Path file(UUID id) {
        return dir.resolve(id + ".brain");
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
