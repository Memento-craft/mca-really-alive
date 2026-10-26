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
        private final List<Object> steps = new ArrayList<>();

        /** Moves to the first slot matching, pressing a button there: 0 left, 1 right, 2 shift-left. */
        MouseScript click(Predicate<Slot> where, int button) {
            steps.add(where);
            steps.add(button);
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
                @SuppressWarnings("unchecked")
                Predicate<Slot> where = (Predicate<Slot>) step;
                int button = (Integer) steps.get(at[0] + 1);
                Hands hands = handle.hands();
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
