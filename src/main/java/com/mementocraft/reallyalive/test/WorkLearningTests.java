package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    /** Brewing needs the stand found and opened in the world first, on top of all of crafting's steps. */
    private static final int BREW_TICKS = 150_000;

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
     * A 9 by 9 dirt floor walled and roofed in, the villager in the middle holding the given
     * thing. The walls, roof and the ground under the dirt are bedrock: a villager that digs (one did, with its hoe, and
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
                // A roof too: newborns stacked the shafts they were given into steps and
                // climbed out over the wall, and spent the rest of the benchmark outside.
                h.setBlock(x, 5, z, Blocks.BEDROCK);
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

    /** Takes away whatever was put on the pen's floor, except the blocks to keep. */
    private static void clearPen(GameTestHelper h, BlockPos... keep) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 8; z <= 16; z++) {
                for (int y = 2; y <= 4; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!h.getBlockState(p).isAir() && !java.util.Arrays.asList(keep).contains(p)) {
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
                                + " outside screens the inventory key down {} ticks, pressed {} times, curious {} ticks; moved things {} times, thrown {}; placed {} blocks; brain {} neurons, {} synapses", tick[0],
                        handle.hands().screensOpened, handle.hands().screenClicks, handle.hands().clicksOnThings, handle.hands().formedTotal, handle.hands().madeTotal,
                        screenTicks[0], attackTicks[0], keyTicks[0], overSlot[0], overThing[0],
                        Math.round(pointerSum[0] / Math.max(1, screenTicks[0])), Math.round(pointerSum[1] / Math.max(1, screenTicks[0])), carried(v, Items.OAK_LOG), carried(v, Items.OAK_PLANKS),
                        handBacks[0], pointerHolding[0], holdingOverGrid[0], holdingOverResult[0],
                        resultTicks[0], resultFree[0], resultOver[0], keyDownOutside[0], keyPressesOutside[0], curiousTicks[0], handle.hands().moves,
                        handle.hands().throwsTotal, handle.hands().placedTotal, handle.brain().neuronCount(), handle.brain().synapseCount());
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

    @GameTest(template = ARENA, timeoutTicks = BREW_TICKS + 400, required = false)
    public static void newbornBrewsOnItsOwn(GameTestHelper h) {
        brewBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = BREW_TICKS + 400, required = false)
    public static void secondNewbornBrewsOnItsOwn(GameTestHelper h) {
        brewBenchmark(h, "2");
    }

    /**
     * A brewing stand in the pen, kept fuelled, and a newborn carrying water bottles and nether
     * wart. Brewing an awkward potion means finding the stand, opening it with the use key, putting
     * a bottle and the wart in their slots, waiting twenty seconds and taking the potion out, all
     * by trial and error; taking a brewed potion is felt as making something.
     */
    private static void brewBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "BREW")) {
            return;
        }
        BlockPos stand = new BlockPos(5, 2, 10);
        Villager v = pen(h, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                net.minecraft.world.item.alchemy.Potions.WATER), BREW_TICKS);
        h.setBlock(stand, Blocks.BREWING_STAND);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] standScreens = {0};
        int[] standTicks = {0};
        int[] aimedAtStand = {0};
        int[] usedOnStand = {0};
        int[] brews = {0};
        int[] handBacks = {0};
        boolean[] wasOpen = {false};
        boolean[] wasBrewing = {false};
        int[] carryingBottle = {0};
        int[] carryingWart = {0};
        int[] buttonHeld = {0};
        int[] overSlotOnStand = {0};
        int[] overThingOnStand = {0};
        double[] pointerOnStand = {0, 0};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            var be = h.getBlockEntity(stand);
            if (!(be instanceof net.minecraft.world.level.block.entity.BrewingStandBlockEntity brewing)) {
                // Something knocked the stand down; put it back.
                h.setBlock(stand, Blocks.BREWING_STAND);
                return;
            }
            boolean open = handle.hands().screen() instanceof net.minecraft.world.inventory.BrewingStandMenu;
            standScreens[0] += open && !wasOpen[0] ? 1 : 0;
            standTicks[0] += open ? 1 : 0;
            if (open) {
                ItemStack onPointer = handle.hands().onPointer();
                carryingBottle[0] += onPointer.is(Items.POTION) ? 1 : 0;
                carryingWart[0] += onPointer.is(Items.NETHER_WART) ? 1 : 0;
                buttonHeld[0] += handle.command().attack() ? 1 : 0;
                var at = handle.hands().pointedSlot();
                overSlotOnStand[0] += at != null ? 1 : 0;
                overThingOnStand[0] += at != null && at.hasItem() ? 1 : 0;
                pointerOnStand[0] += handle.hands().pointerX();
                pointerOnStand[1] += handle.hands().pointerY();
                if (onPointer.is(Items.POTION) && carryingBottle[0] <= 6) {
                    // What pulls the eye with a bottle on the pointer?
                    float[] vision = new float[com.mementocraft.reallyalive.brain.SenseLayout.VISION_SIZE];
                    float[] colour = new float[com.mementocraft.reallyalive.brain.SenseLayout.COLOUR_SIZE];
                    com.mementocraft.reallyalive.sense.ScreenSight.see(handle.hands().screen(), handle.hands().pointerX(),
                            handle.hands().pointerY(), vision, colour);
                    StringBuilder seen = new StringBuilder();
                    for (int y = 0; y < com.mementocraft.reallyalive.brain.SenseLayout.VISION_H; y++) {
                        seen.append(" |");
                        for (int x = 0; x < com.mementocraft.reallyalive.brain.SenseLayout.VISION_W; x++) {
                            float c = vision[com.mementocraft.reallyalive.brain.SenseLayout.visionIndex(x, y,
                                    com.mementocraft.reallyalive.brain.SenseLayout.CH_CLIMBABLE)];
                            seen.append(c > 0.9f ? '#' : c > 0.2f ? '+' : c > 0f ? '.' : ' ');
                        }
                    }
                    LOG.info("BREW newborn {} carries a bottle: pointer {},{}; over {}; eyes{}", which, handle.hands().pointerX(),
                            handle.hands().pointerY(), handle.hands().pointedSlot() == null ? "nothing" : handle.hands().pointedSlot().index, seen);
                }
            }
            wasOpen[0] = open;
            boolean brewingNow = brewing.getItem(3).is(Items.NETHER_WART) && !brewing.getItem(0).isEmpty();
            brews[0] += wasBrewing[0] && !brewingNow ? 1 : 0;
            wasBrewing[0] = brewingNow;
            if (handle.hands().target() instanceof BlockHitResult hit && hit.getBlockPos().equals(h.absolutePos(stand))) {
                aimedAtStand[0]++;
                usedOnStand[0] += handle.command().use() ? 1 : 0;
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                clearPen(h, stand);
                // Potions it brewed are taken away, so it cannot put one back in and take it out
                // again, and so are the empty bottles of water it drank.
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    var c = v.getInventory().getItem(i).get(DataComponents.POTION_CONTENTS);
                    if (c != null && !c.is(net.minecraft.world.item.alchemy.Potions.WATER)
                            || v.getInventory().getItem(i).is(Items.GLASS_BOTTLE)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                var inHand = v.getMainHandItem().get(DataComponents.POTION_CONTENTS);
                if (inHand != null && !inHand.is(net.minecraft.world.item.alchemy.Potions.WATER)) {
                    v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                // Empty bottles it put into the stand are tidied away too: they can never brew.
                for (int i = 0; i < 3; i++) {
                    if (brewing.getItem(i).is(Items.GLASS_BOTTLE)) {
                        brewing.setItem(i, ItemStack.EMPTY);
                    }
                }
                // The stand is kept fuelled, and a villager out of bottles or wart is handed more.
                if (brewing.getItem(4).isEmpty()) {
                    brewing.setItem(4, new ItemStack(Items.BLAZE_POWDER, 64));
                }
                if (carried(v, Items.POTION) + carried(v, Items.NETHER_WART) < 2) {
                    v.getInventory().clearContent();
                    v.setItemSlot(EquipmentSlot.MAINHAND, net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                            net.minecraft.world.item.alchemy.Potions.WATER));
                    handBacks[0]++;
                }
                if (carried(v, Items.NETHER_WART) == 0) {
                    v.getInventory().addItem(new ItemStack(Items.NETHER_WART, 16));
                }
                if (carried(v, Items.POTION) < 3) {
                    v.getInventory().addItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
                            net.minecraft.world.item.alchemy.Potions.WATER));
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("BREW newborn {}: by tick {}: aimed at the stand {} ticks, pressing use {}; opened it {} times, open {} ticks;"
                                + " stand holds {} | {} | {} , wart {}, fuel {}; brews done {}, made {}; screens opened in all {}, moved things {} times;"
                                + " handed back {} times; carries {} water bottles, {} wart, {} glass bottles;"
                                + " on the stand's pointer a bottle {} ticks, wart {}; left button held on it {} ticks,"
                                + " over a slot {}, over a thing {}, mean pointer {},{}; clicks in all {}", which, tick[0], aimedAtStand[0], usedOnStand[0], standScreens[0], standTicks[0],
                        brewing.getItem(0), brewing.getItem(1), brewing.getItem(2), brewing.getItem(3), brewing.getItem(4), brews[0],
                        handle.hands().madeTotal, handle.hands().screensOpened, handle.hands().moves, handBacks[0],
                        carried(v, Items.POTION), carried(v, Items.NETHER_WART), carried(v, Items.GLASS_BOTTLE),
                        carryingBottle[0], carryingWart[0], buttonHeld[0], overSlotOnStand[0], overThingOnStand[0],
                        Math.round(pointerOnStand[0] / Math.max(1, standTicks[0])), Math.round(pointerOnStand[1] / Math.max(1, standTicks[0])),
                        handle.hands().screenClicks);
            }
            if (tick[0] == BREW_TICKS) {
                String report = String.format("BREW: made %d things in %d ticks; opened the stand %d times, brews done %d",
                        handle.hands().madeTotal, BREW_TICKS, standScreens[0], brews[0]);
                LOG.info("BREW newborn {} learning result: {}", which, report);
                if (handle.hands().madeTotal >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornSetsAMachineGoing(GameTestHelper h) {
        spinBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornSetsAMachineGoing(GameTestHelper h) {
        spinBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornSetsAMachineGoing(GameTestHelper h) {
        spinBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornSetsAMachineGoing(GameTestHelper h) {
        spinBenchmark(h, "4");
    }

    /**
     * With Create installed: a turning motor in the pen and a newborn carrying shafts. A shaft
     * placed on the motor's turning end, or on the end of a turning shaft, turns too, and that is
     * felt as setting a machine going; a shaft put anywhere else stays still. Learning shows as
     * more shafts set going, and a larger share of the shafts placed. The shafts are taken away
     * now and then so there is always somewhere to start again.
     */
    private static void spinBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "SPIN")) {
            return;
        }
        var motor = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        var shaft = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (motor.isEmpty() || shaft.isEmpty()) {
            LOG.info("SPIN: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        BlockPos at = new BlockPos(5, 2, 9);
        var motorState = motor.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.SOUTH);
        Villager v = pen(h, new ItemStack(shaft.get(), 64), TICKS);
        h.setBlock(at, motorState);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] goingPerWindow = new int[TICKS / WINDOW];
        int[] placedPerWindow = new int[TICKS / WINDOW];
        int[] start = {0, 0};
        int[] aimedAtMotor = {0};
        int[] refusedBefore = {0};
        java.util.Map<String, Integer> refusedOn = new java.util.TreeMap<>();
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (!h.getBlockState(at).is(motor.get())) {
                h.setBlock(at, motorState);
            }
            if (handle.hands().target() instanceof BlockHitResult hit && hit.getBlockPos().equals(h.absolutePos(at))) {
                aimedAtMotor[0]++;
            }
            if (handle.hands().placeRefused > refusedBefore[0]) {
                String on = handle.hands().target() instanceof BlockHitResult hit
                        ? h.getLevel().getBlockState(hit.getBlockPos()).getBlock().getDescriptionId() + " " + hit.getDirection()
                        : String.valueOf(handle.hands().target().getType());
                refusedOn.merge(on, 1, Integer::sum);
            }
            refusedBefore[0] = handle.hands().placeRefused;
            if (tick[0] % (REGROW * 6) == 0 && !handle.hands().screenOpen()) {
                clearPen(h, at);
                regrow(h, v);
                if (carried(v, shaft.get()) < 16) {
                    v.getInventory().clearContent();
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(shaft.get(), 64));
                }
            }
            if (tick[0] % WINDOW == 0) {
                int w = tick[0] / WINDOW - 1;
                goingPerWindow[w] = handle.hands().setGoingTotal - start[0];
                placedPerWindow[w] = handle.hands().placedTotal - start[1];
                start[0] = handle.hands().setGoingTotal;
                start[1] = handle.hands().placedTotal;
                LOG.info("SPIN newborn {}: ticks {}-{}: {} shafts set going of {} placed; aimed at the motor {} ticks in all;"
                                + " screens opened {}; mean pitch {}; carries {} shafts, in hand {}; place tries {}, refused {}; at {}",
                        which, tick[0] - WINDOW, tick[0], goingPerWindow[w],
                        placedPerWindow[w], aimedAtMotor[0], handle.hands().screensOpened, Math.round(v.getXRot()),
                        carried(v, shaft.get()), v.getMainHandItem(), handle.hands().placeTries, handle.hands().placeRefused,
                        h.relativePos(v.blockPosition()));
                LOG.info("SPIN newborn {}: refused on {}", which, refusedOn);
            }
            if (tick[0] == TICKS) {
                // Set going is rare, so the first and last two windows are compared.
                int n = goingPerWindow.length;
                int first = goingPerWindow[0] + goingPerWindow[1];
                int last = goingPerWindow[n - 2] + goingPerWindow[n - 1];
                String report = String.format("SPIN: %d set going of %d placed in the first %d ticks, %d of %d in the last; %d in all",
                        first, placedPerWindow[0] + placedPerWindow[1], 2 * WINDOW, last,
                        placedPerWindow[n - 2] + placedPerWindow[n - 1], handle.hands().setGoingTotal);
                LOG.info("SPIN newborn {} learning result: {}", which, report);
                if (handle.hands().setGoingTotal >= 5 && last > first) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornRunsAPress(GameTestHelper h) {
        pressBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornRunsAPress(GameTestHelper h) {
        pressBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornRunsAPress(GameTestHelper h) {
        pressBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornRunsAPress(GameTestHelper h) {
        pressBenchmark(h, "4");
    }

    /**
     * With Create installed: a turning press over a depot, and a newborn carrying iron. Iron put
     * on the depot (the use key on it) is pressed into sheets, and taking the sheets back with an
     * empty hand is felt as making something. Taking back unpressed iron is not. The sheets it
     * carries are taken away now and then, and it is given more iron when it has none left.
     */
    private static void pressBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "PRESS")) {
            return;
        }
        java.util.function.Function<String, ResourceLocation> create = id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var depot = BuiltInRegistries.BLOCK.getOptional(create.apply("depot"));
        var sheet = BuiltInRegistries.ITEM.getOptional(create.apply("iron_sheet"));
        if (motor.isEmpty() || press.isEmpty() || depot.isEmpty() || sheet.isEmpty()) {
            LOG.info("PRESS: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        BlockPos onto = new BlockPos(5, 2, 9);
        BlockPos pressAt = onto.above(2);
        BlockPos motorAt = pressAt.east();
        var pressState = press.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST);
        var motorState = motor.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST);
        Villager v = pen(h, new ItemStack(Items.IRON_INGOT, 16), TICKS);
        h.setBlock(onto, depot.get());
        h.setBlock(pressAt, pressState);
        h.setBlock(motorAt, motorState);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] madePerWindow = new int[TICKS / WINDOW];
        int[] start = {0};
        int[] aimedAtDepot = {0};
        int[] usedOnDepot = {0};
        int[] handBacks = {0};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (!h.getBlockState(onto).is(depot.get())) {
                h.setBlock(onto, depot.get());
            }
            if (!h.getBlockState(pressAt).is(press.get())) {
                h.setBlock(pressAt, pressState);
            }
            if (!h.getBlockState(motorAt).is(motor.get())) {
                h.setBlock(motorAt, motorState);
            }
            if (handle.hands().target() instanceof BlockHitResult hit && hit.getBlockPos().equals(h.absolutePos(onto))) {
                aimedAtDepot[0]++;
                usedOnDepot[0] += handle.command().use() ? 1 : 0;
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                clearPen(h, onto, pressAt, motorAt);
                regrow(h, v);
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    if (v.getInventory().getItem(i).is(sheet.get())) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                if (v.getMainHandItem().is(sheet.get())) {
                    v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
                boolean onDepot = false;
                var be = h.getBlockEntity(onto);
                if (be != null) {
                    var items = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            h.absolutePos(onto), null);
                    if (items != null) {
                        for (int i = 0; i < items.getSlots(); i++) {
                            onDepot |= !items.getStackInSlot(i).isEmpty();
                        }
                    }
                }
                if (carried(v, Items.IRON_INGOT) == 0 && !onDepot) {
                    v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
                    handBacks[0]++;
                }
            }
            if (tick[0] % WINDOW == 0) {
                int w = tick[0] / WINDOW - 1;
                madePerWindow[w] = handle.hands().madeTotal - start[0];
                start[0] = handle.hands().madeTotal;
                LOG.info("PRESS newborn {}: ticks {}-{}: took sheets {} times; aimed at the depot {} ticks, pressing use {}; iron handed back {} times;"
                                + " carries {} iron, in hand {}; screens opened {}, thrown {}", which, tick[0] - WINDOW, tick[0], madePerWindow[w],
                        aimedAtDepot[0], usedOnDepot[0], handBacks[0], carried(v, Items.IRON_INGOT), v.getMainHandItem(),
                        handle.hands().screensOpened, handle.hands().throwsTotal);
            }
            if (tick[0] == TICKS) {
                int n = madePerWindow.length;
                int first = madePerWindow[0] + madePerWindow[1];
                int last = madePerWindow[n - 2] + madePerWindow[n - 1];
                String report = String.format("PRESS: took sheets %d times in the first %d ticks, %d in the last; %d in all; iron handed back %d times",
                        first, 2 * WINDOW, last, handle.hands().madeTotal, handBacks[0]);
                LOG.info("PRESS newborn {} learning result: {}", which, report);
                if (handle.hands().madeTotal >= 5 && last > first) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornPowersAndRunsAPress(GameTestHelper h) {
        chainBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornPowersAndRunsAPress(GameTestHelper h) {
        chainBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornPowersAndRunsAPress(GameTestHelper h) {
        chainBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornPowersAndRunsAPress(GameTestHelper h) {
        chainBenchmark(h, "4");
    }

    /**
     * With Create installed: the PRESS pen, but with a one-block gap between the motor and the
     * press, and a newborn carrying shafts as well as iron. The press only works once a shaft is
     * put in the gap, so making sheets takes building the drive first. The gap is opened again now
     * and then.
     */
    private static void chainBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "CHAIN")) {
            return;
        }
        java.util.function.Function<String, ResourceLocation> create = id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var depot = BuiltInRegistries.BLOCK.getOptional(create.apply("depot"));
        var shaft = BuiltInRegistries.ITEM.getOptional(create.apply("shaft"));
        if (motor.isEmpty() || press.isEmpty() || depot.isEmpty() || shaft.isEmpty()) {
            LOG.info("CHAIN: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        BlockPos onto = new BlockPos(5, 2, 9);
        BlockPos pressAt = onto.above(2);
        BlockPos gap = pressAt.east();
        BlockPos motorAt = gap.east();
        var pressState = press.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST);
        var motorState = motor.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST);
        Villager v = pen(h, new ItemStack(shaft.get(), 16), TICKS);
        v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
        h.setBlock(onto, depot.get());
        h.setBlock(pressAt, pressState);
        h.setBlock(motorAt, motorState);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] madePerWindow = new int[TICKS / WINDOW];
        int[] start = {0};
        int[] powered = {0};
        int[] pressTurning = {0};
        boolean[] wasTurning = {false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (!h.getBlockState(onto).is(depot.get())) {
                h.setBlock(onto, depot.get());
            }
            if (!h.getBlockState(pressAt).is(press.get())) {
                h.setBlock(pressAt, pressState);
            }
            if (!h.getBlockState(motorAt).is(motor.get())) {
                h.setBlock(motorAt, motorState);
            }
            boolean turning = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(pressAt));
            powered[0] += turning && !wasTurning[0] ? 1 : 0;
            pressTurning[0] += turning ? 1 : 0;
            wasTurning[0] = turning;
            if (tick[0] % (REGROW * 12) == 0 && !handle.hands().screenOpen()) {
                // The drive comes apart again, so there is always building to do.
                h.setBlock(gap, Blocks.AIR);
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                clearPen(h, onto, pressAt, motorAt, gap);
                regrow(h, v);
                if (carried(v, shaft.get()) < 4) {
                    v.getInventory().addItem(new ItemStack(shaft.get(), 16));
                }
                if (carried(v, Items.IRON_INGOT) == 0) {
                    v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    if (!v.getInventory().getItem(i).is(shaft.get()) && !v.getInventory().getItem(i).is(Items.IRON_INGOT)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                int w = tick[0] / WINDOW - 1;
                madePerWindow[w] = handle.hands().madeTotal - start[0];
                start[0] = handle.hands().madeTotal;
                LOG.info("CHAIN newborn {}: ticks {}-{}: took sheets {} times; press powered {} times, turning {} ticks; set going {}, placed {};"
                                + " in hand {}; wheel turned {} times", which, tick[0] - WINDOW, tick[0], madePerWindow[w], powered[0], pressTurning[0],
                        handle.hands().setGoingTotal, handle.hands().placedTotal, v.getMainHandItem(), handle.hands().wheelTurns);
            }
            if (tick[0] == TICKS) {
                String report = String.format("CHAIN: took sheets %d times, powered the press %d times", handle.hands().madeTotal, powered[0]);
                LOG.info("CHAIN newborn {} learning result: {}", which, report);
                if (handle.hands().madeTotal >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
}
