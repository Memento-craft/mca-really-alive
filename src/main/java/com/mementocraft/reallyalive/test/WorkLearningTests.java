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
                for (int y = 2; y <= 3; y++) {
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

    private static void regrow(GameTestHelper h) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 8; z <= 16; z++) {
                BlockPos p = new BlockPos(x, 1, z);
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
        h.onEachTick(() -> {
            tick[0]++;
            if (handle.hands().screenOpen()) {
                screenTicks[0]++;
            }
            boolean aimDirt = handle.hands().target() instanceof BlockHitResult hit
                    && v.level().getBlockState(hit.getBlockPos()).is(Blocks.DIRT);
            boolean use = handle.command().use();
            onDirt[0] += aimDirt ? 1 : 0;
            using[0] += use ? 1 : 0;
            usingOnDirt[0] += aimDirt && use ? 1 : 0;
            pitch[0] += v.getXRot();
            if (tick[0] % REGROW == 0) {
                regrow(h);
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
                for (int y = 2; y <= 3; y++) {
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

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS + 400, required = false)
    public static void newbornCraftsPlanksOnItsOwn(GameTestHelper h) {
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
        double[] pointerSum = {0, 0};
        int[] holdingOverGrid = {0};
        int[] holdingOverResult = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                // Logs placed about the pen are cleared away; one that has no logs left is handed
                // more, as a parent would, so the benchmark keeps measuring crafting, not building.
                clearPen(h);
                regrow(h);
                if (carried(v, Items.OAK_LOG) == 0) {
                    v.getInventory().removeItemType(Items.OAK_LOG, 64);
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_LOG, 64));
                    handBacks[0]++;
                }
            }
            if (handle.hands().screenOpen()) {
                screenTicks[0]++;
                attackTicks[0] += handle.command().attack() ? 1 : 0;
                keyTicks[0] += handle.command().inventory() ? 1 : 0;
                overSlot[0] += handle.hands().pointedSlot() != null ? 1 : 0;
                overThing[0] += handle.hands().pointedSlot() != null && handle.hands().pointedSlot().hasItem() ? 1 : 0;
                if (screenTicks[0] <= 3) {
                    // What the eyes see on the first screen: where its things are against the pointer.
                    float[] vision = new float[com.mementocraft.reallyalive.brain.SenseLayout.VISION_SIZE];
                    float[] colour = new float[com.mementocraft.reallyalive.brain.SenseLayout.COLOUR_SIZE];
                    var menu = handle.hands().screen();
                    com.mementocraft.reallyalive.sense.ScreenSight.see(menu, handle.hands().pointerX(), handle.hands().pointerY(), vision, colour);
                    StringBuilder seen = new StringBuilder();
                    for (int y = 0; y < com.mementocraft.reallyalive.brain.SenseLayout.VISION_H; y++) {
                        for (int x = 0; x < com.mementocraft.reallyalive.brain.SenseLayout.VISION_W; x++) {
                            if (vision[com.mementocraft.reallyalive.brain.SenseLayout.visionIndex(x, y, com.mementocraft.reallyalive.brain.SenseLayout.CH_CREATURE)] > 0) {
                                seen.append(" thing@").append(x).append(',').append(y);
                            }
                        }
                    }
                    StringBuilder slots = new StringBuilder();
                    for (var sl : menu.slots) {
                        if (sl.hasItem()) {
                            slots.append(" ").append(sl.index).append('@').append(sl.x).append(',').append(sl.y).append('=').append(sl.getItem());
                        }
                    }
                    LOG.info("CRAFT screen look: pointer {},{}; seen{}; slots{}; command yaw {} pitch {}", handle.hands().pointerX(),
                            handle.hands().pointerY(), seen, slots, handle.command().yawDelta(), handle.command().pitchDelta());
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
                LOG.info("CRAFT newborn: by tick {}: opened {} screens, clicked {} slots ({} holding something), saw {} results form, made {};"
                                + " in screens {} ticks, left button {}, inventory key {}, over a slot {}, over a thing {}, mean pointer {},{}; carries {} logs, {} planks;"
                                + " logs handed back {} times; a stack on the pointer {} ticks, over the grid {}, over the result {}", tick[0],
                        handle.hands().screensOpened, handle.hands().screenClicks, handle.hands().clicksOnThings, handle.hands().formedTotal, handle.hands().madeTotal,
                        screenTicks[0], attackTicks[0], keyTicks[0], overSlot[0], overThing[0],
                        Math.round(pointerSum[0] / Math.max(1, screenTicks[0])), Math.round(pointerSum[1] / Math.max(1, screenTicks[0])), carried(v, Items.OAK_LOG), carried(v, Items.OAK_PLANKS),
                        handBacks[0], pointerHolding[0], holdingOverGrid[0], holdingOverResult[0]);
            }
            if (tick[0] == CRAFT_TICKS) {
                String report = String.format("CRAFT: made %d things in %d ticks; opened its inventory %d times, open %d%% of the time,"
                                + " with a stack on the pointer %d%% of that",
                        handle.hands().madeTotal, CRAFT_TICKS, handle.hands().screensOpened, 100 * screenTicks[0] / CRAFT_TICKS,
                        screenTicks[0] == 0 ? 0 : 100 * pointerHolding[0] / screenTicks[0]);
                LOG.info("CRAFT newborn learning result: {}", report);
                if (handle.hands().madeTotal >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
}
