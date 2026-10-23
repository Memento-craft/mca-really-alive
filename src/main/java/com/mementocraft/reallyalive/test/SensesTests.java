package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.LobeId;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.SenseLayout;
import com.mementocraft.reallyalive.sense.Senses;
import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import static com.mementocraft.reallyalive.test.McaVillagerTests.EAST;
import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * In-game checks that an MCA villager's eyes see the real world: blocks, which side they are on,
 * liquids, hazards and creatures, and that what it sees reaches the occipital lobe.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class SensesTests {
    private static final Logger LOG = LogUtils.getLogger();
    /** A 24 x 24 stone floor at y 1, open sky above. */
    private static final String ARENA = "arena";
    private static final double RANGE = 24;
    private static final int W = SenseLayout.VISION_W;
    private static final int H = SenseLayout.VISION_H;

    private SensesTests() {}

    /** An MCA villager in the middle of the arena, facing +x, level gaze, brain on but keys idle. */
    private static Villager onlooker(GameTestHelper helper) {
        Villager v = spawnMcaVillager(helper, new Vec3(3.5, 2, 12.5));
        attach(helper, v).hold(MotorCommand.IDLE);
        return v;
    }

    private static float[] look(Villager v) {
        v.setYRot(EAST);
        v.setXRot(0f);
        float[] vision = new float[SenseLayout.VISION_SIZE];
        Senses.see(v, RANGE, vision);
        return vision;
    }

    private static float at(float[] vision, int x, int y, int channel) {
        return vision[SenseLayout.visionIndex(x, y, channel)];
    }

    /** One letter per ray, top row first: '#' solid, '~' liquid, '!' hazard, 'c' creature, 'M' monster. */
    private static String picture(float[] vision) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < H; y++) {
            sb.append('\n');
            for (int x = 0; x < W; x++) {
                boolean creature = at(vision, x, y, SenseLayout.CH_CREATURE) > 0;
                boolean hazard = at(vision, x, y, SenseLayout.CH_HAZARD) > 0;
                float solid = at(vision, x, y, SenseLayout.CH_SOLID);
                sb.append(creature ? (hazard ? 'M' : 'c') : hazard ? '!' : solid >= 1f ? '#' : solid > 0f ? '~' : '.');
            }
        }
        return sb.toString();
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(x, y, z, block);
                }
            }
        }
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void seesAWallInFront(GameTestHelper helper) {
        Villager v = onlooker(helper);
        fill(helper, 7, 2, 0, 7, 8, 23, Blocks.STONE);
        helper.runAfterDelay(10, () -> {
            float[] vision = look(v);
            LOG.info("wall in front:{}", picture(vision));
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    helper.assertTrue(at(vision, x, y, SenseLayout.CH_SOLID) == 1f, "ray " + x + "," + y + " missed the wall");
                    helper.assertTrue(at(vision, x, y, SenseLayout.CH_CREATURE) == 0f, "ray " + x + "," + y + " saw a creature");
                }
            }
            // Straight ahead the wall is 3.5 blocks from the villager's eye.
            double expected = 1.0 - (7.0 - 3.5) / RANGE;
            float ahead = at(vision, W / 2, H / 2, SenseLayout.CH_NEAR);
            helper.assertTrue(Math.abs(ahead - expected) < 0.03, "wall ahead read as nearness " + ahead + ", expected about " + expected);
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void seesWhichSideThingsAreOn(GameTestHelper helper) {
        Villager v = onlooker(helper);
        // A pillar ahead and to the left: facing +x, left is -z.
        fill(helper, 8, 2, 8, 8, 6, 8, Blocks.STONE);
        helper.runAfterDelay(10, () -> {
            float[] vision = look(v);
            LOG.info("pillar ahead-left:{}", picture(vision));
            int left = 0;
            int right = 0;
            // Rows at and above eye level, where the floor is out of sight.
            for (int y = 0; y < H / 2; y++) {
                for (int x = 0; x < W; x++) {
                    if (at(vision, x, y, SenseLayout.CH_SOLID) > 0) {
                        if (x < W / 2) {
                            left++;
                        } else {
                            right++;
                        }
                    }
                }
            }
            helper.assertTrue(left > 0, "pillar on the left was not seen");
            helper.assertTrue(right == 0, "pillar on the left was seen on the right");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void seesCreaturesAndMonsters(GameTestHelper helper) {
        Villager v = onlooker(helper);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, new Vec3(8.5, 2, 12.5));
        // Ahead and to the left, inside the field of view.
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(10.5, 2, 9.5));
        zombie.setInvulnerable(true);
        helper.runAfterDelay(10, () -> {
            float[] vision = look(v);
            LOG.info("cow ahead, zombie ahead-left:{}", picture(vision));
            boolean cowSeen = false;
            boolean monsterSeen = false;
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    boolean creature = at(vision, x, y, SenseLayout.CH_CREATURE) > 0;
                    boolean hazard = at(vision, x, y, SenseLayout.CH_HAZARD) > 0;
                    if (creature && !hazard && x >= W / 2 - 2 && x < W / 2 + 2) {
                        cowSeen = true;
                    }
                    if (creature && hazard) {
                        monsterSeen = true;
                        helper.assertTrue(x < W / 2, "zombie on the left was seen on the right");
                    }
                }
            }
            helper.assertTrue(cowSeen, "cow straight ahead was not seen as a creature");
            helper.assertTrue(monsterSeen, "zombie was not seen as a dangerous creature");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void seesLavaAsDangerAndWaterAsLiquid(GameTestHelper helper) {
        Villager v = onlooker(helper);
        // Lava in the floor ahead-left, water ahead-right, both sunk into the floor.
        fill(helper, 5, 1, 7, 9, 1, 12, Blocks.LAVA);
        fill(helper, 5, 1, 13, 9, 1, 18, Blocks.WATER);
        helper.runAfterDelay(10, () -> {
            float[] vision = look(v);
            LOG.info("lava ahead-left, water ahead-right:{}", picture(vision));
            boolean lava = false;
            boolean water = false;
            for (int y = H / 2; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    float hazard = at(vision, x, y, SenseLayout.CH_HAZARD);
                    float solid = at(vision, x, y, SenseLayout.CH_SOLID);
                    if (hazard > 0) {
                        lava = true;
                        helper.assertTrue(x < W / 2, "lava on the left was seen on the right");
                    } else if (solid > 0 && solid < 1) {
                        water = true;
                        helper.assertTrue(x >= W / 2, "water on the right was seen on the left");
                    }
                }
            }
            helper.assertTrue(lava, "lava was not seen as a hazard");
            helper.assertTrue(water, "water was not seen as a liquid");
            helper.succeed();
        });
    }

    /** What the eyes see must reach the brain: a wall in front stirs the occipital lobe more than open sky. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void sightReachesTheOccipitalLobe(GameTestHelper helper) {
        Villager walled = spawnMcaVillager(helper, new Vec3(3.5, 2, 6.5));
        Villager open = spawnMcaVillager(helper, new Vec3(3.5, 2, 18.5));
        // Both look up a little so the open one sees only sky.
        MotorCommand lookUp = new MotorCommand(0, 0, false, false, false, 0f, 0f);
        BrainManager.Handle hw = attach(helper, walled);
        BrainManager.Handle ho = attach(helper, open);
        hw.hold(lookUp);
        ho.hold(lookUp);
        hw.brain().setBabbleScale(0f);
        ho.brain().setBabbleScale(0f);
        fill(helper, 5, 2, 0, 5, 11, 11, Blocks.STONE);
        helper.onEachTick(() -> {
            walled.setXRot(-40f);
            open.setXRot(-40f);
        });
        helper.runAtTickTime(200, () -> {
            float a = hw.brain().lobeActivity(LobeId.OCCIPITAL);
            float b = ho.brain().lobeActivity(LobeId.OCCIPITAL);
            LOG.info("occipital activity: facing a wall {}, facing open sky {}", a, b);
            helper.assertTrue(a > b * 1.2f + 1e-4f, "occipital lobe did not respond to the wall: " + a + " vs " + b);
            helper.succeed();
        });
    }
}
