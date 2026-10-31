// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.signal.SignalEdgeGroup;

/**
 * 持有"越红灯授权"的列车，不再被已占用的信号区段拦截。
 *
 * <p>注入目标方法本身（而不是调用点），因为 Create 的调用点在 lambda 内，重定向不可靠。
 */
@Mixin(value = SignalEdgeGroup.class, remap = false)
public abstract class SignalEdgeGroupMixin {

    @Inject(
            method = "isOccupiedUnless(Lcom/simibubi/create/content/trains/entity/Train;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void cts$allowOverride(Train train, CallbackInfoReturnable<Boolean> cir) {
        if (SignalOverrideManager.isOverriding(train)) {
            cir.setReturnValue(false);
        }
    }
}
