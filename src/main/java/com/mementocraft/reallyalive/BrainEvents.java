package com.mementocraft.reallyalive;

import com.mementocraft.reallyalive.brain.Genome;
import com.mementocraft.reallyalive.brain.Rng;
import com.mementocraft.reallyalive.command.BrainCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
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

/** Game events. Only villagers whose brain is switched on are touched. */
public final class BrainEvents {
    private BrainEvents() {}

    @SubscribeEvent
    public static void serverStarting(ServerStartingEvent e) {
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
        if (v.isAlive()) {
            BrainManager.get().detach(v, true);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
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
