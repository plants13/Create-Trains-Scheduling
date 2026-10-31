// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.signal.SignalBoundary;

/**
 * 事件制越红灯授权：列车<em>实际通过</em>一个信号后，授权立即失效。
 *
 * <p>实现方式是在 {@code Train#frontSignalListener()} / {@code #backSignalListener()} 的返回值外面
 * 包一层：Create 在列车真实移动时会把这两个监听器交给 {@code TravellingPoint}，
 * 每经过一个边点（信号/车站等）就会回调。我们只在边点是 {@link SignalBoundary} 时消费授权。
 *
 * <p>为什么不用 Navigation 的信号扫描：那是"提前看"（每 tick 反复扫），不是"已经通过"，
 * 用它来消费授权会导致刚授权就立刻失效。
 */
@Mixin(value = Train.class, remap = false)
public abstract class TrainSignalPassMixin {

    @Inject(method = "frontSignalListener", at = @At("RETURN"), cancellable = true)
    private void cts$wrapFrontSignalListener(CallbackInfoReturnable<TravellingPoint.IEdgePointListener> cir) {
        cir.setReturnValue(cts$wrap(cir.getReturnValue()));
    }

    @Inject(method = "backSignalListener", at = @At("RETURN"), cancellable = true)
    private void cts$wrapBackSignalListener(CallbackInfoReturnable<TravellingPoint.IEdgePointListener> cir) {
        cir.setReturnValue(cts$wrap(cir.getReturnValue()));
    }

    private TravellingPoint.IEdgePointListener cts$wrap(TravellingPoint.IEdgePointListener original) {
        if (original == null) {
            return null;
        }
        Train self = (Train) (Object) this;
        return (distance, couple) -> {
            boolean result = original.test(distance, couple);
            if (couple != null && couple.getFirst() instanceof SignalBoundary) {
                SignalOverrideManager.consumeNextSignal(self);
            }
            return result;
        };
    }
}
