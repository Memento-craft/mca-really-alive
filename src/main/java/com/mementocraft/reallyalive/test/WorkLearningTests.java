package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * Benchmarks ({@code required = false}): does a newborn brain, left alone with a tool or material
 * and nothing but its inborn motives, come to use it? In a walled dirt pen with a hoe in hand,
 * tilling (turning dirt into farmland feels good) should become more frequent as it learns. With
 * logs in hand and its inventory key, making planks (making things feels good) would need it to
 * find the key, the log, the grid and the result by itself; this one mostly reports how far it gets.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class WorkLearningTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";
    private static final int TICKS = 60_000;
    private static final int WINDOW = 10_000;
    /** Crafting takes a long chain of chance steps before the first success, so it gets longer. */
    private static final int CRAFT_TICKS = 150_000;
    /** Tilled ground goes back to dirt this often, so there is always something to till. */
    private static final int REGROW = 200;

    private WorkLearningTests() {}

    /**
     * Benchmarks to run, from {@code -Pbenchmarks=TILL,CRAFT} (all when unset), so one slow
     * benchmark can be rerun without waiting for the others.
     */
    static boolean selected(GameTestHelper h, String name) {
        String only = System.getProperty("reallyalive.benchmarks", "");
        if (only.isBlank() || java.util.Arrays.asList(only.toUpperCase(java.util.Locale.ROOT).split(",")).contains(name)) {
            return true;
        }
        // The benchmarks switch on lockstep for the whole server, and the other tests have only
        // ever run beside them; keep that the same when the benchmarks are left out.
        BrainManager.get().setLockstep(true);
        h.succeed();
        return false;
    }

    /**
     * A 9 by 9 dirt floor walled in, the villager in the middle holding the given thing. The walls
     * and the ground under the dirt are bedrock: a villager that digs (one did, with its hoe, and
     * walked out under the wall) stays in the pen, and the dirt it digs up grows back.
     */
    private static Villager pen(GameTestHelper h, ItemStack inHand, int ticks) {
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(ticks + 400);
        for (int x = 0; x <= 10; x++) {
            for (int z = 7; z <= 17; z++) {
                boolean wall = x == 0 || x == 10 || z == 7 || z == 17;
                h.setBlock(x, 0, z, Blocks.BEDROCK);
                h.setBlock(x, 1, z, wall ? Blocks.BEDROCK : Blocks.DIRT);
                for (int y = 2; y <= 4; y++) {
                    h.setBlock(x, y, z, wall ? Blocks.BEDROCK : Blocks.AIR);
                }
            }
        }
        Villager v = spawnMcaVillager(h, new Vec3(5.5, 2, 12.5));
        v.setAge(-24000);
        v.getInventory().clearContent();
        v.setItemSlot(EquipmentSlot.MAINHAND, inHand);
        attach(h, v);
        return v;
    }

    /**
     * Turns the pen's floor back into dirt, except where the villager stands: filling in the hole
     * it dug and stood in once buried it alive, and the benchmark went on measuring a dead body.
     */
    private static void regrow(GameTestHelper h, Villager v) {
        BlockPos at = h.relativePos(v.blockPosition());
        for (int x = 1; x <= 9; x++) {
            for (int z = 8; z <= 16; z++) {
                BlockPos p = new BlockPos(x, 1, z);
                if (p.getX() == at.getX() && p.getZ() == at.getZ() && at.getY() <= 2) {
                    continue;
                }
                if (!h.getBlockState(p).is(Blocks.DIRT)) {
                    h.setBlock(p, Blocks.DIRT);
                    h.setBlock(p.above(), Blocks.AIR);
                }
            }
        }
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornLearnsToTill(GameTestHelper h) {
        if (!selected(h, "TILL")) {
            return;
        }
        ItemStack hoe = new ItemStack(Items.WOODEN_HOE);
        hoe.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        Villager v = pen(h, hoe, TICKS);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] windowStart = {0};
        int[] perWindow = new int[TICKS / WINDOW];
        int[] screenTicks = {0};
        int[] handBacks = {0};
        // Per window: ticks aiming at dirt in reach, ticks pressing use, and both at once; pitch sum.
        int[] onDirt = {0};
        int[] using = {0};
        int[] usingOnDirt = {0};
        double[] pitch = {0};
        boolean[] escaped = {false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (handle.hands().screenOpen()) {
                screenTicks[0]++;
            }
            BlockPos rel = h.relativePos(v.blockPosition());
            net.minecraft.world.phys.AABB inside = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(1, 1, 8)).getCenter(),
                    h.absolutePos(new BlockPos(9, 4, 16)).getCenter()).inflate(0.6);
            if (!escaped[0] && !inside.contains(v.position())) {
                // How did it get out? Say where it is and what is around it, once.
                escaped[0] = true;
                LOG.info("TILL newborn left the pen at tick {}: at {} (relative {}), on {}, holding {}, screen {}, passenger of {}",
                        tick[0], v.blockPosition(), rel, v.level().getBlockState(v.blockPosition().below()), v.getMainHandItem(),
                        handle.hands().screenOpen(), v.getVehicle());
            }
            boolean aimDirt = handle.hands().target() instanceof BlockHitResult hit
                    && v.level().getBlockState(hit.getBlockPos()).is(Blocks.DIRT);
            boolean use = handle.command().use();
            onDirt[0] += aimDirt ? 1 : 0;
            using[0] += use ? 1 : 0;
            usingOnDirt[0] += aimDirt && use ? 1 : 0;
            pitch[0] += v.getXRot();
            if (tick[0] % REGROW == 0) {
                // Dug-up dirt it puts down again is cleared too: one villager built a stair of it
                // out over the wall.
                clearPen(h);
                regrow(h, v);
                // Babbling can put the hoe away (a shift-click in its inventory, the wheel): hand it
                // back, as a parent would, so the benchmark keeps measuring tilling.
                if (!v.getMainHandItem().is(Items.WOODEN_HOE) && !handle.hands().screenOpen()) {
                    v.getInventory().removeAllItems();
                    v.setItemSlot(EquipmentSlot.MAINHAND, hoe.copy());
                    handBacks[0]++;
                }
            }
            if (tick[0] % WINDOW == 0) {
                int w = tick[0] / WINDOW - 1;
                perWindow[w] = handle.hands().changedTotal - windowStart[0];
                windowStart[0] = handle.hands().changedTotal;
                LOG.info("TILL newborn: ticks {}-{}: {} tilled; aiming at dirt {}% of ticks, pressing use {}%, use while on dirt {}%,"
                                + " mean pitch {}; holding {}; now at {} aiming at {}", tick[0] - WINDOW, tick[0], perWindow[w], 100 * onDirt[0] / WINDOW,
                        100 * using[0] / WINDOW, 100 * usingOnDirt[0] / Math.max(1, onDirt[0]), Math.round(pitch[0] / WINDOW),
                        v.getMainHandItem(), h.relativePos(v.blockPosition()), aimed(h, handle));
                onDirt[0] = 0;
                using[0] = 0;
                usingOnDirt[0] = 0;
                pitch[0] = 0;
            }
            if (tick[0] == TICKS) {
                int first = perWindow[0];
                int last = perWindow[perWindow.length - 1];
                String report = String.format("TILL: %d tilled in the first %d ticks, %d in the last; a screen was open %d%% of the time;"
                                + " the hoe was handed back %d times", first, WINDOW, last, 100 * screenTicks[0] / TICKS, handBacks[0]);
                LOG.info("TILL newborn learning result: {}", report);
                // With the working gaze a newborn tills from the start (188 in the first window,
                // 303 in the last, where a level gaze tilled 9 then nothing): it must keep at it
                // and get better, not merely start.
                if (last >= 100 && last > first) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    private static int carried(Villager v, net.minecraft.world.item.Item item) {
        int n = v.getMainHandItem().is(item) ? v.getMainHandItem().getCount() : 0;
        return n + v.getInventory().countItem(item);
    }

    /** Takes away whatever was put on the pen's floor. */
    private static void clearPen(GameTestHelper h) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 8; z <= 16; z++) {
                for (int y = 2; y <= 4; y++) {
                    if (!h.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        h.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
    }

    private static String aimed(GameTestHelper h, BrainManager.Handle handle) {
        if (handle.hands().target() instanceof BlockHitResult hit) {
            return h.getLevel().getBlockState(hit.getBlockPos()).getBlock() + " at " + h.relativePos(hit.getBlockPos());
        }
        return String.valueOf(handle.hands().target());
    }

    /**
     * Planting: tilled ground and seeds in hand. A seed set on farmland is a block placed, which
     * feels good, so planting should become more frequent; the ground is cleared and tilled again
     * every so often so there is always somewhere to plant.
     */
    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornLearnsToPlant(GameTestHelper h) {
        if (!selected(h, "PLANT")) {
            return;
        }
        Villager v = pen(h, new ItemStack(Items.WHEAT_SEEDS, 64), TICKS);
        replant(h, v);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] perWindow = new int[TICKS / WINDOW];
        int[] windowStart = {0};
        int[] planted = {0};
        int[] handBacks = {0};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            planted[0] = handle.hands().placedTotal;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                replant(h, v);
                if (carried(v, Items.WHEAT_SEEDS) == 0) {
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WHEAT_SEEDS, 64));
                    handBacks[0]++;
                }
            }
            if (tick[0] % WINDOW == 0) {
                int w = tick[0] / WINDOW - 1;
                perWindow[w] = planted[0] - windowStart[0];
                windowStart[0] = planted[0];
                LOG.info("PLANT newborn: ticks {}-{}: {} planted; mean pitch now {}; carries {} seeds; seeds handed back {} times",
                        tick[0] - WINDOW, tick[0], perWindow[w], Math.round(v.getXRot()), carried(v, Items.WHEAT_SEEDS), handBacks[0]);
            }
            if (tick[0] == TICKS) {
                int first = perWindow[0];
                int last = perWindow[perWindow.length - 1];
                String report = String.format("PLANT: %d planted in the first %d ticks, %d in the last; seeds handed back %d times",
                        first, WINDOW, last, handBacks[0]);
                LOG.info("PLANT newborn learning result: {}", report);
                if (last >= 50 && last > first) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    /** Turns the pen's floor back into bare farmland, wet so it stays tilled. */
    private static void replant(GameTestHelper h, Villager v) {
        BlockPos at = h.relativePos(v.blockPosition());
        for (int x = 1; x <= 9; x++) {
            for (int z = 8; z <= 16; z++) {
                if (x == at.getX() && z == at.getZ() && at.getY() <= 2) {
                    continue;
                }
                h.setBlock(x, 1, z, Blocks.FARMLAND.defaultBlockState().setValue(net.minecraft.world.level.block.FarmBlock.MOISTURE, 7));
                for (int y = 2; y <= 4; y++) {
                    if (!h.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        h.setBlock(x, y, z, Blocks.AIR);
                    }
                }
            }
        }
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS + 400, required = false)
    public static void newbornCraftsPlanksOnItsOwn(GameTestHelper h) {
        craftBenchmark(h, "1");
    }

    /** Two more newborns, since whether one stumbles on crafting varies a lot from brain to brain. */
    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS + 400, required = false)
    public static void secondNewbornCraftsPlanksOnItsOwn(GameTestHelper h) {
        craftBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS + 400, required = false)
    public static void thirdNewbornCraftsPlanksOnItsOwn(GameTestHelper h) {
        craftBenchmark(h, "3");
    }

    private static void craftBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "CRAFT")) {
            return;
        }
        Villager v = pen(h, new ItemStack(Items.OAK_LOG, 64), CRAFT_TICKS);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] screenTicks = {0};
        int[] pointerHolding = {0};
        // While a screen is open: ticks pressing the left button, the inventory key, and over a slot.
        int[] attackTicks = {0};
        int[] keyTicks = {0};
        int[] overSlot = {0};
        int[] handBacks = {0};
        int[] overThing = {0};
        int[] resultTicks = {0};
        int[] resultFree = {0};
        int[] resultOver = {0};
        double[] pointerSum = {0, 0};
        int[] holdingOverGrid = {0};
        int[] holdingOverResult = {0};
        // Outside screens: ticks with the inventory key held, and presses of it.
        int[] keyDownOutside = {0};
        int[] keyPressesOutside = {0};
        int[] curiousTicks = {0};
        boolean[] keyWasDown = {false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                // Logs placed about the pen are cleared away; one that has no logs left is handed
                // more, as a parent would, so the benchmark keeps measuring crafting, not building.
                clearPen(h);
                regrow(h, v);
                if (carried(v, Items.OAK_LOG) == 0) {
                    v.getInventory().removeItemType(Items.OAK_LOG, 64);
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_LOG, 64));
                    handBacks[0]++;
                }
            }
            boolean keyDown = handle.command().inventory();
            if (!handle.hands().screenOpen()) {
                keyDownOutside[0] += keyDown ? 1 : 0;
                keyPressesOutside[0] += keyDown && !keyWasDown[0] ? 1 : 0;
                curiousTicks[0] += handle.hands().curiosity() > 0f ? 1 : 0;
            }
            keyWasDown[0] = keyDown;
            if (handle.hands().screenOpen()) {
                screenTicks[0]++;
                attackTicks[0] += handle.command().attack() ? 1 : 0;
                keyTicks[0] += handle.command().inventory() ? 1 : 0;
                overSlot[0] += handle.hands().pointedSlot() != null ? 1 : 0;
                overThing[0] += handle.hands().pointedSlot() != null && handle.hands().pointedSlot().hasItem() ? 1 : 0;
                if (handle.hands().screen().getSlot(0).hasItem()) {
                    // A result is showing: is the pointer free to take it, and is it over it?
                    resultTicks[0]++;
                    resultFree[0] += handle.hands().onPointer().isEmpty() ? 1 : 0;
                    if (handle.hands().onPointer().isEmpty() && resultFree[0] <= 6) {
                        // The first ticks with something made and the hand free: what pulls the eye?
                        float[] vision = new float[com.mementocraft.reallyalive.brain.SenseLayout.VISION_SIZE];
                        float[] colour = new float[com.mementocraft.reallyalive.brain.SenseLayout.COLOUR_SIZE];
                        com.mementocraft.reallyalive.sense.ScreenSight.see(handle.hands().screen(), handle.hands().pointerX(),
                                handle.hands().pointerY(), vision, colour);
                        StringBuilder seen = new StringBuilder();
                        for (int y = 0; y < com.mementocraft.reallyalive.brain.SenseLayout.VISION_H; y++) {
                            seen.append(" |");
                            for (int x = 0; x < com.mementocraft.reallyalive.brain.SenseLayout.VISION_W; x++) {
                                float c = vision[com.mementocraft.reallyalive.brain.SenseLayout.visionIndex(x, y,
                                        com.mementocraft.reallyalive.brain.SenseLayout.CH_CREATURE)];
                                seen.append(c > 0.9f ? '#' : c > 0.2f ? '+' : c > 0f ? '.' : ' ');
                            }
                        }
                        LOG.info("CRAFT newborn {} made something, hand free: pointer {},{}; command yaw {} pitch {}; eyes{}", which,
                                handle.hands().pointerX(), handle.hands().pointerY(), handle.command().yawDelta(),
                                handle.command().pitchDelta(), seen);
                    }
                    resultOver[0] += handle.hands().pointedSlot() != null && handle.hands().pointedSlot().index == 0 ? 1 : 0;
                }
                pointerSum[0] += handle.hands().pointerX();
                pointerSum[1] += handle.hands().pointerY();
                if (!handle.hands().onPointer().isEmpty()) {
                    pointerHolding[0]++;
                    // Where the pointer is while it holds a stack: the crafting grid, its result, or elsewhere.
                    net.minecraft.world.inventory.Slot at = handle.hands().pointedSlot();
                    if (at != null && at.index >= 1 && at.index <= 4) {
                        holdingOverGrid[0]++;
                    } else if (at != null && at.index == 0) {
                        holdingOverResult[0]++;
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("CRAFT newborn " + which + ": by tick {}: opened {} screens, clicked {} slots ({} holding something), saw {} results form, made {};"
                                + " in screens {} ticks, left button {}, inventory key {}, over a slot {}, over a thing {}, mean pointer {},{}; carries {} logs, {} planks;"
                                + " logs handed back {} times; a stack on the pointer {} ticks, over the grid {}, over the result {};"
                                + " a result showing {} ticks, with the pointer free {}, over it {};"
                                + " outside screens the inventory key down {} ticks, pressed {} times, curious {} ticks; moved things {} times; brain {} neurons, {} synapses", tick[0],
                        handle.hands().screensOpened, handle.hands().screenClicks, handle.hands().clicksOnThings, handle.hands().formedTotal, handle.hands().madeTotal,
                        screenTicks[0], attackTicks[0], keyTicks[0], overSlot[0], overThing[0],
                        Math.round(pointerSum[0] / Math.max(1, screenTicks[0])), Math.round(pointerSum[1] / Math.max(1, screenTicks[0])), carried(v, Items.OAK_LOG), carried(v, Items.OAK_PLANKS),
                        handBacks[0], pointerHolding[0], holdingOverGrid[0], holdingOverResult[0],
                        resultTicks[0], resultFree[0], resultOver[0], keyDownOutside[0], keyPressesOutside[0], curiousTicks[0], handle.hands().moves,
                        handle.brain().neuronCount(), handle.brain().synapseCount());
            }
            if (tick[0] == CRAFT_TICKS) {
                String report = String.format("CRAFT: made %d things in %d ticks; opened its inventory %d times, open %d%% of the time,"
                                + " with a stack on the pointer %d%% of that",
                        handle.hands().madeTotal, CRAFT_TICKS, handle.hands().screensOpened, 100 * screenTicks[0] / CRAFT_TICKS,
                        screenTicks[0] == 0 ? 0 : 100 * pointerHolding[0] / screenTicks[0]);
                LOG.info("CRAFT newborn {} learning result: {}", which, report);
                if (handle.hands().madeTotal >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
}
