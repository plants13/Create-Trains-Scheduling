// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.compat.crn.DepartureQueue;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;

import de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition;

/**
 * 在 CRN 计算完分隔列车扣车时间后叠加“先到先发”队列等待。
 *
 * <p>CRN 的 {@code ScheduleRuntimeMixin} 会调用本方法决定是否取消 tickConditions，
 * 因此这里返回的排队时间会直接让后续列车继续留在站台。
 */
@Mixin(value = TrainSeparationCondition.class, remap = false)
public class TrainSeparationQueueMixin {

    @Inject(method = "remainingHoldTicks", at = @At("RETURN"), cancellable = true, remap = false)
    private static void cts$queueHold(Train train, ScheduleEntry entry,
            CallbackInfoReturnable<Long> callback) {
        long original = callback.getReturnValue();
        long adjusted = DepartureQueue.adjust(train, entry, original);
        if (adjusted != original) {
            callback.setReturnValue(adjusted);
        }
    }
}
