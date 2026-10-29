package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.body.Hands;
import com.mementocraft.reallyalive.brain.FeelChannel;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mementocraft.reallyalive.brain.ThingCode;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * In-game checks that a brain villager's right click, mouse wheel and inventory key do what a
 * player's do: till and plant, open doors, eat, pick up what it walks over, bring the next thing
 * into its hand, and work screens with a mouse pointer: craft planks in its own 2x2 grid and at a
 * crafting table, and brew a potion. The keys are pressed by the test, the way a player would, to
 * show the hands can do it; whether a brain learns to is for the learning benchmarks. Also checks
 * that the vanilla farmer's handless harvesting never starts for a brain villager.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class UseTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";
    private static final MotorCommand USE = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true);
    private static final MotorCommand INVENTORY = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, false, true, false);
    private static final MotorCommand WHEEL = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, false, false, true);
    private static final Vec3 STAND = new Vec3(3.5, 2, 12.5);

    private UseTests() {}

    private static Villager worker(GameTestHelper h, ItemStack inHand) {
        Villager v = spawnMcaVillager(h, STAND);
        v.setItemSlot(EquipmentSlot.MAINHAND, inHand);
        attach(h, v).hold(MotorCommand.IDLE);
        return v;
    }

    private static BrainManager.Handle handle(Villager v) {
        return BrainManager.get().handle(v);
    }

    /** Turns the villager's head to look at a point in the test's coordinates. */
    private static void aim(GameTestHelper h, Villager v, Vec3 relative) {
        Vec3 to = h.absoluteVec(relative).subtract(v.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        v.setYRot(yaw);
        v.setYHeadRot(yaw);
        v.setYBodyRot(yaw);
        v.setXRot(pitch);
    }

    /** Holds use for a few ticks while aimed at a point, then lets go and checks the result. */
    private static void useOn(GameTestHelper h, ItemStack inHand, Vec3 aimAt, Runnable check) {
        Villager v = worker(h, inHand);
        BrainManager.Handle handle = handle(v);
        h.runAtTickTime(8, () -> aim(h, v, aimAt));
        h.runAtTickTime(10, () -> {
            aim(h, v, aimAt);
            handle.hold(USE);
        });
        h.runAtTickTime(12, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(20, () -> {
            check.run();
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void tillsDirtWithAHoe(GameTestHelper h) {
        BlockPos dirt = new BlockPos(5, 1, 12);
        h.setBlock(dirt, Blocks.DIRT);
        useOn(h, new ItemStack(Items.WOODEN_HOE), new Vec3(5.5, 2.0, 12.5), () -> {
            h.assertBlockPresent(Blocks.FARMLAND, dirt);
            Villager v = h.getLevel().getEntitiesOfClass(Villager.class, h.getBounds()).get(0);
            h.assertTrue(handle(v).hands().changedTotal == 1, "tilling did not count as changing the world");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void plantsSeedsOnFarmland(GameTestHelper h) {
        BlockPos field = new BlockPos(5, 1, 12);
        h.setBlock(field, Blocks.FARMLAND);
        useOn(h, new ItemStack(Items.WHEAT_SEEDS, 3), new Vec3(5.5, 1.94, 12.5), () -> {
            h.assertBlockPresent(Blocks.WHEAT, field.above());
            Villager v = h.getLevel().getEntitiesOfClass(Villager.class, h.getBounds()).get(0);
            h.assertTrue(v.getMainHandItem().getCount() == 2, "planting did not use one seed");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void opensADoor(GameTestHelper h) {
        BlockPos door = new BlockPos(5, 2, 12);
        // Facing west, so the closed door stands across the villager's line of sight.
        var closed = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, net.minecraft.core.Direction.WEST);
        h.setBlock(door, closed);
        h.setBlock(door.above(), closed.setValue(DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        // An empty hand on a door opens it, as a player's does.
        useOn(h, ItemStack.EMPTY, new Vec3(5.5, 2.8, 12.5), () ->
                h.assertTrue(h.getBlockState(door).getValue(DoorBlock.OPEN), "the door did not open"));
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void eatsBreadWhileUseIsHeld(GameTestHelper h) {
        Villager v = worker(h, new ItemStack(Items.BREAD, 2));
        BrainManager.Handle handle = handle(v);
        float[] felt = {0f};
        h.runAtTickTime(8, () -> aim(h, v, new Vec3(3.5, 9, 30)));
        h.runAtTickTime(10, () -> handle.hold(USE));
        h.onEachTick(() -> felt[0] = Math.max(felt[0], handle.frame().feel[FeelChannel.USING_ITEM.ordinal()]));
        // A player takes 32 ticks to eat; let go a little after.
        h.runAtTickTime(50, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(55, () -> {
            h.assertTrue(v.getMainHandItem().getCount() == 1, "holding use on bread for 40 ticks did not eat one, holding "
                    + v.getMainHandItem());
            h.assertTrue(felt[0] > 0.5f, "eating was not felt, most " + felt[0]);
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void picksUpWhatItWalksOver(GameTestHelper h) {
        Villager v = worker(h, ItemStack.EMPTY);
        h.runAtTickTime(10, () -> {
            ItemEntity apple = new ItemEntity(h.getLevel(), v.getX(), v.getY() + 0.2, v.getZ(), new ItemStack(Items.APPLE, 3));
            apple.setPickUpDelay(0);
            h.getLevel().addFreshEntity(apple);
        });
        h.runAtTickTime(20, () -> {
            h.assertTrue(v.getMainHandItem().is(Items.APPLE) && v.getMainHandItem().getCount() == 3,
                    "the apples were not picked up, holding " + v.getMainHandItem());
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void theWheelBringsTheNextThingIntoTheHand(GameTestHelper h) {
        Villager v = worker(h, new ItemStack(Items.STICK));
        v.getInventory().clearContent();
        v.getInventory().addItem(new ItemStack(Items.DIRT, 5));
        BrainManager.Handle handle = handle(v);
        h.runAtTickTime(10, () -> handle.hold(WHEEL));
        h.runAtTickTime(12, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(15, () -> {
            h.assertTrue(v.getMainHandItem().is(Items.DIRT), "the wheel did not bring the dirt into its hand: " + v.getMainHandItem());
            h.assertTrue(v.getInventory().hasAnyOf(java.util.Set.of(Items.STICK)), "the stick it held was lost");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void seesWhatItAimsAtAndHolds(GameTestHelper h) {
        BlockPos table = new BlockPos(5, 2, 12);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        Villager v = worker(h, new ItemStack(Items.WOODEN_HOE));
        BrainManager.Handle handle = handle(v);
        h.runAtTickTime(8, () -> aim(h, v, new Vec3(5.5, 2.5, 12.5)));
        h.runAtTickTime(10, () -> aim(h, v, new Vec3(5.5, 2.5, 12.5)));
        h.runAtTickTime(12, () -> {
            float[] want = new float[handle.frame().focus.length];
            ThingCode.fill(want, "minecraft:crafting_table", "CraftingTableBlock");
            h.assertTrue(java.util.Arrays.equals(want, handle.frame().focus), "does not see the crafting table it aims at");
            ThingCode.fill(want, "minecraft:wooden_hoe", "HoeItem");
            h.assertTrue(java.util.Arrays.equals(want, handle.frame().held), "does not feel the hoe in its hand");
            h.succeed();
        });
    }

    // ---------------------------------------------------------------- screens

    /**
     * A player's mouse work, a step per tick: move the pointer onto a slot, or press and release a
     * button there. Moves are made with the mouse-look keys, like the brain's.
     */
    private static final class MouseScript {
        private static final String OUTSIDE = "outside";
        private final List<Object> steps = new ArrayList<>();

        /** Moves to the first slot matching, pressing a button there: 0 left, 1 right, 2 shift-left. */
        MouseScript click(Predicate<Slot> where, int button) {
            steps.add(where);
            steps.add(button);
            return this;
        }

        /** Moves past the left edge of the window and presses and lets go of the left button there. */
        MouseScript clickOutside() {
            steps.add(OUTSIDE);
            steps.add(0);
            return this;
        }

        MouseScript key(MotorCommand keys) {
            steps.add(keys);
            return this;
        }

        MouseScript waitTicks(int ticks) {
            // Each idle step lasts two ticks, like a key press and release.
            for (int i = 0; i < ticks / 2; i++) {
                steps.add(MotorCommand.IDLE);
            }
            return this;
        }

        /** Plays the script from the given tick and runs the check one tick after it ends. */
        void play(GameTestHelper h, BrainManager.Handle handle, int from, Runnable check) {
            int[] at = {0};
            int[] phase = {0};
            int[] tick = {0};
            h.onEachTick(() -> {
                tick[0]++;
                if (tick[0] < from) {
                    return;
                }
                if (at[0] >= steps.size()) {
                    handle.hold(MotorCommand.IDLE);
                    if (phase[0]++ == 1) {
                        check.run();
                        h.succeed();
                    }
                    return;
                }
                Object step = steps.get(at[0]);
                if (step instanceof MotorCommand keys) {
                    // A key goes down for a tick, then up for one.
                    handle.hold(phase[0] == 0 ? keys : MotorCommand.IDLE);
                    if (phase[0]++ == 1) {
                        phase[0] = 0;
                        at[0]++;
                    }
                    return;
                }
                Hands hands = handle.hands();
                if (OUTSIDE.equals(step)) {
                    if (phase[0] == 0) {
                        handle.hold(new MotorCommand(0, 0, false, false, false, -400f / Hands.POINTER_PER_DEGREE, 0f));
                    } else if (phase[0] == 1) {
                        h.assertTrue(Hands.outside(hands.screen(), hands.pointerX(), hands.pointerY()), "the pointer did not leave the window");
                        handle.hold(new MotorCommand(0, 0, false, false, false, 0f, 0f, true, false));
                    } else {
                        handle.hold(MotorCommand.IDLE);
                    }
                    if (phase[0]++ == 2) {
                        phase[0] = 0;
                        at[0] += 2;
                    }
                    return;
                }
                @SuppressWarnings("unchecked")
                Predicate<Slot> where = (Predicate<Slot>) step;
                int button = (Integer) steps.get(at[0] + 1);
                AbstractContainerMenu menu = hands.screen();
                h.assertTrue(menu != null, "no screen is open at step " + at[0]);
                Slot slot = menu.slots.stream().filter(where).findFirst().orElse(null);
                h.assertTrue(slot != null, "no such slot on " + menu.getClass().getSimpleName());
                if (phase[0] == 0) {
                    float dx = slot.x + 8 - hands.pointerX();
                    float dy = slot.y + 8 - hands.pointerY();
                    handle.hold(new MotorCommand(0, 0, false, false, false, dx / Hands.POINTER_PER_DEGREE, dy / Hands.POINTER_PER_DEGREE));
                } else if (phase[0] == 1) {
                    h.assertTrue(hands.pointedSlot() == slot, "the pointer did not reach slot " + slot.index);
                    handle.hold(new MotorCommand(0, 0, false, button == 2, false, 0f, 0f, button != 1, button == 1));
                } else {
                    handle.hold(MotorCommand.IDLE);
                }
                if (phase[0]++ == 2) {
                    phase[0] = 0;
                    at[0] += 2;
                }
            });
        }
    }

    private static Predicate<Slot> slot(int index) {
        return s -> s.index == index;
    }

    /** The slot of the screen showing this villager inventory place (0 is the hand, then its inventory). */
    private static Predicate<Slot> carried(int standInIndex) {
        return s -> s.container instanceof net.minecraft.world.entity.player.Inventory && s.getContainerSlot() == standInIndex;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craftsPlanksInItsOwnInventory(GameTestHelper h) {
        Villager v = worker(h, new ItemStack(Items.OAK_LOG, 1));
        BrainManager.Handle handle = handle(v);
        // Inventory key, pick up the log in its hand, put it in the 2x2 grid, shift-click the
        // planks out, inventory key again.
        new MouseScript()
                .key(INVENTORY)
                .click(carried(0), 0)
                .click(slot(InventoryMenu.CRAFT_SLOT_START), 0)
                .click(slot(InventoryMenu.RESULT_SLOT), 2)
                .key(INVENTORY)
                .play(h, handle, 10, () -> {
                    h.assertFalse(handle.hands().screenOpen(), "the inventory did not close");
                    int planks = count(v, Items.OAK_PLANKS);
                    h.assertTrue(planks == 4, "crafted " + planks + " planks, not 4");
                    h.assertTrue(count(v, Items.OAK_LOG) == 0, "the log was not used");
                    h.assertTrue(handle.hands().madeTotal == 1, "making the planks was not felt");
                });
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craftsAtACraftingTable(GameTestHelper h) {
        BlockPos table = new BlockPos(5, 2, 12);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        Villager v = worker(h, new ItemStack(Items.OAK_PLANKS, 2));
        BrainManager.Handle handle = handle(v);
        boolean[] opened = {false};
        h.runAtTickTime(8, () -> aim(h, v, new Vec3(5.5, 2.5, 12.5)));
        h.runAtTickTime(10, () -> {
            aim(h, v, new Vec3(5.5, 2.5, 12.5));
            handle.hold(USE);
        });
        h.runAtTickTime(12, () -> {
            handle.hold(MotorCommand.IDLE);
            opened[0] = handle.hands().screen() instanceof CraftingMenu;
        });
        // Two planks one above the other make four sticks: right-click puts one plank down.
        new MouseScript()
                .click(carried(0), 0)
                .click(slot(2), 1)
                .click(slot(5), 1)
                .click(slot(0), 2)
                .key(INVENTORY)
                .play(h, handle, 14, () -> {
                    h.assertTrue(opened[0], "use on the crafting table did not open it");
                    h.assertTrue(count(v, Items.STICK) == 4, "crafted " + count(v, Items.STICK) + " sticks, not 4");
                    h.assertTrue(count(v, Items.OAK_PLANKS) == 0, "planks left over");
                });
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void brewsAnAwkwardPotion(GameTestHelper h) {
        BlockPos stand = new BlockPos(5, 2, 12);
        h.setBlock(stand, Blocks.BREWING_STAND);
        Villager v = worker(h, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        v.getInventory().clearContent();
        v.getInventory().addItem(new ItemStack(Items.NETHER_WART));
        v.getInventory().addItem(new ItemStack(Items.BLAZE_POWDER));
        BrainManager.Handle handle = handle(v);
        h.runAtTickTime(8, () -> aim(h, v, new Vec3(5.5, 2.3, 12.5)));
        h.runAtTickTime(10, () -> {
            aim(h, v, new Vec3(5.5, 2.3, 12.5));
            handle.hold(USE);
        });
        h.runAtTickTime(12, () -> handle.hold(MotorCommand.IDLE));
        // Bottle, wart and powder in with shift-clicks, wait out the brewing (400 ticks), take the
        // potion out with a click and put it back in the hand slot.
        new MouseScript()
                .click(carried(0), 2)
                .click(carried(1), 2)
                .click(carried(2), 2)
                .waitTicks(420)
                .click(slot(0), 0)
                .click(carried(0), 0)
                .key(INVENTORY)
                .play(h, handle, 14, () -> {
                    ItemStack hand = v.getMainHandItem();
                    PotionContents brewed = hand.get(DataComponents.POTION_CONTENTS);
                    LOG.info("brewed {}", hand);
                    h.assertTrue(brewed != null && brewed.is(Potions.AWKWARD), "did not brew an awkward potion, holding " + hand);
                    h.assertTrue(handle.hands().madeTotal >= 1, "brewing was not felt as making something");
                    h.assertTrue(handle.hands().screensOpened >= 1 && !(handle.hands().screen() instanceof BrewingStandMenu),
                            "the brewing stand screen was not opened and closed");
                });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void vanillaFarmingNeverStartsForABrainVillager(GameTestHelper h) {
        Villager v = worker(h, ItemStack.EMPTY);
        h.runAtTickTime(5, () -> {
            boolean started = new HarvestFarmland().tryStart(h.getLevel(), v, h.getLevel().getGameTime());
            h.assertFalse(started, "the vanilla farmer's harvesting started for a brain villager");
            h.succeed();
        });
    }

    /**
     * With Create installed (it is never required), its wrench turns a shaft for a villager as it
     * does for a player: Create's blocks answer the stand-in's click like any player's.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void createsWrenchTurnsAShaftIfCreateIsInstalled(GameTestHelper h) {
        var wrench = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "wrench"));
        var shaft = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (wrench.isEmpty() || shaft.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos at = new BlockPos(5, 2, 12);
        h.setBlock(at, shaft.get().defaultBlockState());
        BlockState before = h.getBlockState(at);
        useOn(h, new ItemStack(wrench.get()), new Vec3(5.5, 2.5, 12.5), () -> {
            BlockState after = h.getBlockState(at);
            LOG.info("Create shaft before {} after the wrench {}", before, after);
            h.assertTrue(after.getBlock() == shaft.get() && !after.equals(before), "the wrench did not turn the shaft: " + after);
        });
    }

    /**
     * With Create installed, a shaft placed against a turning motor turns, and the villager feels
     * it set a machine going: the inborn pull towards building machinery.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void feelsAShaftItPlacedStartTurningIfCreateIsInstalled(GameTestHelper h) {
        var motor = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        var shaft = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (motor.isEmpty() || shaft.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos at = new BlockPos(6, 2, 12);
        h.setBlock(at, motor.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST));
        useOn(h, new ItemStack(shaft.get(), 4), new Vec3(6.0, 2.5, 12.5), () -> {
            Villager v = h.getLevel().getEntitiesOfClass(Villager.class, h.getBounds()).get(0);
            BlockPos placed = at.west();
            LOG.info("Shaft at {}: {}, turning {}, set going {}", placed, h.getBlockState(placed),
                    com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(placed)), handle(v).hands().setGoingTotal);
            h.assertTrue(com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(placed)), "the shaft is not turning");
            h.assertTrue(handle(v).hands().setGoingTotal == 1, "setting the shaft going was not felt");
        });
    }

    /** Letting go of a stack outside the window throws it out, and picking it up again is not collecting. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void throwsAStackOutOfItsInventory(GameTestHelper h) {
        Villager v = worker(h, new ItemStack(Items.WHEAT, 3));
        BrainManager.Handle handle = handle(v);
        new MouseScript()
                .key(INVENTORY)
                .click(carried(0), 0)
                .clickOutside()
                .key(INVENTORY)
                .play(h, handle, 5, () -> {
                    var thrown = h.getLevel().getEntitiesOfClass(ItemEntity.class, h.getBounds().inflate(4));
                    LOG.info("thrown {}, hand {}", thrown, v.getMainHandItem());
                    h.assertTrue(handle.hands().throwsTotal == 1, "nothing was thrown");
                    h.assertTrue(thrown.size() == 1 && thrown.get(0).getItem().is(Items.WHEAT) && thrown.get(0).getItem().getCount() == 3,
                            "the wheat was not thrown out: " + thrown);
                    h.assertTrue(count(v, Items.WHEAT) == 0, "the villager still carries wheat");
                });
    }

    /**
     * With Create installed: flour taken from a turning millstone with an empty hand is felt as
     * making something, as a crafted thing taken from a result slot is.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void takesFlourFromAMillstoneIfCreateIsInstalled(GameTestHelper h) {
        var motor = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        var millstone = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "millstone"));
        var flour = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "wheat_flour"));
        if (motor.isEmpty() || millstone.isEmpty() || flour.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos stone = new BlockPos(6, 3, 12);
        h.setBlock(stone.below(), motor.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
        h.setBlock(stone, millstone.get());
        Villager v = worker(h, ItemStack.EMPTY);
        BrainManager.Handle handle = handle(v);
        h.runAtTickTime(5, () -> {
            Vec3 top = h.absoluteVec(new Vec3(6.5, 4.1, 12.5));
            ItemEntity wheat = new ItemEntity(h.getLevel(), top.x, top.y, top.z, new ItemStack(Items.WHEAT));
            wheat.setDeltaMovement(Vec3.ZERO);
            h.getLevel().addFreshEntity(wheat);
        });
        Vec3 side = new Vec3(6.0, 3.5, 12.5);
        h.runAtTickTime(400, () -> aim(h, v, side));
        h.runAtTickTime(402, () -> {
            aim(h, v, side);
            handle.hold(USE);
        });
        h.runAtTickTime(404, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(410, () -> {
            LOG.info("from the millstone: carries {} flour, made {}", count(v, flour.get()), handle.hands().madeTotal);
            h.assertTrue(count(v, flour.get()) >= 1, "no flour was taken from the millstone");
            h.assertTrue(handle.hands().madeTotal == 1, "taking the flour was not felt as making something");
            h.succeed();
        });
    }

    /**
     * With Create installed: iron put on a depot under a turning press is pressed into a sheet,
     * and the sheet taken back with an empty hand is felt as making something; the iron itself,
     * taken back unpressed, would not be.
     */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void pressesIronOnADepotIfCreateIsInstalled(GameTestHelper h) {
        var create = (java.util.function.Function<String, ResourceLocation>) id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var depot = BuiltInRegistries.BLOCK.getOptional(create.apply("depot"));
        var sheet = BuiltInRegistries.ITEM.getOptional(create.apply("iron_sheet"));
        if (motor.isEmpty() || press.isEmpty() || depot.isEmpty() || sheet.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos onto = new BlockPos(6, 2, 12);
        h.setBlock(onto, depot.get());
        // The press stands two above the depot: its head comes down into the gap between.
        BlockPos pressAt = onto.above(2);
        h.setBlock(pressAt, press.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST));
        h.setBlock(pressAt.east(), motor.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST));
        Villager v = worker(h, new ItemStack(Items.IRON_INGOT, 2));
        BrainManager.Handle handle = handle(v);
        Vec3 side = new Vec3(6.3, 2.8, 12.5);
        for (int at : new int[] {10, 500}) {
            h.runAtTickTime(at - 2, () -> aim(h, v, side));
            h.runAtTickTime(at, () -> {
                aim(h, v, side);
                handle.hold(USE);
            });
            h.runAtTickTime(at + 2, () -> handle.hold(MotorCommand.IDLE));
        }
        h.runAtTickTime(20, () -> LOG.info("onto the depot: hand {}, press turning {}", v.getMainHandItem(),
                com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(pressAt))));
        h.runAtTickTime(510, () -> {
            LOG.info("from the depot: carries {} sheets, {} iron, made {}", count(v, sheet.get()), count(v, Items.IRON_INGOT),
                    handle.hands().madeTotal);
            h.assertTrue(count(v, sheet.get()) >= 1, "no sheet was taken from the depot");
            h.assertTrue(handle.hands().madeTotal == 1, "taking the sheet was not felt as making something");
            h.succeed();
        });
    }

    /**
     * A recipe book opens to pictures of its recipes, a page at a time; a page looked at for a
     * second is remembered, and a remembered recipe marks where its picture put things on the
     * crafting grid: planks one above the other for sticks.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void readsARecipeBookAndRemembersWhereThingsGo(GameTestHelper h) {
        // Only sticks: two recipes, from planks and from bamboo.
        ItemStack book = com.mementocraft.reallyalive.body.RecipeBook.write(h.getLevel(), "Sticks", List.of(Items.STICK));
        Villager v = worker(h, book);
        v.getInventory().addItem(new ItemStack(Items.OAK_PLANKS, 4));
        BrainManager.Handle handle = handle(v);
        new MouseScript()
                .key(USE)
                .waitTicks(26)
                .click(slot(com.mementocraft.reallyalive.body.RecipeBook.Menu.NEXT), 0)
                .waitTicks(26)
                .key(INVENTORY)
                .waitTicks(2)
                .key(INVENTORY)
                .play(h, handle, 5, () -> {
                    var hint = handle.hands().recipeHint(handle.hands().screen());
                    LOG.info("recipes learnt {}, screen {}, hint {}", handle.hands().recipesLearnt,
                            handle.hands().screen() == null ? null : handle.hands().screen().getClass().getSimpleName(),
                            hint == null ? null : hint.want().keySet());
                    h.assertTrue(handle.hands().recipesLearnt == 2, "it did not remember the two pages it looked at: " + handle.hands().recipesLearnt);
                    h.assertTrue(handle.hands().screen() instanceof InventoryMenu, "its own inventory is not open");
                    h.assertTrue(hint != null && hint.want().keySet().equals(java.util.Set.of(1, 3)),
                            "the stick recipe did not mark the two grid places one above the other: " + (hint == null ? null : hint.want().keySet()));
                });
    }

    /**
     * Walking over things with the hand full of something else puts them in the pockets, once; and
     * with the pockets full too they stay where they lie. Taking them once copied them into the
     * pockets and left them on the ground, and a full villager dropped a fresh copy every tick:
     * hundreds of thousands of items froze the server.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void picksUpThingsWithoutCopyingThem(GameTestHelper h) {
        Villager v = worker(h, new ItemStack(Items.COBBLESTONE, 64));
        Villager full = spawnMcaVillager(h, new Vec3(3.5, 2, 15.5));
        full.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 64));
        for (int i = 0; i < full.getInventory().getContainerSize(); i++) {
            full.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        attach(h, full).hold(MotorCommand.IDLE);
        for (Villager at : List.of(v, full)) {
            var item = new net.minecraft.world.entity.item.ItemEntity(h.getLevel(), at.getX(), at.getY(), at.getZ(), new ItemStack(Items.DIRT, 5));
            item.setNoPickUpDelay();
            h.getLevel().addFreshEntity(item);
        }
        h.runAtTickTime(40, () -> {
            var box = new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(0, 0, 9))).inflate(0, 0, 0)
                    .minmax(new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(8, 5, 18))));
            int lying = 0;
            for (var item : h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, box)) {
                lying += item.getItem().is(Items.DIRT) ? item.getItem().getCount() : 0;
            }
            int pocketed = count(v, Items.DIRT);
            LOG.info("dirt: pocketed {}, by the full villager {}, lying {}", pocketed, count(full, Items.DIRT), lying);
            h.assertTrue(pocketed == 5, "the villager with room did not pocket the 5 dirt exactly once: " + pocketed);
            h.assertTrue(count(full, Items.DIRT) == 0 && lying == 5, "the full villager's dirt was not left lying as it was: " + lying + " lying");
            h.succeed();
        });
    }

    /**
     * Watching someone craft is remembered as a book page is: someone in front of a villager
     * takes sticks from a grid (planks one above the other), and the villager's own grid then
     * marks those two places. A villager facing the other way sees nothing.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void remembersARecipeItWatchedBeingCrafted(GameTestHelper h) {
        Villager watcher = worker(h, new ItemStack(Items.OAK_PLANKS, 4));
        Villager away = spawnMcaVillager(h, new Vec3(3.5, 2, 14.5));
        attach(h, away).hold(MotorCommand.IDLE);
        BrainManager.Handle handle = handle(watcher);
        // Someone at work in front of it: a villager without a brain, standing in for a player.
        Villager crafter = spawnMcaVillager(h, new Vec3(6.5, 2, 12.5));
        h.runAtTickTime(5, () -> {
            aim(h, watcher, new Vec3(6.5, 3.5, 12.5));
            aim(h, away, new Vec3(0.5, 3.5, 14.5));
        });
        h.runAtTickTime(8, () -> {
            aim(h, watcher, new Vec3(6.5, 3.5, 12.5));
            aim(h, away, new Vec3(0.5, 3.5, 14.5));
            var sticks = h.getLevel().getRecipeManager().byKey(ResourceLocation.withDefaultNamespace("stick")).orElseThrow();
            com.mementocraft.reallyalive.BrainEvents.seenCrafting(h.getLevel(), crafter, sticks);
            LOG.info("watched: {} times, learnt {}; facing away: {} times", handle.hands().sawCraftedTotal, handle.hands().recipesLearnt,
                    handle(away).hands().sawCraftedTotal);
            h.assertTrue(handle.hands().sawCraftedTotal == 1, "the villager looking at the crafter did not see the sticks made");
            h.assertTrue(handle(away).hands().sawCraftedTotal == 0, "a villager looking away saw the crafting");
        });
        new MouseScript()
                .waitTicks(10)
                .key(INVENTORY)
                .play(h, handle, 12, () -> {
                    var hint = handle.hands().recipeHint(handle.hands().screen());
                    LOG.info("watched recipe hint {}", hint == null ? null : hint.want().keySet());
                    h.assertTrue(handle.hands().screen() instanceof InventoryMenu, "its own inventory is not open");
                    h.assertTrue(hint != null && hint.want().keySet().equals(java.util.Set.of(1, 3)),
                            "the watched stick recipe did not mark the two grid places one above the other: " + (hint == null ? null : hint.want().keySet()));
                });
    }

    /**
     * With Create installed: a water wheel put against the side of a press, where a stream of
     * water runs past, turns the press. Placing it is an ordinary use on the press's side, and
     * the press it set going is felt.
     */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void setsAPressGoingWithAWaterWheelIfCreateIsInstalled(GameTestHelper h) {
        var create = (java.util.function.Function<String, ResourceLocation>) id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var press = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_press"));
        var depot = BuiltInRegistries.BLOCK.getOptional(create.apply("depot"));
        var wheel = BuiltInRegistries.ITEM.getOptional(create.apply("water_wheel"));
        if (press.isEmpty() || depot.isEmpty() || wheel.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos onto = new BlockPos(6, 2, 12);
        h.setBlock(onto, depot.get());
        BlockPos pressAt = onto.above(2);
        h.setBlock(pressAt, press.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.EAST));
        // A stream: a spring in a glass trough above where the wheel goes, running off past it.
        BlockPos spring = new BlockPos(7, 5, 11);
        for (BlockPos g : List.of(spring.west(), spring.east(), spring.north(), spring.below(), spring.south().west(), spring.south().east())) {
            h.setBlock(g, Blocks.GLASS);
        }
        h.setBlock(spring, Blocks.WATER);
        BlockPos wheelAt = pressAt.east();
        Villager v = spawnMcaVillager(h, new Vec3(9.5, 2, 12.5));
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(wheel.get()));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        Vec3 side = new Vec3(7.02, 4.5, 12.5);
        h.runAtTickTime(28, () -> aim(h, v, side));
        h.runAtTickTime(30, () -> {
            aim(h, v, side);
            handle.hold(USE);
        });
        h.runAtTickTime(32, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(120, () -> {
            boolean turning = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(pressAt));
            LOG.info("water wheel: {} at the press's side, press turning {}, set going {}, hand {}", h.getBlockState(wheelAt), turning,
                    handle.hands().setGoingTotal, v.getMainHandItem());
            h.assertTrue(h.getBlockState(wheelAt).getBlock().asItem() == wheel.get(), "the wheel was not placed at the press's side: " + h.getBlockState(wheelAt));
            h.assertTrue(turning, "the water wheel does not turn the press");
            h.assertTrue(handle.hands().setGoingTotal >= 1, "setting the press going with the wheel was not felt");
            h.succeed();
        });
    }

    /**
     * With Create installed: a steam engine finished by hand, as a player finishes one. A boiler
     * needs water coming in, not water standing in it, and a bucket cannot fill a tank outside
     * creative mode, so the plumbing is there already: a fluid tank on a blaze burner, fed by a
     * pump (turned by a motor through a cogwheel) from a pool. (A magma block's passive heat ran
     * the boiler but turned no engine.) The villager feeds the burner coal, puts a steam engine
     * on the tank's side and a shaft into the engine, and the shaft turns.
     */
    @GameTest(template = ARENA, timeoutTicks = 1100)
    public static void finishesASteamEngineIfCreateIsInstalled(GameTestHelper h) throws ReflectiveOperationException {
        var create = (java.util.function.Function<String, ResourceLocation>) id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var tank = BuiltInRegistries.BLOCK.getOptional(create.apply("fluid_tank"));
        var pump = BuiltInRegistries.BLOCK.getOptional(create.apply("mechanical_pump"));
        var pipe = BuiltInRegistries.BLOCK.getOptional(create.apply("fluid_pipe"));
        var cog = BuiltInRegistries.BLOCK.getOptional(create.apply("cogwheel"));
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var engine = BuiltInRegistries.ITEM.getOptional(create.apply("steam_engine"));
        var shaft = BuiltInRegistries.ITEM.getOptional(create.apply("shaft"));
        if (tank.isEmpty() || pump.isEmpty() || pipe.isEmpty() || cog.isEmpty() || motor.isEmpty() || engine.isEmpty() || shaft.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos tankAt = new BlockPos(6, 3, 12);
        var burner = BuiltInRegistries.BLOCK.getOptional(create.apply("blaze_burner")).orElseThrow();
        h.setBlock(tankAt.below(), withValue(burner.defaultBlockState(), "blaze", "smouldering"));
        // A boiler warms one heat level for every four blocks of tank, so a one-block tank turns
        // nothing: the tank stands four high, as a player's smallest working boiler does.
        for (int y = 0; y < 4; y++) {
            h.setBlock(tankAt.above(y), tank.get());
        }
        BlockPos pumpAt = tankAt.south();
        h.setBlock(pumpAt.below(), Blocks.STONE);
        h.setBlock(pumpAt, pump.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH).rotate(h.getTestRotation()));
        // A pool of springs that fill in again, walled in with glass so it cannot flood the place
        // (loose water washed the villager away), with the pump's pipe running into it.
        for (int x = 4; x <= 8; x++) {
            for (int z = 14; z <= 17; z++) {
                boolean wall = x == 4 || x == 8 || z == 14 || z == 17;
                h.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 3, z), wall ? Blocks.GLASS : Blocks.WATER);
            }
        }
        BlockPos pipeAt = pumpAt.south();
        h.setBlock(pipeAt, pipe.get());
        // Set down as a player places it: joined to the pump, open to the pool.
        BlockPos pipeAbs = h.absolutePos(pipeAt);
        h.getLevel().setBlockAndUpdate(pipeAbs, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(h.getLevel().getBlockState(pipeAbs), h.getLevel(), pipeAbs));
        BlockPos cogAt = pumpAt.east();
        h.setBlock(cogAt, cog.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.Z).rotate(h.getTestRotation()));
        h.setBlock(cogAt.south(), motor.get().defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH).rotate(h.getTestRotation()));
        // A boiler also wants ten units of water a tick for each heat level: the motor runs fast
        // enough for the pump to bring that much.
        Object motorBe = h.getLevel().getBlockEntity(h.absolutePos(cogAt.south()));
        Object speed = motorBe.getClass().getField("generatedSpeed").get(motorBe);
        speed.getClass().getMethod("setValue", int.class).invoke(speed, 128);
        BlockPos engineAt = tankAt.north();
        Villager v = spawnMcaVillager(h, new Vec3(6.5, 2, 8.5));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        Vec3 tankSide = new Vec3(6.5, 3.5, 11.98);
        Vec3 engineFront = new Vec3(6.5, 3.5, 11.3);
        Class<?> engineBlock = Class.forName("com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock");
        java.lang.reflect.Method shaftPos = engineBlock.getMethod("getShaftPos", net.minecraft.world.level.block.state.BlockState.class, BlockPos.class);
        // Where the engine wants its shaft, in the world's own coordinates.
        BlockPos[] shaftAt = {null};
        // The burner's side shows past the tank's: the villager feeds it coal there first.
        Vec3 burnerSide = new Vec3(6.5, 2.5, 11.98);
        Object[][] steps = {{60, burnerSide, new ItemStack(Items.COAL, 8)}, {100, tankSide, new ItemStack(engine.get())},
                {140, engineFront, new ItemStack(shaft.get(), 4)}};
        for (Object[] step : steps) {
            int at = (int) step[0];
            Vec3 aimAt = (Vec3) step[1];
            ItemStack give = (ItemStack) step[2];
            h.runAtTickTime(at - 4, () -> {
                v.setItemSlot(EquipmentSlot.MAINHAND, give);
                aim(h, v, aimAt);
            });
            h.runAtTickTime(at, () -> {
                aim(h, v, aimAt);
                handle.hold(USE);
                LOG.info("steam engine step at tick {}: villager at {} looking {} {}, aimed at {}", at, h.relativeVec(v.position()), v.getYRot(), v.getXRot(), handle.hands().target() instanceof net.minecraft.world.phys.BlockHitResult b
                        ? h.getBlockState(h.relativePos(b.getBlockPos())) + " at " + h.relativePos(b.getBlockPos()) + " face " + b.getDirection() : handle.hands().target());
            });
            h.runAtTickTime(at + 2, () -> handle.hold(MotorCommand.IDLE));
            h.runAtTickTime(at + 10, () -> {
                var state = h.getBlockState(engineAt);
                if (state.getBlock().asItem() == engine.get() && shaftAt[0] == null) {
                    try {
                        shaftAt[0] = ((BlockPos) shaftPos.invoke(null, state, h.absolutePos(engineAt))).immutable();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                LOG.info("steam engine step at tick {}: hand {}, pump turning {}, engine place {}, shaft place {} {}", at, v.getMainHandItem(),
                        com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(pumpAt)), state, shaftAt[0],
                        shaftAt[0] == null ? "" : h.getLevel().getBlockState(shaftAt[0]));
            });
        }
        for (int t = 200; t <= 1000; t += 200) {
            int when = t;
            h.runAtTickTime(t, () -> {
                try {
                    Object be = h.getLevel().getBlockEntity(h.absolutePos(tankAt));
                    Object boiler = be.getClass().getField("boiler").get(be);
                    LOG.info("steam engine boiler at tick {}: burner {}, passive heat {}, active heat {}, water supply {}, shaft turning {}", when,
                            h.getBlockState(tankAt.below()),
                            boiler.getClass().getField("passiveHeat").get(boiler), boiler.getClass().getField("activeHeat").get(boiler),
                            boiler.getClass().getField("waterSupply").get(boiler),
                            shaftAt[0] != null && com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), shaftAt[0]));
                } catch (ReflectiveOperationException e) {
                    LOG.info("steam engine boiler: {}", e.toString());
                }
            });
        }
        h.runAtTickTime(1000, () -> {
            boolean turning = shaftAt[0] != null && com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), shaftAt[0]);
            LOG.info("steam engine: rotation {}, pump {}, pipe {}, engine {}, shaft {} turning {}, set going {}, tank {}", h.getTestRotation(),
                    h.getBlockState(pumpAt), h.getBlockState(pipeAt), h.getBlockState(engineAt),
                    shaftAt[0] == null ? null : h.getLevel().getBlockState(shaftAt[0]), turning, handle.hands().setGoingTotal,
                    h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, h.absolutePos(tankAt), null) instanceof
                            net.neoforged.neoforge.fluids.capability.IFluidHandler f ? f.getFluidInTank(0).getAmount() + " mB" : "?");
            h.assertTrue(h.getBlockState(engineAt).getBlock().asItem() == engine.get(), "the engine is not on the tank's side: " + h.getBlockState(engineAt));
            h.assertTrue(shaftAt[0] != null && !h.getLevel().getBlockState(shaftAt[0]).isAir(), "no shaft went into the engine");
            h.assertTrue(turning, "the steam engine does not turn its shaft");
            h.succeed();
        });
    }

    /**
     * With Create installed: a whole steam boiler built by hand, as a player builds one. Only the
     * stone footing, springs and a motor turning a cogwheel are there. The villager sets a blaze
     * burner on the ground and four fluid tanks over it and the footing, a pump against the tank
     * (sneaking, so it pushes towards the tank, not away), a pipe out to the springs, then feeds the
     * burner coal, puts a steam engine on the tank and a shaft into the engine, and the shaft turns.
     */
    @GameTest(template = ARENA, timeoutTicks = 1600)
    public static void buildsASteamBoilerIfCreateIsInstalled(GameTestHelper h) throws ReflectiveOperationException {
        var create = (java.util.function.Function<String, ResourceLocation>) id -> ResourceLocation.fromNamespaceAndPath("create", id);
        var tank = BuiltInRegistries.ITEM.getOptional(create.apply("fluid_tank"));
        var pump = BuiltInRegistries.ITEM.getOptional(create.apply("mechanical_pump"));
        var pipe = BuiltInRegistries.ITEM.getOptional(create.apply("fluid_pipe"));
        var burner = BuiltInRegistries.ITEM.getOptional(create.apply("blaze_burner"));
        var cog = BuiltInRegistries.BLOCK.getOptional(create.apply("cogwheel"));
        var motor = BuiltInRegistries.BLOCK.getOptional(create.apply("creative_motor"));
        var engine = BuiltInRegistries.ITEM.getOptional(create.apply("steam_engine"));
        var shaft = BuiltInRegistries.ITEM.getOptional(create.apply("shaft"));
        if (tank.isEmpty() || pump.isEmpty() || pipe.isEmpty() || burner.isEmpty() || cog.isEmpty() || motor.isEmpty() || engine.isEmpty()
                || shaft.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        var rotation = h.getTestRotation();
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        // Footing for the three tanks not over the burner.
        for (BlockPos p : new BlockPos[] {new BlockPos(7, 2, 12), new BlockPos(6, 2, 13), new BlockPos(7, 2, 13)}) {
            h.setBlock(p, Blocks.STONE);
        }
        // The motor, set fast enough for the pump to bring the boiler its water, turns a cogwheel
        // beside where the pump goes.
        BlockPos cogAt = new BlockPos(8, 3, 11);
        h.setBlock(cogAt, cog.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X).rotate(rotation));
        h.setBlock(cogAt.east(), motor.get().defaultBlockState().setValue(facing, net.minecraft.core.Direction.WEST).rotate(rotation));
        Object motorBe = h.getLevel().getBlockEntity(h.absolutePos(cogAt.east()));
        Object speed = motorBe.getClass().getField("generatedSpeed").get(motorBe);
        speed.getClass().getMethod("setValue", int.class).invoke(speed, 128);
        // Springs beside where the pump goes, six of them so they fill in again as the pump draws,
        // walled in glass; one runs out west into the gap the pipe will fill. A slab, low enough to
        // see the pipe's place over, keeps the water from the gap's south side.
        for (int x = 9; x <= 12; x++) {
            for (int z = 10; z <= 14; z++) {
                BlockPos p = new BlockPos(x, 3, z);
                boolean spring = x >= 10 && x <= 11 && z >= 11 && z <= 13;
                if (spring) {
                    h.setBlock(p.below(), Blocks.STONE);
                    h.setBlock(p, Blocks.WATER);
                } else if (x >= 10) {
                    h.setBlock(p, Blocks.GLASS);
                }
            }
        }
        h.setBlock(new BlockPos(9, 3, 13), Blocks.STONE_SLAB);
        // The water running into the gap reaches the pump's place too: floored and walled in.
        h.setBlock(new BlockPos(9, 2, 12), Blocks.STONE);
        h.setBlock(new BlockPos(8, 2, 12), Blocks.STONE);
        h.setBlock(new BlockPos(8, 3, 13), Blocks.GLASS);
        Villager v = spawnMcaVillager(h, new Vec3(6.5, 2, 11.2));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        MotorCommand sneakUse = new MotorCommand(0, 0, false, true, false, 0f, 0f, false, true);
        Vec3 front = new Vec3(6.5, 2, 8.5);
        // Each step: where it stands, where it looks, what it holds, whether it sneaks.
        Object[][] steps = {
                {new Vec3(6.5, 2, 11.2), new Vec3(6.5, 1.98, 12.5), new ItemStack(burner.get()), false},
                {new Vec3(6.5, 2, 11.2), new Vec3(6.5, 2.99, 13.5), new ItemStack(tank.get(), 4), false},
                {new Vec3(7.5, 2, 11.2), new Vec3(7.5, 2.99, 13.5), new ItemStack(tank.get(), 3), false},
                {new Vec3(6.5, 2, 11.2), new Vec3(6.5, 2.86, 12.5), new ItemStack(tank.get(), 2), false},
                {new Vec3(7.5, 2, 11.2), new Vec3(7.5, 2.99, 12.5), new ItemStack(tank.get(), 1), false},
                {new Vec3(9.6, 3, 12.5), new Vec3(8.02, 3.5, 12.5), new ItemStack(pump.get()), true},
                {new Vec3(9.5, 2, 14.5), new Vec3(8.98, 3.7, 12.5), new ItemStack(pipe.get()), false},
                {front, new Vec3(6.5, 2.5, 11.98), new ItemStack(Items.COAL, 8), false},
                {front, new Vec3(6.5, 3.5, 11.98), new ItemStack(engine.get()), false},
                {front, new Vec3(6.5, 3.5, 11.3), new ItemStack(shaft.get(), 4), false}};
        BlockPos engineAt = new BlockPos(6, 3, 11);
        Class<?> engineBlock = Class.forName("com.simibubi.create.content.kinetics.steamEngine.SteamEngineBlock");
        java.lang.reflect.Method shaftPos = engineBlock.getMethod("getShaftPos", net.minecraft.world.level.block.state.BlockState.class, BlockPos.class);
        BlockPos[] shaftAt = {null};
        for (int i = 0; i < steps.length; i++) {
            int at = 40 + 40 * i;
            Vec3 standAt = (Vec3) steps[i][0];
            Vec3 aimAt = (Vec3) steps[i][1];
            ItemStack give = (ItemStack) steps[i][2];
            boolean sneak = (boolean) steps[i][3];
            h.runAtTickTime(at - 4, () -> {
                Vec3 to = h.absoluteVec(standAt);
                v.teleportTo(to.x, to.y, to.z);
                v.setItemSlot(EquipmentSlot.MAINHAND, give.copy());
                aim(h, v, aimAt);
            });
            h.runAtTickTime(at, () -> {
                aim(h, v, aimAt);
                handle.hold(sneak ? sneakUse : USE);
                LOG.info("boiler build: aimed at {}", handle.hands().target() instanceof net.minecraft.world.phys.BlockHitResult b
                        ? h.getLevel().getBlockState(b.getBlockPos()) + " at " + h.relativePos(b.getBlockPos()) + " face " + b.getDirection() : handle.hands().target());
            });
            h.runAtTickTime(at + 2, () -> handle.hold(MotorCommand.IDLE));
            h.runAtTickTime(at + 10, () -> {
                StringBuilder built = new StringBuilder();
                for (BlockPos p : new BlockPos[] {new BlockPos(6, 2, 12), new BlockPos(6, 3, 12), new BlockPos(7, 3, 12), new BlockPos(6, 3, 13),
                        new BlockPos(7, 3, 13), new BlockPos(8, 3, 12), new BlockPos(9, 3, 12), new BlockPos(10, 3, 12), engineAt}) {
                    var state = h.getBlockState(p);
                    if (!state.isAir()) {
                        built.append(' ').append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ()).append('=').append(state);
                    }
                }
                var state = h.getBlockState(engineAt);
                if (state.getBlock().asItem() == engine.get() && shaftAt[0] == null) {
                    try {
                        shaftAt[0] = ((BlockPos) shaftPos.invoke(null, state, h.absolutePos(engineAt))).immutable();
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
                LOG.info("boiler build: after aiming at {}, hand {}; built{}", aimAt, v.getMainHandItem(), built);
            });
        }
        int end = 40 + 40 * steps.length + 300;
        h.runAtTickTime(end, () -> {
            boolean turning = shaftAt[0] != null && com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), shaftAt[0]);
            String boiler = "?";
            try {
                Object be = h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(6, 3, 12)));
                Object b = be.getClass().getField("boiler").get(be);
                boiler = "active heat " + b.getClass().getField("activeHeat").get(b) + ", water supply " + b.getClass().getField("waterSupply").get(b);
            } catch (ReflectiveOperationException | NullPointerException e) {
                boiler = e.toString();
            }
            LOG.info("boiler build: rotation {}, {}, engine {}, shaft {} turning {}", rotation, boiler, h.getBlockState(engineAt),
                    shaftAt[0] == null ? null : h.getLevel().getBlockState(shaftAt[0]), turning);
            h.assertTrue(turning, "the steam engine on the boiler it built does not turn its shaft");
            h.succeed();
        });
    }

    /**
     * With Create installed: a first piece of automation, set up by hand. A motor turns a shaft
     * ending in the open; the villager sets a mechanical drill down at the shaft's end, where it
     * takes the shaft's turning and faces away from it, onto a block of stone that grows back as a
     * cobblestone generator's would. The drill then mines it again and again with no hand on it.
     */
    @GameTest(template = ARENA, timeoutTicks = 800)
    public static void setsUpADrillThatMinesOnItsOwnIfCreateIsInstalled(GameTestHelper h) throws ReflectiveOperationException {
        var motor = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        var shaft = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        var drill = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "mechanical_drill"));
        if (motor.isEmpty() || shaft.isEmpty() || drill.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        var rotation = h.getTestRotation();
        BlockPos motorAt = new BlockPos(4, 2, 12);
        h.setBlock(motorAt, motor.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST).rotate(rotation));
        h.setBlock(motorAt.east(), shaft.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X).rotate(rotation));
        Object motorBe = h.getLevel().getBlockEntity(h.absolutePos(motorAt));
        Object speed = motorBe.getClass().getField("generatedSpeed").get(motorBe);
        speed.getClass().getMethod("setValue", int.class).invoke(speed, 64);
        BlockPos drillAt = motorAt.east(2);
        BlockPos rock = drillAt.east();
        h.setBlock(rock, Blocks.STONE);
        Villager v = spawnMcaVillager(h, new Vec3(6.5, 2, 9.5));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(drill.get()));
        Vec3 floor = new Vec3(6.5, 1.98, 12.5);
        h.runAtTickTime(20, () -> aim(h, v, floor));
        h.runAtTickTime(24, () -> {
            aim(h, v, floor);
            handle.hold(USE);
        });
        h.runAtTickTime(26, () -> handle.hold(MotorCommand.IDLE));
        int[] mined = {0};
        int[] tick = {0};
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] > 30 && tick[0] % 10 == 0 && h.getBlockState(rock).isAir()) {
                mined[0]++;
                h.setBlock(rock, Blocks.STONE);
            }
        });
        h.runAtTickTime(700, () -> {
            LOG.info("drill: rotation {}, drill {} turning {}, mined {} times, set going {}", rotation, h.getBlockState(drillAt),
                    com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(drillAt)), mined[0], handle.hands().setGoingTotal);
            h.assertTrue(mined[0] >= 3, "the drill mined the stone " + mined[0] + " times");
            h.succeed();
        });
    }

    /**
     * With Create installed: a windmill finished by hand, as a player finishes one. A windmill
     * bearing stands ready; the villager sets a sail on its face, then sails on that sail's edges
     * (a sail used on a sail goes beside it, in the same plane) until nine make a square, and
     * starts it with an empty hand on the bearing's side. Sails hold to each other in their own
     * plane, so no glue is needed.
     */
    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void finishesAWindmillIfCreateIsInstalled(GameTestHelper h) {
        var bearing = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "windmill_bearing"));
        var sail = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("create", "white_sail"));
        if (bearing.isEmpty() || sail.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos bearingAt = new BlockPos(6, 3, 12);
        h.setBlock(bearingAt, bearing.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH)
                .rotate(h.getTestRotation()));
        Villager v = spawnMcaVillager(h, new Vec3(6.5, 2, 8.5));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        // The bearing's face first, then the edges of the sails already there: the middle one's
        // four, then the left and right of the top and bottom ones.
        Vec3[] aims = {new Vec3(6.5, 3.5, 12.02),
                new Vec3(6.5, 3.9, 11.5), new Vec3(6.5, 3.1, 11.5), new Vec3(6.1, 3.5, 11.5), new Vec3(6.9, 3.5, 11.5),
                new Vec3(6.1, 4.5, 11.5), new Vec3(6.9, 4.5, 11.5), new Vec3(6.1, 2.5, 11.5), new Vec3(6.9, 2.5, 11.5)};
        int[] sails = {0};
        for (int i = 0; i < aims.length; i++) {
            int at = 20 + 20 * i;
            Vec3 aimAt = aims[i];
            h.runAtTickTime(at - 4, () -> {
                if (!v.getMainHandItem().is(sail.get())) {
                    v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(sail.get(), 9));
                }
                aim(h, v, aimAt);
            });
            h.runAtTickTime(at, () -> {
                aim(h, v, aimAt);
                handle.hold(USE);
            });
            h.runAtTickTime(at + 2, () -> handle.hold(MotorCommand.IDLE));
            h.runAtTickTime(at + 10, () -> {
                int n = 0;
                for (int x = 4; x <= 8; x++) {
                    for (int y = 1; y <= 5; y++) {
                        n += h.getBlockState(new BlockPos(x, y, 11)).getBlock().asItem() == sail.get() ? 1 : 0;
                    }
                }
                sails[0] = n;
                LOG.info("windmill: after aiming at {}, {} sails up, hand {}", aimAt, n, v.getMainHandItem());
            });
        }
        // Round to the bearing's side, where it shows past the sails, to start it by hand.
        int start = 20 + 20 * aims.length;
        Vec3 bearingSide = new Vec3(5.98, 3.5, 12.5);
        h.runAtTickTime(start - 10, () -> {
            v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            v.teleportTo(h.absoluteVec(new Vec3(4.5, 2, 12.5)).x, h.absoluteVec(new Vec3(4.5, 2, 12.5)).y, h.absoluteVec(new Vec3(4.5, 2, 12.5)).z);
        });
        h.runAtTickTime(start - 4, () -> aim(h, v, bearingSide));
        h.runAtTickTime(start, () -> {
            aim(h, v, bearingSide);
            handle.hold(USE);
        });
        h.runAtTickTime(start + 2, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(start + 60, () -> {
            boolean turning = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(bearingAt));
            LOG.info("windmill: rotation {}, {} sails, bearing {} turning {}, set going {}", h.getTestRotation(), sails[0],
                    h.getBlockState(bearingAt), turning, handle.hands().setGoingTotal);
            h.assertTrue(sails[0] >= 8, "only " + sails[0] + " sails went up");
            h.assertTrue(turning, "the windmill does not turn");
            h.succeed();
        });
    }

    /**
     * With Create installed: holding use on a hand crank turns it, and the shaft behind it, as a
     * player's held right button does. The shaft it drives is felt set going; the crank itself,
     * turning only under the hand, is not.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void turnsAHandCrankIfCreateIsInstalled(GameTestHelper h) {
        var crank = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "hand_crank"));
        var shaft = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        if (crank.isEmpty() || shaft.isEmpty()) {
            LOG.info("Create is not installed: nothing to check");
            h.succeed();
            return;
        }
        BlockPos at = new BlockPos(5, 2, 12);
        // The crank faces the villager, west, and turns the shaft behind it.
        h.setBlock(at, crank.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.WEST));
        h.setBlock(at.east(), shaft.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
        Villager v = worker(h, ItemStack.EMPTY);
        BrainManager.Handle handle = handle(v);
        Vec3 aimAt = new Vec3(5.5, 2.5, 12.5);
        h.runAtTickTime(8, () -> aim(h, v, aimAt));
        h.runAtTickTime(10, () -> {
            aim(h, v, aimAt);
            handle.hold(USE);
        });
        h.runAtTickTime(30, () -> {
            boolean turning = com.mementocraft.reallyalive.body.Machines.turning(h.getLevel(), h.absolutePos(at.east()));
            LOG.info("hand crank held: shaft turning {}, set going {}", turning, handle.hands().setGoingTotal);
            handle.hold(MotorCommand.IDLE);
            h.assertTrue(turning, "the shaft behind the crank is not turning");
            h.assertTrue(handle.hands().setGoingTotal == 1, "the shaft the crank turned was felt set going "
                    + handle.hands().setGoingTotal + " times, not once");
            h.succeed();
        });
    }

    /** A block state with a property set by name, for blocks of mods the tests do not link against. */
    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState withValue(
            net.minecraft.world.level.block.state.BlockState state, String property, String value) {
        @SuppressWarnings("unchecked")
        var p = (net.minecraft.world.level.block.state.properties.Property<T>) state.getBlock().getStateDefinition().getProperty(property);
        return p == null ? state : p.getValue(value).map(v -> state.setValue(p, v)).orElse(state);
    }

    private static int count(Villager v, net.minecraft.world.item.Item item) {
        int n = v.getMainHandItem().is(item) ? v.getMainHandItem().getCount() : 0;
        for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
            ItemStack s = v.getInventory().getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }
}
