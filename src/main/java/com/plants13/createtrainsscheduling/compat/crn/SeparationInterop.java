// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.crn;

import java.util.List;
import java.util.UUID;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;

import de.mrjulsen.crn.core.TrainManager;
import de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition;
import de.mrjulsen.crn.data.schedule.instruction.PrioritizedDestinationInstruction;

/**
 * CRN 分隔列车条件的只读访问 + 发车记录。
 *
 * <p>三个 mixin 原先各自复制了“在 entry 里找 {@link TrainSeparationCondition}、取分隔间隔、
 * 取有效过滤条件、写 DepartureLog”的逻辑；这里统一收拢，mixin 只负责注入位置和平台类型转换。
 *
 * <p>该类的调用方都是服务端线程（Create/CRN 的列车 tick），不额外做同步。
 */
public final class SeparationInterop {

    /** {@link #recordDeparture} 的结果，用于诊断日志。 */
    public record RecordResult(boolean recorded, String how) {}

    private SeparationInterop() {}

    /** 找出 entry 里的第一个 {@link TrainSeparationCondition}，没有则返回 null。 */
    public static TrainSeparationCondition find(ScheduleEntry entry) {
        if (entry == null || entry.conditions == null) {
            return null;
        }
        for (var group : entry.conditions) {
            for (ScheduleWaitCondition condition : group) {
                if (condition instanceof TrainSeparationCondition separation) {
                    return separation;
                }
            }
        }
        return null;
    }

    /** 分隔间隔（tick）；同一 entry 有多个条件时取最大值。 */
    public static long intervalTicks(ScheduleEntry entry) {
        long interval = 0L;
        if (entry == null || entry.conditions == null) {
            return interval;
        }
        for (var group : entry.conditions) {
            for (ScheduleWaitCondition condition : group) {
                if (condition instanceof TrainSeparationCondition separation) {
                    interval = Math.max(interval,
                            separation.getData()
                                    .getInt(TrainSeparationCondition.NBT_TICKS));
                }
            }
        }
        return interval;
    }

    /**
     * CRN 实际用来查询发车记录的过滤条件：优先用条件里自定义的 station filter，
     * 否则回退到 entry 的目标站（普通目标站或优先目标站列表）。
     */
    public static List<String> effectiveFilters(ScheduleEntry entry) {
        TrainSeparationCondition condition = find(entry);
        if (condition != null) {
            String custom = condition.getData()
                    .getString(TrainSeparationCondition.NBT_STATION_FILTER);
            if (custom != null && !custom.isBlank()) {
                return List.of(custom);
            }
        }
        if (entry != null && entry.instruction instanceof DestinationInstruction instruction) {
            return List.of(instruction.getFilter());
        }
        if (entry != null && entry.instruction instanceof PrioritizedDestinationInstruction instruction) {
            return instruction.getFilters();
        }
        return List.of();
    }

    /**
     * 在 CRN 的 DepartureLog 里记录这次发车：当前车站一条，外加每个字面量过滤条件一条。
     *
     * <p>CRN 查询的是 filter 而不是站名；不补过滤条件的话，同一 tick 的下一辆车仍会看到旧时间。
     * glob 过滤条件（含 {@code * ? [}）本身就能匹配当前站名，不重复写入。
     */
    public static RecordResult recordDeparture(ScheduleEntry entry, Train train, long gameTime) {
        TrainManager manager = TrainManager.getInstance();
        if (manager == null || train == null) {
            return new RecordResult(false, "none");
        }
        String trainName = train.name == null ? train.id.toString() : train.name.getString();

        UUID lineId = null;
        UUID categoryId = null;
        String stationName = null;
        var trackedOpt = manager.getTrain(train.id);
        if (trackedOpt.isPresent()) {
            var stopOpt = trackedOpt.get()
                    .getCurrentStop();
            if (stopOpt.isPresent()) {
                var stop = stopOpt.get();
                stationName = stop.getStationName();
                var section = stop.getSection();
                if (section != null) {
                    lineId = section.getTrainLineId();
                    categoryId = section.getTrainCategoryId();
                }
            }
        }
        if (stationName == null || stationName.isBlank()) {
            // 回退：后端还没有该列车的 TrackedTrain / current stop 时，直接用当前车站记录
            GlobalStation station = train.getCurrentStation();
            if (station != null && station.name != null && !station.name.isBlank()) {
                stationName = station.name;
            }
        }

        boolean recorded = false;
        StringBuilder how = new StringBuilder("none");
        if (stationName != null && !stationName.isBlank()) {
            manager.getDepartureLog()
                    .record(stationName, gameTime, lineId, categoryId, trainName);
            recorded = true;
            how = new StringBuilder("station:").append(stationName);
        }
        for (String filter : effectiveFilters(entry)) {
            if (filter == null || filter.isBlank() || filter.equals(stationName)) {
                continue;
            }
            if (filter.indexOf('*') >= 0 || filter.indexOf('?') >= 0 || filter.indexOf('[') >= 0) {
                // glob filter 本身就能匹配当前站名，不需要额外写入字面量
                continue;
            }
            manager.getDepartureLog()
                    .record(filter, gameTime, lineId, categoryId, trainName);
            recorded = true;
            how.append("+filter:")
                    .append(filter);
        }
        return new RecordResult(recorded, how.toString());
    }
}
