// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.plants13.createtrainsscheduling.Config;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.compat.crn.SeparationInterop;
import com.plants13.createtrainsscheduling.compat.crn.StationDepartureQueue;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 复刻 CRN 0.9 的发车时序：条件完成、列车即将离站的同一 tick 就把它记进 DepartureLog。
 *
 * <p>0.10 把扣车改成纯计算，真正的发车记录在 {@code TrackedTrain#onDeparture} 才写入，
 * 导致多辆同名车站列车同 tick 扣车归零、一起发车。这里在 {@link ScheduleRuntime#tickConditions}
 * 完成一个条目的瞬间提前记录，使同一 tick 后续求值的列车立刻读到新的发车时间，从而恢复 0.9 的串行行为。
 *
 * <p>CRN 相关的查找与记录统一委托给 {@link SeparationInterop}（本项目里只有 {@code compat/crn}
 * 和直接以 CRN 类为注入目标的 mixin 允许引用 CRN 类型）。
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
                    SeparationInterop.find(entry) != null);
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
        if (SeparationInterop.find(entry) == null) {
            return;
        }

        String trainName = runtime.train.name == null ? runtime.train.id.toString() : runtime.train.name.getString();
        long now = level.getGameTime();
        SeparationInterop.RecordResult result = SeparationInterop.recordDeparture(entry, runtime.train, now);

        StationDepartureQueue.onDeparted(runtime.train, SeparationInterop.intervalTicks(entry));

        CreateTrainsScheduling.LOGGER.info(
                "[CTS separation] entry complete train={} entry={} separation={} recorded={} via={}",
                trainName,
                this.cts$entryBefore,
                true,
                result.recorded(),
                result.how());
    }
}
