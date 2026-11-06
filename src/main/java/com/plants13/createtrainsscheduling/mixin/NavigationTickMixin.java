// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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
 *
 * <p>用 {@code try/finally} 保证 tick 抛异常时也会清理 ThreadLocal，避免越红灯授权泄漏到其它列车。
 */
@Mixin(value = Navigation.class, remap = false)
public abstract class NavigationTickMixin {

    @Shadow
    public Train train;

    @WrapMethod(method = "tick", remap = false)
    private void cts$wrapTick(Level level, Operation<Void> original) {
        SignalOverrideManager.TICKING.set(this.train);
        try {
            original.call(level);
        } finally {
            SignalOverrideManager.TICKING.remove();
        }
    }
}
