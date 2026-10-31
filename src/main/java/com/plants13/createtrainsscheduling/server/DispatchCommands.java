package com.plants13.createtrainsscheduling.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.dispatch.ForceReleasePermission;
import com.plants13.createtrainsscheduling.dispatch.OneOffScheduleTracker;
import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.plants13.createtrainsscheduling.schedule.RouteBuilder;
import com.plants13.createtrainsscheduling.schedule.ScheduleSelfTest;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * 开发/调度用命令：{@code /cts ...}
 *
 * <p>所有面向玩家的文本都走语言文件（{@code lang/en_us.json}、{@code lang/zh_cn.json}）。
 */
public final class DispatchCommands {

    private static final String PREFIX = "create_trains_scheduling.command.";

    private static final SimpleCommandExceptionType TRAIN_NOT_FOUND =
            new SimpleCommandExceptionType(Component.translatable(PREFIX + "train_not_found"));
    private static final SimpleCommandExceptionType NO_CONDUCTOR =
            new SimpleCommandExceptionType(Component.translatable(PREFIX + "route.no_conductor"));
    private static final SimpleCommandExceptionType NO_STATIONS =
            new SimpleCommandExceptionType(Component.translatable(PREFIX + "route.no_stations"));

    private DispatchCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cts")
                .then(Commands.literal("selftest")
                        .executes(DispatchCommands::selftest))
                .then(Commands.literal("trains")
                        .executes(DispatchCommands::listTrains))
                .then(Commands.literal("stations")
                        .executes(DispatchCommands::listStations))
                .then(Commands.literal("override")
                        .then(Commands.argument("train", StringArgumentType.string())
                                // 默认：事件制（通过下一个信号后立即失效）
                                .executes(ctx -> override(ctx, 0))
                                // 也可指定 ticks，改为时间制
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 72000))
                                        .executes(ctx -> override(ctx,
                                                IntegerArgumentType.getInteger(ctx, "ticks"))))))
                .then(Commands.literal("route")
                        .then(Commands.argument("train", StringArgumentType.string())
                                .then(Commands.argument("stations", StringArgumentType.greedyString())
                                        .executes(DispatchCommands::route))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("train", StringArgumentType.string())
                                .executes(DispatchCommands::clear))));
    }

    private static int selftest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ScheduleSelfTest.run(source.getServer().registryAccess());
        source.sendSuccess(() -> Component.translatable(PREFIX + "selftest.done"), true);
        return 1;
    }

    private static int listTrains(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (Create.RAILWAYS.trains.isEmpty()) {
            source.sendSuccess(() -> Component.translatable(PREFIX + "trains.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(PREFIX + "trains.header",
                Create.RAILWAYS.trains.size()), false);
        for (Train train : Create.RAILWAYS.trains.values()) {
            String name = train.name == null ? train.id.toString() : train.name.getString();
            boolean conductor = train.hasForwardConductor() || train.hasBackwardConductor();
            boolean hasSchedule = train.runtime.getSchedule() != null;
            Component overrideDesc = overrideDescription(train);
            source.sendSuccess(() -> Component.translatable(PREFIX + "trains.entry",
                    name,
                    yesNo(conductor),
                    yesNo(hasSchedule),
                    overrideDesc), false);
        }
        return 1;
    }

    private static int listStations(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Set<String> names = new TreeSet<>();
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (station.name != null && !station.name.isBlank()) {
                    names.add(station.name);
                }
            }
        }
        String joined = names.toString();
        source.sendSuccess(() -> Component.translatable(PREFIX + "stations", names.size(), joined), false);
        return 1;
    }

    private static int override(CommandContext<CommandSourceStack> ctx, int durationTicks)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        boolean allowed = source.hasPermission(2) || (player != null && ForceReleasePermission.canForceRelease(player));
        if (!allowed) {
            source.sendFailure(Component.translatable(PREFIX + "override.no_permission"));
            return 0;
        }
        Train train = findTrain(source, StringArgumentType.getString(ctx, "train"));
        if (durationTicks <= 0) {
            SignalOverrideManager.grantNextSignal(train);
            source.sendSuccess(() -> Component.translatable(PREFIX + "override.success.next_signal",
                    train.name.getString()), true);
        } else {
            SignalOverrideManager.grantTimed(train, durationTicks);
            source.sendSuccess(() -> Component.translatable(PREFIX + "override.success.timed",
                    train.name.getString(), durationTicks, durationTicks / 20.0), true);
        }
        return 1;
    }

    private static int route(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Train train = findTrain(source, StringArgumentType.getString(ctx, "train"));
        if (!train.hasForwardConductor() && !train.hasBackwardConductor()) {
            throw NO_CONDUCTOR.create();
        }
        List<String> stations = parseStations(StringArgumentType.getString(ctx, "stations"));
        if (stations.isEmpty()) {
            throw NO_STATIONS.create();
        }
        Schedule schedule = RouteBuilder.oneOff(source.getServer().registryAccess(), stations);
        train.runtime.setSchedule(schedule, true);
        OneOffScheduleTracker.markDispatched(train);
        source.sendSuccess(() -> Component.translatable(PREFIX + "route.success",
                train.name.getString(), String.join(" -> ", stations)), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Train train = findTrain(source, StringArgumentType.getString(ctx, "train"));
        train.runtime.discardSchedule();
        OneOffScheduleTracker.markCleared(train);
        source.sendSuccess(() -> Component.translatable(PREFIX + "clear.success",
                train.name.getString()), true);
        return 1;
    }

    private static Component overrideDescription(Train train) {
        if (SignalOverrideManager.hasNextSignalGrant(train)) {
            return Component.translatable(PREFIX + "override.mode.next_signal");
        }
        long remaining = SignalOverrideManager.remaining(train);
        if (remaining > 0L) {
            return Component.translatable(PREFIX + "override.mode.timed", remaining / 20.0);
        }
        return Component.translatable(PREFIX + "override.mode.none");
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(PREFIX + (value ? "yes" : "no"));
    }

    private static List<String> parseStations(String spec) {
        List<String> result = new ArrayList<>();
        for (String part : spec.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                result.add(name);
            }
        }
        return result;
    }

    private static Train findTrain(CommandSourceStack source, String name) throws CommandSyntaxException {
        String needle = name.trim();
        Train fallback = null;
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (train.name != null && needle.equalsIgnoreCase(train.name.getString())) {
                return train;
            }
            if (needle.equalsIgnoreCase(train.id.toString())) {
                return train;
            }
            if (fallback == null && train.name != null && train.name.getString().toLowerCase()
                    .contains(needle.toLowerCase())) {
                fallback = train;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        CreateTrainsScheduling.LOGGER.warn("[CTS] train not found: {}", needle);
        throw TRAIN_NOT_FOUND.create();
    }
}
