package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.brain.WordCode;
import com.mementocraft.reallyalive.speech.Speech;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * In-game checks of hearing and speech: game sounds reach the ears by kind and get quieter with
 * distance, chat words are heard within 16 blocks, then the end of the line, and a villager's own
 * words are heard by itself and by villagers near it. The imitation benchmark lets a newborn babble,
 * then says words to it and counts how often it says the same word back.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class SpeechTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";

    private SpeechTests() {}

    private static BrainManager.Handle listener(GameTestHelper h, Villager v) {
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        return handle;
    }

    /**
     * Naming: with another villager near, a brain villager looking at a cow is now and then told the
     * word "cow", the way parents name things for a child, so it can link the word to what its
     * concept lobe holds.
     */
    @GameTest(template = ARENA, timeoutTicks = 1200)
    public static void villagersAreToldWhatTheyAreLookingAt(GameTestHelper h) {
        Villager v = spawnMcaVillager(h, new Vec3(3.5, 2, 12.5));
        BrainManager.Handle handle = listener(h, v);
        spawnMcaVillager(h, new Vec3(3.5, 2, 10.5));
        h.spawnWithNoFreeWill(EntityType.COW, new Vec3(7.5, 2, 12.5));
        h.succeedWhen(() -> {
            v.setXRot(0f);
            h.assertTrue(handle.frame().named.contains("cow"), "not told \"cow\" yet");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void linesAreSplitIntoWordsAndJoinedAsWritten(GameTestHelper h) {
        List<String> words = WordCode.tokenize("How is it going?");
        h.assertTrue(words.equals(List.of("how", "is", "it", "going", "?")), "tokenized as " + words);
        String line = Speech.join(List.of("well", ",", "how", "is", "it", "going", "?"));
        h.assertTrue(line.equals("well, how is it going?"), "joined as '" + line + "'");
        h.succeed();
    }

    /** Index of a word in a brain's vocabulary, or -1. Only call it while the brain is not stepping. */
    private static int wordId(BrainManager.Handle handle, String word) {
        for (int i = 0; ; i++) {
            String w = handle.brain().word(i);
            if (w == null) {
                return -1;
            }
            if (w.equals(word)) {
                return i;
            }
        }
    }

    /** Index of the first plain word a brain knows, or -1. */
    private static int anyWordId(BrainManager.Handle handle) {
        for (int i = 0; ; i++) {
            String w = handle.brain().word(i);
            if (w == null) {
                return -1;
            }
            if (!w.isEmpty() && Character.isLetter(w.charAt(0))) {
                return i;
            }
        }
    }

    /**
     * A player says a line: a villager 4 blocks away hears its words, then the end of the line with
     * the sound of a player's voice; one 25 blocks away hears nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hearsChatWithinSixteenBlocks(GameTestHelper h) {
        Villager near = spawnMcaVillager(h, new Vec3(18.5, 2, 18.5));
        Villager far = spawnMcaVillager(h, new Vec3(1.5, 2, 1.5));
        BrainManager.Handle hn = listener(h, near);
        BrainManager.Handle hf = listener(h, far);
        boolean[] heard = {false, false};
        boolean[] turnWithVoice = {false};
        h.onEachTick(() -> {
            if (h.getTick() == 10) {
                Speech.playerSays(h.getLevel(), h.absoluteVec(new Vec3(21.5, 3.6, 21.5)), "an Apple!");
            }
            heard[0] |= hn.frame().heardWords.contains("apple");
            heard[1] |= hf.frame().heardWords.contains("apple");
            if (hn.frame().heardWords.contains(WordCode.TURN)) {
                turnWithVoice[0] |= hn.frame().sound[SoundChannel.PLAYER_VOICE.ordinal()] > 0.5f
                        && hn.frame().heardWords.equals(List.of("an", "apple", "!", WordCode.TURN));
            }
            if (h.getTick() == 20) {
                LOG.info("chat 'an Apple!': near villager heard 'apple' {}, line end with a player's voice {}; far villager heard it {}",
                        heard[0], turnWithVoice[0], heard[1]);
                h.assertTrue(heard[0], "the villager 4 blocks away did not hear the word");
                h.assertTrue(turnWithVoice[0], "the line and its end were not heard together with a player's voice");
                h.assertFalse(heard[1], "the villager 28 blocks away heard the word");
                h.succeed();
            }
        });
    }

    /** Real game sounds reach the ears as the right kind, louder near than far. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void hearsGameSoundsByKind(GameTestHelper h) {
        Villager near = spawnMcaVillager(h, new Vec3(12.5, 2, 12.5));
        Villager far = spawnMcaVillager(h, new Vec3(12.5, 2, 2.5));
        BrainManager.Handle hn = listener(h, near);
        BrainManager.Handle hf = listener(h, far);
        // A walker: its footsteps are real step sounds from the game.
        Villager walker = spawnMcaVillager(h, new Vec3(3.5, 2, 14.5));
        attach(h, walker).hold(new MotorCommand(1, 0, false, false, false, 0f, 0f));
        Map<SoundChannel, Float> loudNear = new EnumMap<>(SoundChannel.class);
        Map<SoundChannel, Float> loudFar = new EnumMap<>(SoundChannel.class);
        Object[][] plays = {
                {20, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, SoundChannel.BELL},
                {30, SoundEvents.WOODEN_DOOR_OPEN, SoundSource.BLOCKS, SoundChannel.DOOR},
                {40, SoundEvents.COW_AMBIENT, SoundSource.NEUTRAL, SoundChannel.ANIMAL},
                {50, SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, SoundChannel.HOSTILE},
                {60, SoundEvents.VILLAGER_AMBIENT, SoundSource.NEUTRAL, SoundChannel.VILLAGER_VOICE},
                {70, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, SoundChannel.WATER},
                {80, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, SoundChannel.BUILDING},
                {90, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, SoundChannel.DIGGING},
        };
        BlockPos source = new BlockPos(14, 2, 12);
        h.onEachTick(() -> {
            for (Object[] play : plays) {
                if (h.getTick() == (int) play[0]) {
                    h.getLevel().playSound(null, h.absolutePos(source), (SoundEvent) play[1], (SoundSource) play[2], 1f, 1f);
                }
            }
            for (SoundChannel c : SoundChannel.values()) {
                loudNear.merge(c, hn.frame().sound[c.ordinal()], Math::max);
                loudFar.merge(c, hf.frame().sound[c.ordinal()], Math::max);
            }
            if (h.getTick() == 110) {
                LOG.info("loudest sounds heard 2 blocks away: {}; 10 blocks away: {}", loudNear, loudFar);
                for (Object[] play : plays) {
                    SoundChannel c = (SoundChannel) play[3];
                    h.assertTrue(loudNear.get(c) > 0f, c + " was not heard");
                    // Villagers talk on their own, so the far one may hear its own voice loudest.
                    if (c != SoundChannel.VILLAGER_VOICE) {
                        h.assertTrue(loudNear.get(c) > loudFar.get(c), c + " was no louder 2 blocks away than 10");
                    }
                }
                h.assertTrue(loudNear.get(SoundChannel.STEPS) > 0f, "a villager walking past was not heard");
                h.succeed();
            }
        });
    }

    /**
     * A villager says a word and ends its line: it hears both as its own, a villager nearby hears
     * them as someone else's, and the line shows in chat.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hearsItsOwnWordsAndOthersHearThem(GameTestHelper h) {
        Villager speaker = spawnMcaVillager(h, new Vec3(10.5, 2, 12.5));
        Villager other = spawnMcaVillager(h, new Vec3(14.5, 2, 12.5));
        BrainManager.Handle hs = listener(h, speaker);
        BrainManager.Handle ho = listener(h, other);
        int id = anyWordId(hs);
        int turn = wordId(hs, WordCode.TURN);
        h.assertTrue(id >= 0 && turn >= 0, "the brain knows no words");
        String word = hs.brain().word(id);
        boolean[] heard = {false, false, false};
        h.onEachTick(() -> {
            if (h.getTick() == 10) {
                hs.hold(MotorCommand.IDLE.saying(id));
            } else if (h.getTick() == 12) {
                hs.hold(MotorCommand.IDLE.saying(turn));
            } else if (h.getTick() == 14) {
                hs.hold(MotorCommand.IDLE);
            }
            var fs = hs.frame();
            for (int i = 0; i < fs.heardWords.size(); i++) {
                if (fs.heardOwn.get(i)) {
                    heard[0] |= fs.heardWords.get(i).equals(word);
                    heard[2] |= fs.heardWords.get(i).equals(WordCode.TURN);
                }
            }
            var fo = ho.frame();
            for (int i = 0; i < fo.heardWords.size(); i++) {
                heard[1] |= fo.heardWords.get(i).equals(word) && !fo.heardOwn.get(i);
            }
            if (h.getTick() == 24) {
                LOG.info("villager said '{}' and ended the line: heard as its own {}, its line end as its own {},"
                        + " by the villager 4 blocks away {}; chat line '{}'", word, heard[0], heard[2], heard[1], hs.lastLine());
                h.assertTrue(heard[0], "the speaker did not hear its own word as its own");
                h.assertTrue(heard[2], "the speaker did not hear the end of its own line");
                h.assertTrue(heard[1], "the villager nearby did not hear the word");
                h.assertTrue(word.equals(hs.lastLine()), "the line in chat was '" + hs.lastLine() + "', not '" + word + "'");
                h.succeed();
            }
        });
    }

    // ------------------------------------------------------------------ imitation benchmark

    private static final int BABBLE_TICKS = 20000;
    private static final int TRIALS = 200;
    private static final int WINDOW = 40;
    private static final String[] WORDS = {"hello", "food", "home", "come", "yes", "no", "friend", "water"};

    /**
     * A newborn babbles on its own for 20k ticks, hearing itself. Then, every other 40-tick window, a
     * player beside it says one word; the windows in between are silent. Reports how often it says
     * the word back in a window where it heard it, against how often it says that same word in a
     * silent window.
     */
    @GameTest(template = ARENA, timeoutTicks = BABBLE_TICKS + TRIALS * WINDOW + 400, required = false)
    public static void newbornSaysBackWordsItHears(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(BABBLE_TICKS + TRIALS * WINDOW + 400);
        Villager v = spawnMcaVillager(h, new Vec3(12.5, 2, 12.5));
        v.setAge(-24000);
        BrainManager.Handle handle = attach(h, v);
        int[] tick = {0};
        int[] babbled = {0};
        String[] target = {null};
        boolean[] saidTarget = {false};
        int[] wordsInWindow = {0};
        // [windows, of those with the target said] for heard and silent windows.
        int[] heard = {0, 0};
        int[] silent = {0, 0};
        int[] anyWord = {0, 0};
        java.util.Random random = new java.util.Random(7);
        h.onEachTick(() -> {
            tick[0]++;
            String said = handle.spoken();
            boolean spoke = said != null && !said.equals(WordCode.TURN);
            if (tick[0] <= BABBLE_TICKS) {
                if (spoke) {
                    babbled[0]++;
                }
                return;
            }
            int t = tick[0] - BABBLE_TICKS - 1;
            int trial = t / WINDOW;
            if (trial >= TRIALS) {
                return;
            }
            if (t % WINDOW == 0) {
                target[0] = WORDS[random.nextInt(WORDS.length)];
                saidTarget[0] = false;
                wordsInWindow[0] = 0;
                if (trial % 2 == 0) {
                    // Beside it, at head height.
                    Speech.playerSays(h.getLevel(), v.getEyePosition().add(1.5, 0, 0), target[0]);
                }
            }
            if (spoke) {
                wordsInWindow[0]++;
                saidTarget[0] |= said.equals(target[0]);
            }
            if (t % WINDOW == WINDOW - 1) {
                int[] counter = trial % 2 == 0 ? heard : silent;
                counter[0]++;
                if (saidTarget[0]) {
                    counter[1]++;
                }
                if (wordsInWindow[0] > 0) {
                    anyWord[trial % 2]++;
                }
            }
            if (trial == TRIALS - 1 && t % WINDOW == WINDOW - 1) {
                int echo = 100 * heard[1] / heard[0];
                int chance = 100 * silent[1] / silent[0];
                String report = String.format("after %d ticks of babbling (%d words said), it said the word it had just heard"
                                + " back in %d%% of %d tries, and that same word in %d%% of %d silent windows;"
                                + " it said something in %d%% of heard windows and %d%% of silent ones",
                        BABBLE_TICKS, babbled[0], echo, heard[0], chance, silent[0],
                        100 * anyWord[0] / heard[0], 100 * anyWord[1] / silent[0]);
                LOG.info("imitation result: {}", report);
                if (echo >= 20 && echo > 2 * chance) {
                    h.succeed();
                } else {
                    h.fail("not imitating yet: " + report);
                }
            }
        });
    }

    private static final int HELLO_TRIES = 5;
    private static final int HELLO_WAIT = 300;

    /**
     * A player says "hello." beside an adult brain villager, five times, and the benchmark logs
     * whatever it says back. Passes if it answers at least once.
     */
    @GameTest(template = ARENA, timeoutTicks = HELLO_TRIES * HELLO_WAIT + 200, required = false)
    public static void adultAnswersHello(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager v = spawnMcaVillager(h, new Vec3(12.5, 2, 12.5));
        BrainManager.Handle handle = attach(h, v);
        List<String> answers = new java.util.ArrayList<>();
        int[] linesBefore = {0};
        h.onEachTick(() -> {
            int t = (int) h.getTick() - 20;
            if (t < 0) {
                return;
            }
            int attempt = t / HELLO_WAIT;
            if (t % HELLO_WAIT == 0 && attempt < HELLO_TRIES) {
                linesBefore[0] = handle.lines();
                Speech.playerSays(h.getLevel(), v.getEyePosition().add(1.5, 0, 0), "hello.");
            }
            if (t % HELLO_WAIT == HELLO_WAIT - 1 && attempt < HELLO_TRIES) {
                String answer = handle.lines() > linesBefore[0] ? handle.lastLine() : null;
                answers.add(answer == null ? "(nothing)" : answer);
                LOG.info("hello benchmark: a player said \"hello.\", try {}: the villager answered {}",
                        attempt + 1, answer == null ? "nothing" : "\"" + answer + "\"");
                if (attempt == HELLO_TRIES - 1) {
                    LOG.info("hello benchmark result: answers {}", answers);
                    if (answers.stream().anyMatch(a -> !a.equals("(nothing)"))) {
                        h.succeed();
                    } else {
                        h.fail("never answered hello: " + answers);
                    }
                }
            }
        });
    }

    /**
     * A villager knows who it is: asked its name it gives the name MCA gave it, and asked its work
     * it gives its profession.
     */
    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void villagerSaysItsNameAndWork(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager v = spawnMcaVillager(h, new Vec3(12.5, 2, 12.5));
        v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER));
        if (!v.hasCustomName()) {
            v.setCustomName(net.minecraft.network.chat.Component.literal("Tibbs"));
        }
        String name = v.getCustomName().getString().trim().split("\\s+")[0].toLowerCase();
        BrainManager.Handle handle = attach(h, v);
        String[] questions = {"what's your name?", "what do you do for work?"};
        String[] expected = {name, "farmer"};
        String[] answers = new String[2];
        int[] linesBefore = {0};
        h.onEachTick(() -> {
            int t = (int) h.getTick() - 20;
            if (t < 0) {
                return;
            }
            int q = t / 400;
            if (t % 400 == 0 && q < 2) {
                linesBefore[0] = handle.lines();
                Speech.playerSays(h.getLevel(), v.getEyePosition().add(1.5, 0, 0), questions[q]);
            }
            if (t % 400 == 399 && q < 2) {
                answers[q] = handle.lines() > linesBefore[0] ? handle.lastLine() : "(nothing)";
                LOG.info("self benchmark: asked \"{}\", the villager ({}) answered \"{}\"", questions[q], name, answers[q]);
                if (q == 1) {
                    for (int i = 0; i < 2; i++) {
                        h.assertTrue(answers[i].toLowerCase().contains(expected[i]),
                                "asked \"" + questions[i] + "\" it said \"" + answers[i] + "\", not " + expected[i]);
                    }
                    h.succeed();
                }
            }
        });
    }
}
