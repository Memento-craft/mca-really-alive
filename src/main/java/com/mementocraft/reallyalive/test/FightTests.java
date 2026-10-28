package com.mementocraft.reallyalive.test;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.ReallyAlive;
import com.mementocraft.reallyalive.brain.FeelChannel;
import com.mementocraft.reallyalive.brain.MotorCommand;
import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.block.Blocks;
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
 * Fights. The required tests prove the body can fight as a player does: with a player's keys a
 * villager beats a zombie, a blow is felt from the side it came from and makes the one who struck
 * it a threat, a shield blocks and a bow shoots. The benchmarks ({@code required = false}) let a
 * villager's own brain fight, bout after bout, against a zombie, a skeleton, another brain villager
 * (both learning) and a sparring player, and report how its fighting changes as it learns.
 *
 * <p>A bout ends when the foe dies (a win), when the villager is down to {@link #LOSS_HEALTH} (a
 * loss: it is healed, so it never dies and loses its brain), or after {@link #BOUT_TICKS}.
 */
@GameTestHolder(ReallyAlive.MODID)
@PrefixGameTestTemplate(false)
public final class FightTests {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String ARENA = "arena";
    static final int BOUT_TICKS = 400;
    /** Above the hardest blow in these fights (a stone sword's critical hit, 7.5), so no one dies. */
    static final float LOSS_HEALTH = 8f;
    private static final int BOUTS = 300;
    private static final int WINDOW = 25;
    /** Where the villager starts each bout, and its foe, five blocks apart in the pen. */
    private static final Vec3 HOME = new Vec3(2.5, 2, 12.5);
    private static final Vec3 FOE = new Vec3(7.5, 2, 12.5);

    private FightTests() {}

    /**
     * A 9 by 9 bedrock floor walled and roofed in bedrock, so it cannot dig out, nothing burns in
     * the sun and nothing wanders off.
     */
    static void pen(GameTestHelper h) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 7; z <= 17; z++) {
                boolean wall = x == 0 || x == 10 || z == 7 || z == 17;
                h.setBlock(x, 0, z, Blocks.BEDROCK);
                h.setBlock(x, 1, z, Blocks.BEDROCK);
                for (int y = 2; y <= 4; y++) {
                    h.setBlock(x, y, z, wall ? Blocks.BEDROCK : Blocks.AIR);
                }
                h.setBlock(x, 5, z, Blocks.BEDROCK);
            }
        }
    }

    static ItemStack unbreakable(net.minecraft.world.item.Item item) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        return s;
    }

    /** An adult brain villager at home, facing east, a stone sword in hand. */
    private static Villager fighter(GameTestHelper h, Vec3 at) {
        Villager v = spawnMcaVillager(h, at);
        v.getInventory().clearContent();
        v.setItemSlot(EquipmentSlot.MAINHAND, unbreakable(Items.STONE_SWORD));
        return v;
    }

    /**
     * What a plain player presses to fight: turn to face the foe, walk at it until in reach, and
     * click whenever the attack has recharged (a fresh press each time, as a click is).
     */
    static MotorCommand playerKeys(Villager v, BrainManager.Handle handle, Entity foe, boolean wasAttacking) {
        Vec3 to = foe.getBoundingBox().getCenter().subtract(v.getEyePosition());
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90f - v.getYRot());
        float pitch = (float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG) - v.getXRot();
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        boolean ready = handle.hands().attackRecharge() >= 0.95f && handle.hands().targetInReach();
        return new MotorCommand(flat > 2.2 ? 1 : 0, 0, false, false, false,
                Mth.clamp(yaw, -24f, 24f), Mth.clamp(pitch, -16f, 16f), ready && !wasAttacking, false);
    }

    // ------------------------------------------------------------------ required: the body can fight

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void playerKeysBeatAZombie(GameTestHelper h) {
        pen(h);
        Villager v = fighter(h, HOME);
        BrainManager.Handle handle = attach(h, v);
        Mob zombie = foe(h, Foe.ZOMBIE);
        boolean[] attacking = {false};
        h.onEachTick(() -> {
            if (zombie.isAlive()) {
                MotorCommand keys = playerKeys(v, handle, zombie, attacking[0]);
                attacking[0] = keys.attack();
                handle.hold(keys);
            } else {
                handle.hold(MotorCommand.IDLE);
            }
        });
        h.succeedWhen(() -> {
            h.assertFalse(zombie.isAlive(), "the zombie still stands");
            h.assertTrue(v.isAlive(), "the villager died");
            h.assertTrue(handle.hands().hitsOnThreats > 0, "no blow on the zombie counted as a hit on a threat");
            h.assertTrue(handle.hands().defeatedTotal == 1, "the win was not felt: " + handle.hands().defeatedTotal);
            LOG.info("Player keys beat a zombie at tick {}: {} hits, mean share {}, villager health {}", h.getTick(),
                    handle.hands().hitsOnThreats, handle.hands().hitShareTotal / handle.hands().hitsOnThreats, v.getHealth());
        });
    }

    /**
     * A player who hits a villager from its left is felt on the left, becomes a threat, and is
     * seen as a hazard; a player who never hit it is none.
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void aBlowIsFeltFromItsSideAndMakesAThreat(GameTestHelper h) {
        pen(h);
        BrainManager.get().setLockstep(true);
        Villager v = fighter(h, new Vec3(5.5, 2, 12.5));
        BrainManager.Handle handle = attach(h, v);
        handle.hold(MotorCommand.IDLE);
        SparringPartner p = partner(h, new Vec3(5.5, 2, 10.5));
        SparringPartner bystander = partner(h, new Vec3(3.5, 2, 14.5));
        // Facing east (-90), north (-z) is on its left.
        h.runAtTickTime(5, () -> {
            v.setYRot(EAST);
            v.setYHeadRot(EAST);
            p.attack(v);
        });
        h.runAtTickTime(7, () -> {
            float left = handle.frame().feel[FeelChannel.HURT_LEFT.ordinal()];
            float right = handle.frame().feel[FeelChannel.HURT_RIGHT.ordinal()];
            h.assertTrue(left > 0.5f && right == 0f, "blow from the left felt left " + left + ", right " + right);
            long now = h.getLevel().getGameTime();
            h.assertTrue(handle.hands().threats.isThreat(p, now), "the player who hit it is no threat");
            h.assertFalse(handle.hands().threats.isThreat(bystander, now), "a bystander is a threat");
        });
        h.runAtTickTime(20, () -> {
            // Once the knockback has settled, the one who hit it steps in front of it: a threat in
            // reach, in the middle of the view.
            v.setDeltaMovement(Vec3.ZERO);
            Vec3 ahead = v.position().add(2.0, 0, 0);
            p.moveTo(ahead.x, ahead.y, ahead.z, 90f, 0f);
            v.setXRot(10f);
        });
        h.runAtTickTime(23, () -> {
            h.assertTrue(handle.frame().feel[FeelChannel.THREAT_IN_REACH.ordinal()] > 0.5f, "the threat in the crosshair is not felt: aiming at "
                    + handle.hands().target() + " with yaw " + v.getYRot() + ", pitch " + v.getXRot() + "; partner at " + p.position()
                    + ", villager at " + v.position());
            p.discard();
            bystander.discard();
            h.succeed();
        });
    }

    /** Use held with a shield raises it and blocks; with a bow and arrows, drawn and let go, it shoots. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void shieldBlocksAndBowShoots(GameTestHelper h) {
        pen(h);
        Villager v = spawnMcaVillager(h, new Vec3(3.5, 2, 12.5));
        v.getInventory().clearContent();
        v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        BrainManager.Handle handle = attach(h, v);
        MotorCommand use = new MotorCommand(0, 0, false, false, false, 0f, 0f, false, true);
        handle.hold(use);
        h.runAtTickTime(15, () -> {
            h.assertTrue(v.isBlocking(), "holding use with a shield does not block");
            handle.hold(MotorCommand.IDLE);
        });
        h.runAtTickTime(17, () -> {
            h.assertFalse(v.isBlocking(), "the shield did not come down");
            v.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            v.getInventory().addItem(new ItemStack(Items.ARROW, 4));
            handle.hold(use);
        });
        h.runAtTickTime(45, () -> handle.hold(MotorCommand.IDLE));
        h.runAtTickTime(48, () -> {
            List<AbstractArrow> arrows = h.getLevel().getEntitiesOfClass(AbstractArrow.class, v.getBoundingBox().inflate(16));
            h.assertTrue(!arrows.isEmpty(), "no arrow was shot");
            h.assertTrue(arrows.get(0).getOwner() == v, "the arrow is not the villager's: " + arrows.get(0).getOwner());
            h.assertTrue(v.getInventory().countItem(Items.ARROW) == 3, "arrows left " + v.getInventory().countItem(Items.ARROW));
            h.assertTrue(handle.hands().shotsTotal == 1, "shots counted " + handle.hands().shotsTotal);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ benchmarks: learned fighting

    /** What the villager fights. */
    enum Foe {
        ZOMBIE, SKELETON
    }

    static Mob foe(GameTestHelper h, Foe kind) {
        Mob m = switch (kind) {
            case ZOMBIE -> h.spawn(EntityType.ZOMBIE, FOE);
            case SKELETON -> h.spawn(EntityType.SKELETON, FOE);
        };
        m.setCanPickUpLoot(false);
        m.setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            m.setItemSlot(slot, ItemStack.EMPTY);
        }
        if (m instanceof AbstractSkeleton s) {
            s.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            s.reassessWeaponGoal();
        }
        m.setYRot(90f);
        return m;
    }

    static SparringPartner partner(GameTestHelper h, Vec3 at) {
        SparringPartner p = new SparringPartner(h.getLevel(), "Sparring" + (int) (at.x * 10 + at.z));
        Vec3 abs = h.absoluteVec(at);
        p.moveTo(abs.x, abs.y, abs.z, 90f, 0f);
        p.arm(unbreakable(Items.WOODEN_SWORD));
        h.getLevel().addNewPlayer(p);
        p.settleIn();
        return p;
    }

    /** Tallies for one window of bouts. */
    static final class Tally {
        int bouts;
        int wins;
        int losses;
        int hits;
        float share;
        float dealt;
        float taken;
        int swings;
        int shieldTicks;
        int unprovoked;
        int inReach;
        int useTicks;
        int readyInReach;

        String line() {
            return String.format("%d bouts: %d won, %d lost, %d drawn; %.1f hits a bout, mean blow %.2f of full, %.1f damage dealt"
                            + " and %.1f taken a bout, %d swings at creatures, %d ticks blocking (use held %d ticks); a threat in reach %d ticks, %d of them ready",
                    bouts, wins, losses, bouts - wins - losses, hits / (float) Math.max(1, bouts),
                    share / Math.max(1, hits), dealt / Math.max(1, bouts), taken / Math.max(1, bouts), swings, shieldTicks,
                    useTicks, inReach, readyInReach);
        }
    }

    private static void heal(Villager v, Vec3 at, GameTestHelper h) {
        Vec3 abs = h.absoluteVec(at);
        v.teleportTo(abs.x, abs.y, abs.z);
        v.setDeltaMovement(Vec3.ZERO);
        v.setHealth(v.getMaxHealth());
        v.clearFire();
        v.removeAllEffects();
        v.getInventory().clearContent();
        if (!v.getMainHandItem().is(Items.STONE_SWORD)) {
            v.setItemSlot(EquipmentSlot.MAINHAND, unbreakable(Items.STONE_SWORD));
        }
    }

    /**
     * Heals it and gives back its shield too: babbling in its inventory moved the shield out of
     * its off hand, healing cleared the inventory, and it fought every later bout without one.
     */
    private static void heal(Villager v, Vec3 at, GameTestHelper h, boolean shield) {
        heal(v, at, h);
        if (shield && !v.getOffhandItem().is(Items.SHIELD)) {
            v.setItemSlot(EquipmentSlot.OFFHAND, unbreakable(Items.SHIELD));
        }
    }

    /**
     * Bout after bout of a brain villager against a monster. With {@code playerKeys} a plain
     * player's keys fight instead: the yardstick the brain is measured against.
     */
    private static void bouts(GameTestHelper h, String name, Foe kind, boolean shield, boolean playerKeys, int bouts) {
        if (!WorkLearningTests.selected(h, name)) {
            return;
        }
        pen(h);
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(bouts * BOUT_TICKS + 400);
        Villager v = fighter(h, HOME);
        if (shield) {
            v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
        BrainManager.Handle handle = attach(h, v);
        var hands = handle.hands();
        LOG.info("{}: difficulty {}, villager max health {}", name, h.getLevel().getDifficulty(), v.getMaxHealth());
        Mob[] foe = {foe(h, kind)};
        int[] tick = {0};
        int[] boutTick = {0};
        int[] bout = {0};
        float[] lastHealth = {v.getHealth()};
        boolean[] attacking = {false};
        Tally[] windows = new Tally[bouts / WINDOW];
        for (int i = 0; i < windows.length; i++) {
            windows[i] = new Tally();
        }
        int[] mark = {hands.hitsOnThreats, hands.swingsAtCreatures, hands.blockingTicks};
        float[] fmark = {hands.hitShareTotal, hands.damageDealt};
        h.onEachTick(() -> {
            if (bout[0] >= bouts) {
                return;
            }
            if (!v.isAlive()) {
                h.fail(name + ": the villager died in bout " + bout[0]);
                return;
            }
            tick[0]++;
            boutTick[0]++;
            Tally t = windows[bout[0] / WINDOW];
            t.taken += Math.max(0f, lastHealth[0] - v.getHealth());
            lastHealth[0] = v.getHealth();
            t.useTicks += handle.command().use() ? 1 : 0;
            if (handle.frame().feel[FeelChannel.THREAT_IN_REACH.ordinal()] > 0.5f) {
                t.inReach++;
                t.readyInReach += hands.attackRecharge() >= 0.95f ? 1 : 0;
            }
            Mob m = foe[0];
            if (m.isAlive() && m.getTarget() != v) {
                // Skeletons go for players and golems; this one is set on the villager.
                m.setTarget(v);
            }
            if (playerKeys) {
                MotorCommand keys = m.isAlive() ? playerKeys(v, handle, m, attacking[0]) : MotorCommand.IDLE;
                attacking[0] = keys.attack();
                handle.hold(keys);
            }
            boolean won = !m.isAlive();
            boolean lost = v.getHealth() <= LOSS_HEALTH;
            if (won || lost || boutTick[0] >= BOUT_TICKS) {
                t.bouts++;
                t.wins += won ? 1 : 0;
                t.losses += lost && !won ? 1 : 0;
                t.hits += hands.hitsOnThreats - mark[0];
                t.swings += hands.swingsAtCreatures - mark[1];
                t.shieldTicks += hands.blockingTicks - mark[2];
                t.share += hands.hitShareTotal - fmark[0];
                t.dealt += hands.damageDealt - fmark[1];
                mark[0] = hands.hitsOnThreats;
                mark[1] = hands.swingsAtCreatures;
                mark[2] = hands.blockingTicks;
                fmark[0] = hands.hitShareTotal;
                fmark[1] = hands.damageDealt;
                bout[0]++;
                boutTick[0] = 0;
                if (m.isAlive()) {
                    m.discard();
                }
                for (Entity e : h.getLevel().getEntities((Entity) null, v.getBoundingBox().inflate(16),
                        e -> e instanceof AbstractArrow || e instanceof net.minecraft.world.entity.item.ItemEntity)) {
                    e.discard();
                }
                heal(v, HOME, h, shield);
                lastHealth[0] = v.getHealth();
                if (bout[0] % WINDOW == 0) {
                    LOG.info("{} bouts {}-{}: {}", name, bout[0] - WINDOW, bout[0], t.line());
                }
                if (bout[0] >= bouts) {
                    Tally first = windows[0];
                    Tally last = windows[windows.length - 1];
                    LOG.info("{} learning result: first {} bouts: {} || last {} bouts: {}", name, WINDOW, first.line(), WINDOW, last.line());
                    if (playerKeys ? last.wins > 0 : last.wins > first.wins || last.dealt > first.dealt * 1.5f) {
                        h.succeed();
                    } else {
                        h.fail(name + ": no better at fighting: " + first.line() + " -> " + last.line());
                    }
                    return;
                }
                foe[0] = foe(h, kind);
            }
        });
    }

    @GameTest(template = ARENA, timeoutTicks = WINDOW * BOUT_TICKS + 400, required = false)
    public static void playerKeysFightZombies(GameTestHelper h) {
        bouts(h, "ZOMBIE_PLAYER", Foe.ZOMBIE, false, true, WINDOW);
    }

    @GameTest(template = ARENA, timeoutTicks = WINDOW * BOUT_TICKS + 400, required = false)
    public static void playerKeysFightSkeletons(GameTestHelper h) {
        bouts(h, "SKELETON_PLAYER", Foe.SKELETON, false, true, WINDOW);
    }

    @GameTest(template = ARENA, timeoutTicks = BOUTS * BOUT_TICKS + 400, required = false)
    public static void learnsToFightZombies(GameTestHelper h) {
        bouts(h, "ZOMBIE", Foe.ZOMBIE, false, false, BOUTS);
    }

    @GameTest(template = ARENA, timeoutTicks = BOUTS * BOUT_TICKS + 400, required = false)
    public static void learnsToFightSkeletons(GameTestHelper h) {
        bouts(h, "SKELETON", Foe.SKELETON, true, false, BOUTS);
    }

    /**
     * Two brain villagers in the pen, both learning, neither a threat to the other until one hurts
     * the other. Reports how often bouts turn into fights, and how each fights.
     */
    @GameTest(template = ARENA, timeoutTicks = BOUTS * BOUT_TICKS + 400, required = false)
    public static void villagersSpar(GameTestHelper h) {
        String name = "SPAR";
        if (!WorkLearningTests.selected(h, name)) {
            return;
        }
        pen(h);
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(BOUTS * BOUT_TICKS + 400);
        Villager a = fighter(h, HOME);
        Villager b = fighter(h, FOE);
        b.setYRot(90f);
        BrainManager.Handle ha = attach(h, a);
        BrainManager.Handle hb = attach(h, b);
        int[] boutTick = {0};
        int[] bout = {0};
        int[] fights = new int[BOUTS / WINDOW];
        int[] hitsA = new int[BOUTS / WINDOW];
        int[] hitsB = new int[BOUTS / WINDOW];
        int[] swings = new int[BOUTS / WINDOW];
        int[] mark = {0, 0, 0};
        h.onEachTick(() -> {
            if (bout[0] >= BOUTS) {
                return;
            }
            if (!a.isAlive() || !b.isAlive()) {
                h.fail(name + ": a villager died in bout " + bout[0]);
                return;
            }
            boutTick[0]++;
            boolean down = a.getHealth() <= LOSS_HEALTH || b.getHealth() <= LOSS_HEALTH;
            if (down || boutTick[0] >= BOUT_TICKS) {
                int w = bout[0] / WINDOW;
                int ha1 = ha.hands().hitsOnThreats - mark[0];
                int hb1 = hb.hands().hitsOnThreats - mark[1];
                int sw = ha.hands().swingsAtCreatures + hb.hands().swingsAtCreatures - mark[2];
                hitsA[w] += ha1;
                hitsB[w] += hb1;
                swings[w] += sw;
                fights[w] += ha1 + hb1 > 0 ? 1 : 0;
                mark[0] = ha.hands().hitsOnThreats;
                mark[1] = hb.hands().hitsOnThreats;
                mark[2] = ha.hands().swingsAtCreatures + hb.hands().swingsAtCreatures;
                bout[0]++;
                boutTick[0] = 0;
                heal(a, HOME, h);
                heal(b, FOE, h);
                if (bout[0] % WINDOW == 0) {
                    LOG.info("{} bouts {}-{}: {} turned into fights; hits on a threat {} and {}; {} swings at creatures",
                            name, bout[0] - WINDOW, bout[0], fights[w], hitsA[w], hitsB[w], swings[w]);
                }
                if (bout[0] >= BOUTS) {
                    int l = fights.length - 1;
                    LOG.info("{} learning result: fights {} -> {}, hits back and forth {} -> {}, swings {} -> {}", name,
                            fights[0], fights[l], hitsA[0] + hitsB[0], hitsA[l] + hitsB[l], swings[0], swings[l]);
                    h.succeed();
                }
            }
        });
    }

    /**
     * A brain villager against a sparring player who walks up and swings whenever ready: PvP. The
     * player is the threat from its first blow on.
     */
    @GameTest(template = ARENA, timeoutTicks = BOUTS * BOUT_TICKS + 400, required = false)
    public static void learnsToFightAPlayer(GameTestHelper h) {
        String name = "DUEL";
        if (!WorkLearningTests.selected(h, name)) {
            return;
        }
        pen(h);
        BrainManager.get().setLockstep(true);
        h.getLevel().getServer().tickRateManager().requestGameToSprint(BOUTS * BOUT_TICKS + 400);
        Villager v = fighter(h, HOME);
        BrainManager.Handle handle = attach(h, v);
        var hands = handle.hands();
        SparringPartner p = partner(h, FOE);
        int[] boutTick = {0};
        int[] bout = {0};
        Tally[] windows = new Tally[BOUTS / WINDOW];
        for (int i = 0; i < windows.length; i++) {
            windows[i] = new Tally();
        }
        float[] lastHealth = {v.getHealth(), p.getHealth()};
        int[] mark = {0, 0};
        float[] fmark = {0f};
        h.onEachTick(() -> {
            if (bout[0] >= BOUTS) {
                return;
            }
            if (!v.isAlive()) {
                h.fail(name + ": the villager died in bout " + bout[0]);
                return;
            }
            boutTick[0]++;
            Tally t = windows[bout[0] / WINDOW];
            t.taken += Math.max(0f, lastHealth[0] - v.getHealth());
            t.dealt += Math.max(0f, lastHealth[1] - p.getHealth());
            lastHealth[0] = v.getHealth();
            lastHealth[1] = p.getHealth();
            t.useTicks += handle.command().use() ? 1 : 0;
            if (handle.frame().feel[FeelChannel.THREAT_IN_REACH.ordinal()] > 0.5f) {
                t.inReach++;
                t.readyInReach += hands.attackRecharge() >= 0.95f ? 1 : 0;
            }
            p.fight(v);
            boolean won = p.getHealth() <= LOSS_HEALTH;
            boolean lost = v.getHealth() <= LOSS_HEALTH;
            if (won || lost || boutTick[0] >= BOUT_TICKS) {
                t.bouts++;
                t.wins += won ? 1 : 0;
                t.losses += lost && !won ? 1 : 0;
                t.hits += hands.hitsOnThreats - mark[0];
                t.swings += hands.swingsAtCreatures - mark[1];
                t.share += hands.hitShareTotal - fmark[0];
                mark[0] = hands.hitsOnThreats;
                mark[1] = hands.swingsAtCreatures;
                fmark[0] = hands.hitShareTotal;
                bout[0]++;
                boutTick[0] = 0;
                heal(v, HOME, h);
                p.setHealth(p.getMaxHealth());
                Vec3 at = h.absoluteVec(FOE);
                p.moveTo(at.x, at.y, at.z, 90f, 0f);
                lastHealth[0] = v.getHealth();
                lastHealth[1] = p.getHealth();
                if (bout[0] % WINDOW == 0) {
                    LOG.info("{} bouts {}-{}: {}", name, bout[0] - WINDOW, bout[0], t.line());
                }
                if (bout[0] >= BOUTS) {
                    Tally first = windows[0];
                    Tally last = windows[windows.length - 1];
                    LOG.info("{} learning result: first {} bouts: {} || last {} bouts: {}", name, WINDOW, first.line(), WINDOW, last.line());
                    p.discard();
                    if (last.dealt > first.dealt * 1.5f || last.wins > first.wins) {
                        h.succeed();
                    } else {
                        h.fail(name + ": no better at fighting: " + first.line() + " -> " + last.line());
                    }
                }
            }
        });
    }
}
