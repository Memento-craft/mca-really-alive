package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.SenseLayout;
import com.mementocraft.reallyalive.sense.Senses;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import static com.mementocraft.reallyalive.test.McaVillagerTests.EAST;
import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * In-game checks that a brain villager's mouse buttons work like a player's: digging takes a
 * player's time for the block and tool, letting go loses the progress, digging drops into its
 * hands, use places one block per click and repeats every 4 ticks, never inside a creature, and a
 * click on a creature hits it. Also checks the hand senses and that ladders look climbable.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class HandsTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";
    private static final MotorCommand ATTACK = new MotorCommand(0, 0, false, false, false, 0f, 0f, true, false);
    private static final MotorCommand USE = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true);
    /** In front of the villager at eye height. */
    private static final BlockPos AHEAD = new BlockPos(5, 3, 12);

    private HandsTests() {}

    private static Villager worker(GameTestHelper h, ItemStack inHand, float pitch) {
        Villager v = spawnMcaVillager(h, new Vec3(3.5, 2, 12.5));
        v.setItemSlot(EquipmentSlot.MAINHAND, inHand);
        v.setXRot(pitch);
        attach(h, v).hold(MotorCommand.IDLE);
        return v;
    }

    /**
     * Holds attack on a block from tick 10 and checks it breaks after a player's dig time for that
     * block and tool, with the drop in the villager's hand.
     */
    private static void digTime(GameTestHelper h, Block block, ItemStack tool, int playerTicks, net.minecraft.world.item.Item drop) {
        h.setBlock(AHEAD, block);
        Villager v = worker(h, tool, 0f);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] brokeAt = {-1};
        int[] firstDug = {-1};
        float[] progressSeen = {0f};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] == 10) {
                handle.hold(ATTACK);
            }
            if (tick[0] > 10 && brokeAt[0] < 0) {
                progressSeen[0] = Math.max(progressSeen[0], handle.hands().digProgress());
                if (firstDug[0] < 0 && handle.hands().digProgress() > 0) {
                    firstDug[0] = tick[0];
                }
                if (h.getBlockState(AHEAD).isAir()) {
                    // Ticks the button was down on the block: from the first tick that dug to the one
                    // that broke it. A player digs from the very tick the button goes down.
                    brokeAt[0] = tick[0] - firstDug[0] + 1;
                    handle.hold(MotorCommand.IDLE);
                }
            }
            if (tick[0] == 10 + playerTicks + 20) {
                LOG.info("dug {} with {} in {} ticks (a player takes {}); now holding {}", block.getName().getString(),
                        tool.isEmpty() ? "bare hands" : tool.getHoverName().getString(), brokeAt[0], playerTicks,
                        v.getMainHandItem());
                h.assertTrue(brokeAt[0] > 0, "the block never broke");
                h.assertTrue(brokeAt[0] == playerTicks, "took " + brokeAt[0] + " ticks, a player takes " + playerTicks);
                h.assertTrue(progressSeen[0] > 0f, "dig progress was never felt");
                boolean got = v.getMainHandItem().is(drop) || v.getInventory().hasAnyOf(java.util.Set.of(drop));
                h.assertTrue(got, "the drop did not reach the villager");
                h.succeed();
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void digsDirtByHandAtPlayerSpeed(GameTestHelper h) {
        // Dirt: hardness 0.5, by hand 1 / 0.5 / 30 per tick, so 15 ticks.
        digTime(h, Blocks.DIRT, ItemStack.EMPTY, 15, Items.DIRT);
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void digsStoneWithAWoodenPickaxeAtPlayerSpeed(GameTestHelper h) {
        // Stone: hardness 1.5, wooden pickaxe speed 2, harvestable: 2 / 1.5 / 30 per tick, so 23 ticks.
        digTime(h, Blocks.STONE, new ItemStack(Items.WOODEN_PICKAXE), 23, Items.COBBLESTONE);
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void lettingGoLosesDigProgress(GameTestHelper h) {
        h.setBlock(AHEAD, Blocks.DIRT);
        Villager v = worker(h, ItemStack.EMPTY, 0f);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        // 10 ticks, let go for one, 10 more: 20 ticks in all, but never the 15 in a row dirt needs.
        h.runAtTickTime(10, () -> handle.hold(ATTACK));
        h.runAtTickTime(20, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(21, () -> handle.hold(ATTACK));
        h.runAtTickTime(31, () -> {
            h.assertFalse(h.getBlockState(AHEAD).isAir(), "dirt broke although digging was interrupted");
            handle.hold(MotorCommand.IDLE);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void usePlacesOneBlockPerClickAndRepeatsEveryFourTicks(GameTestHelper h) {
        // Facing a block 3.5 blocks away at eye height: each new block goes on the face of the last,
        // one step nearer, and three fit before the next would be inside the villager.
        h.setBlock(7, 3, 12, Blocks.STONE);
        Villager v = worker(h, new ItemStack(Items.OAK_PLANKS, 16), 0f);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        h.runAtTickTime(10, () -> handle.hold(USE));
        h.runAtTickTime(11, () -> {
            h.assertTrue(v.getMainHandItem().getCount() == 15, "one click should place one block, hand holds " + v.getMainHandItem().getCount());
        });
        // Held for ticks 10..18: placements at 10, 14 and 18.
        h.runAtTickTime(19, () -> {
            handle.hold(MotorCommand.IDLE);
            int placed = 16 - v.getMainHandItem().getCount();
            LOG.info("holding use for 9 ticks placed {} blocks", placed);
            h.assertTrue(placed == 3, "holding use for 9 ticks placed " + placed + " blocks, a player places 3");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void neverPlacesInsideACreature(GameTestHelper h) {
        // Straight down: the face clicked is the floor under its own feet.
        Villager v = worker(h, new ItemStack(Items.OAK_PLANKS, 16), 90f);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        h.runAtTickTime(10, () -> handle.hold(USE));
        h.runAtTickTime(20, () -> {
            handle.hold(MotorCommand.IDLE);
            h.assertTrue(v.getMainHandItem().getCount() == 16, "placed a block inside itself");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aClickHitsTheCreatureInTheCrosshair(GameTestHelper h) {
        Villager v = worker(h, ItemStack.EMPTY, 10f);
        Mob cow = h.spawnWithNoFreeWill(EntityType.COW, new Vec3(5.5, 2, 12.5));
        BrainManager.Handle handle = BrainManager.get().handle(v);
        float before = cow.getHealth();
        h.runAtTickTime(10, () -> handle.hold(ATTACK));
        // Held, not clicked again: a player hits once per click.
        h.runAtTickTime(40, () -> {
            handle.hold(MotorCommand.IDLE);
            float lost = before - cow.getHealth();
            LOG.info("a fist hit took {} health from a cow", lost);
            h.assertTrue(Math.abs(lost - 1f) < 1e-3, "a player's fist does 1 damage, this did " + lost);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void feelsWhatItHoldsAndWhatItAimsAt(GameTestHelper h) {
        h.setBlock(AHEAD, Blocks.DIRT);
        Villager v = worker(h, new ItemStack(Items.OAK_PLANKS, 4), 0f);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        h.runAtTickTime(10, () -> {
            h.assertTrue(handle.hands().holdingBlock(), "does not feel the planks in its hand");
            h.assertTrue(handle.hands().targetInReach(), "does not feel the dirt in reach");
            v.setXRot(-60f);
        });
        h.runAtTickTime(15, () -> {
            h.assertFalse(handle.hands().targetInReach(), "feels a target while looking at the sky");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void seesALadderAsClimbable(GameTestHelper h) {
        for (int z = 0; z < 24; z++) {
            for (int y = 2; y <= 8; y++) {
                h.setBlock(7, y, z, Blocks.STONE);
            }
        }
        var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
        for (int y = 2; y <= 8; y++) {
            h.setBlock(6, y, 12, ladder);
        }
        Villager v = worker(h, ItemStack.EMPTY, 0f);
        h.runAtTickTime(10, () -> {
            v.setYRot(EAST);
            float[] vision = new float[SenseLayout.VISION_SIZE];
            Senses.see(v, 24, vision);
            int climbable = 0;
            int wall = 0;
            for (int y = 0; y < SenseLayout.VISION_H; y++) {
                for (int x = 0; x < SenseLayout.VISION_W; x++) {
                    if (vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_CLIMBABLE)] > 0) {
                        climbable++;
                        h.assertTrue(Math.abs(x - SenseLayout.VISION_W / 2 + 0.5) < 3, "ladder seen off to the side at column " + x);
                    } else if (vision[SenseLayout.visionIndex(x, y, SenseLayout.CH_SOLID)] > 0) {
                        wall++;
                    }
                }
            }
            LOG.info("ladder ahead: {} climbable rays, {} plain solid rays", climbable, wall);
            h.assertTrue(climbable > 0, "the ladder was not seen as climbable");
            h.assertTrue(wall > climbable, "the wall beside the ladder was seen as climbable");
            h.succeed();
        });
    }
}
