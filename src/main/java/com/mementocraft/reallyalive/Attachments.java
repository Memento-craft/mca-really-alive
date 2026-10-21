package com.mementocraft.reallyalive;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class Attachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ReallyAlive.MODID);

    public static final Supplier<AttachmentType<BrainData>> BRAIN = ATTACHMENT_TYPES.register("brain",
            () -> AttachmentType.builder(() -> new BrainData()).serialize(BrainData.CODEC).build());

    private Attachments() {}
}
