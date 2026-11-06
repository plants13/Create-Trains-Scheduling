// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import java.util.List;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.plants13.createtrainsscheduling.Config;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;

import de.mrjulsen.crn.core.TrainManager;
import de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition;
import de.mrjulsen.crn.data.schedule.instruction.PrioritizedDestinationInstruction;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 复刻 CRN 0.9 的发车时序：条件完成、列车即将离站的同一 tick 就把它记进 DepartureLog。
 *
 * <p>0.10 把扣车改成纯计算，真正的发车记录在 {@code TrackedTrain#onDeparture} 才写入，
 * 导致多辆同名车站列车同 tick 扣车归零、一起发车。这里在 {@link ScheduleRuntime#tickConditions}
 * 完成一个条目的瞬间提前记录，使同一 tick 后续求值的列车立刻读到新的发车时间，从而恢复 0.9 的串行行为。
 *
 * <p>注意 CRN 的扣车查询用的是“条目指令 filter / 自定义 StationFilter”，不是当前车站名；
 * 因此这里除了当前车站，还会把实际查询用的 filter 一起写进 DepartureLog。
 */
@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeSeparationMixin {

    @Unique
    private boolean cts$wasPostTransit;

    @Unique
    private int cts$entryBefore;

    @Unique
    private long cts$lastDebugTick = -100L;

    @Unique
    private boolean cts$loggedActive;

    @Inject(method = "tickConditions", at = @At("HEAD"), remap = false, require = 1)
    private void cts$captureEntry(Level level, CallbackInfo callback) {
        this.cts$wasPostTransit = false;
        if (!(level instanceof ServerLevel)) {
            return;
        }
        ScheduleRuntime runtime = (ScheduleRuntime) (Object) this;
        if (!this.cts$loggedActive) {
            this.cts$loggedActive = true;
            CreateTrainsScheduling.LOGGER.info("[CTS separation] claim mixin active (tickConditions reached)");
        }
        if (runtime.state != ScheduleRuntime.State.POST_TRANSIT || runtime.schedule == null) {
            return;
        }
        if (runtime.currentEntry < 0 || runtime.currentEntry >= runtime.schedule.entries.size()) {
            return;
        }
        this.cts$wasPostTransit = true;
        this.cts$entryBefore = runtime.currentEntry;
        if (Config.debugLogging() && level.getGameTime() - this.cts$lastDebugTick >= 20L) {
            this.cts$lastDebugTick = level.getGameTime();
            ScheduleEntry entry = runtime.schedule.entries.get(runtime.currentEntry);
            CreateTrainsScheduling.LOGGER.info(
                    "[CTS separation] waiting train={} entry={} progress={} separation={}",
                    runtime.train == null ? "?" : runtime.train.name.getString(),
                    runtime.currentEntry,
                    runtime.conditionProgress,
                    TrainSeparationCondition.isPresentIn(entry));
        }
    }

    @Inject(method = "tickConditions", at = @At("RETURN"), remap = false, require = 1)
    private void cts$claimDepartureOnCompletion(Level level, CallbackInfo callback) {
        if (!this.cts$wasPostTransit) {
            return;
        }
        this.cts$wasPostTransit = false;

        ScheduleRuntime runtime = (ScheduleRuntime) (Object) this;
        if (runtime.schedule == null || runtime.state == null || runtime.train == null) {
            return;
        }
        // 本 tick 没有完成条目（仍在等待/被 CRN 扣车取消）时不记录
        if (runtime.state == ScheduleRuntime.State.POST_TRANSIT && runtime.currentEntry == this.cts$entryBefore) {
            return;
        }
        if (this.cts$entryBefore < 0 || this.cts$entryBefore >= runtime.schedule.entries.size()) {
            return;
        }
        ScheduleEntry entry = runtime.schedule.entries.get(this.cts$entryBefore);
        if (!TrainSeparationCondition.isPresentIn(entry)) {
            return;
        }

        long now = level.getGameTime();
        String trainName = runtime.train.name == null ? runtime.train.id.toString() : runtime.train.name.getString();
        TrainManager manager = TrainManager.getInstance();
        if (manager == null) {
            return;
        }

        UUID lineId = null;
        UUID categoryId = null;
        String stationName = null;
        var trackedOpt = manager.getTrain(runtime.train.id);
        if (trackedOpt.isPresent()) {
            var tracked = trackedOpt.get();
            var stopOpt = tracked.getCurrentStop();
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
            GlobalStation station = runtime.train.getCurrentStation();
            if (station != null && station.name != null && !station.name.isBlank()) {
                stationName = station.name;
            }
        }

        boolean recorded = false;
        StringBuilder how = new StringBuilder("none");
        if (stationName != null && !stationName.isBlank()) {
            manager.getDepartureLog().record(stationName, now, lineId, categoryId, trainName);
            recorded = true;
            how = new StringBuilder("station:").append(stationName);
        }

        // CRN 实际查询的是 filter，而不是当前站名；不补这一条，同 tick 的下一辆车仍会看到旧时间
        for (String filter : cts$effectiveFilters(entry)) {
            if (filter == null || filter.isBlank() || filter.equals(stationName)) {
                continue;
            }
            if (filter.indexOf('*') >= 0 || filter.indexOf('?') >= 0 || filter.indexOf('[') >= 0) {
                // glob filter 本身就能匹配当前站名，不需要额外写入字面量
                continue;
            }
            manager.getDepartureLog().record(filter, now, lineId, categoryId, trainName);
            recorded = true;
            how.append("+filter:").append(filter);
        }

        CreateTrainsScheduling.LOGGER.info(
                "[CTS separation] entry complete train={} entry={} separation={} recorded={} via={}",
                trainName,
                this.cts$entryBefore,
                true,
                recorded,
                how);
    }

    @Unique
    private static List<String> cts$effectiveFilters(ScheduleEntry entry) {
        TrainSeparationCondition condition = null;
        if (entry.conditions != null) {
            for (var group : entry.conditions) {
                for (ScheduleWaitCondition wait : group) {
                    if (wait instanceof TrainSeparationCondition separation) {
                        condition = separation;
                        break;
                    }
                }
                if (condition != null) {
                    break;
                }
            }
        }
        if (condition != null) {
            String custom = condition.getData().getString(TrainSeparationCondition.NBT_STATION_FILTER);
            if (custom != null && !custom.isBlank()) {
                return List.of(custom);
            }
        }
        if (entry.instruction instanceof DestinationInstruction instruction) {
            return List.of(instruction.getFilter());
        }
        if (entry.instruction instanceof PrioritizedDestinationInstruction instruction) {
            return instruction.getFilters();
        }
        return List.of();
    }
}
