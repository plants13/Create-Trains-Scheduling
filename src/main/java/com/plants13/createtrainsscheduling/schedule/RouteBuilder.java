package com.plants13.createtrainsscheduling.schedule;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * 构造"一次性路线"用的时刻表。
 *
 * <p>一次性 = 非循环：列车跑完最后一个站后 Create 会自动 pause/completed，不会绕回去。
 */
public final class RouteBuilder {

    private RouteBuilder() {}

    /**
     * 按车站序列构造一个非循环时刻表。
     *
     * @param registries   注册表访问（服务器上 {@code server.registryAccess()}）
     * @param stationNames 依次经过的车站名
     */
    public static Schedule oneOff(HolderLookup.Provider registries, List<String> stationNames) {
        List<ScheduleEntry> entries = new ArrayList<>();
        for (String stationName : stationNames) {
            DestinationInstruction instruction = new DestinationInstruction();
            CompoundTag data = new CompoundTag();
            data.putString("Text", stationName);
            instruction.setData(registries, data);
            entries.add(new ScheduleEntry(instruction, new ArrayList<>()));
        }
        return new Schedule(entries, false, 0);
    }
}
