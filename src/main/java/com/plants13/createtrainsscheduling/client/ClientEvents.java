// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.compat.trainmap.TrainMapSyncClient;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import java.util.UUID;

import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * 客户端事件入口。
 *
 * <p>注意：这个类本身不引用任何 Xaero 类型；只有确认 Xaero 已加载后才会触碰
 * {@link XaeroOverlay} / Xaero 相关逻辑，避免没装 Xaero 时的 NoClassDefFoundError。
 */
@EventBusSubscriber(modid = CreateTrainsScheduling.MODID, value = Dist.CLIENT)
public final class ClientEvents {

    private static int statusRefreshTicks;

    private ClientEvents() {}

    @SubscribeEvent
    public static void onMouseButtonPre(InputEvent.MouseButton.Pre event) {
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("xaeroworldmap")) {
            return;
        }
        XaeroOverlay.onMouseClick(event);
    }

    /** 地图 Screen 完全渲染完之后再画菜单，保证在 Create 叠加层之上。 */
    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!MapScreens.isAnyMap(event.getScreen())) {
            return;
        }
        DispatchOverlay.renderScreenLayer(event.getGuiGraphics());
    }

    /** 菜单/选站模式下按 Esc 关闭，不关地图。 */
    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!XaeroDispatchMenu.isActive()) {
            return;
        }
        if (event.getKeyCode() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            XaeroDispatchMenu.close();
            event.setCanceled(true);
        }
    }

    /**
     * 地图打开期间主动请求 Create 的 train map 同步数据。
     *
     * <p>Create 自己只在其 overlay 开启时才请求；我们关掉它的 overlay 时也需要位置数据，
     * 所以这里补一份请求（Create 内部对请求有节流）。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ModList modList = ModList.get();
        if (modList == null || !modList.isLoaded("xaeroworldmap")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!MapScreens.isAnyMap(mc.screen)) {
            return;
        }
        try {
            TrainMapSyncClient.requestData();
        } catch (Throwable ignored) {
            // Create 内部实现变动时忽略
        }

        DispatchOverlay.refreshConfig();

        // Create 的 train map overlay 开关关闭时，收起残留菜单
        if (XaeroDispatchMenu.isActive() && !DispatchOverlay.isTrainMapOverlayEnabled()) {
            XaeroDispatchMenu.close();
        }

        // 菜单打开期间定期刷新列车真实状态（服务端权威）
        if (XaeroDispatchMenu.isActive()) {
            if (++statusRefreshTicks >= 20) {
                statusRefreshTicks = 0;
                UUID id = XaeroDispatchMenu.trainId();
                if (id != null) {
                    TrainStatusCache.request(id);
                }
            }
        } else {
            statusRefreshTicks = 0;
        }
    }
}
