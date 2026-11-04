// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

/**
 * 暴露 Screen 的 protected 方法 addRenderableWidget，供车站 GUI 附加控件使用。
 *
 * <p>Mixin 的 {@code @Shadow} 只会在目标类里查找成员，不会去父类找；因此这里单独
 * 给 {@link Screen} 做 accessor（NeoForge 1.21 运行时使用官方映射名）。
 */
@Mixin(value = Screen.class, remap = false)
public interface ScreenWidgetAccessor {

    @Invoker("addRenderableWidget")
    <T extends GuiEventListener & Renderable & NarratableEntry> T cts$addRenderableWidget(T widget);
}
