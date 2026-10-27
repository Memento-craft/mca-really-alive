package com.mementocraft.reallyalive;

import com.mementocraft.reallyalive.brain.Genome;
import com.mementocraft.reallyalive.brain.Rng;
import com.mementocraft.reallyalive.body.Hands;
import com.mementocraft.reallyalive.body.StandIn;
import com.mementocraft.reallyalive.command.BrainCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.entity.EntityTypeTest;
import com.mementocraft.reallyalive.brain.SoundChannel;
import com.mementocraft.reallyalive.sense.Hearing;
import com.mementocraft.reallyalive.speech.Speech;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Game events. Only villagers whose brain is switched on are touched. */
public final class BrainEvents {
    private BrainEvents() {}

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent e) {
        Hearing.reset();
        LAST_STEP.clear();
        BrainManager.start(e.getServer());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) {
        BrainManager.stop();
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        BrainCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile shot) {
            // An arrow or trident a stand-in let loose was the villager's: what it hits knows who shot it.
            Hands shooter = StandIn.owner(shot.getOwner());
            if (shooter != null) {
                shot.setOwner(shooter.body());
            }
            return;
        }
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Villager v) || BrainManager.get() == null) {
            return;
        }
        boolean enabled = v.hasData(Attachments.BRAIN) && v.getData(Attachments.BRAIN).enabled();
        if (enabled || Config.AUTO_ENABLE.get()) {
            BrainManager.get().attach(v);
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Villager v) || BrainManager.get() == null) {
            return;
        }
        // An unloaded or discarded villager is no longer alive when it leaves, so checking isAlive()
        // leaked its brain (and never saved it); only a killed one has nothing to keep.
        if (v.getRemovalReason() != net.minecraft.world.entity.Entity.RemovalReason.KILLED) {
            BrainManager.get().detach(v, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (!e.getEntity().level().isClientSide() && e.getEntity() instanceof Villager v && BrainManager.get() != null) {
            BrainManager.get().hurt(v, e.getSource());
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide() && BrainManager.get() != null && !e.isCanceled()) {
            BrainManager.get().died(e.getEntity());
        }
        if (!e.getEntity().level().isClientSide() && e.getEntity() instanceof Villager v && BrainManager.get() != null
                && v.hasData(Attachments.BRAIN)) {
            BrainManager.get().forget(v);
        }
    }

    @SubscribeEvent
    public static void beforeTick(EntityTickEvent.Pre e) {
        if (e.getEntity() instanceof Villager v && !v.level().isClientSide() && BrainManager.get() != null) {
            BrainManager.get().beforeTick(v);
        }
    }

    @SubscribeEvent
    public static void afterTick(EntityTickEvent.Post e) {
        if (e.getEntity() instanceof Villager v && !v.level().isClientSide() && BrainManager.get() != null) {
            BrainManager.get().afterTick(v);
        }
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        BrainCommand.tickWatchers(e.getServer());
        playerSteps(e);
    }

    // ---------------------------------------------------------------- hearing

    /** Ticks between the footsteps of a walking player. */
    private static final int STEP_TICKS = 6;
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_STEP = new HashMap<>();

    /**
     * A player's footsteps are played on their own client, so the server never hears them: make
     * them up from how the player moves, at a footstep's volume.
     */
    private static void playerSteps(ServerTickEvent.Post e) {
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            Vec3 was = LAST_POS.put(p.getUUID(), p.position());
            if (was == null || !p.onGround() || p.isSpectator() || p.position().subtract(was).horizontalDistanceSqr() < 0.0036) {
                continue;
            }
            long now = p.level().getGameTime();
            Long last = LAST_STEP.get(p.getUUID());
            if (last == null || now - last >= STEP_TICKS) {
                LAST_STEP.put(p.getUUID(), now);
                Hearing.sound(p.level(), p.position(), SoundChannel.STEPS, 0.15f);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void soundAt(PlayLevelSoundEvent.AtPosition e) {
        heard(e, e.getPosition());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void soundFrom(PlayLevelSoundEvent.AtEntity e) {
        heard(e, e.getEntity().position());
    }

    private static void heard(PlayLevelSoundEvent e, Vec3 pos) {
        if (e.isCanceled() || e.getLevel().isClientSide() || e.getSound() == null || BrainManager.get() == null) {
            return;
        }
        SoundChannel channel = Hearing.classify(e.getSound(), e.getSource());
        if (channel != null) {
            Hearing.sound(e.getLevel(), pos, channel, e.getNewVolume());
        }
    }

    /** Breaking a block makes its sound on the breaker's own client: the server hears it here. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void blockBroken(BlockEvent.BreakEvent e) {
        if (!e.isCanceled() && e.getLevel() instanceof ServerLevel level && BrainManager.get() != null) {
            Hearing.sound(level, Vec3.atCenterOf(e.getPos()), SoundChannel.DIGGING, 1f);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void chat(ServerChatEvent e) {
        if (!e.isCanceled() && BrainManager.get() != null) {
            Speech.playerSays(e.getPlayer().level(), e.getPlayer().getEyePosition(), e.getRawText());
        }
    }

    // ---------------------------------------------------------------- making things

    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent e) {
        made(e.getEntity());
    }

    @SubscribeEvent
    public static void smelted(PlayerEvent.ItemSmeltedEvent e) {
        made(e.getEntity());
    }

    @SubscribeEvent
    public static void brewed(PlayerBrewedPotionEvent e) {
        // The event fires for any potion taken from a stand's bottle slot, so a water bottle put in
        // (or an empty bottle) taken straight back out would be felt as brewing; only a brewed
        // potion counts.
        var contents = e.getStack().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null || contents.is(net.minecraft.world.item.alchemy.Potions.WATER)) {
            return;
        }
        made(e.getEntity());
    }

    /** A villager's stand-in took something it made out of a result slot. */
    private static void made(Object player) {
        Hands hands = StandIn.owner(player);
        if (hands != null) {
            hands.made();
        }
    }

    @SubscribeEvent
    public static void save(LevelEvent.Save e) {
        if (e.getLevel() instanceof ServerLevel level && BrainManager.get() != null) {
            BrainManager.get().saveAll(level.getEntities(EntityTypeTest.forClass(Villager.class), v -> true));
        }
    }

    /** A child of two brain villagers inherits a mix of both parents' genes. */
    @SubscribeEvent
    public static void baby(BabyEntitySpawnEvent e) {
        if (!Config.INHERIT.get() || !(e.getParentA() instanceof Villager a) || !(e.getParentB() instanceof Villager b)
                || !(e.getChild() instanceof Villager child)) {
            return;
        }
        if (!a.hasData(Attachments.BRAIN) || !b.hasData(Attachments.BRAIN)) {
            return;
        }
        BrainData da = a.getData(Attachments.BRAIN);
        BrainData db = b.getData(Attachments.BRAIN);
        if (!da.hasGenome() || !db.hasGenome()) {
            return;
        }
        Genome genes = Genome.inherit(Genome.fromArray(da.genome()), Genome.fromArray(db.genome()),
                new Rng(child.getUUID().getLeastSignificantBits()));
        BrainData dc = child.getData(Attachments.BRAIN);
        dc.setGenome(genes.toArray());
        dc.setEnabled(da.enabled() || db.enabled());
    }
}
