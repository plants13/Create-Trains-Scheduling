// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

/** 判断当前打开的界面是哪个地图 mod 的全屏地图（用类名字符串，避免硬依赖）。 */
public final class MapScreens {

    private static final String XAERO_MAP_CLASS = "xaero.map.gui.GuiMap";
    private static final String JOURNEYMAP_FULLSCREEN_CLASS = "journeymap.client.ui.fullscreen.Fullscreen";

    private MapScreens() {}

    public static boolean isXaero(Object screen) {
        return screen != null && screen.getClass().getName().startsWith(XAERO_MAP_CLASS);
    }

    public static boolean isJourneyMap(Object screen) {
        return screen != null && screen.getClass().getName().startsWith(JOURNEYMAP_FULLSCREEN_CLASS);
    }

    public static boolean isAnyMap(Object screen) {
        return isXaero(screen) || isJourneyMap(screen);
    }
}
