// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin.xaero;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.client.XaeroOverlay;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 在 Xaero 世界地图渲染过程中叠加我们的调度层。
 *
 * <p>注入点与 Create 本体的 Xaero 集成完全一致（{@code GuiGraphics.blit} 调用前），
 * 这样我们的坐标变换和 Create 画车站/线路时处于同一渲染阶段，不会出现整体上下偏移。
 *
 * <p>普通类不能直接引用 mixin 类，所以相机/缩放用 {@link Shadow} 读出后作为普通参数传入。
 * {@code require = 0} 让注入点失效时静默降级；运行时异常有一次性闩锁。
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class GuiMapMixin {

    @Shadow
    private double cameraX;

    @Shadow
    private double cameraZ;

    @Shadow
    private double scale;

    @Unique
    private boolean cts$overlayFailed = false;

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V"),
            require = 0)
    private void cts$renderDispatchOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                                           CallbackInfo ci) {
        if (cts$overlayFailed) {
            return;
        }
        try {
            XaeroOverlay.render(graphics, mouseX, mouseY, partialTicks, cameraX, cameraZ, scale);
        } catch (Throwable t) {
            cts$overlayFailed = true;
            CreateTrainsScheduling.LOGGER.error("[CTS] Xaero overlay failed, disabling it for this session", t);
        }
    }
}
