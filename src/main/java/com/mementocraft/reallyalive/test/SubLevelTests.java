package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * With Sable installed (Create Aeronautics' sub-levels; never required), a brain villager stands on
 * a sub-level, its crosshair finds the sub-level's blocks and a block it places lands on the
 * sub-level, as for a player. Sable is reached by reflection so the mod never links against it.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class SubLevelTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final MotorCommand USE = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true);

    private SubLevelTests() {}

    /** Makes the blocks a sub-level, as Aeronautics' physics assembler does; null if Sable is missing. */
    private static Object assemble(ServerLevel level, BlockPos anchor, List<BlockPos> blocks) throws ReflectiveOperationException {
        Class<?> box = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3i");
        Object bounds = box.getMethod("from", Iterable.class).invoke(null, blocks);
        Class<?> helper = Class.forName("dev.ryanhcode.sable.api.SubLevelAssemblyHelper");
        Class<?> boxApi = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3ic");
        return helper.getMethod("assembleBlocks", ServerLevel.class, BlockPos.class, Iterable.class, boxApi)
                .invoke(null, level, anchor, blocks, bounds);
    }

    /** The sub-level a block position belongs to, or null for the world itself. */
    private static Object subLevelAt(Level level, BlockPos pos) throws ReflectiveOperationException {
        Class<?> sable = Class.forName("dev.ryanhcode.sable.Sable");
        Object companion = sable.getField("HELPER").get(null);
        return companion.getClass().getMethod("getContaining", Level.class, Vec3i.class).invoke(companion, level, pos);
    }

    private static void aim(Villager v, Vec3 at) {
        Vec3 to = at.subtract(v.getEyePosition());
        float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(to.y, to.horizontalDistance()) * Mth.RAD_TO_DEG);
        v.setYRot(yaw);
        v.setYHeadRot(yaw);
        v.setYBodyRot(yaw);
        v.setXRot(pitch);
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void standsOnSeesAndBuildsOnASubLevelIfSableIsInstalled(GameTestHelper h) throws ReflectiveOperationException {
        if (!ModList.get().isLoaded("sable")) {
            LOG.info("Sable is not installed: nothing to check");
            h.succeed();
            return;
        }
        // A 5x5 plank raft resting on the arena floor.
        List<BlockPos> raft = new ArrayList<>();
        for (int x = 3; x <= 7; x++) {
            for (int z = 10; z <= 14; z++) {
                BlockPos at = new BlockPos(x, 2, z);
                h.setBlock(at, Blocks.OAK_PLANKS);
                raft.add(h.absolutePos(at));
            }
        }
        Object subLevel = assemble(h.getLevel(), h.absolutePos(new BlockPos(5, 2, 12)), raft);
        h.assertTrue(subLevel != null, "Sable did not make the raft a sub-level");
        LOG.info("Raft assembled as {}", subLevel);

        Villager v = spawnMcaVillager(h, new Vec3(4.5, 3.05, 12.5));
        v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 4));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        Vec3 deckAhead = h.absoluteVec(new Vec3(6.5, 3.0, 12.5));
        BlockHitResult[] aimed = new BlockHitResult[1];

        h.runAtTickTime(40, () -> {
            double feet = v.getY() - h.absoluteVec(Vec3.ZERO).y;
            LOG.info("Villager on the raft at relative y {} (deck top 3.0), on ground {}", feet, v.onGround());
            h.assertTrue(feet > 2.9, "the villager fell through the raft: y " + feet);
            aim(v, deckAhead);
        });
        h.runAtTickTime(42, () -> {
            aim(v, deckAhead);
            HitResult target = handle.hands().target();
            LOG.info("Crosshair on the raft: {}", target);
            if (!(target instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
                h.fail("the crosshair did not find the raft: " + target);
                return;
            }
            try {
                h.assertTrue(subLevelAt(h.getLevel(), hit.getBlockPos()) != null,
                        "the crosshair found a block outside the sub-level: " + hit.getBlockPos());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            h.assertTrue(h.getLevel().getBlockState(hit.getBlockPos()).is(Blocks.OAK_PLANKS),
                    "the crosshair is on " + h.getLevel().getBlockState(hit.getBlockPos()));
            aimed[0] = hit;
            handle.hold(USE);
        });
        h.runAtTickTime(44, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(50, () -> {
            BlockPos placed = aimed[0].getBlockPos().relative(aimed[0].getDirection());
            LOG.info("After placing: {} at {}, hand {}", h.getLevel().getBlockState(placed), placed, v.getMainHandItem());
            h.assertTrue(h.getLevel().getBlockState(placed).is(Blocks.COBBLESTONE),
                    "the cobblestone did not land on the raft at " + placed);
            h.assertTrue(handle.hands().placedTotal == 1, "placing on the raft did not count as placing, so it would not feel good");
            try {
                h.assertTrue(subLevelAt(h.getLevel(), placed) != null, "the placed block is not on the sub-level");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            h.succeed();
        });
    }
}
