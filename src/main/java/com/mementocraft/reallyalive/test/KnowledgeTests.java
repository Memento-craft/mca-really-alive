package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.WordCode;
import com.mementocraft.reallyalive.speech.Speech;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * In-game checks of what villagers keep of each other: a villager knows the crops it harvested
 * itself, sees a death and remembers it, may choose to tell it to a villager near, and answers a
 * player asking about the dead. Nothing is kept for them anywhere but in their brains.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class KnowledgeTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";

    private KnowledgeTests() {}

    /** First name as a villager says it. */
    private static String first(Villager v) {
        return WordCode.tokenize(v.getCustomName().getString()).get(0);
    }

    private static Villager named(GameTestHelper h, Vec3 at, String name) {
        Villager v = spawnMcaVillager(h, at);
        v.setCustomName(Component.literal(name));
        return v;
    }

    /** Breaking a ripe crop, however it is broken, is counted by the villager that broke it; an unripe one is not. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void villagerCountsTheCropsItHarvests(GameTestHelper h) {
        Villager v = named(h, new Vec3(5.5, 2, 5.5), "Misu");
        BrainManager.Handle handle = attach(h, v);
        BlockPos ripe = new BlockPos(6, 2, 7);
        BlockPos green = new BlockPos(7, 2, 7);
        h.setBlock(ripe.below(), Blocks.FARMLAND);
        h.setBlock(green.below(), Blocks.FARMLAND);
        h.setBlock(ripe, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        h.setBlock(green, ((CropBlock) Blocks.WHEAT).getStateForAge(2));
        h.runAfterDelay(10, () -> {
            h.getLevel().destroyBlock(h.absolutePos(ripe), true, v);
            h.getLevel().destroyBlock(h.absolutePos(green), true, v);
        });
        h.runAfterDelay(40, () -> {
            BrainManager.get().setLockstep(true);
            Integer wheat = handle.brain().deeds().get("wheat");
            LOG.info("harvest: villager counted {}", handle.brain().deeds());
            h.assertTrue(wheat != null && wheat == 1, "counted " + handle.brain().deeds() + ", not 1 wheat");
            h.succeed();
        });
    }

    /**
     * Told what bread takes and asked for some, a villager holds bread as its goal with the steps
     * under it, and works down them as it does each: breaking the wheat, carrying it, having bread.
     */
    @GameTest(template = ARENA, timeoutTicks = 700, batch = "knowledge_goal")
    public static void villagerWorksThroughAPlan(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager v = named(h, new Vec3(5.5, 2, 5.5), "Misu");
        BrainManager.Handle handle = attach(h, v);
        BlockPos crop = new BlockPos(6, 2, 7);
        Vec3 player = v.getEyePosition().add(1.5, 0, 0);
        List<String> answers = new ArrayList<>();
        int[] seen = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            List<String> plan = handle.brain().plan();
            if (t == 10) {
                Speech.playerSays(h.getLevel(), player, "you need wheat to make bread.", "Steve");
            } else if (t == 60) {
                Speech.playerSays(h.getLevel(), player, "you get wheat by breaking wheat.", "Steve");
            } else if (t == 110) {
                Speech.playerSays(h.getLevel(), player, "can you make some bread?", "Steve");
            } else if (t == 200) {
                Speech.playerSays(h.getLevel(), player, "what are you doing?", "Steve");
            } else if (t == 380) {
                LOG.info("plan: asked for bread, it is after {} and said {}", plan, answers);
                h.assertTrue(plan.equals(List.of("broke wheat", "wheat", "bread")), "asked for bread, it is after " + plan);
                h.assertTrue(answers.stream().anyMatch(l -> l.contains("i want some bread")), "asked what it is doing, it said " + answers);
                h.setBlock(crop.below(), Blocks.FARMLAND);
                h.setBlock(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
                h.getLevel().destroyBlock(h.absolutePos(crop), false, v);
            } else if (t == 420) {
                h.assertTrue(plan.equals(List.of("wheat", "bread")), "having broken the wheat, it is after " + plan);
                v.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WHEAT, 3));
            } else if (t == 460) {
                h.assertTrue(plan.equals(List.of("bread")), "carrying wheat, it is after " + plan);
                v.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD, 1));
            } else if (t == 500) {
                h.assertTrue(plan.isEmpty(), "with bread, it is still after " + plan);
                LOG.info("plan: worked through it, and knows bread needs {}", handle.brain().needsOf("bread"));
                h.succeed();
            }
            if (handle.lines() > seen[0]) {
                seen[0] = handle.lines();
                answers.add(t + ": " + handle.lastLine());
            }
        });
    }

    /**
     * Hunger sets a villager's own goal. It eats a bread while hungry and learns that eating bread
     * eases hunger; hungry again with no bread, it sets out to get some and says so.
     */
    @GameTest(template = ARENA, timeoutTicks = 500, batch = "knowledge_hunger")
    public static void hungerSetsTheGoalOfEating(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager v = named(h, new Vec3(5.5, 2, 5.5), "Misu");
        v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        MotorCommand use = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true);
        Vec3 player = v.getEyePosition().add(1.5, 0, 0);
        List<String> answers = new ArrayList<>();
        int[] seen = {0};
        float[] before = {0f};
        h.onEachTick(() -> {
            long t = h.getTick();
            List<String> plan = handle.brain().plan();
            if (t == 5) {
                handle.setHunger(0.6f);
            } else if (t == 8) {
                h.assertTrue(plan.isEmpty(), "knowing nothing that eases hunger, it is after " + plan);
                v.setXRot(-80f); // looking at the sky, so use eats what is in hand
                before[0] = handle.hunger();
                handle.hold(use);
            } else if (t == 50) {
                handle.hold(null); // its own keys again, voice too
            } else if (t == 60) {
                LOG.info("hunger: ate, hunger {} -> {}, knows hunger needs {}", before[0], handle.hunger(), handle.brain().needsOf("hunger"));
                h.assertTrue(v.getMainHandItem().isEmpty(), "it did not eat the bread, holding " + v.getMainHandItem());
                h.assertTrue(handle.hunger() < before[0] - 0.2f, "eating took hunger only from " + before[0] + " to " + handle.hunger());
                h.assertTrue(handle.brain().needsOf("hunger").contains("ate bread"), "hunger needs " + handle.brain().needsOf("hunger"));
                handle.setHunger(0.7f);
            } else if (t == 80) {
                h.assertTrue(plan.equals(List.of("bread", "ate bread")), "hungry again, it is after " + plan);
                Speech.playerSays(h.getLevel(), player, "what are you doing?", "Steve");
            } else if (t == 300) {
                LOG.info("hunger: asked what it is doing, it said {}", answers);
                h.assertTrue(answers.stream().anyMatch(l -> l.contains("hungry")), "asked what it is doing, it said " + answers);
                handle.hold(MotorCommand.IDLE);
                v.getInventory().addItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD));
            } else if (t == 330) {
                h.assertTrue(plan.equals(List.of("ate bread")), "with bread, it is after " + plan);
                h.succeed();
            }
            if (handle.lines() > seen[0]) {
                seen[0] = handle.lines();
                answers.add(t + ": " + handle.lastLine());
            }
        });
    }

    /** Hunger and tiredness are the body's and are kept with it when the brain is put away and back. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void hungerAndTirednessAreKept(GameTestHelper h) {
        Villager v = named(h, new Vec3(5.5, 2, 5.5), "Misu");
        BrainManager.Handle handle = attach(h, v);
        handle.setHunger(0.42f);
        handle.setTiredness(0.37f);
        h.runAfterDelay(10, () -> {
            BrainManager.get().detach(v, true);
            var data = net.minecraft.nbt.NbtOps.INSTANCE;
            var saved = com.mementocraft.reallyalive.BrainData.CODEC.encodeStart(data, v.getData(com.mementocraft.reallyalive.Attachments.BRAIN)).getOrThrow();
            var back = com.mementocraft.reallyalive.BrainData.CODEC.parse(data, saved).getOrThrow();
            h.assertTrue(Math.abs(back.hunger() - 0.42f) < 0.01f && Math.abs(back.tiredness() - 0.37f) < 0.01f,
                    "saved and read back, hunger " + back.hunger() + " tiredness " + back.tiredness());
            BrainManager.Handle again = BrainManager.get().attach(v);
            h.assertTrue(again != null && Math.abs(again.hunger() - 0.42f) < 0.01f, "brought back, hunger " + (again == null ? null : again.hunger()));
            h.succeed();
        });
    }

    /**
     * Tiredness sets a villager's own goal too. Put to bed at night tired, it rests and learns that
     * sleeping in a bed eases tiredness; tired again, it sets out to sleep in a bed and says so.
     */
    @GameTest(template = ARENA, timeoutTicks = 600, batch = "knowledge_sleep")
    public static void tirednessSetsTheGoalOfSleeping(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        long dayTime = h.getLevel().getDayTime();
        h.getLevel().setDayTime(18_000); // midnight
        BlockPos foot = new BlockPos(5, 2, 7);
        var bed = Blocks.RED_BED.defaultBlockState().setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.SOUTH);
        h.setBlock(foot, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT));
        h.setBlock(foot.south(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD));
        Villager v = named(h, new Vec3(5.5, 2, 5.5), "Misu");
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        MotorCommand use = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true);
        Vec3 player = v.getEyePosition().add(1.5, 0, 0);
        List<String> answers = new ArrayList<>();
        int[] seen = {0};
        float[] before = {0f};
        h.onEachTick(() -> {
            long t = h.getTick();
            List<String> plan = handle.brain().plan();
            if (t == 5) {
                handle.setTiredness(0.6f);
                Vec3 to = h.absoluteVec(new Vec3(5.5, 2.3, 7.5)).subtract(v.getEyePosition());
                float yaw = (float) (net.minecraft.util.Mth.atan2(to.z, to.x) * net.minecraft.util.Mth.RAD_TO_DEG) - 90f;
                v.setYRot(yaw);
                v.setYHeadRot(yaw);
                v.setXRot((float) -(net.minecraft.util.Mth.atan2(to.y, to.horizontalDistance()) * net.minecraft.util.Mth.RAD_TO_DEG));
            } else if (t == 8) {
                h.assertTrue(plan.isEmpty(), "knowing nothing that eases tiredness, it is after " + plan);
                handle.hold(use);
            } else if (t == 12) {
                handle.hold(MotorCommand.IDLE);
                h.assertTrue(v.isSleeping(), "use on the bed at night did not put it to bed");
                before[0] = handle.tiredness();
            } else if (t == 250) {
                LOG.info("sleep: slept, tiredness {} -> {}, knows tiredness needs {}", before[0], handle.tiredness(),
                        handle.brain().needsOf("tiredness"));
                h.assertTrue(v.isSleeping(), "it did not stay asleep");
                h.assertTrue(handle.tiredness() < before[0] - 0.02f, "sleep took tiredness only from " + before[0] + " to " + handle.tiredness());
                h.assertTrue(handle.brain().needsOf("tiredness").contains("slept in bed"), "tiredness needs " + handle.brain().needsOf("tiredness"));
                v.stopSleeping();
                handle.setTiredness(0.7f);
                handle.hold(null);
            } else if (t == 270) {
                h.assertTrue(plan.equals(List.of("slept in bed")), "tired again, it is after " + plan);
                Speech.playerSays(h.getLevel(), player, "what are you doing?", "Steve");
            } else if (t == 500) {
                LOG.info("sleep: asked what it is doing, it said {}", answers);
                h.getLevel().setDayTime(dayTime);
                h.assertTrue(answers.stream().anyMatch(l -> l.contains("tired")), "asked what it is doing, it said " + answers);
                h.succeed();
            }
            if (handle.lines() > seen[0]) {
                seen[0] = handle.lines();
                answers.add(t + ": " + handle.lastLine());
            }
        });
    }

    /**
     * A villager that sees another die remembers the death and the dead by name; asked "what
     * happened to misu?" it says so. Runs in its own batch, away from other tests' chatter.
     */
    @GameTest(template = ARENA, timeoutTicks = 1200, batch = "knowledge")
    public static void villagerRemembersADeathItSaw(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager witness = named(h, new Vec3(10.5, 2, 10.5), "Tomas");
        Villager dying = named(h, new Vec3(13.5, 2, 10.5), "Misu");
        BrainManager.Handle handle = attach(h, witness);
        String name = first(dying);
        List<String> answers = new ArrayList<>();
        int[] seen = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 20) {
                witness.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dying.getEyePosition());
                dying.kill();
            }
            if (t == 60) {
                List<String> kept = handle.brain().recollectionsOf(name);
                LOG.info("death: the witness remembers of {}: {}", name, kept);
                h.assertTrue(kept.contains(name + " died ."), "the witness keeps " + kept);
            }
            if (t == 80) {
                Speech.playerSays(h.getLevel(), witness.getEyePosition().add(1.5, 0, 0), "what happened to " + name + "?", "Steve");
            }
            if (t > 80 && handle.lines() > seen[0]) {
                seen[0] = handle.lines();
                answers.add(handle.lastLine());
            }
            if (t == 600) {
                LOG.info("death: asked what happened to {}, the witness said {}", name, answers);
                h.assertTrue(answers.stream().anyMatch(a -> a.contains("died")), "asked, it said " + answers);
                h.succeed();
            }
        });
    }

    /**
     * Passing it on: a villager who saw a death, standing with another, chooses in its own time to
     * tell it, and the other then remembers it too. How soon is up to its brain, so this is a
     * benchmark, with the urge to tell raised so it needn't take a game day.
     */
    @GameTest(template = ARENA, timeoutTicks = 4800, batch = "knowledge_told", required = false)
    public static void villagerTellsOfADeath(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager witness = named(h, new Vec3(10.5, 2, 10.5), "Tomas");
        Villager dying = named(h, new Vec3(13.5, 2, 10.5), "Misu");
        BrainManager.Handle teller = attach(h, witness);
        teller.brain().setTellRate(0.3f);
        String name = first(dying);
        List<String> said = new ArrayList<>();
        int[] seen = {0};
        // The listener comes along only after the death, so it can know of it only by being told.
        BrainManager.Handle[] heard = {null};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 20) {
                witness.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dying.getEyePosition());
                dying.kill();
            }
            if (t == 60) {
                heard[0] = attach(h, named(h, new Vec3(10.5, 2, 12.5), "Lia"));
            }
            BrainManager.Handle hearer = heard[0];
            if (hearer == null) {
                return;
            }
            if (teller.lines() > seen[0]) {
                seen[0] = teller.lines();
                said.add(teller.lastLine());
            }
            if (hearer.brain().recollectionsOf(name).stream().anyMatch(l -> l.contains("died"))) {
                LOG.info("told: after {} ticks the listener knows {}; the teller said {}", t,
                        hearer.brain().recollectionsOf(name), said);
                h.succeed();
            }
            if (t == 4700) {
                LOG.info("told: the teller said {} and the listener knows {}", said, hearer.brain().recollectionsOf(name));
                h.fail("the death was not passed on");
            }
        });
    }

    /**
     * Reading: a villager given a signed book presses use with it in hand and takes in what it
     * says: "misu was the best farmer in the village." is now something it remembers of Misu,
     * whom it never met.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void villagerReadsABook(GameTestHelper h) {
        Villager v = named(h, new Vec3(16.5, 2, 4.5), "Lia");
        BrainManager.Handle handle = attach(h, v);
        net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
        book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
                net.minecraft.server.network.Filterable.passThrough("Misu"), "Joshua", 0,
                List.of(net.minecraft.server.network.Filterable.passThrough(Component.literal("misu was the best farmer in the village."))), true));
        v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, book);
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 10) {
                handle.hold(new com.mementocraft.reallyalive.brain.MotorCommand(0, 0, false, false, false, 0f, 0f, false, true));
            }
            if (t == 14) {
                handle.hold(com.mementocraft.reallyalive.brain.MotorCommand.IDLE);
            }
            List<String> kept = handle.brain().recollectionsOf("misu");
            if (kept.stream().anyMatch(l -> l.contains("farmer"))) {
                LOG.info("read: after {} ticks the reader remembers of misu: {}", t, kept);
                h.succeed();
            }
        });
    }

    /**
     * Writing: alone with a book and quill after seeing a death, a villager may choose to write
     * it down; the line goes onto the page, not into chat. A benchmark: whether and when it writes
     * is its own choice (the urge raised so it needn't take a game day).
     */
    @GameTest(template = ARENA, timeoutTicks = 4800, batch = "knowledge_written", required = false)
    public static void villagerWritesDownADeath(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager writer = named(h, new Vec3(10.5, 2, 10.5), "Tomas");
        Villager dying = named(h, new Vec3(12.5, 2, 10.5), "Misu");
        BrainManager.Handle handle = attach(h, writer);
        handle.brain().setTellRate(0.3f);
        writer.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK));
        String name = first(dying);
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 20) {
                writer.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dying.getEyePosition());
                dying.kill();
            }
            if (handle.linesWritten() > 0) {
                List<List<String>> lines = com.mementocraft.reallyalive.body.Books.lines(writer.getMainHandItem());
                LOG.info("written: after {} ticks the book reads {} (lines said aloud: {})", t, lines, handle.lines());
                h.assertTrue(lines.stream().anyMatch(l -> l.contains(name)), "the book reads " + lines);
                h.succeed();
            }
            if (t == 4700) {
                LOG.info("written: nothing written; it holds {}", writer.getMainHandItem());
                h.fail("it never wrote the death down");
            }
        });
    }

    /**
     * Watching: a child facing a villager that digs sees its hand work (the attack key), which is
     * what its own motor pools mirror; facing away, it sees nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void childSeesWhatAnotherVillagerDoes(GameTestHelper h) {
        Villager digger = named(h, new Vec3(4.5, 2, 16.5), "Misu");
        Villager child = named(h, new Vec3(4.5, 2, 18.5), "Lia");
        child.setAge(-24000);
        BrainManager.Handle hd = attach(h, digger);
        BrainManager.Handle hc = attach(h, child);
        hd.hold(new com.mementocraft.reallyalive.brain.MotorCommand(0, 0, false, false, false, 0f, 0f, true, false));
        hc.hold(com.mementocraft.reallyalive.brain.MotorCommand.IDLE);
        boolean[] saw = {false};
        h.onEachTick(() -> {
            child.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, digger.getEyePosition());
            child.setYHeadRot(child.getYRot());
            saw[0] |= hc.frame().watched[com.mementocraft.reallyalive.brain.MotorChannel.ATTACK.ordinal()] > 0f;
            if (saw[0]) {
                LOG.info("watch: the child saw the digger's hand work at tick {}", h.getTick());
                h.succeed();
            }
        });
    }
}
