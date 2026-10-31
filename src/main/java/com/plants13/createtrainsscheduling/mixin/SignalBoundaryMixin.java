package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.signal.SignalBoundary;

/**
 * 持有"越红灯授权"的列车，忽略信号被红石强制置红的状态。
 *
 * <p>用 {@link SignalOverrideManager#TICKING} 判断"当前是谁在扫描信号"，
 * 这样信号灯的渲染/状态计算不受影响，只影响导航判定。
 */
@Mixin(value = SignalBoundary.class, remap = false)
public abstract class SignalBoundaryMixin {

    @Inject(
            method = "isForcedRed(Lcom/simibubi/create/content/trains/graph/TrackNode;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void cts$allowForcedRedOverride(TrackNode side, CallbackInfoReturnable<Boolean> cir) {
        Train ticking = SignalOverrideManager.TICKING.get();
        if (ticking != null && SignalOverrideManager.isOverriding(ticking)) {
            cir.setReturnValue(false);
        }
    }
}
