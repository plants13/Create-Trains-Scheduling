// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.compat.crn.StationDepartureQueue;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;

import de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition;

/**
 * 把车站级 FIFO 等待叠加到 CRN 的 remainingHoldTicks 上。
 *
 * <p>CRN 只按目标站 filter 分组；这个注入让同一车站、不同目的地的列车也按到达顺序排队，
 * 并让倒计时（同样读取 remainingHoldTicks）显示“轮到本车”的时间。
 */
@Mixin(value = TrainSeparationCondition.class, remap = false)
public abstract class TrainSeparationQueueMixin {

    @Inject(method = "remainingHoldTicks", at = @At("RETURN"), remap = false, cancellable = true, require = 1)
    private static void cts$applyStationQueue(Train train, ScheduleEntry entry, CallbackInfoReturnable<Long> cir) {
        long original = cir.getReturnValue();
        long interval = cts$interval(entry);
        long adjusted = StationDepartureQueue.adjust(train, original, interval);
        if (adjusted != original) {
            cir.setReturnValue(adjusted);
        }
    }

    private static long cts$interval(ScheduleEntry entry) {
        long interval = 0L;
        if (entry == null || entry.conditions == null) {
            return 0L;
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
}
