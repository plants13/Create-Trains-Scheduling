package com.plants13.createtrainsscheduling.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.dispatch.ForceReleasePermission;
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
 * <p>目前是 v1 功能的命令行入口，正式 UI（GUI/Web）后续接在同一批服务之上。
 */
public final class DispatchCommands {

    private static final SimpleCommandExceptionType TRAIN_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("找不到该名称的列车"));
    private static final SimpleCommandExceptionType NO_CONDUCTOR =
            new SimpleCommandExceptionType(Component.literal("该列车无法自动运行（需要烈焰人燃烧室或坐垫上的非玩家生物）"));
    private static final SimpleCommandExceptionType NO_STATIONS =
            new SimpleCommandExceptionType(Component.literal("没有有效车站"));

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
                                .executes(ctx -> override(ctx, SignalOverrideManager.DEFAULT_DURATION_TICKS))))
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
        source.sendSuccess(() -> Component.literal("JSON <-> Schedule 往返自检已执行，结果见日志"), true);
        return 1;
    }

    private static int listTrains(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (Create.RAILWAYS.trains.isEmpty()) {
            source.sendSuccess(() -> Component.literal("当前没有列车"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("列车列表（" + Create.RAILWAYS.trains.size() + "）："), false);
        for (Train train : Create.RAILWAYS.trains.values()) {
            String name = train.name == null ? train.id.toString() : train.name.getString();
            boolean conductor = train.hasForwardConductor() || train.hasBackwardConductor();
            boolean hasSchedule = train.runtime.getSchedule() != null;
            String line = String.format(" - %s | 可自动运行=%s | 有时刻表=%s | 授权剩余=%ss",
                    name, conductor ? "是" : "否", hasSchedule ? "是" : "否",
                    SignalOverrideManager.remaining(train) / 20);
            source.sendSuccess(() -> Component.literal(line), false);
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
        source.sendSuccess(() -> Component.literal("车站（" + names.size() + "）：" + names), false);
        return 1;
    }

    private static int override(CommandContext<CommandSourceStack> ctx, long durationTicks)
            throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        boolean allowed = source.hasPermission(2) || (player != null && ForceReleasePermission.canForceRelease(player));
        if (!allowed) {
            source.sendFailure(Component.literal("你没有强制放行权限（需要 OP 或被加入 forceReleasePlayers 授权组）"));
            return 0;
        }
        Train train = findTrain(source, StringArgumentType.getString(ctx, "train"));
        SignalOverrideManager.grant(train, durationTicks);
        source.sendSuccess(() -> Component.literal(String.format(
                "已授权列车 %s 越过前方红灯（%d 秒）",
                train.name.getString(), durationTicks / 20)), true);
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
        source.sendSuccess(() -> Component.literal(String.format(
                "已向列车 %s 下发一次性路线：%s", train.name.getString(), String.join(" -> ", stations))), true);
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Train train = findTrain(source, StringArgumentType.getString(ctx, "train"));
        train.runtime.discardSchedule();
        source.sendSuccess(() -> Component.literal("已清除列车 " + train.name.getString() + " 的时刻表"), true);
        return 1;
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
