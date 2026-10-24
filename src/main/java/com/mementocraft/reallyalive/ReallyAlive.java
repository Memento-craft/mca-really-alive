package com.mementocraft.reallyalive;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ReallyAlive.MODID)
public final class ReallyAlive {
    public static final String MODID = "reallyalive";

    public ReallyAlive(IEventBus modBus, ModContainer container) {
        Attachments.ATTACHMENT_TYPES.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        NeoForge.EVENT_BUS.register(BrainEvents.class);
    }
}
