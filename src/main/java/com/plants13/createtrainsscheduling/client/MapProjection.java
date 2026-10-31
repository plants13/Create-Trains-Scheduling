// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;

/**
 * 世界坐标与地图屏幕坐标之间的换算。
 *
 * <p>两种地图 mod 的缩放公式不同，但换算方式一致；由各自的接入层构造后交给
 * {@link DispatchOverlay} 共用。
 */
public record MapProjection(double cameraX, double cameraZ, double scale,
                            double screenWidth, double screenHeight) {

    /** 通用构造：scale 已换算成"世界 1 格 = 屏幕多少像素"。 */
    public static MapProjection of(double cameraX, double cameraZ, double scale) {
        if (!(scale > 0) || Double.isNaN(scale) || Double.isInfinite(scale)) {
            return null;
        }
        Window window = Minecraft.getInstance()
                .getWindow();
        return new MapProjection(cameraX, cameraZ, scale,
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    /** Xaero：原始 scale 需要除以 GUI 缩放和界面缩放。 */
    public static MapProjection fromXaero(double cameraX, double cameraZ, double mapScale) {
        Window window = Minecraft.getInstance()
                .getWindow();
        double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        double interfaceScale = (double) window.getWidth() / window.getScreenWidth();
        return of(cameraX, cameraZ, mapScale / guiScale / interfaceScale);
    }

    /** JourneyMap：blockSize 只需除以 GUI 缩放。 */
    public static MapProjection fromJourneyMap(double cameraX, double cameraZ, double blockSize) {
        Window window = Minecraft.getInstance()
                .getWindow();
        double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
        return of(cameraX, cameraZ, blockSize / guiScale);
    }

    public void applyTo(PoseStack pose) {
        pose.translate(screenWidth / 2.0f, screenHeight / 2.0f, 0);
        pose.scale((float) scale, (float) scale, 1);
        pose.translate(-cameraX, -cameraZ, 0);
    }

    public double screenToWorldX(double screenX) {
        return cameraX + (screenX - screenWidth / 2.0) / scale;
    }

    public double screenToWorldZ(double screenY) {
        return cameraZ + (screenY - screenHeight / 2.0) / scale;
    }
}
