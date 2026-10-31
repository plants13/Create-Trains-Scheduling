package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.world.level.Level;

/**
 * 记录"当前正在被 Navigation 驱动的列车"，供信号判定注入使用。
 *
 * <p>Create 的信号扫描在 {@code Navigation#tick} 的 lambda 里，直接重定向 tick 方法体扫不到目标，
 * 因此改成：这里只维护线程上下文，真正的放行在
 * {@link SignalEdgeGroupMixin} / {@link SignalBoundaryMixin} 里注入目标方法本身。
 */
@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationTickMixin {

    @Shadow
    public Train train;

    @Inject(method = "tick", at = @At("HEAD"))
    private void cts$rememberTickingTrain(Level level, CallbackInfo ci) {
        SignalOverrideManager.TICKING.set(this.train);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void cts$forgetTickingTrain(Level level, CallbackInfo ci) {
        SignalOverrideManager.TICKING.remove();
    }
}
