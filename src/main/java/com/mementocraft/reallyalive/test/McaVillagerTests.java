package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.body.BrainDriven;
import com.mementocraft.reallyalive.body.PlayerInputMoveControl;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * In-game checks that the brain really drives MCA's villagers. Run with {@code ./gradlew
 * runGameTestServer}; MCA is on the dev runtime classpath. Only runs when the {@code reallyalive}
 * game test namespace is enabled, so it never touches normal play.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class McaVillagerTests {
    private static final Logger LOG = LogUtils.getLogger();
    /** A stone floor at y 1 (y 0 is the test's structure block), so bodies stand at y 2. */
    private static final String FLOOR = "flat_stone";
    /** Yaw that faces +x, down the long side of the test floor. */
    static final float EAST = -90f;
    private static final MotorCommand WALK = new MotorCommand(1, 0, false, false, false, 0f, 0f);
    private static final MotorCommand SPRINT = new MotorCommand(1, 0, false, false, true, 0f, 0f);
    private static final MotorCommand SPRINT_JUMP = new MotorCommand(1, 0, true, false, true, 0f, 0f);
    private static final MotorCommand SNEAK = new MotorCommand(1, 0, false, true, false, 0f, 0f);

    private McaVillagerTests() {}

    static Villager spawnMcaVillager(GameTestHelper helper, Vec3 at) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(ResourceLocation.fromNamespaceAndPath("mca", "male_villager"))
                .orElse(null);
        if (type == null) {
            helper.fail("MCA is not loaded: no mca:male_villager entity");
        }
        if (!(helper.spawn(type, at) instanceof Villager v)) {
            helper.fail("mca:male_villager is not a villager");
            throw new IllegalStateException();
        }
        v.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(v.blockPosition()),
                MobSpawnType.SPAWN_EGG, null);
        v.setAge(0);
        v.setYRot(EAST);
        v.setYHeadRot(EAST);
        v.yBodyRot = EAST;
        return v;
    }

    static BrainManager.Handle attach(GameTestHelper helper, Villager v) {
        BrainManager.Handle h = BrainManager.get().attach(v);
        if (h == null) {
            helper.fail("brain did not attach", v);
        }
        return h;
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void mcaVillagerGetsABrainAndGivesItBack(GameTestHelper helper) {
        Villager v = spawnMcaVillager(helper, new Vec3(3.5, 2, 6.5));
        String mcaMove = v.getMoveControl().getClass().getName();
        BrainManager.Handle h = attach(helper, v);
        helper.assertTrue(v.getMoveControl() instanceof PlayerInputMoveControl, "brain did not take the move control");
        helper.assertTrue(BrainDriven.isDriven(v), "villager is not marked brain driven");
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(h.brain().ticks() > 50, "brain ran only " + h.brain().ticks() + " steps in 100 ticks");
            BrainManager.get().detach(v, false);
            helper.assertTrue(v.getMoveControl().getClass().getName().equals(mcaMove),
                    "MCA's move control was not given back, got " + v.getMoveControl().getClass().getName());
            helper.assertFalse(BrainDriven.isDriven(v), "villager is still marked brain driven");
            helper.succeed();
        });
    }

    @GameTest(template = FLOOR, timeoutTicks = 300)
    public static void mcaAiDoesNotMoveABrainVillager(GameTestHelper helper) {
        Villager v = spawnMcaVillager(helper, new Vec3(6.5, 2, 6.5));
        attach(helper, v).hold(MotorCommand.IDLE);
        helper.runAfterDelay(20, () -> {
            Vec3 start = v.position();
            helper.runAfterDelay(200, () -> {
                double moved = v.position().subtract(start).horizontalDistance();
                helper.assertTrue(moved < 0.25, "villager moved " + moved + " blocks with no keys pressed");
                helper.succeed();
            });
        });
    }

    /** With nobody holding its keys, the brain's own motor output moves the MCA villager. */
    @GameTest(template = FLOOR, timeoutTicks = 300)
    public static void mcaVillagerMovesUnderItsOwnBrain(GameTestHelper helper) {
        Villager v = spawnMcaVillager(helper, new Vec3(20.5, 2, 6.5));
        BrainManager.Handle h = attach(helper, v);
        h.brain().setBabbleScale(4f);
        Vec3 start = v.position();
        float startYaw = v.getYRot();
        int[] pressed = {0};
        helper.onEachTick(() -> {
            MotorCommand c = h.command();
            if (c.forward() != 0 || c.strafe() != 0 || c.jump() || c.sneak() || c.sprint()) {
                pressed[0]++;
            }
        });
        helper.runAtTickTime(250, () -> {
            double moved = v.position().subtract(start).horizontalDistance();
            float turned = Math.abs(net.minecraft.util.Mth.wrapDegrees(v.getYRot() - startYaw));
            LOG.info("own brain: {} ticks with keys down, moved {} blocks, turned {} degrees, {} brain steps",
                    pressed[0], String.format("%.2f", moved), String.format("%.1f", turned), h.brain().ticks());
            helper.assertTrue(pressed[0] > 0, "the brain never pressed a key");
            helper.assertTrue(moved > 0.5 || turned > 10f, "the brain's keys did not move the villager");
            helper.succeed();
        });
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void mcaVillagerWalksLikeAPlayer(GameTestHelper helper) {
        raceAgainstPlayer(helper, WALK, false);
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void mcaVillagerSprintsLikeAPlayer(GameTestHelper helper) {
        raceAgainstPlayer(helper, SPRINT, false);
    }

    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void mcaVillagerSprintJumpsLikeAPlayer(GameTestHelper helper) {
        raceAgainstPlayer(helper, SPRINT_JUMP, false);
    }

    /** Sneaking up to a hole in the floor: both must stop at the same spot without falling in. */
    @GameTest(template = FLOOR, timeoutTicks = 200)
    public static void mcaVillagerSneaksToTheEdgeLikeAPlayer(GameTestHelper helper) {
        for (int z = 0; z < 12; z++) {
            for (int x = 6; x < 9; x++) {
                helper.setBlock(x, 1, z, Blocks.AIR);
            }
        }
        raceAgainstPlayer(helper, SNEAK, true);
    }

    /**
     * An MCA villager holding the keys and a player holding the same keys, side by side on stone,
     * must move the same amount on every tick.
     */
    private static void raceAgainstPlayer(GameTestHelper helper, MotorCommand keys, boolean toTheEdge) {
        Villager v = spawnMcaVillager(helper, new Vec3(1.5, 2, 3.5));
        attach(helper, v).hold(keys);

        Player p = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 8.5));
        p.moveTo(at.x, at.y, at.z, EAST, 0f);
        double floor = at.y;

        List<Vec3> villagerPath = new ArrayList<>();
        List<Vec3> playerPath = new ArrayList<>();
        // What a player's keyboard does each tick (LocalPlayer.aiStep and serverAiStep), then its
        // movement. Everything is read in this one runnable: a separate runAtTickTime would replace
        // it on that tick.
        helper.onEachTick(() -> {
            float slow = keys.sneak() ? (float) p.getAttributeValue(Attributes.SNEAKING_SPEED) : 1f;
            p.setShiftKeyDown(keys.sneak());
            p.setSprinting(keys.sprint());
            p.setJumping(keys.jump());
            p.zza = keys.forward() * slow;
            p.xxa = keys.strafe() * slow;
            p.aiStep();
            villagerPath.add(v.position());
            playerPath.add(p.position());
            if (villagerPath.size() < RACE_TICKS) {
                return;
            }
            List<Vec3> sv = steps(villagerPath);
            List<Vec3> sp = steps(playerPath);
            // The villager may start pressing a tick or two before or after the player: line the
            // two up at the offset where they agree best, then every tick's movement must match.
            double worst = Double.MAX_VALUE;
            int offset = 0;
            for (int k = -3; k <= 3; k++) {
                double w = 0;
                for (int t = 3; t + 3 < sp.size(); t++) {
                    w = Math.max(w, sv.get(t + k).distanceTo(sp.get(t)));
                }
                if (w < worst) {
                    worst = w;
                    offset = k;
                }
            }
            Vec3 endV = villagerPath.get(villagerPath.size() - 1).subtract(villagerPath.get(0));
            Vec3 endP = playerPath.get(playerPath.size() - 1).subtract(playerPath.get(0));
            String report = String.format("after %d ticks villager moved %s, player %s; worst per-tick gap %.6f, offset %d (%s)",
                    RACE_TICKS, fmt(endV), fmt(endP), worst, offset, keys.keys());
            LOG.info(report);
            helper.assertTrue(endP.horizontalDistance() > 1, "the reference player did not move: " + report);
            if (toTheEdge) {
                helper.assertTrue(p.getY() >= floor - 1e-6, "the reference player fell: " + report);
                helper.assertTrue(v.getY() >= floor - 1e-6, "villager fell off the edge: " + report);
            }
            helper.assertTrue(worst < 1e-4, "paths differ: " + report);
            helper.succeed();
        });
    }

    private static final int RACE_TICKS = 80;

    /** How far the body moved on each tick. */
    private static List<Vec3> steps(List<Vec3> path) {
        List<Vec3> out = new ArrayList<>();
        for (int i = 1; i < path.size(); i++) {
            out.add(path.get(i).subtract(path.get(i - 1)));
        }
        return out;
    }

    private static String fmt(Vec3 d) {
        return String.format("(%.3f, %.3f, %.3f)", d.x, d.y, d.z);
    }
}
