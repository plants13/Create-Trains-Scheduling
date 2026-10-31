// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client.journeymap;

import com.mojang.blaze3d.platform.Window;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.client.DispatchOverlay;
import com.plants13.createtrainsscheduling.client.MapProjection;
import com.plants13.createtrainsscheduling.client.MapScreens;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.client.display.Context.UI;
import journeymap.api.v2.client.event.FullscreenRenderEvent;
import journeymap.api.v2.client.fullscreen.IFullscreen;
import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.common.event.FullscreenEventRegistry;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.lwjgl.glfw.GLFW;

/**
 * JourneyMap 接入层（软依赖）。
 *
 * <p>由 JourneyMap 通过 {@code @JourneyMapPlugin} 注解自动发现；本类只在装了 JourneyMap 时才会被加载，
 * 其他代码不要引用它。
 *
 * <p>渲染：订阅 {@link FullscreenEventRegistry#FULLSCREEN_RENDER_EVENT}，把相机/缩放换算成
 * {@link MapProjection} 后交给共享的 {@link DispatchOverlay}。
 * 输入：注册 NeoForge 事件监听，把鼠标/ESC 转给共享层。
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public class JourneyMapDispatchPlugin implements IClientPlugin {

    private static final String MOD_ID = CreateTrainsScheduling.MODID;
    private static volatile MapProjection lastProjection;
    private static boolean failed;

    @Override
    public void initialize(IClientAPI jmClientApi) {
        FullscreenEventRegistry.FULLSCREEN_RENDER_EVENT.subscribe(MOD_ID, this::onRender);
        NeoForge.EVENT_BUS.addListener(this::onMouseButton);
        NeoForge.EVENT_BUS.addListener(this::onKeyPressed);
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    private void onRender(FullscreenRenderEvent event) {
        if (failed) {
            return;
        }
        try {
            IFullscreen fullscreen = event.getFullscreen();
            Screen screen = fullscreen.getScreen();
            UIState state = fullscreen.getUiState();
            if (state == null || state.ui != UI.Fullscreen || !state.active) {
                return;
            }
            MapProjection projection = MapProjection.fromJourneyMap(
                    fullscreen.getCenterBlockX(true), fullscreen.getCenterBlockZ(true), state.blockSize);
            if (projection == null) {
                return;
            }
            lastProjection = projection;

            GuiGraphics graphics = event.getGraphics();
            DispatchOverlay.render(graphics, projection, event.getPartialTicks());
        } catch (Throwable t) {
            failed = true;
            CreateTrainsScheduling.LOGGER.error(
                    "[CTS] JourneyMap overlay failed, disabling it for this session", t);
        }
    }

    private void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!MapScreens.isJourneyMap(mc.screen)) {
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

    private void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE && DispatchOverlay.onEsc()) {
            event.setCanceled(true);
        }
    }

}
