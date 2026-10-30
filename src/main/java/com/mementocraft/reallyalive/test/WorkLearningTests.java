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

import java.util.List;

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
    /** The longest a crafting benchmark may be asked to run with -PcraftTicks, for a lifetime's learning curve. */
    private static final int CRAFT_TICKS_MAX = 1_000_000;
    /** Tilled ground goes back to dirt this often, so there is always something to till. */
    private static final int REGROW = 200;
    private static final int LITTER = 32;
    /** How often dropped things are tidied: often enough that a flood of them cannot build up between sweeps. */
    private static final int TIDY_EVERY = 20;
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
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

    /**
     * Keeps a pen from filling with dropped things: past {@code LITTER} items lying about, the
     * oldest are cleared away, as they would be on a long day in a real village. Without this a
     * newborn that throws what it is given again and again slows the whole server to a crawl.
     */
    private static void tidyLitter(GameTestHelper h) {
        var litter = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(-3, -2, 4)).getCenter(), h.absolutePos(new BlockPos(13, 9, 20)).getCenter()));
        if (litter.size() > 10 * LITTER) {
            // Far more than a villager could throw: something is making items. Say what and where.
            var first = litter.get(0);
            LOG.warn("{} items lying about the pen, such as {} at {}", litter.size(), first.getItem(), h.relativePos(first.blockPosition()));
        }
        if (litter.size() > LITTER) {
            litter.sort(java.util.Comparator.comparingInt(net.minecraft.world.entity.item.ItemEntity::getAge).reversed());
            litter.subList(0, litter.size() - LITTER).forEach(net.minecraft.world.entity.Entity::discard);
        }
    }

    /** Takes away whatever was put on the pen's floor, except the blocks to keep. */
    private static void clearPen(GameTestHelper h, BlockPos... keep) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 8; z <= 16; z++) {
                for (int y = 2; y <= 4; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!h.getBlockState(p).isAir() && h.getBlockState(p).getFluidState().isEmpty() && !java.util.Arrays.asList(keep).contains(p)) {
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
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

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsPlanksOnItsOwn(GameTestHelper h) {
        craftBenchmark(h, "1");
    }

    /** Two more newborns, since whether one stumbles on crafting varies a lot from brain to brain. */
    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsPlanksOnItsOwn(GameTestHelper h) {
        craftBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsPlanksOnItsOwn(GameTestHelper h) {
        craftBenchmark(h, "3");
    }

    /**
     * With Create installed: andesite alloy instead of logs. Two alloys one above the other in
     * the grid make eight shafts, the first part of any machine.
     */
    private static void shaftBenchmark(GameTestHelper h, String which) {
        var alloy = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "andesite_alloy"));
        var shaft = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (alloy.isEmpty() || shaft.isEmpty()) {
            if (selected(h, "SHAFT")) {
                LOG.info("SHAFT: Create is not installed, nothing to learn");
                h.succeed();
            }
            return;
        }
        craftBenchmark(h, which, "SHAFT", alloy.get(), shaft.get());
    }

    /** SHAFT with a book of how shafts are made among its things: does looking at it help? */
    private static void shaftBookBenchmark(GameTestHelper h, String which) {
        var alloy = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "andesite_alloy"));
        var shaft = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (alloy.isEmpty() || shaft.isEmpty()) {
            if (selected(h, "SHAFTBOOK")) {
                LOG.info("SHAFTBOOK: Create is not installed, nothing to learn");
                h.succeed();
            }
            return;
        }
        ItemStack book = com.mementocraft.reallyalive.body.RecipeBook.write(h.getLevel(), "Shafts", List.of(shaft.get()));
        craftBenchmark(h, which, "SHAFTBOOK", alloy.get(), shaft.get(), book);
    }

    /** SHAFT handed two alloys at a time, exactly what one craft takes: a smaller first step. */
    private static void twoAlloysBenchmark(GameTestHelper h, String which) {
        var alloy = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "andesite_alloy"));
        var shaft = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (alloy.isEmpty() || shaft.isEmpty()) {
            if (selected(h, "SHAFT2")) {
                LOG.info("SHAFT2: Create is not installed, nothing to learn");
                h.succeed();
            }
            return;
        }
        craftBenchmark(h, which, "SHAFT2", alloy.get(), shaft.get(), null, 2);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsShaftsFromTwoAlloys(GameTestHelper h) {
        twoAlloysBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsShaftsFromTwoAlloys(GameTestHelper h) {
        twoAlloysBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsShaftsFromTwoAlloys(GameTestHelper h) {
        twoAlloysBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsShaftsWithABook(GameTestHelper h) {
        shaftBookBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsShaftsWithABook(GameTestHelper h) {
        shaftBookBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsShaftsWithABook(GameTestHelper h) {
        shaftBookBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsShaftsOnItsOwn(GameTestHelper h) {
        shaftBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsShaftsOnItsOwn(GameTestHelper h) {
        shaftBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsShaftsOnItsOwn(GameTestHelper h) {
        shaftBenchmark(h, "3");
    }

    private static void craftBenchmark(GameTestHelper h, String which) {
        craftBenchmark(h, which, "CRAFT", Items.OAK_LOG, Items.OAK_PLANKS);
    }

    /**
     * A newborn carrying 64 of a thing that crafts into another in its own 2x2 grid. Taking what
     * it crafted out of the result slot is felt as making something.
     */
    private static void craftBenchmark(GameTestHelper h, String which, String name,
                                       net.minecraft.world.item.Item from, net.minecraft.world.item.Item into) {
        craftBenchmark(h, which, name, from, into, null);
    }

    /** The same, with a recipe book among its things, kept there if it loses it. */
    private static void craftBenchmark(GameTestHelper h, String which, String name,
                                       net.minecraft.world.item.Item from, net.minecraft.world.item.Item into, ItemStack book) {
        craftBenchmark(h, which, name, from, into, book, 64);
    }

    /** The same, handed only {@code count} at a time: exactly what one recipe takes, as a first lesson. */
    private static void craftBenchmark(GameTestHelper h, String which, String name, net.minecraft.world.item.Item from,
                                       net.minecraft.world.item.Item into, ItemStack book, int count) {
        if (!selected(h, name)) {
            return;
        }
        int ticks = Math.min(CRAFT_TICKS_MAX, Integer.getInteger("reallyalive.craftTicks", CRAFT_TICKS));
        Villager v = pen(h, new ItemStack(from, count), ticks);
        if (book != null) {
            v.getInventory().addItem(book.copy());
        }
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                // Logs placed about the pen are cleared away; one that has no logs left is handed
                // more, as a parent would, so the benchmark keeps measuring crafting, not building.
                clearPen(h);
                regrow(h, v);
                if (book != null && carried(v, book.getItem()) == 0) {
                    v.getInventory().addItem(book.copy());
                }
                if (carried(v, from) == 0) {
                    v.getInventory().removeItemType(from, 64);
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(from, count));
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
                        LOG.info(name + " newborn {} made something, hand free: pointer {},{}; command yaw {} pitch {}; eyes{}", which,
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
                LOG.info(name + " newborn " + which + ": by tick {}: opened {} screens, clicked {} slots ({} holding something), saw {} results form, made {};"
                                + " in screens {} ticks, left button {}, inventory key {}, over a slot {}, over a thing {}, mean pointer {},{}; carries {} logs, {} planks;"
                                + " logs handed back {} times; a stack on the pointer {} ticks, over the grid {}, over the result {};"
                                + " a result showing {} ticks, with the pointer free {}, over it {};"
                                + " outside screens the inventory key down {} ticks, pressed {} times, curious {} ticks; moved things {} times, thrown {}; placed {} blocks; recipes learnt {}, places matched {}, wheel turned {}; shared out {}, dealt one {}; brain {} neurons, {} synapses", tick[0],
                        handle.hands().screensOpened, handle.hands().screenClicks, handle.hands().clicksOnThings, handle.hands().formedTotal, handle.hands().madeTotal,
                        screenTicks[0], attackTicks[0], keyTicks[0], overSlot[0], overThing[0],
                        Math.round(pointerSum[0] / Math.max(1, screenTicks[0])), Math.round(pointerSum[1] / Math.max(1, screenTicks[0])), carried(v, from), carried(v, into),
                        handBacks[0], pointerHolding[0], holdingOverGrid[0], holdingOverResult[0],
                        resultTicks[0], resultFree[0], resultOver[0], keyDownOutside[0], keyPressesOutside[0], curiousTicks[0], handle.hands().moves,
                        handle.hands().throwsTotal, handle.hands().placedTotal, handle.hands().recipesLearnt, handle.hands().matchedTotal, handle.hands().wheelTurns, handle.hands().sharedOutTotal, handle.hands().dealtOneTotal,
                        handle.brain().neuronCount(), handle.brain().synapseCount());
            }
            if (tick[0] == ticks) {
                String report = String.format(name + ": made %d things in %d ticks; opened its inventory %d times, open %d%% of the time,"
                                + " with a stack on the pointer %d%% of that",
                        handle.hands().madeTotal, ticks, handle.hands().screensOpened, 100 * screenTicks[0] / ticks,
                        screenTicks[0] == 0 ? 0 : 100 * pointerHolding[0] / screenTicks[0]);
                LOG.info(name + " newborn {} learning result: {}", which, report);
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
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
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
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
    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornCranksAPress(GameTestHelper h) {
        crankBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornCranksAPress(GameTestHelper h) {
        crankBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornCranksAPress(GameTestHelper h) {
        crankBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornCranksAPress(GameTestHelper h) {
        crankBenchmark(h, "4");
    }

    /**
     * With Create installed: the PRESS pen with no motor, and a newborn carrying a hand crank as
     * well as iron. The press runs only while a crank set on its side is turned (use held on it),
     * so making sheets takes building its own power source and working it. The crank is taken
     * back now and then and handed over again, so it can be put somewhere better.
     */
    private static void crankBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "CRANK")) {
            return;
        }
        java.util.function.Function<String, ResourceLocation> create = id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var depot = BuiltInRegistries.BLOCK.getOptional(create.apply("depot"));
        var crank = BuiltInRegistries.BLOCK.getOptional(create.apply("hand_crank"));
        if (press.isEmpty() || depot.isEmpty() || crank.isEmpty()) {
            LOG.info("CRANK: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        // The depot is set in the floor, so the press's sides, where a crank goes, are at eye height.
        BlockPos onto = new BlockPos(5, 1, 9);
        BlockPos pressAt = onto.above(2);
        var pressState = press.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST);
        net.minecraft.world.item.Item crankItem = crank.get().asItem();
        Villager v = pen(h, new ItemStack(crankItem), TICKS);
        v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
        h.setBlock(onto, depot.get());
        h.setBlock(pressAt, pressState);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] powered = {0};
        int[] pressTurning = {0};
        int[] cranksPlaced = {0};
        boolean[] wasTurning = {false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            if (!h.getBlockState(onto).is(depot.get())) {
                h.setBlock(onto, depot.get());
            }
            if (!h.getBlockState(pressAt).is(press.get())) {
                h.setBlock(pressAt, pressState);
            }
            boolean turning = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(pressAt));
            powered[0] += turning && !wasTurning[0] ? 1 : 0;
            pressTurning[0] += turning ? 1 : 0;
            wasTurning[0] = turning;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                boolean crankInPen = false;
                for (int x = 1; x <= 9; x++) {
                    for (int z = 8; z <= 16; z++) {
                        for (int y = 2; y <= 4; y++) {
                            crankInPen |= h.getBlockState(new BlockPos(x, y, z)).is(crank.get());
                        }
                    }
                }
                if (tick[0] % (REGROW * 12) == 0 || !crankInPen && carried(v, crankItem) == 0) {
                    // The crank comes back to its hands, to put somewhere (maybe better) again.
                    clearPen(h, onto, pressAt);
                    regrow(h, v);
                    if (carried(v, crankItem) == 0) {
                        v.getInventory().addItem(new ItemStack(crankItem));
                        cranksPlaced[0]++;
                    }
                }
                if (carried(v, Items.IRON_INGOT) == 0) {
                    v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    ItemStack s = v.getInventory().getItem(i);
                    if (!s.is(crankItem) && !s.is(Items.IRON_INGOT)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("CRANK newborn {}: by tick {}: press powered {} times, turning {} ticks; took sheets {} times; set going {}, placed {};"
                                + " cranks handed back {}; in hand {}; wheel turned {} times", which, tick[0], powered[0], pressTurning[0],
                        handle.hands().madeTotal, handle.hands().setGoingTotal, handle.hands().placedTotal, cranksPlaced[0],
                        v.getMainHandItem(), handle.hands().wheelTurns);
            }
            if (tick[0] == TICKS) {
                String report = String.format("CRANK: powered the press %d times (%d ticks), took sheets %d times",
                        powered[0], pressTurning[0], handle.hands().madeTotal);
                LOG.info("CRANK newborn {} learning result: {}", which, report);
                if (powered[0] >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornTurnsAPressWithAWaterWheel(GameTestHelper h) {
        wheelBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornTurnsAPressWithAWaterWheel(GameTestHelper h) {
        wheelBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornTurnsAPressWithAWaterWheel(GameTestHelper h) {
        wheelBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornTurnsAPressWithAWaterWheel(GameTestHelper h) {
        wheelBenchmark(h, "4");
    }

    /**
     * With Create installed: the CRANK pen with streams running past both sides of the press, and
     * a newborn carrying a water wheel as well as iron. A wheel set in the stream against the
     * press's side turns it for good, with no hand on it; anywhere else it does nothing. The
     * wheel is taken back now and then and handed over again, so it can be put somewhere better.
     */
    private static void wheelBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "WHEEL")) {
            return;
        }
        java.util.function.Function<String, ResourceLocation> create = id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var depot = BuiltInRegistries.BLOCK.getOptional(create.apply("depot"));
        var crank = BuiltInRegistries.BLOCK.getOptional(create.apply("water_wheel"));
        if (press.isEmpty() || depot.isEmpty() || crank.isEmpty()) {
            LOG.info("WHEEL: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        // The depot is set in the floor, so the press's sides, where a crank goes, are at eye height.
        BlockPos onto = new BlockPos(5, 1, 9);
        BlockPos pressAt = onto.above(2);
        var pressState = press.get().defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH).rotate(h.getTestRotation());
        net.minecraft.world.item.Item crankItem = crank.get().asItem();
        Villager v = pen(h, new ItemStack(crankItem), TICKS);
        v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
        h.setBlock(onto, depot.get());
        h.setBlock(pressAt, pressState);
        // Two streams, one each side of the press where its shaft comes out: a spring in a glass
        // trough, running out over the place by the press where a wheel would catch it. The
        // press faces south, so its shaft comes out at the face newborns come up to and set their
        // wheels on (with streams east and west only, they set them south of it, dry).
        java.util.List<BlockPos> keepList = new java.util.ArrayList<>(java.util.List.of(onto, pressAt));
        java.util.List<BlockPos> drains = new java.util.ArrayList<>();
        java.util.List<BlockPos> springs = new java.util.ArrayList<>();
        for (net.minecraft.core.Direction side : new net.minecraft.core.Direction[] {net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.NORTH}) {
            BlockPos spring = pressAt.relative(side).above().relative(side.getCounterClockWise());
            var flow = side.getClockWise();
            BlockPos[] trough = {spring.relative(side), spring.relative(side.getOpposite()), spring.below(),
                    spring.relative(flow).relative(side), spring.relative(flow).relative(side.getOpposite())};
            for (BlockPos g : trough) {
                h.setBlock(g, Blocks.GLASS);
                keepList.add(g);
            }
            h.setBlock(spring, Blocks.WATER);
            keepList.add(spring);
            springs.add(spring);
            // A drain under the stream: water running out over the floor carried newborns off to
            // the far corner, where they set their wheels down, nowhere near the press.
            BlockPos drain = pressAt.relative(side).below(2);
            h.setBlock(drain, Blocks.AIR);
            drains.add(drain);
        }
        BlockPos[] keep = keepList.toArray(BlockPos[]::new);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] powered = {0};
        int[] pressTurning = {0};
        int[] cranksPlaced = {0};
        boolean[] wasTurning = {false};
        BlockPos[] lastWheel = {null};
        int[] rescued = {0};
        int[] wheelsSeen = {0};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            if (h.relativePos(v.blockPosition()).getY() <= 1 && drains.contains(h.relativePos(v.blockPosition()))) {
                // Fallen into a drain, under the falling water: a newborn cannot climb out and
                // drowned there once. It is lifted out, as a parent would.
                Vec3 out = h.absoluteVec(new Vec3(7.5, 2, 12.5));
                v.teleportTo(out.x, out.y, out.z);
                rescued[0]++;
            }
            if (!h.getBlockState(onto).is(depot.get())) {
                h.setBlock(onto, depot.get());
            }
            if (!h.getBlockState(pressAt).is(press.get())) {
                h.setBlock(pressAt, pressState);
            }
            boolean turning = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(pressAt));
            powered[0] += turning && !wasTurning[0] ? 1 : 0;
            pressTurning[0] += turning ? 1 : 0;
            wasTurning[0] = turning;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                boolean crankInPen = false;
                for (int x = 1; x <= 9; x++) {
                    for (int z = 8; z <= 16; z++) {
                        for (int y = 2; y <= 4; y++) {
                            BlockPos p = new BlockPos(x, y, z);
                            if (h.getBlockState(p).is(crank.get())) {
                                crankInPen = true;
                                if (!p.equals(lastWheel[0]) && wheelsSeen[0]++ < 40) {
                                    // Where the wheels go: what keeps them from the press's side.
                                    LOG.info("WHEEL newborn {}: wheel at {} {}, turning {}, by the press {}", which, p, h.getBlockState(p),
                                            com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(p)),
                                            p.equals(pressAt.south()) || p.equals(pressAt.north()));
                                }
                                lastWheel[0] = p;
                            }
                        }
                    }
                }
                // A wheel set down where it turns nothing comes back sooner, for another try.
                if (tick[0] % (REGROW * 12) == 0 || tick[0] % (REGROW * 3) == 0 && !turning || !crankInPen && carried(v, crankItem) == 0) {
                    // The wheel comes back to its hands, to put somewhere (maybe better) again.
                    clearPen(h, keep);
                    regrow(h, v);
                    drains.forEach(d -> h.setBlock(d, Blocks.AIR));
                    // A wheel set into a spring took its place: the spring comes back.
                    springs.forEach(sp -> h.setBlock(sp, Blocks.WATER));
                    if (carried(v, crankItem) == 0) {
                        v.getInventory().addItem(new ItemStack(crankItem));
                        cranksPlaced[0]++;
                    }
                }
                if (carried(v, Items.IRON_INGOT) == 0) {
                    v.getInventory().addItem(new ItemStack(Items.IRON_INGOT, 16));
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    ItemStack s = v.getInventory().getItem(i);
                    if (!s.is(crankItem) && !s.is(Items.IRON_INGOT)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("WHEEL newborn {}: by tick {}: press powered {} times, turning {} ticks; took sheets {} times; set going {}, placed {};"
                                + " wheels handed back {}; in hand {}; wheel turned {} times; lifted out of a drain {} times", which, tick[0], powered[0], pressTurning[0],
                        handle.hands().madeTotal, handle.hands().setGoingTotal, handle.hands().placedTotal, cranksPlaced[0],
                        v.getMainHandItem(), handle.hands().wheelTurns, rescued[0]);
            }
            if (tick[0] == TICKS) {
                String report = String.format("WHEEL: powered the press %d times (%d ticks), took sheets %d times",
                        powered[0], pressTurning[0], handle.hands().madeTotal);
                LOG.info("WHEEL newborn {} learning result: {}", which, report);
                if (powered[0] >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornRunsASteamEngine(GameTestHelper h) {
        steamBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornRunsASteamEngine(GameTestHelper h) {
        steamBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornRunsASteamEngine(GameTestHelper h) {
        steamBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornRunsASteamEngine(GameTestHelper h) {
        steamBenchmark(h, "4");
    }

    /**
     * With Create installed: a newborn in the pen with a boiler already plumbed in (a two by two
     * fluid tank against the north wall, a blaze burner under one corner, water pumped in from a
     * pool outside), carrying ready-made parts: coal, a steam engine and shafts. The engine runs
     * only when all three are done: coal in the burner, the engine on the tank's side and a shaft
     * in front of the engine. Each step is counted on its own, so the report says how far it got.
     * The engine and shafts are taken back now and then and handed over again.
     */
    private static void steamBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "STEAM")) {
            return;
        }
        java.util.function.Function<String, ResourceLocation> create = id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var tank = BuiltInRegistries.BLOCK.getOptional(create.apply("fluid_tank"));
        var burner = BuiltInRegistries.BLOCK.getOptional(create.apply("blaze_burner"));
        var pump = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_pump"));
        var pipe = BuiltInRegistries.BLOCK.getOptional(create.apply("fluid_pipe"));
        var cog = BuiltInRegistries.BLOCK.getOptional(create.apply("cogwheel"));
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var engine = BuiltInRegistries.BLOCK.getOptional(create.apply("steam_engine"));
        var poweredShaft = BuiltInRegistries.BLOCK.getOptional(create.apply("powered_shaft"));
        var shaft = BuiltInRegistries.ITEM.getOptional(create.apply("shaft"));
        if (tank.isEmpty() || burner.isEmpty() || pump.isEmpty() || pipe.isEmpty() || cog.isEmpty() || motor.isEmpty() || engine.isEmpty()
                || poweredShaft.isEmpty() || shaft.isEmpty()) {
            LOG.info("STEAM: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        net.minecraft.world.item.Item engineItem = engine.get().asItem();
        net.minecraft.world.item.Item shaftItem = shaft.get();
        Villager v = pen(h, new ItemStack(Items.COAL, 16), TICKS);
        v.getInventory().addItem(new ItemStack(engineItem));
        v.getInventory().addItem(new ItemStack(shaftItem, 4));
        var rotation = h.getTestRotation();
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        // The boiler: a tank of four blocks (one heat level), a burner under its south-west corner
        // whose south side shows, stone under the rest.
        java.util.List<BlockPos> keep = new java.util.ArrayList<>();
        for (int x = 4; x <= 5; x++) {
            for (int z = 8; z <= 9; z++) {
                h.setBlock(new BlockPos(x, 3, z), tank.get());
                keep.add(new BlockPos(x, 3, z));
                keep.add(new BlockPos(x, 2, z));
            }
        }
        BlockPos burnerAt = new BlockPos(4, 2, 9);
        var unlit = withValue(burner.get().defaultBlockState(), "blaze", "smouldering").rotate(rotation);
        Runnable underTank = () -> {
            for (BlockPos p : keep) {
                if (p.getY() == 2 && !p.equals(burnerAt) && !h.getBlockState(p).is(Blocks.STONE)) {
                    h.setBlock(p, Blocks.STONE);
                }
            }
            if (!h.getBlockState(burnerAt).is(burner.get())) {
                h.setBlock(burnerAt, unlit);
            }
        };
        underTank.run();
        // The plumbing, set into and beyond the north wall: a pump pushing south into the tank,
        // turned through a cogwheel by a fast motor, drawing on a glass-walled pool of springs.
        BlockPos pumpAt = new BlockPos(5, 3, 7);
        h.setBlock(pumpAt, pump.get().defaultBlockState().setValue(facing, net.minecraft.core.Direction.SOUTH).rotate(rotation));
        BlockPos cogAt = pumpAt.east();
        h.setBlock(cogAt, cog.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z).rotate(rotation));
        BlockPos motorAt = cogAt.north();
        h.setBlock(motorAt, motor.get().defaultBlockState().setValue(facing, net.minecraft.core.Direction.SOUTH).rotate(rotation));
        for (int x = 3; x <= 7; x++) {
            for (int z = 2; z <= 6; z++) {
                BlockPos p = new BlockPos(x, 3, z);
                h.setBlock(p.below(), Blocks.STONE);
                if (!p.equals(motorAt)) {
                    h.setBlock(p, x == 3 || x == 7 || z == 2 || z == 6 ? Blocks.GLASS : Blocks.WATER);
                }
            }
        }
        BlockPos pipeAt = pumpAt.north();
        h.setBlock(pipeAt, pipe.get());
        BlockPos pipeAbs = h.absolutePos(pipeAt);
        h.getLevel().setBlockAndUpdate(pipeAbs, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(
                h.getLevel().getBlockState(pipeAbs), h.getLevel(), pipeAbs));
        try {
            Object motorBe = h.getLevel().getBlockEntity(h.absolutePos(motorAt));
            Object speed = motorBe.getClass().getField("generatedSpeed").get(motorBe);
            speed.getClass().getMethod("setValue", int.class).invoke(speed, 128);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        BlockPos[] kept = keep.toArray(BlockPos[]::new);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] ran = {0};
        int[] runTicks = {0};
        int[] litTicks = {0};
        int[] lit = {0};
        int[] engineTicks = {0};
        int[] shaftTicks = {0};
        int[] handedBack = {0};
        boolean[] was = {false, false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            boolean burning = !h.getBlockState(burnerAt).equals(unlit);
            lit[0] += burning && !was[1] ? 1 : 0;
            litTicks[0] += burning ? 1 : 0;
            was[1] = burning;
            boolean engineOn = false;
            boolean shaftIn = false;
            boolean running = false;
            for (int x = 1; x <= 9; x++) {
                for (int z = 8; z <= 16; z++) {
                    for (int y = 2; y <= 4; y++) {
                        var state = h.getBlockState(new BlockPos(x, y, z));
                        engineOn |= state.is(engine.get());
                        if (state.is(poweredShaft.get())) {
                            shaftIn = true;
                            running |= com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(new BlockPos(x, y, z)));
                        }
                    }
                }
            }
            engineTicks[0] += engineOn ? 1 : 0;
            shaftTicks[0] += shaftIn ? 1 : 0;
            ran[0] += running && !was[0] ? 1 : 0;
            runTicks[0] += running ? 1 : 0;
            was[0] = running;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                regrow(h, v);
                underTank.run();
                if (tick[0] % (REGROW * 12) == 0 || !engineOn && carried(v, engineItem) == 0) {
                    // The parts come back to its hands, to put together (maybe better) again.
                    clearPen(h, kept);
                    if (carried(v, engineItem) == 0) {
                        v.getInventory().addItem(new ItemStack(engineItem));
                        handedBack[0]++;
                    }
                }
                if (carried(v, shaftItem) == 0) {
                    v.getInventory().addItem(new ItemStack(shaftItem, 4));
                }
                if (carried(v, Items.COAL) == 0) {
                    v.getInventory().addItem(new ItemStack(Items.COAL, 16));
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    ItemStack s = v.getInventory().getItem(i);
                    if (!s.is(engineItem) && !s.is(shaftItem) && !s.is(Items.COAL)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("STEAM newborn {}: by tick {}: burner lit {} times ({} ticks), engine on the tank {} ticks, shaft in {} ticks,"
                                + " engine ran {} times ({} ticks); set going {}, placed {}; engines handed back {}; in hand {}",
                        which, tick[0], lit[0], litTicks[0], engineTicks[0], shaftTicks[0], ran[0], runTicks[0],
                        handle.hands().setGoingTotal, handle.hands().placedTotal, handedBack[0], v.getMainHandItem());
            }
            if (tick[0] == TICKS) {
                String report = String.format("STEAM: lit the burner %d times, engine on the tank %d ticks, shaft in %d ticks, ran the engine %d times (%d ticks)",
                        lit[0], engineTicks[0], shaftTicks[0], ran[0], runTicks[0]);
                LOG.info("STEAM newborn {} learning result: {}", which, report);
                if (ran[0] >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornRunsAWindmill(GameTestHelper h) {
        windmillBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornRunsAWindmill(GameTestHelper h) {
        windmillBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornRunsAWindmill(GameTestHelper h) {
        windmillBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornRunsAWindmill(GameTestHelper h) {
        windmillBenchmark(h, "4");
    }

    /**
     * With Create installed: a newborn in the pen with a windmill bearing against the north wall,
     * facing in, carrying sails. A windmill wants at least eight sails held together in one plane
     * before the bearing's face, and an empty hand on the bearing to start it; sails used on a
     * sail go beside it. Sails on the bearing and the windmill running are counted apart. Now and
     * then the windmill is stopped, the sails taken back and handed over again.
     */
    private static void windmillBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "WINDMILL")) {
            return;
        }
        var bearing = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "windmill_bearing"));
        var sail = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "white_sail"));
        if (bearing.isEmpty() || sail.isEmpty()) {
            LOG.info("WINDMILL: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        net.minecraft.world.item.Item sailItem = sail.get();
        Villager v = pen(h, new ItemStack(sailItem, 16), TICKS);
        // The roof one higher: a turning windmill's sails lifted newborns standing by it into the
        // roof, and they suffocated there.
        for (int x = 0; x <= 10; x++) {
            for (int z = 7; z <= 17; z++) {
                h.setBlock(x, 5, z, x == 0 || x == 10 || z == 7 || z == 17 ? Blocks.BEDROCK : Blocks.AIR);
                h.setBlock(x, 6, z, Blocks.BEDROCK);
            }
        }
        BlockPos bearingAt = new BlockPos(5, 3, 8);
        var bearingState = bearing.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.SOUTH)
                .rotate(h.getTestRotation());
        h.setBlock(bearingAt, bearingState);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] ran = {0};
        int[] runTicks = {0};
        int[] mostSails = {0};
        int[] sailTicks = {0};
        int[] handedBack = {0};
        boolean[] was = {false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                BlockPos eyes = BlockPos.containing(v.getEyePosition());
                h.fail("the villager died at tick " + tick[0] + " at " + h.relativeVec(v.position()) + ", eyes in "
                        + h.getLevel().getBlockState(eyes) + " (rotation " + h.getTestRotation() + ")");
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            if (!h.getBlockState(bearingAt).is(bearing.get())) {
                h.setBlock(bearingAt, bearingState);
            }
            boolean running = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(bearingAt));
            ran[0] += running && !was[0] ? 1 : 0;
            runTicks[0] += running ? 1 : 0;
            was[0] = running;
            if (tick[0] % 20 == 0 && !running) {
                // Sails in the plane before the bearing's face.
                int n = 0;
                for (int x = 1; x <= 9; x++) {
                    for (int y = 2; y <= 4; y++) {
                        n += h.getBlockState(new BlockPos(x, y, 9)).getBlock().asItem() == sailItem ? 1 : 0;
                    }
                }
                mostSails[0] = Math.max(mostSails[0], n);
                sailTicks[0] += h.getBlockState(bearingAt.south()).getBlock().asItem() == sailItem ? 20 : 0;
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                regrow(h, v);
                if (tick[0] % (REGROW * 12) == 0) {
                    // The windmill is stopped and the sails come back to its hands, to put up again.
                    if (running && h.getLevel().getBlockEntity(h.absolutePos(bearingAt)) instanceof Object be) {
                        try {
                            be.getClass().getMethod("disassemble").invoke(be);
                        } catch (ReflectiveOperationException e) {
                            throw new IllegalStateException(e);
                        }
                    }
                    clearPen(h, bearingAt);
                    handedBack[0]++;
                }
                int carried = carried(v, sailItem);
                if (carried < 16 && tick[0] % (REGROW * 12) == 0 || carried == 0 && !running) {
                    v.getInventory().addItem(new ItemStack(sailItem, 16 - carried));
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    if (!v.getInventory().getItem(i).is(sailItem)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("WINDMILL newborn {}: by tick {}: most sails up {}, a sail on the bearing {} ticks, windmill ran {} times ({} ticks);"
                                + " set going {}, stopped {}, placed {}; handed back {}; in hand {}", which, tick[0], mostSails[0], sailTicks[0], ran[0], runTicks[0],
                        handle.hands().setGoingTotal, handle.hands().stoppedTotal, handle.hands().placedTotal, handedBack[0], v.getMainHandItem());
            }
            if (tick[0] == TICKS) {
                String report = String.format("WINDMILL: most sails up %d, a sail on the bearing %d ticks, ran the windmill %d times (%d ticks)",
                        mostSails[0], sailTicks[0], ran[0], runTicks[0]);
                LOG.info("WINDMILL newborn {} learning result: {}", which, report);
                if (ran[0] >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornSetsUpADrill(GameTestHelper h) {
        drillBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornSetsUpADrill(GameTestHelper h) {
        drillBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornSetsUpADrill(GameTestHelper h) {
        drillBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornSetsUpADrill(GameTestHelper h) {
        drillBenchmark(h, "4");
    }

    /**
     * With Create installed: a first piece of automation. A motor in the pen's west wall turns a
     * shaft ending in the open, two blocks short of a stone that grows back as a cobblestone
     * generator's would. A newborn carries a mechanical drill: set down at the shaft's end it
     * turns and mines the stone again and again, with no hand on it. Stone mined by the drill and
     * stone dug by hand are counted apart. The drill is taken back now and then and handed over.
     */
    private static void drillBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "DRILL")) {
            return;
        }
        var motor = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        var shaft = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        var drill = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "mechanical_drill"));
        if (motor.isEmpty() || shaft.isEmpty() || drill.isEmpty()) {
            LOG.info("DRILL: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        net.minecraft.world.item.Item drillItem = drill.get().asItem();
        Villager v = pen(h, new ItemStack(drillItem), TICKS);
        var rotation = h.getTestRotation();
        BlockPos motorAt = new BlockPos(0, 2, 12);
        var motorState = motor.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST).rotate(rotation);
        var shaftState = shaft.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X).rotate(rotation);
        BlockPos shaftAt = motorAt.east();
        BlockPos drillAt = shaftAt.east();
        BlockPos rock = drillAt.east();
        h.setBlock(motorAt, motorState);
        h.setBlock(shaftAt, shaftState);
        h.setBlock(rock, Blocks.STONE);
        try {
            Object motorBe = h.getLevel().getBlockEntity(h.absolutePos(motorAt));
            Object speed = motorBe.getClass().getField("generatedSpeed").get(motorBe);
            speed.getClass().getMethod("setValue", int.class).invoke(speed, 64);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] byDrill = {0};
        int[] byHand = {0};
        int[] drillTicks = {0};
        int[] handedBack = {0};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            if (!h.getBlockState(shaftAt).is(shaft.get())) {
                h.setBlock(shaftAt, shaftState);
            }
            boolean drilling = h.getBlockState(drillAt).is(drill.get())
                    && com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(drillAt));
            drillTicks[0] += drilling ? 1 : 0;
            if (tick[0] % 10 == 0 && h.getBlockState(rock).isAir()) {
                if (drilling) {
                    byDrill[0]++;
                } else {
                    byHand[0]++;
                }
                h.setBlock(rock, Blocks.STONE);
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                regrow(h, v);
                boolean drillInPen = false;
                for (int x = 1; x <= 9; x++) {
                    for (int z = 8; z <= 16; z++) {
                        for (int y = 2; y <= 4; y++) {
                            drillInPen |= h.getBlockState(new BlockPos(x, y, z)).is(drill.get());
                        }
                    }
                }
                if (tick[0] % (REGROW * 12) == 0 || tick[0] % (REGROW * 3) == 0 && !drilling || !drillInPen && carried(v, drillItem) == 0) {
                    clearPen(h, shaftAt, rock);
                    if (carried(v, drillItem) == 0) {
                        v.getInventory().addItem(new ItemStack(drillItem));
                        handedBack[0]++;
                    }
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    if (!v.getInventory().getItem(i).is(drillItem)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("DRILL newborn {}: by tick {}: mined by the drill {} times ({} ticks drilling), dug by hand {}; set going {}, placed {};"
                                + " handed back {}; in hand {}", which, tick[0], byDrill[0], drillTicks[0], byHand[0],
                        handle.hands().setGoingTotal, handle.hands().placedTotal, handedBack[0], v.getMainHandItem());
            }
            if (tick[0] == TICKS) {
                String report = String.format("DRILL: mined by the drill %d times (%d ticks drilling), dug by hand %d times",
                        byDrill[0], drillTicks[0], byHand[0]);
                LOG.info("DRILL newborn {} learning result: {}", which, report);
                if (byDrill[0] >= 10) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void newbornJoinsABelt(GameTestHelper h) {
        beltBenchmark(h, "1");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void secondNewbornJoinsABelt(GameTestHelper h) {
        beltBenchmark(h, "2");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void thirdNewbornJoinsABelt(GameTestHelper h) {
        beltBenchmark(h, "3");
    }

    @GameTest(template = ARENA, timeoutTicks = TICKS + 400, required = false)
    public static void fourthNewbornJoinsABelt(GameTestHelper h) {
        beltBenchmark(h, "4");
    }

    /**
     * With Create installed: an automated line to finish. Two turning shafts lie on the pen's
     * floor four apart, a press waits over the place between them, and a chest of iron drops
     * ingots through a funnel onto the first. A newborn carries belt connectors: used on one
     * shaft and then the other, one lays a belt that carries the ingots under the press, which
     * makes sheets of them with no hand on it. Belts laid and sheets made are counted; now and
     * then the belt is taken up and the connector handed back.
     */
    private static void beltBenchmark(GameTestHelper h, String which) {
        if (!selected(h, "BELT")) {
            return;
        }
        java.util.function.Function<String, ResourceLocation> create = id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var shaft = BuiltInRegistries.BLOCK.getOptional(create.apply("shaft"));
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var funnel = BuiltInRegistries.BLOCK.getOptional(create.apply("andesite_funnel"));
        var belt = BuiltInRegistries.BLOCK.getOptional(create.apply("belt"));
        var connector = BuiltInRegistries.ITEM.getOptional(create.apply("belt_connector"));
        var sheet = BuiltInRegistries.ITEM.getOptional(create.apply("iron_sheet"));
        if (motor.isEmpty() || shaft.isEmpty() || press.isEmpty() || funnel.isEmpty() || belt.isEmpty() || connector.isEmpty() || sheet.isEmpty()) {
            LOG.info("BELT: Create is not installed, nothing to learn");
            h.succeed();
            return;
        }
        net.minecraft.world.item.Item connectorItem = connector.get();
        Villager v = pen(h, new ItemStack(connectorItem, 2), TICKS);
        var rotation = h.getTestRotation();
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        var shaftState = shaft.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X).rotate(rotation);
        BlockPos first = new BlockPos(3, 2, 9);
        BlockPos last = new BlockPos(3, 2, 13);
        BlockPos beltMotor = first.west();
        BlockPos pressAt = new BlockPos(3, 4, 11);
        BlockPos pressMotor = pressAt.east();
        BlockPos chestAt = first.above(2);
        BlockPos funnelAt = first.above();
        var funnelState = withValue(funnel.get().defaultBlockState().setValue(facing, net.minecraft.core.Direction.DOWN), "extracting", "true");
        Runnable lay = () -> {
            h.setBlock(first, shaftState);
            h.setBlock(last, shaftState);
        };
        lay.run();
        h.setBlock(beltMotor, motor.get().defaultBlockState().setValue(facing, net.minecraft.core.Direction.EAST).rotate(rotation));
        h.setBlock(pressAt, press.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST)
                .rotate(rotation));
        h.setBlock(pressMotor, motor.get().defaultBlockState().setValue(facing, net.minecraft.core.Direction.WEST).rotate(rotation));
        try {
            for (BlockPos m : new BlockPos[] {beltMotor, pressMotor}) {
                Object be = h.getLevel().getBlockEntity(h.absolutePos(m));
                Object speed = be.getClass().getField("generatedSpeed").get(be);
                speed.getClass().getMethod("setValue", int.class).invoke(speed, 64);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        h.setBlock(chestAt, Blocks.CHEST);
        h.setBlock(funnelAt, funnelState);
        BlockPos[] keep = {beltMotor, pressAt, pressMotor, chestAt, funnelAt};
        var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(0, 0, 6)).getCenter(), h.absolutePos(new BlockPos(10, 6, 18)).getCenter());
        BrainManager.Handle handle = BrainManager.get().handle(v);
        int[] tick = {0};
        int[] laid = {0};
        int[] beltTicks = {0};
        int[] sheets = {0};
        int[] handedBack = {0};
        boolean[] was = {false};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            boolean joined = h.getBlockState(first.south(2)).is(belt.get());
            laid[0] += joined && !was[0] ? 1 : 0;
            beltTicks[0] += joined ? 1 : 0;
            was[0] = joined;
            if (tick[0] % 20 == 0) {
                // Sheets come off the belt's end: counted and cleared away, in its hands too.
                for (var item : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
                    if (item.getItem().is(sheet.get())) {
                        sheets[0] += item.getItem().getCount();
                        item.discard();
                    }
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    if (v.getInventory().getItem(i).is(sheet.get())) {
                        sheets[0] += v.getInventory().getItem(i).getCount();
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
            }
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                if (h.getBlockEntity(chestAt) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest && chest.getItem(0).isEmpty()) {
                    chest.setItem(0, new ItemStack(Items.IRON_INGOT, 4));
                }
                if (!h.getBlockState(funnelAt).is(funnel.get())) {
                    h.setBlock(funnelAt, funnelState);
                }
                if (tick[0] % (REGROW * 12) == 0 || tick[0] % (REGROW * 3) == 0 && !joined) {
                    // The belt is taken up and the shafts laid again, for another go.
                    clearPen(h, keep);
                    regrow(h, v);
                    lay.run();
                    if (carried(v, connectorItem) < 2) {
                        v.getInventory().addItem(new ItemStack(connectorItem, 2 - carried(v, connectorItem)));
                        handedBack[0]++;
                    }
                }
                for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                    ItemStack s = v.getInventory().getItem(i);
                    // Ingots it picked up from under the funnel are taken back: they filled its
                    // hands, and the connectors lay unused in its pockets.
                    if (!s.is(connectorItem)) {
                        v.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                if (v.getMainHandItem().is(Items.IRON_INGOT)) {
                    v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("BELT newborn {}: by tick {}: laid a belt {} times ({} ticks), {} sheets made; set going {}, placed {}; handed back {}; in hand {}",
                        which, tick[0], laid[0], beltTicks[0], sheets[0], handle.hands().setGoingTotal, handle.hands().placedTotal, handedBack[0],
                        v.getMainHandItem());
            }
            if (tick[0] == TICKS) {
                String report = String.format("BELT: laid a belt %d times (%d ticks), %d sheets made", laid[0], beltTicks[0], sheets[0]);
                LOG.info("BELT newborn {} learning result: {}", which, report);
                if (laid[0] >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }

    /** A block state with a property set by name, for blocks of mods the tests do not link against. */
    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState withValue(
            net.minecraft.world.level.block.state.BlockState state, String property, String value) {
        @SuppressWarnings("unchecked")
        var p = (net.minecraft.world.level.block.state.properties.Property<T>) state.getBlock().getStateDefinition().getProperty(property);
        return p == null ? state : p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsACrankAtATable(GameTestHelper h) {
        tableCrankBenchmark(h, "1", false);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsACrankAtATable(GameTestHelper h) {
        tableCrankBenchmark(h, "2", false);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsACrankAtATable(GameTestHelper h) {
        tableCrankBenchmark(h, "3", false);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsACrankItWatched(GameTestHelper h) {
        tableCrankBenchmark(h, "1", false, true);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsACrankItWatched(GameTestHelper h) {
        tableCrankBenchmark(h, "2", false, true);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsACrankItWatched(GameTestHelper h) {
        tableCrankBenchmark(h, "3", false, true);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void newbornCraftsACrankWithABook(GameTestHelper h) {
        tableCrankBenchmark(h, "1", true);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void secondNewbornCraftsACrankWithABook(GameTestHelper h) {
        tableCrankBenchmark(h, "2", true);
    }

    @GameTest(template = ARENA, timeoutTicks = CRAFT_TICKS_MAX + 400, required = false)
    public static void thirdNewbornCraftsACrankWithABook(GameTestHelper h) {
        tableCrankBenchmark(h, "3", true);
    }

    /**
     * With Create installed: a crafting table in the pen and a newborn carrying exactly what a
     * hand crank takes, three planks and an andesite alloy, laid out as three planks in a row with
     * the alloy under the first. The first power source made from scratch, and the first recipe
     * that needs a table's 3x3 grid and two kinds of thing. TABLECRANKBOOK adds a picture book of
     * the crank's recipe. Cranks it makes are taken from it and counted, and it is handed the
     * planks and alloy again. TABLECRANKWATCH has someone at the table instead, making a crank
     * every little while in front of whoever looks.
     */
    private static void tableCrankBenchmark(GameTestHelper h, String which, boolean withBook) {
        tableCrankBenchmark(h, which, withBook, false);
    }

    /** How often the one at the table in TABLECRANKWATCH makes a crank. */
    private static final int DEMO_EVERY = 200;

    private static void tableCrankBenchmark(GameTestHelper h, String which, boolean withBook, boolean watch) {
        String name = watch ? "TABLECRANKWATCH" : withBook ? "TABLECRANKBOOK" : "TABLECRANK";
        if (!selected(h, name)) {
            return;
        }
        var alloy = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "andesite_alloy"));
        var crank = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "hand_crank"));
        if (alloy.isEmpty() || crank.isEmpty()) {
            LOG.info("{}: Create is not installed, nothing to learn", name);
            h.succeed();
            return;
        }
        int ticks = Math.min(CRAFT_TICKS_MAX, Integer.getInteger("reallyalive.craftTicks", CRAFT_TICKS));
        Villager v = pen(h, new ItemStack(Items.OAK_PLANKS, 3), ticks);
        v.getInventory().addItem(new ItemStack(alloy.get()));
        ItemStack book = withBook ? com.mementocraft.reallyalive.body.RecipeBook.write(h.getLevel(), "Hand crank", List.of(crank.get())) : null;
        if (book != null) {
            v.getInventory().addItem(book.copy());
        }
        BlockPos table = new BlockPos(5, 2, 10);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        BrainManager.Handle handle = BrainManager.get().handle(v);
        // Someone at work at the table: a villager without a brain, standing in for a player, and
        // the hands it crafts with.
        Villager crafter = watch ? spawnMcaVillager(h, new Vec3(6.5, 2, 9.5)) : null;
        if (crafter != null) {
            crafter.getInventory().clearContent();
            attach(h, crafter).hold(com.mementocraft.reallyalive.brain.MotorCommand.IDLE);
        }
        Teacher teacher = watch ? new Teacher(h.getLevel(), "crank" + which) : null;
        var crankRecipe = watch ? h.getLevel().getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).stream()
                .filter(r -> r.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe
                        && r.value().getResultItem(h.getLevel().registryAccess()).is(crank.get()))
                .map(r -> (net.minecraft.world.item.crafting.ShapedRecipe) r.value()).findFirst().orElseThrow() : null;
        int[] tick = {0};
        int[] cranks = {0};
        int[] tableTicks = {0};
        int[] tableOpened = {0};
        boolean[] wasAtTable = {false};
        int[] handedBack = {0};
        h.onEachTick(() -> {
            if (!v.isAlive()) {
                h.fail("the villager died at tick " + tick[0]);
                return;
            }
            tick[0]++;
            if (tick[0] % TIDY_EVERY == 0) {
                tidyLitter(h);
            }
            if (teacher != null && tick[0] % DEMO_EVERY == 0 && crafter.isAlive()) {
                teacher.stand(crafter.position(), v.getEyePosition());
                teacher.craft(h.absolutePos(table), crankRecipe);
            }
            boolean atTable = handle.hands().screen() instanceof net.minecraft.world.inventory.CraftingMenu;
            tableTicks[0] += atTable ? 1 : 0;
            tableOpened[0] += atTable && !wasAtTable[0] ? 1 : 0;
            wasAtTable[0] = atTable;
            if (tick[0] % REGROW == 0 && !handle.hands().screenOpen()) {
                clearPen(h, table);
                if (!h.getBlockState(table).is(Blocks.CRAFTING_TABLE)) {
                    h.setBlock(table, Blocks.CRAFTING_TABLE);
                }
                regrow(h, v);
                int made = carried(v, crank.get());
                if (made > 0) {
                    cranks[0] += made;
                    v.getInventory().removeItemType(crank.get(), 64);
                    if (v.getMainHandItem().is(crank.get())) {
                        v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                    }
                }
                if (book != null && carried(v, book.getItem()) == 0) {
                    v.getInventory().addItem(book.copy());
                }
                if (carried(v, Items.OAK_PLANKS) < 3 || carried(v, alloy.get()) < 1) {
                    // Whatever was lost or turned into something else is handed over again.
                    for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
                        ItemStack s = v.getInventory().getItem(i);
                        if (book == null || !com.mementocraft.reallyalive.body.RecipeBook.isRecipeBook(s)) {
                            v.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.OAK_PLANKS, 3));
                    v.getInventory().addItem(new ItemStack(alloy.get()));
                    handedBack[0]++;
                }
            }
            if (tick[0] % WINDOW == 0) {
                LOG.info("{} newborn {}: by tick {}: cranks made {}; at the table {} times, {} ticks; screens {}, clicks {}, results formed {}, made {};"
                                + " shared out {}, dealt one {}; recipes learnt {}, places matched {}; handed back {}; watched {}, moves copied {}; made kinds {}", name, which, tick[0], cranks[0],
                        tableOpened[0], tableTicks[0], handle.hands().screensOpened, handle.hands().screenClicks, handle.hands().formedTotal,
                        handle.hands().madeTotal, handle.hands().sharedOutTotal, handle.hands().dealtOneTotal, handle.hands().recipesLearnt,
                        handle.hands().matchedTotal, handedBack[0], handle.hands().sawCraftedTotal, handle.hands().copiedTotal, handle.hands().madeKinds);
            }
            if (tick[0] == ticks) {
                String report = String.format("%s: made %d hand cranks in %d ticks, at the table %d times", name, cranks[0], ticks, tableOpened[0]);
                LOG.info("{} newborn {} learning result: {}", name, which, report);
                if (cranks[0] >= 3) {
                    h.succeed();
                } else {
                    h.fail("not learned yet: " + report);
                }
            }
        });
    }
}
