package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.mementocraft.reallyalive.test.McaVillagerTests.EAST;
import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * Obstacle courses for MCA villagers: a step to jump, a ladder to climb, a pool to swim and a ledge
 * to sneak up to, each with a goal on the far side that the villager can smell and see (a cow that
 * stands still on it). An open lane with nothing in the way is the control.
 *
 * <p>For each course there are two tests. The "passable" test holds the keys a player would use and
 * must reach the goal: it proves the course is fair and that the body can do it. The "learns" test
 * lets the villager's own brain try, episode after episode, and passes once it reaches the goal in
 * most of its last tries. The learning tests are benchmarks ({@code required = false}): they report
 * how far learning has got without failing the build.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class LearningTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";
    /** The lane runs along +x at z 11..13, walled with barriers the eyes see through. */
    private static final int LANE = 12;
    private static final int EPISODES = 150;
    private static final int WINDOW = 10;

    private LearningTests() {}

    /** One obstacle course. */
    private enum Course {
        OPEN(200, new MotorCommand(1, 0, false, false, false, 0f, 0f)) {
            @Override
            void build(GameTestHelper h) {}

            @Override
            BlockPos goal() {
                return new BlockPos(9, 2, LANE);
            }
        },
        JUMP(200, new MotorCommand(1, 0, true, false, false, 0f, 0f)) {
            @Override
            void build(GameTestHelper h) {
                fill(h, 6, 2, LANE - 1, 6, 2, LANE + 1, Blocks.STONE);
            }

            @Override
            BlockPos goal() {
                return new BlockPos(9, 2, LANE);
            }
        },
        CLIMB(300, new MotorCommand(1, 0, false, false, false, 0f, 0f)) {
            @Override
            void build(GameTestHelper h) {
                fill(h, 7, 2, LANE - 1, 11, 5, LANE + 1, Blocks.STONE);
                BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
                for (int y = 2; y <= 5; y++) {
                    h.setBlock(6, y, LANE, ladder);
                }
            }

            @Override
            BlockPos goal() {
                return new BlockPos(9, 6, LANE);
            }
        },
        SWIM(300, new MotorCommand(1, 0, true, false, false, 0f, 0f)) {
            @Override
            void build(GameTestHelper h) {
                fill(h, 0, 2, LANE - 1, 15, 3, LANE + 1, Blocks.STONE);
                fill(h, 5, 2, LANE - 1, 9, 3, LANE + 1, Blocks.WATER);
            }

            @Override
            Vec3 start() {
                return new Vec3(2.5, 4, LANE + 0.5);
            }

            @Override
            BlockPos goal() {
                return new BlockPos(12, 4, LANE);
            }
        },
        LEDGE(300, new MotorCommand(1, 0, false, true, false, 0f, 0f)) {
            @Override
            void build(GameTestHelper h) {
                fill(h, 0, 2, LANE - 1, 8, 6, LANE + 1, Blocks.STONE);
            }

            @Override
            Vec3 start() {
                return new Vec3(2.5, 7, LANE + 0.5);
            }

            /** Just past the edge: reachable only by walking right up to it. */
            @Override
            BlockPos goal() {
                return new BlockPos(9, 7, LANE);
            }

            /** Hovering out past the edge, so it cannot stop a villager that walks off. */
            @Override
            Vec3 marker() {
                return new Vec3(12.5, 7, LANE + 0.5);
            }

            @Override
            boolean fell(GameTestHelper h, Villager v) {
                return v.getY() < h.absoluteVec(start()).y - 0.5;
            }
        };

        final int episodeTicks;
        /** The keys a player would hold to get through. */
        final MotorCommand playerKeys;

        Course(int episodeTicks, MotorCommand playerKeys) {
            this.episodeTicks = episodeTicks;
            this.playerKeys = playerKeys;
        }

        abstract void build(GameTestHelper h);

        abstract BlockPos goal();

        Vec3 start() {
            return new Vec3(2.5, 2, LANE + 0.5);
        }

        boolean fell(GameTestHelper h, Villager v) {
            return false;
        }

        /** Where the cow that marks the goal stands. */
        Vec3 marker() {
            return Vec3.atBottomCenterOf(goal());
        }

        void buildLane(GameTestHelper h) {
            fill(h, 0, 2, LANE - 2, 23, 11, LANE - 2, Blocks.BARRIER);
            fill(h, 0, 2, LANE + 2, 23, 11, LANE + 2, Blocks.BARRIER);
            build(h);
            // Something to see on the goal: a cow that never moves and cannot be hurt.
            Mob marker = h.spawnWithNoFreeWill(EntityType.COW, marker());
            marker.setInvulnerable(true);
            marker.setSilent(true);
            marker.setNoGravity(true);
        }
    }

    private static void fill(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    h.setBlock(x, y, z, block);
                }
            }
        }
    }

    /** Puts the villager back at the start, whole and facing the course, and sets its goal again. */
    private static void reset(GameTestHelper h, Course c, Villager v, BrainManager.Handle handle) {
        Vec3 at = h.absoluteVec(c.start());
        v.teleportTo(at.x, at.y, at.z);
        v.setDeltaMovement(Vec3.ZERO);
        v.resetFallDistance();
        v.setYRot(EAST);
        v.setYHeadRot(EAST);
        v.setXRot(0f);
        v.setHealth(v.getMaxHealth());
        v.setAirSupply(v.getMaxAirSupply());
        v.clearFire();
        handle.setGoal(h.absolutePos(c.goal()));
    }

    // ------------------------------------------------------------------ passable with player keys

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void jumpCourseIsPassable(GameTestHelper h) {
        passable(h, Course.JUMP);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void climbCourseIsPassable(GameTestHelper h) {
        passable(h, Course.CLIMB);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void swimCourseIsPassable(GameTestHelper h) {
        passable(h, Course.SWIM);
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void ledgeCourseIsPassable(GameTestHelper h) {
        passable(h, Course.LEDGE);
    }

    private static void passable(GameTestHelper h, Course c) {
        c.buildLane(h);
        Villager v = spawnMcaVillager(h, c.start());
        BrainManager.Handle handle = attach(h, v);
        handle.hold(c.playerKeys);
        handle.setGoal(h.absolutePos(c.goal()));
        h.succeedWhen(() -> {
            h.assertFalse(c.fell(h, v), c + ": fell off");
            h.assertTrue(handle.goal() == null, c + ": goal not reached yet");
            LOG.info("{} course passed with {} at tick {}", c, c.playerKeys.keys(), h.getTick());
        });
    }

    // ------------------------------------------------------------------ learned by the brain

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 200 + 200, required = false)
    public static void learnsToWalkToASeenGoal(GameTestHelper h) {
        learns(h, Course.OPEN);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 200 + 200, required = false)
    public static void learnsToJumpOverAStep(GameTestHelper h) {
        learns(h, Course.JUMP);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void learnsToClimbALadder(GameTestHelper h) {
        learns(h, Course.CLIMB);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void learnsToSwimAcrossAPool(GameTestHelper h) {
        learns(h, Course.SWIM);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void learnsToSneakUpToALedge(GameTestHelper h) {
        learns(h, Course.LEDGE);
    }

    /** Outcome of one try. */
    private enum Outcome { REACHED, FELL, TIMED_OUT }

    private static void learns(GameTestHelper h, Course c) {
        // Run the server as fast as it can: learning takes many tries. Lockstep keeps the brain
        // stepping once per tick, as it would in real time.
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(c.episodeTicks * EPISODES + 400);
        c.buildLane(h);
        // A newborn, like the toy world's learners: it babbles the most and has its inborn reflexes.
        Villager v = spawnMcaVillager(h, c.start());
        v.setAge(-24000);
        BrainManager.Handle handle = attach(h, v);
        reset(h, c, v, handle);

        List<Outcome> outcomes = new ArrayList<>();
        int[] ticks = {0};
        int[] sneakNearEdge = {0, 0};
        // Ticks in the last tries with the jump key down: in water, and on dry ground.
        int[] jumpWet = {0, 0};
        int[] jumpDry = {0, 0};
        // Closest each try got to the goal, as a fraction of the start distance still left.
        double startDist = Vec3.atBottomCenterOf(c.goal()).distanceTo(c.start());
        double[] closest = {1.0};
        double[] closestSum = {0.0};
        h.onEachTick(() -> {
            if (outcomes.size() >= EPISODES) {
                return;
            }
            ticks[0]++;
            if (handle.goal() != null) {
                closest[0] = Math.min(closest[0], h.relativeVec(v.position()).distanceTo(Vec3.atBottomCenterOf(c.goal())) / startDist);
            }
            if (outcomes.size() >= EPISODES - WINDOW) {
                int[] counter = v.isInWater() ? jumpWet : jumpDry;
                counter[1]++;
                if (handle.command().jump()) {
                    counter[0]++;
                }
            }
            if (c == Course.LEDGE && h.relativeVec(v.position()).x > 7) {
                sneakNearEdge[1]++;
                if (v.isShiftKeyDown()) {
                    sneakNearEdge[0]++;
                }
            }
            Outcome done = handle.goal() == null ? Outcome.REACHED
                    : c.fell(h, v) || !v.isAlive() ? Outcome.FELL
                    : ticks[0] >= c.episodeTicks ? Outcome.TIMED_OUT : null;
            if (done == null) {
                return;
            }
            outcomes.add(done);
            closestSum[0] += done == Outcome.REACHED ? 0.0 : closest[0];
            closest[0] = 1.0;
            ticks[0] = 0;
            if (outcomes.size() % WINDOW == 0) {
                LOG.info("{} learning: episodes {}-{}: {} reached, {} fell, closest approach left {}% of the way on average; brain {} steps",
                        c, outcomes.size() - WINDOW + 1, outcomes.size(),
                        count(outcomes, outcomes.size() - WINDOW, Outcome.REACHED),
                        count(outcomes, outcomes.size() - WINDOW, Outcome.FELL),
                        Math.round(100 * closestSum[0] / WINDOW), handle.brain().ticks());
                closestSum[0] = 0.0;
            }
            if (outcomes.size() < EPISODES) {
                reset(h, c, v, handle);
                return;
            }
            int first = count(outcomes.subList(0, WINDOW), 0, Outcome.REACHED);
            int last = count(outcomes, EPISODES - WINDOW, Outcome.REACHED);
            int lastFalls = count(outcomes, EPISODES - WINDOW, Outcome.FELL);
            String report = String.format("%s: reached the goal in %d of the first %d tries and %d of the last %d (%d falls);"
                            + " in the last tries jump was held %d%% of the time in water, %d%% on land%s",
                    c, first, WINDOW, last, WINDOW, lastFalls, percent(jumpWet), percent(jumpDry),
                    c == Course.LEDGE ? String.format("; sneaking %d%% of the time near the edge",
                            percent(sneakNearEdge)) : "");
            LOG.info("{} learning result: {}", c, report);
            h.getLevel().getServer().tickRateManager().stopSprinting();
            boolean sneaks = c != Course.LEDGE || sneakNearEdge[0] * 2 >= sneakNearEdge[1];
            if (last >= 7 && last > first && lastFalls <= 2 && sneaks) {
                h.succeed();
            } else {
                h.fail("not learned yet: " + report);
            }
        });
    }

    private static int percent(int[] hitsOfTotal) {
        return hitsOfTotal[1] == 0 ? 0 : 100 * hitsOfTotal[0] / hitsOfTotal[1];
    }

    private static int count(List<Outcome> outcomes, int from, Outcome which) {
        int n = 0;
        for (int i = from; i < outcomes.size(); i++) {
            if (outcomes.get(i) == which) {
                n++;
            }
        }
        return n;
    }
}
