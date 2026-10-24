package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.SenseLayout;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.speech.Lexicon;
import com.mementocraft.reallyalive.speech.Speech;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * distance, chat words take lexicon slots and are heard within 16 blocks, and a villager's own
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

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void lexiconGivesEachNewWordTheNextSlot(GameTestHelper h) {
        h.assertTrue(Lexicon.tokenize("Hello, hello WORLD! it's 3pm").equals(List.of("hello", "hello", "world", "it", "s", "pm")),
                "tokenized as " + Lexicon.tokenize("Hello, hello WORLD! it's 3pm"));
        Lexicon lexicon = new Lexicon();
        h.assertTrue(lexicon.slot("hello") == 0 && lexicon.slot("world") == 1 && lexicon.slot("hello") == 0,
                "words did not take slots in order");
        for (int i = 2; i < SenseLayout.VOCABULARY; i++) {
            lexicon.slot("w" + (char) ('a' + i / 26) + (char) ('a' + i % 26));
        }
        h.assertTrue(lexicon.slot("overflow") == -1, "a word beyond the vocabulary got a slot");
        h.assertTrue("world".equals(lexicon.word(1)) && lexicon.word(SenseLayout.VOCABULARY) == null, "slots read back wrong");
        h.succeed();
    }

    /** A player says a line: a villager 4 blocks away hears its words, one 25 blocks away does not. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hearsChatWithinSixteenBlocks(GameTestHelper h) {
        Villager near = spawnMcaVillager(h, new Vec3(18.5, 2, 18.5));
        Villager far = spawnMcaVillager(h, new Vec3(1.5, 2, 1.5));
        BrainManager.Handle hn = listener(h, near);
        BrainManager.Handle hf = listener(h, far);
        int slot = Lexicon.get(h.getLevel().getServer()).slot("apple");
        boolean[] heard = {false, false};
        float[] voice = {0f};
        h.onEachTick(() -> {
            if (h.getTick() == 10) {
                Speech.playerSays(h.getLevel(), h.absoluteVec(new Vec3(21.5, 3.6, 21.5)), "an Apple!");
            }
            heard[0] |= hn.frame().words[slot] > 0;
            heard[1] |= hf.frame().words[slot] > 0;
            voice[0] = Math.max(voice[0], hn.frame().sound[SoundChannel.PLAYER_VOICE.ordinal()]);
            if (h.getTick() == 20) {
                LOG.info("chat 'an Apple!': near villager heard 'apple' {}, voice {}; far villager heard it {}", heard[0], voice[0], heard[1]);
                h.assertTrue(heard[0], "the villager 4 blocks away did not hear the word");
                h.assertFalse(heard[1], "the villager 28 blocks away heard the word");
                h.assertTrue(voice[0] > 0.5f, "the player's voice was not heard as a voice");
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

    /** A villager says a word: it hears itself on the next tick, a villager nearby hears it too. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hearsItsOwnWordsAndOthersHearThem(GameTestHelper h) {
        Villager speaker = spawnMcaVillager(h, new Vec3(10.5, 2, 12.5));
        Villager other = spawnMcaVillager(h, new Vec3(14.5, 2, 12.5));
        BrainManager.Handle hs = listener(h, speaker);
        BrainManager.Handle ho = listener(h, other);
        int slot = Lexicon.get(h.getLevel().getServer()).slot("bread");
        boolean[] heard = {false, false};
        h.onEachTick(() -> {
            if (h.getTick() == 10) {
                hs.hold(MotorCommand.IDLE.saying(slot));
            } else if (h.getTick() == 11) {
                hs.hold(MotorCommand.IDLE);
            }
            heard[0] |= hs.frame().words[slot] > 0;
            heard[1] |= ho.frame().words[slot] > 0;
            if (h.getTick() == 20) {
                LOG.info("villager said 'bread': heard by itself {}, by the villager 4 blocks away {}", heard[0], heard[1]);
                h.assertTrue(heard[0], "the speaker did not hear its own word");
                h.assertTrue(heard[1], "the villager nearby did not hear the word");
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
        Lexicon lexicon = Lexicon.get(h.getLevel().getServer());
        int[] slots = new int[WORDS.length];
        for (int i = 0; i < WORDS.length; i++) {
            slots[i] = lexicon.slot(WORDS[i]);
        }
        Villager v = spawnMcaVillager(h, new Vec3(12.5, 2, 12.5));
        v.setAge(-24000);
        BrainManager.Handle handle = attach(h, v);
        int[] tick = {0};
        int[] babbled = {0};
        int[] target = {-1};
        boolean[] saidTarget = {false};
        int[] wordsInWindow = {0};
        // [windows, of those with the target said] for heard and silent windows.
        int[] heard = {0, 0};
        int[] silent = {0, 0};
        int[] anyWord = {0, 0};
        MotorCommand[] last = {null};
        java.util.Random random = new java.util.Random(7);
        h.onEachTick(() -> {
            tick[0]++;
            MotorCommand c = handle.command();
            boolean spoke = c != last[0] && c.say() >= 0;
            last[0] = c;
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
                target[0] = slots[random.nextInt(slots.length)];
                saidTarget[0] = false;
                wordsInWindow[0] = 0;
                if (trial % 2 == 0) {
                    // Beside it, at head height.
                    Speech.playerSays(h.getLevel(), v.getEyePosition().add(1.5, 0, 0), lexicon.word(target[0]));
                }
            }
            if (spoke) {
                wordsInWindow[0]++;
                saidTarget[0] |= c.say() == target[0];
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
}
