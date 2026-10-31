// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import com.mojang.blaze3d.platform.Window;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.client.event.InputEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Xaero 世界地图的接入层：只负责把 Xaero 的相机/缩放换算成 {@link MapProjection}，
 * 并把鼠标事件转给 {@link DispatchOverlay}。真正的绘制/交互逻辑在共享层。
 */
public final class XaeroOverlay {

    private static volatile MapProjection lastProjection;

    private XaeroOverlay() {}

    /** 由 {@code GuiMapMixin} 在 Xaero 地图渲染过程中调用。 */
    public static void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                              double cameraX, double cameraZ, double mapScale) {
        MapProjection projection = MapProjection.fromXaero(cameraX, cameraZ, mapScale);
        if (projection == null) {
            return;
        }
        lastProjection = projection;
        DispatchOverlay.render(graphics, projection, partialTicks);
    }

    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!MapScreens.isXaero(mc.screen) || event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        MapProjection projection = lastProjection;
        if (projection == null) {
            return;
        }
        Window window = mc.getWindow();
        double guiMouseX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double guiMouseY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        if (DispatchOverlay.onClick(projection, guiMouseX, guiMouseY, event.getButton())) {
            event.setCanceled(true);
        }
    }

    public static boolean isXaeroMapScreen(Object screen) {
        return MapScreens.isXaero(screen);
    }
}
