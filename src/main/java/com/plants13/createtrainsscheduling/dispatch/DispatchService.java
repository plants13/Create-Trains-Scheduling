// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.dispatch;

import java.util.ArrayList;
import java.util.List;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.schedule.RouteBuilder;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.network.chat.Component;

/**
 * 调度服务层：GUI 与命令共用。
 *
 * <p>"设定目的地"有两条路径：
 * <ul>
 *   <li><b>NATIVE</b>：站名在列车的轨道网络上唯一 → 用原生 {@code DestinationInstruction} 下发时刻表，
 *       与 Create/CRN 的显示屏、旅程预测完全兼容；</li>
 *   <li><b>EXACT</b>：站名不唯一（或含 glob 通配符）→ 原生匹配无法保证去哪一个，
 *       于是绕过时刻表，直接用站点对象 {@code Navigation.findPathTo(station)} 精确导航。</li>
 * </ul>
 * EXACT 模式下 Create/CRN 的时刻表显示不会出现这一行程，需要我们自己的状态提示。
 */
public final class DispatchService {

    private static final String PREFIX = "create_trains_scheduling.dispatch.";

    public enum Mode {
        NATIVE,
        EXACT
    }

    public record Result(boolean success, Mode mode, Component message) {

        static Result ok(Mode mode, Component message) {
            return new Result(true, mode, message);
        }

        static Result fail(Component message) {
            return new Result(false, null, message);
        }
    }

    private DispatchService() {}

    /** 单站目的地。 */
    public static Result setDestination(Train train, GlobalStation target) {
        if (!canRunAutomatically(train)) {
            return Result.fail(Component.translatable(PREFIX + "no_conductor"));
        }
        if (train.graph == null) {
            return Result.fail(Component.translatable(PREFIX + "no_graph"));
        }

        if (!isAmbiguous(train, target.name)) {
            Schedule schedule = RouteBuilder.oneOff(List.of(target.name));
            train.runtime.setSchedule(schedule, true);
            OneOffScheduleTracker.markDispatched(train);
            return Result.ok(Mode.NATIVE,
                    Component.translatable(PREFIX + "destination.native", target.name));
        }

        // 重名：退化为按站点对象的精确导航
        train.runtime.discardSchedule();
        DirectDispatchTracker.markCleared(train);
        DiscoveredPath path = train.navigation.findPathTo(target, Double.MAX_VALUE);
        if (path == null) {
            return Result.fail(Component.translatable(PREFIX + "destination.unreachable", target.name));
        }
        train.runtime.paused = false;
        if (train.navigation.startNavigation(path) < 0) {
            return Result.fail(Component.translatable(PREFIX + "destination.start_failed", target.name));
        }
        DirectDispatchTracker.mark(train);
        CreateTrainsScheduling.LOGGER.info(
                "[CTS] exact dispatch (ambiguous name '{}'): train {} -> station {}",
                target.name, train.name == null ? train.id : train.name.getString(), target.id);
        return Result.ok(Mode.EXACT,
                Component.translatable(PREFIX + "destination.exact", target.name));
    }

    /**
     * 禁用 / 恢复时刻表。
     *
     * <p>关键区别：<b>手动放置</b>的时刻表（{@code isAutoSchedule == false}）只能"打断"——
     * 取消导航并暂停，时刻表本身保留，之后可以恢复；只有自动/本 mod 下发的时刻表才真正清除。
     * 否则手动时刻表会连同物品一起丢失。
     */
    public static Result setScheduleEnabled(Train train, boolean enabled) {
        if (train == null || train.runtime.getSchedule() == null) {
            return Result.fail(Component.translatable(PREFIX + "schedule.none"));
        }
        if (enabled) {
            train.runtime.paused = false;
            return Result.ok(Mode.NATIVE, Component.translatable(PREFIX + "schedule.resumed"));
        }
        if (train.runtime.isAutoSchedule) {
            train.runtime.discardSchedule();
            OneOffScheduleTracker.markCleared(train);
            DirectDispatchTracker.markCleared(train);
            return Result.ok(Mode.NATIVE, Component.translatable(PREFIX + "schedule.disabled_auto"));
        }

        // 手动时刻表：打断但保留
        train.navigation.cancelNavigation();
        train.runtime.paused = true;
        DirectDispatchTracker.markCleared(train);
        return Result.ok(Mode.NATIVE, Component.translatable(PREFIX + "schedule.disabled_manual"));
    }

    /**
     * 多站进路：按顺序下发一整条站点序列。
     *
     * <p>多站必须走原生 {@code DestinationInstruction}（按站名匹配），所以只要有任何一站
     * 站名在本轨道网络上不唯一，就拒绝下发，避免列车去错站；单站仍然可以走精确导航回退。
     */
    public static Result setRoute(Train train, List<GlobalStation> stations) {
        if (stations == null || stations.isEmpty()) {
            return Result.fail(Component.translatable(PREFIX + "route.empty"));
        }
        if (!canRunAutomatically(train)) {
            return Result.fail(Component.translatable(PREFIX + "no_conductor"));
        }
        if (train.graph == null) {
            return Result.fail(Component.translatable(PREFIX + "no_graph"));
        }
        for (GlobalStation station : stations) {
            if (isAmbiguous(train, station.name)) {
                return Result.fail(Component.translatable(PREFIX + "route.ambiguous_station", station.name));
            }
        }
        List<String> names = stations.stream()
                .map(station -> station.name)
                .toList();
        Schedule schedule = RouteBuilder.oneOff(names, true);
        train.runtime.setSchedule(schedule, true);
        OneOffScheduleTracker.markDispatched(train);
        String joined = String.join(" -> ", names);
        if (names.size() > 1 && RouteBuilder.hasWaypointInstruction()) {
            return Result.ok(Mode.NATIVE, Component.translatable(PREFIX + "route.dispatched_waypoint",
                    names.size() - 1, joined));
        }
        return Result.ok(Mode.NATIVE,
                Component.translatable(PREFIX + "route.dispatched", joined));
    }

    /** 站名是否在该列车的轨道网络上不唯一（或不适合按名字匹配）。 */
    public static boolean isAmbiguous(Train train, String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        // 含 glob 通配符的名字，原生匹配会变成模式匹配，无法保证精确
        if (name.indexOf('*') >= 0) {
            return true;
        }
        if (train.graph == null) {
            return true;
        }
        List<GlobalStation> matches = sameNameStations(train, name);
        return matches.size() != 1;
    }

    /** 该轨道网络上所有同名车站。 */
    public static List<GlobalStation> sameNameStations(Train train, String name) {
        List<GlobalStation> matches = new ArrayList<>();
        if (train.graph == null || name == null) {
            return matches;
        }
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (name.equals(station.name)) {
                matches.add(station);
            }
        }
        return matches;
    }

    public static boolean canRunAutomatically(Train train) {
        return train != null && (train.hasForwardConductor() || train.hasBackwardConductor());
    }
}
