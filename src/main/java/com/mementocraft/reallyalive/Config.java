package com.mementocraft.reallyalive;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue AUTO_ENABLE = BUILDER
            .comment("Give every villager a brain automatically. When false, use /reallyalive brain enable.")
            .define("autoEnable", false);

    public static final ModConfigSpec.BooleanValue INHERIT = BUILDER
            .comment("Children of two brain villagers are born with a brain built from both parents' genes.")
            .define("inheritToChildren", true);

    public static final ModConfigSpec.IntValue MAX_BRAINS = BUILDER
            .comment("Most brains that may run at once. Extra villagers keep their normal AI.")
            .defineInRange("maxActiveBrains", 64, 1, 4096);

    public static final ModConfigSpec.IntValue THREADS = BUILDER
            .comment("Worker threads that run brains off the main server thread.")
            .defineInRange("brainThreads", Math.max(1, Runtime.getRuntime().availableProcessors() / 2), 1, 64);

    public static final ModConfigSpec.IntValue VISION_RANGE = BUILDER
            .comment("How far villagers can see, in blocks.")
            .defineInRange("visionRange", 24, 4, 96);

    public static final ModConfigSpec.IntValue VISION_INTERVAL = BUILDER
            .comment("Cast eye rays every N ticks; the last view is reused in between.")
            .defineInRange("visionInterval", 2, 1, 20);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
