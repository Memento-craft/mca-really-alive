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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
    /** Placing is learned slowly: the step course gets a long run. */
    private static final int STEP_EPISODES = 300;
    private static final int STEP_DIRT = 16;

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
        },
        /**
         * A ledge two blocks high, too high to jump: the way up is to place one of the 16 dirt it
         * holds as a step. Each try starts with the dirt back in hand and the steps gone.
         */
        STEP(400, new MotorCommand(1, 0, true, false, false, 0f, 0f), STEP_EPISODES) {
            @Override
            void build(GameTestHelper h) {
                fill(h, 7, 2, LANE - 1, 11, 3, LANE + 1, Blocks.STONE);
            }

            @Override
            BlockPos goal() {
                return new BlockPos(9, 4, LANE);
            }

            @Override
            void prepare(GameTestHelper h, Villager v, BrainManager.Handle handle) {
                for (int x = 0; x <= 23; x++) {
                    for (int y = 2; y <= 11; y++) {
                        for (int z = LANE - 1; z <= LANE + 1; z++) {
                            if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) {
                                h.setBlock(x, y, z, Blocks.AIR);
                            }
                        }
                    }
                }
                v.getInventory().clearContent();
                v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, STEP_DIRT));
                handle.hands().recount();
            }
        };

        final int episodeTicks;
        /** The keys a player would hold to get through. */
        final MotorCommand playerKeys;
        final int episodes;

        Course(int episodeTicks, MotorCommand playerKeys) {
            this(episodeTicks, playerKeys, EPISODES);
        }

        Course(int episodeTicks, MotorCommand playerKeys, int episodes) {
            this.episodeTicks = episodeTicks;
            this.playerKeys = playerKeys;
            this.episodes = episodes;
        }

        /** Puts back anything the last try changed. */
        void prepare(GameTestHelper h, Villager v, BrainManager.Handle handle) {}

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
        c.prepare(h, v, handle);
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

    /** A player's way up: look down at the floor before the ledge, place a dirt step, then jump up. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void stepCourseIsPassable(GameTestHelper h) {
        Course c = Course.STEP;
        c.buildLane(h);
        Villager v = spawnMcaVillager(h, new Vec3(5.5, 2, LANE + 0.5));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        c.prepare(h, v, handle);
        handle.setGoal(h.absolutePos(c.goal()));
        h.runAtTickTime(10, () -> {
            v.setXRot(60f);
            handle.hold(new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true));
        });
        h.runAtTickTime(11, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(15, () -> {
            h.assertTrue(v.getMainHandItem().getCount() == STEP_DIRT - 1, "no step was placed");
            v.setXRot(0f);
            handle.hold(c.playerKeys);
        });
        h.succeedWhen(() -> {
            h.assertTrue(handle.goal() == null, "STEP: goal not reached yet");
            LOG.info("STEP course passed by placing a step at tick {}", h.getTick());
        });
    }

    private static void passable(GameTestHelper h, Course c) {
        c.buildLane(h);
        Villager v = spawnMcaVillager(h, c.start());
        BrainManager.Handle handle = attach(h, v);
        handle.hold(c.playerKeys);
        handle.setGoal(h.absolutePos(c.goal()));
        int[] climbing = {0};
        h.onEachTick(() -> {
            if (v.onClimbable()) {
                climbing[0]++;
            }
        });
        h.succeedWhen(() -> {
            h.assertFalse(c.fell(h, v), c + ": fell off");
            h.assertTrue(handle.goal() == null, c + ": goal not reached yet");
            LOG.info("{} course passed with {} at tick {} ({} ticks on a ladder)", c, c.playerKeys.keys(), h.getTick(), climbing[0]);
        });
    }

    // ------------------------------------------------------------------ learned by the brain

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 200 + 200, required = false)
    public static void learnsToWalkToASeenGoal(GameTestHelper h) {
        learns(h, Course.OPEN, false);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 200 + 200, required = false)
    public static void learnsToJumpOverAStep(GameTestHelper h) {
        learns(h, Course.JUMP, false);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void learnsToClimbALadder(GameTestHelper h) {
        learns(h, Course.CLIMB, false);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void learnsToSwimAcrossAPool(GameTestHelper h) {
        learns(h, Course.SWIM, false);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void learnsToSneakUpToALedge(GameTestHelper h) {
        learns(h, Course.LEDGE, false);
    }

    @GameTest(template = ARENA, timeoutTicks = STEP_EPISODES * 400 + 200, required = false)
    public static void learnsToPlaceAStepUpALedge(GameTestHelper h) {
        learns(h, Course.STEP, false);
    }

    @GameTest(template = ARENA, timeoutTicks = STEP_EPISODES * 400 + 200, required = false)
    public static void adultLearnsToPlaceAStepUpALedge(GameTestHelper h) {
        learns(h, Course.STEP, true);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 200 + 200, required = false)
    public static void adultLearnsToWalkToASeenGoal(GameTestHelper h) {
        learns(h, Course.OPEN, true);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 200 + 200, required = false)
    public static void adultLearnsToJumpOverAStep(GameTestHelper h) {
        learns(h, Course.JUMP, true);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void adultLearnsToClimbALadder(GameTestHelper h) {
        learns(h, Course.CLIMB, true);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void adultLearnsToSwimAcrossAPool(GameTestHelper h) {
        learns(h, Course.SWIM, true);
    }

    @GameTest(template = ARENA, timeoutTicks = EPISODES * 300 + 200, required = false)
    public static void adultLearnsToSneakUpToALedge(GameTestHelper h) {
        learns(h, Course.LEDGE, true);
    }

    /** Outcome of one try. */
    private enum Outcome { REACHED, FELL, TIMED_OUT }

    private static void learns(GameTestHelper h, Course c, boolean adult) {
        if (!WorkLearningTests.selected(h, c.name())) {
            return;
        }
        String who = c + (adult ? " adult" : " newborn");
        // Run the server as fast as it can: learning takes many tries. Lockstep keeps the brain
        // stepping once per tick, as it would in real time.
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(c.episodeTicks * c.episodes + 400);
        c.buildLane(h);
        // A newborn babbles the most and has its inborn reflexes; an adult starts with a fresh brain.
        Villager v = spawnMcaVillager(h, c.start());
        if (!adult) {
            v.setAge(-24000);
        }
        BrainManager.Handle handle = attach(h, v);
        reset(h, c, v, handle);

        List<Outcome> outcomes = new ArrayList<>();
        int[] ticks = {0};
        int[] sneakNearEdge = {0, 0};
        // Ticks in the last tries with the jump key down: in water, and on dry ground.
        int[] jumpWet = {0, 0};
        int[] jumpDry = {0, 0};
        // Ticks in the last tries on a ladder, and of those, pushing forward into it.
        int[] onLadder = {0, 0};
        int[] pushingLadder = {0, 0};
        // Ticks in the last tries with a screen open (its own inventory, opened by the inventory key).
        int[] inScreen = {0, 0};
        // Ticks holding a block, and of those, pressing use, in the first and the last tries; and
        // blocks placed in the first and the last tries.
        int[] useHeldFirst = {0, 0};
        int[] useHeldLast = {0, 0};
        int[] placed = {0, 0};
        StepStats step = new StepStats();
        // Closest each try got to the goal, as a fraction of the start distance still left.
        double startDist = Vec3.atBottomCenterOf(c.goal()).distanceTo(c.start());
        double[] closest = {1.0};
        double[] closestSum = {0.0};
        h.onEachTick(() -> {
            if (outcomes.size() >= c.episodes) {
                return;
            }
            ticks[0]++;
            if (handle.goal() != null) {
                closest[0] = Math.min(closest[0], h.relativeVec(v.position()).distanceTo(Vec3.atBottomCenterOf(c.goal())) / startDist);
            }
            if (outcomes.size() >= c.episodes - WINDOW) {
                onLadder[1]++;
                inScreen[1]++;
                if (handle.hands().screenOpen()) {
                    inScreen[0]++;
                }
                if (v.onClimbable()) {
                    onLadder[0]++;
                    pushingLadder[1]++;
                    if (handle.command().forward() > 0) {
                        pushingLadder[0]++;
                    }
                }
                int[] counter = v.isInWater() ? jumpWet : jumpDry;
                counter[1]++;
                if (handle.command().jump()) {
                    counter[0]++;
                }
            }
            int[] use = outcomes.size() < WINDOW ? useHeldFirst : outcomes.size() >= c.episodes - WINDOW ? useHeldLast : null;
            if (use != null && handle.hands().holdingBlock()) {
                use[1]++;
                if (handle.command().use()) {
                    use[0]++;
                }
            }
            if (c == Course.STEP) {
                step.tick(h, v, handle);
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
            if (c == Course.STEP && (outcomes.size() < WINDOW || outcomes.size() >= c.episodes - WINDOW)) {
                int left = v.getMainHandItem().is(Items.DIRT) ? v.getMainHandItem().getCount() : 0;
                placed[outcomes.size() < WINDOW ? 0 : 1] += STEP_DIRT - left - v.getInventory().countItem(Items.DIRT);
            }
            outcomes.add(done);
            if (c == Course.STEP && outcomes.size() % STEP_STATS_WINDOW == 0) {
                step.report(who, outcomes.size(), count(outcomes, outcomes.size() - STEP_STATS_WINDOW, Outcome.REACHED));
            }
            closestSum[0] += done == Outcome.REACHED ? 0.0 : closest[0];
            closest[0] = 1.0;
            ticks[0] = 0;
            if (outcomes.size() % WINDOW == 0) {
                LOG.info("{} learning: episodes {}-{}: {} reached, {} fell, closest approach left {}% of the way on average; brain {} steps",
                        who, outcomes.size() - WINDOW + 1, outcomes.size(),
                        count(outcomes, outcomes.size() - WINDOW, Outcome.REACHED),
                        count(outcomes, outcomes.size() - WINDOW, Outcome.FELL),
                        Math.round(100 * closestSum[0] / WINDOW), handle.brain().ticks());
                closestSum[0] = 0.0;
            }
            if (outcomes.size() < c.episodes) {
                reset(h, c, v, handle);
                return;
            }
            int first = count(outcomes.subList(0, WINDOW), 0, Outcome.REACHED);
            int last = count(outcomes, c.episodes - WINDOW, Outcome.REACHED);
            int lastFalls = count(outcomes, c.episodes - WINDOW, Outcome.FELL);
            String report = String.format("%s: reached the goal in %d of the first %d tries and %d of the last %d (%d falls);"
                            + " in the last tries jump was held %d%% of the time in water, %d%% on land,"
                            + " a screen was open %d%% of the time%s",
                    c, first, WINDOW, last, WINDOW, lastFalls, percent(jumpWet), percent(jumpDry), percent(inScreen),
                    c == Course.LEDGE ? String.format("; sneaking %d%% of the time near the edge",
                            percent(sneakNearEdge)) : "")
                    + (c == Course.STEP ? String.format("; holding a block, it pressed place %d%% of the time in the first tries"
                            + " and %d%% in the last; blocks placed: %d in the first tries, %d in the last",
                            percent(useHeldFirst), percent(useHeldLast), placed[0], placed[1]) : "")
                    + (c == Course.CLIMB ? String.format("; on the ladder %d%% of the time, pushing W %d%% of that",
                            percent(onLadder), percent(pushingLadder)) : "");
            LOG.info("{} learning result: {}", who, report);
            // On the ledge, reaching the edge without falling is the goal; how often it sneaks
            // there is only reported, since stopping short is as good a way as any.
            if (last >= 7 && last > first && lastFalls <= (c == Course.LEDGE ? 0 : 2)) {
                h.succeed();
            } else {
                h.fail("not learned yet: " + report);
            }
        });
    }

    private static final int STEP_STATS_WINDOW = 50;

    /**
     * Why placing comes and goes on the step course, per 50 tries: presses while holding dirt and
     * what came of them, where the blocks landed, the gaze while pressing, and dopamine with and
     * without the button down.
     */
    private static final class StepStats {
        int holdTicks;
        int pressTicks;
        double pitchSum;
        double dopaPress;
        int dopaPressTicks;
        double dopaIdle;
        int dopaIdleTicks;
        int floor;
        int wall;
        int top;
        int other;
        int seenPlaced;
        int tries0;
        int noTarget0;
        int refused0;
        int placed0;
        BrainManager.Handle handle;

        void tick(GameTestHelper h, Villager v, BrainManager.Handle handle) {
            var hands = handle.hands();
            if (this.handle == null) {
                this.handle = handle;
                mark();
            }
            boolean holding = v.getMainHandItem().is(Items.DIRT);
            boolean press = handle.command().use();
            float dopamine = handle.brain().dopamine();
            if (holding) {
                holdTicks++;
                if (press) {
                    pressTicks++;
                    pitchSum += v.getXRot();
                }
            }
            if (press) {
                dopaPress += dopamine;
                dopaPressTicks++;
            } else {
                dopaIdle += dopamine;
                dopaIdleTicks++;
            }
            if (hands.placedTotal > seenPlaced) {
                seenPlaced = hands.placedTotal;
                // Ledge from x 7 to 11, two high on the floor at y 1; the villager stands at y 2.
                Vec3 at = h.relativeVec(Vec3.atCenterOf(hands.lastPlaced));
                int x = (int) Math.floor(at.x);
                int y = (int) Math.floor(at.y);
                if (y == 2 && x < 7) {
                    floor++;
                } else if (x == 6 && y > 2) {
                    wall++;
                } else if (x >= 7 && x <= 11 && y >= 4) {
                    top++;
                } else {
                    other++;
                }
            }
        }

        private void mark() {
            var hands = handle.hands();
            tries0 = hands.placeTries;
            noTarget0 = hands.placeNoTarget;
            refused0 = hands.placeRefused;
            placed0 = hands.placedTotal;
        }

        void report(String who, int episodes, int reached) {
            if (handle == null) {
                return;
            }
            var hands = handle.hands();
            int tries = hands.placeTries - tries0;
            LOG.info("{} placing, episodes {}-{}: reached {}; holding dirt {} ticks, pressing use {} of them ({}%);"
                            + " {} place tries: {} placed, {} aimed at nothing in reach, {} refused (would overlap a body or no room);"
                            + " placed on the floor before the ledge {}, against the ledge face higher up {}, on top {}, elsewhere {};"
                            + " mean pitch while pressing {}; mean dopamine pressing {} vs not {}",
                    who, episodes - STEP_STATS_WINDOW + 1, episodes, reached, holdTicks, pressTicks,
                    holdTicks == 0 ? 0 : 100 * pressTicks / holdTicks, tries, hands.placedTotal - placed0,
                    hands.placeNoTarget - noTarget0, hands.placeRefused - refused0, floor, wall, top, other,
                    pressTicks == 0 ? "-" : String.format("%.1f", pitchSum / pressTicks),
                    dopaPressTicks == 0 ? "-" : String.format("%.4f", dopaPress / dopaPressTicks),
                    dopaIdleTicks == 0 ? "-" : String.format("%.4f", dopaIdle / dopaIdleTicks));
            holdTicks = pressTicks = dopaPressTicks = dopaIdleTicks = floor = wall = top = other = 0;
            pitchSum = dopaPress = dopaIdle = 0;
            mark();
        }
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
