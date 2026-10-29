package com.mementocraft.reallyalive.command;

import com.mementocraft.reallyalive.BrainManager;
import com.mementocraft.reallyalive.brain.Brain;
import com.mementocraft.reallyalive.brain.LobeId;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /reallyalive brain enable|disable|info|babble}, {@code /reallyalive goal} and
 * {@code /reallyalive watch}, which shows a villager's key presses and lobe activity above the hotbar.
 */
public final class BrainCommand {
    private static final Map<UUID, UUID> WATCHERS = new HashMap<>();

    private BrainCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("reallyalive").requires(s -> s.hasPermission(2))
                .then(Commands.literal("brain")
                        .then(Commands.literal("enable").then(Commands.argument("villagers", EntityArgument.entities())
                                .executes(c -> forEach(c, v -> BrainManager.get().attach(v) != null, "Brain switched on"))))
                        .then(Commands.literal("disable").then(Commands.argument("villagers", EntityArgument.entities())
                                .executes(c -> forEach(c, v -> {
                                    BrainManager.get().detach(v, false);
                                    return true;
                                }, "Brain switched off"))))
                        .then(Commands.literal("info").then(Commands.argument("villager", EntityArgument.entity())
                                .executes(BrainCommand::info)))
                        .then(Commands.literal("memories").then(Commands.argument("villager", EntityArgument.entity())
                                .executes(BrainCommand::memories)))
                        .then(Commands.literal("babble").then(Commands.argument("villagers", EntityArgument.entities())
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0f, 4f))
                                        .executes(c -> {
                                            float scale = FloatArgumentType.getFloat(c, "scale");
                                            return forEach(c, v -> withBrain(v, b -> b.setBabbleScale(scale)),
                                                    "Babbling set to " + scale);
                                        }))))
                        // Sets villagers on getting a thing or doing a deed, as asking them would.
                        .then(Commands.literal("want").then(Commands.argument("villagers", EntityArgument.entities())
                                .then(Commands.argument("thing", StringArgumentType.greedyString())
                                        .executes(c -> {
                                            String thing = StringArgumentType.getString(c, "thing");
                                            return forEach(c, v -> withBrain(v, b -> b.want(thing)), "Now after " + thing);
                                        })))))
                .then(Commands.literal("goal").then(Commands.argument("villagers", EntityArgument.entities())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(c -> {
                                    BlockPos pos = BlockPosArgument.getLoadedBlockPos(c, "pos");
                                    return forEach(c, v -> setGoal(v, pos), "Goal set; it smells it and enjoys getting closer");
                                }))
                        .then(Commands.literal("clear")
                                .executes(c -> forEach(c, v -> setGoal(v, null), "Goal cleared")))))
                .then(Commands.literal("recipebook").executes(c -> {
                    // A book of how machine parts are made, to hand to a villager.
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getInventory().add(com.mementocraft.reallyalive.body.RecipeBook.machinery(p.level()));
                    c.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                            "A recipe book of machinery: give it to a villager to look at"), false);
                    return 1;
                }))
                .then(Commands.literal("watch")
                        .then(Commands.argument("villager", EntityArgument.entity()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            WATCHERS.put(p.getUUID(), EntityArgument.getEntity(c, "villager").getUUID());
                            return 1;
                        }))
                        .then(Commands.literal("stop").executes(c -> {
                            WATCHERS.remove(c.getSource().getPlayerOrException().getUUID());
                            return 1;
                        }))));
    }

    private interface VillagerAction {
        boolean apply(Villager v);
    }

    private interface BrainAction {
        void apply(Brain b);
    }

    private static int forEach(CommandContext<CommandSourceStack> c, VillagerAction action, String message)
            throws CommandSyntaxException {
        if (BrainManager.get() == null) {
            return 0;
        }
        List<Villager> villagers = new ArrayList<>();
        for (Entity e : EntityArgument.getEntities(c, "villagers")) {
            if (e instanceof Villager v) {
                villagers.add(v);
            }
        }
        int done = 0;
        for (Villager v : villagers) {
            if (action.apply(v)) {
                done++;
            }
        }
        int count = done;
        c.getSource().sendSuccess(() -> Component.literal(message + " (" + count + " villager"
                + (count == 1 ? "" : "s") + ")"), true);
        return done;
    }

    private static boolean withBrain(Villager v, BrainAction action) {
        BrainManager.Handle h = BrainManager.get().handle(v);
        if (h == null) {
            return false;
        }
        action.apply(h.brain());
        return true;
    }

    private static boolean setGoal(Villager v, BlockPos pos) {
        BrainManager.Handle h = BrainManager.get().handle(v);
        if (h == null) {
            return false;
        }
        h.setGoal(pos);
        return true;
    }

    /** What a villager remembers of people, what it counted of its own work, and how keen it is to tell. */
    private static int memories(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "villager");
        if (!(e instanceof Villager v) || BrainManager.get() == null || BrainManager.get().handle(v) == null) {
            c.getSource().sendFailure(Component.literal("That villager has no brain switched on"));
            return 0;
        }
        Brain b = BrainManager.get().handle(v).brain();
        StringBuilder sb = new StringBuilder();
        BrainManager.Handle h = BrainManager.get().handle(v);
        sb.append(String.format("harvested %s, finds telling worth %.2f%nhunger %.2f, tiredness %.2f, boredom %.2f, loneliness %.2f, fear %.2f",
                b.deeds(), b.tellValue(), h.hunger(), h.tiredness(), h.boredom(), h.loneliness(), h.fear()));
        for (String need : new String[] {"hunger", "tiredness", "boredom", "loneliness", "fear"}) {
            if (!b.needsOf(need).isEmpty()) {
                sb.append("\n  ").append(need).append(" is eased by ").append(b.needsOf(need));
            }
        }
        java.util.List<String> plan = b.plan();
        if (!plan.isEmpty()) {
            sb.append("\nafter ").append(plan.get(plan.size() - 1)).append(", next step first: ").append(plan);
            for (String step : plan) {
                sb.append("\n  ").append(step).append(" needs ").append(b.needsOf(step));
            }
        }
        for (String person : b.peopleRemembered()) {
            sb.append("\n").append(person).append(':');
            for (String line : b.recollectionsOf(person)) {
                sb.append("\n  ").append(line);
            }
        }
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Entity e = EntityArgument.getEntity(c, "villager");
        if (!(e instanceof Villager v) || BrainManager.get() == null || BrainManager.get().handle(v) == null) {
            c.getSource().sendFailure(Component.literal("That villager has no brain switched on"));
            return 0;
        }
        Brain b = BrainManager.get().handle(v).brain();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d neurons, %d synapses, maturity %.2f, %d ticks lived, %d neurons reborn%n",
                b.neuronCount(), b.synapseCount(), b.maturity(), b.ticks(), b.resets()));
        for (LobeId l : LobeId.values()) {
            sb.append(String.format("  %-10s %5d neurons, firing %.3f%n", l.name().toLowerCase(), b.neuronCount(l),
                    b.lobeActivity(l)));
        }
        sb.append(String.format("dopamine %+.2f, surprise %.3f, babbling x%.2f, keys %s",
                b.dopamine(), b.surprise(), b.babbleScale(), b.lastCommand().keys()));
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    /** Shows each watcher their villager's keys and lobe activity above the hotbar. */
    public static void tickWatchers(MinecraftServer server) {
        if (WATCHERS.isEmpty() || BrainManager.get() == null || server.getTickCount() % 2 != 0) {
            return;
        }
        WATCHERS.forEach((playerId, villagerId) -> {
            ServerPlayer p = server.getPlayerList().getPlayer(playerId);
            if (p == null) {
                return;
            }
            Entity e = p.serverLevel().getEntity(villagerId);
            if (!(e instanceof Villager v) || BrainManager.get().handle(v) == null) {
                p.displayClientMessage(Component.literal("(no brain)"), true);
                return;
            }
            BrainManager.Handle h = BrainManager.get().handle(v);
            Brain b = h.brain();
            String hud = String.format("%s | VIS %s CON %s MEM %s CER %s | dopa %+.2f",
                    h.command().keys(), bar(b.lobeActivity(LobeId.OCCIPITAL)), bar(b.lobeActivity(LobeId.CONCEPT)),
                    bar(b.lobeActivity(LobeId.MEMORY)), bar(b.lobeActivity(LobeId.CEREBELLUM)), b.dopamine());
            p.displayClientMessage(Component.literal(hud), true);
        });
    }

    private static String bar(float activity) {
        int filled = Math.min(5, Math.round(activity / 0.02f));
        return "|".repeat(filled) + ".".repeat(5 - filled);
    }
}
