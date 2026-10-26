package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.WordCode;
import com.mementocraft.reallyalive.speech.Speech;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.mementocraft.reallyalive.test.McaVillagerTests.attach;
import static com.mementocraft.reallyalive.test.McaVillagerTests.spawnMcaVillager;

/**
 * In-game checks of what villagers keep of each other: a villager knows the crops it harvested
 * itself, sees a death and remembers it, may choose to tell it to a villager near, and answers a
 * player asking about the dead. Nothing is kept for them anywhere but in their brains.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class KnowledgeTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";

    private KnowledgeTests() {}

    /** First name as a villager says it. */
    private static String first(Villager v) {
        return WordCode.tokenize(v.getCustomName().getString()).get(0);
    }

    private static Villager named(GameTestHelper h, Vec3 at, String name) {
        Villager v = spawnMcaVillager(h, at);
        v.setCustomName(Component.literal(name));
        return v;
    }

    /** Breaking a ripe crop, however it is broken, is counted by the villager that broke it; an unripe one is not. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void villagerCountsTheCropsItHarvests(GameTestHelper h) {
        Villager v = named(h, new Vec3(5.5, 2, 5.5), "Misu");
        BrainManager.Handle handle = attach(h, v);
        BlockPos ripe = new BlockPos(6, 2, 7);
        BlockPos green = new BlockPos(7, 2, 7);
        h.setBlock(ripe.below(), Blocks.FARMLAND);
        h.setBlock(green.below(), Blocks.FARMLAND);
        h.setBlock(ripe, ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        h.setBlock(green, ((CropBlock) Blocks.WHEAT).getStateForAge(2));
        h.runAfterDelay(10, () -> {
            h.getLevel().destroyBlock(h.absolutePos(ripe), true, v);
            h.getLevel().destroyBlock(h.absolutePos(green), true, v);
        });
        h.runAfterDelay(40, () -> {
            BrainManager.get().setLockstep(true);
            Integer wheat = handle.brain().deeds().get("wheat");
            LOG.info("harvest: villager counted {}", handle.brain().deeds());
            h.assertTrue(wheat != null && wheat == 1, "counted " + handle.brain().deeds() + ", not 1 wheat");
            h.succeed();
        });
    }

    /**
     * A villager that sees another die remembers the death and the dead by name; asked "what
     * happened to misu?" it says so. Runs in its own batch, away from other tests' chatter.
     */
    @GameTest(template = ARENA, timeoutTicks = 1200, batch = "knowledge")
    public static void villagerRemembersADeathItSaw(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager witness = named(h, new Vec3(10.5, 2, 10.5), "Tomas");
        Villager dying = named(h, new Vec3(13.5, 2, 10.5), "Misu");
        BrainManager.Handle handle = attach(h, witness);
        String name = first(dying);
        List<String> answers = new ArrayList<>();
        int[] seen = {0};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 20) {
                witness.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dying.getEyePosition());
                dying.kill();
            }
            if (t == 60) {
                List<String> kept = handle.brain().recollectionsOf(name);
                LOG.info("death: the witness remembers of {}: {}", name, kept);
                h.assertTrue(kept.contains(name + " died ."), "the witness keeps " + kept);
            }
            if (t == 80) {
                Speech.playerSays(h.getLevel(), witness.getEyePosition().add(1.5, 0, 0), "what happened to " + name + "?", "Steve");
            }
            if (t > 80 && handle.lines() > seen[0]) {
                seen[0] = handle.lines();
                answers.add(handle.lastLine());
            }
            if (t == 600) {
                LOG.info("death: asked what happened to {}, the witness said {}", name, answers);
                h.assertTrue(answers.stream().anyMatch(a -> a.contains("died")), "asked, it said " + answers);
                h.succeed();
            }
        });
    }

    /**
     * Passing it on: a villager who saw a death, standing with another, chooses in its own time to
     * tell it, and the other then remembers it too. How soon is up to its brain, so this is a
     * benchmark, with the urge to tell raised so it needn't take a game day.
     */
    @GameTest(template = ARENA, timeoutTicks = 4800, batch = "knowledge_told", required = false)
    public static void villagerTellsOfADeath(GameTestHelper h) {
        BrainManager.get().setLockstep(true);
        Villager witness = named(h, new Vec3(10.5, 2, 10.5), "Tomas");
        Villager dying = named(h, new Vec3(13.5, 2, 10.5), "Misu");
        BrainManager.Handle teller = attach(h, witness);
        teller.brain().setTellRate(0.3f);
        String name = first(dying);
        List<String> said = new ArrayList<>();
        int[] seen = {0};
        // The listener comes along only after the death, so it can know of it only by being told.
        BrainManager.Handle[] heard = {null};
        h.onEachTick(() -> {
            long t = h.getTick();
            if (t == 20) {
                witness.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dying.getEyePosition());
                dying.kill();
            }
            if (t == 60) {
                heard[0] = attach(h, named(h, new Vec3(10.5, 2, 12.5), "Lia"));
            }
            BrainManager.Handle hearer = heard[0];
            if (hearer == null) {
                return;
            }
            if (teller.lines() > seen[0]) {
                seen[0] = teller.lines();
                said.add(teller.lastLine());
            }
            if (hearer.brain().recollectionsOf(name).stream().anyMatch(l -> l.contains("died"))) {
                LOG.info("told: after {} ticks the listener knows {}; the teller said {}", t,
                        hearer.brain().recollectionsOf(name), said);
                h.succeed();
            }
            if (t == 4700) {
                LOG.info("told: the teller said {} and the listener knows {}", said, hearer.brain().recollectionsOf(name));
                h.fail("the death was not passed on");
            }
        });
    }
}
