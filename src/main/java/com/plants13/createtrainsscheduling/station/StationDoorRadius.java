// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.station;

import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.util.Mth;

/**
 * 车站“开门半径”设置（F7）。
 *
 * <p>存放在车站方块实体的 persistent data 里，随方块 NBT 保存；0 表示不限制。
 */
public final class StationDoorRadius {

    public static final String KEY = "CtsDoorRadius";
    public static final int MAX = 256;

    private StationDoorRadius() {}

    public static int get(StationBlockEntity station) {
        return station == null ? 0 : Mth.clamp(station.getPersistentData()
                .getInt(KEY), 0, MAX);
    }

    public static void set(StationBlockEntity station, int radius) {
        if (station == null) {
            return;
        }
        station.getPersistentData()
                .putInt(KEY, Mth.clamp(radius, 0, MAX));
        station.setChanged();
    }
}
