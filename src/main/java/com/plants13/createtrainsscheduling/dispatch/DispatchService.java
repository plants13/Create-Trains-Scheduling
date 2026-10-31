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

import net.minecraft.core.HolderLookup;
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
    public static Result setDestination(HolderLookup.Provider registries, Train train, GlobalStation target) {
        if (!canRunAutomatically(train)) {
            return Result.fail(Component.translatable(PREFIX + "no_conductor"));
        }
        if (train.graph == null) {
            return Result.fail(Component.translatable(PREFIX + "no_graph"));
        }

        if (!isAmbiguous(train, target.name)) {
            Schedule schedule = RouteBuilder.oneOff(registries, List.of(target.name));
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
