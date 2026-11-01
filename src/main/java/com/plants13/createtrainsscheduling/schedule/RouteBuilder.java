// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 构造"一次性路线"用的时刻表。
 *
 * <p>一次性 = 非循环：列车跑完最后一个站后 Create 会自动 pause/completed，不会绕回去。
 *
 * <p>途经站支持"通过不停车"：Steam 'n' Rails 注册了 {@code railways:waypoint_destination}
 * 指令（继承 Create 的 DestinationInstruction），我们用 Create 的指令表按 id 查表构造，
 * 因此不需要编译期依赖 SnR；没装 SnR 时自动退回普通停靠。
 */
public final class RouteBuilder {

    private static final ResourceLocation SNR_WAYPOINT =
            ResourceLocation.fromNamespaceAndPath("railways", "waypoint_destination");

    private RouteBuilder() {}

    /** 是否安装了提供"途经点"指令的 SnR。 */
    public static boolean hasWaypointInstruction() {
        for (Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> pair : Schedule.INSTRUCTION_TYPES) {
            if (pair.getFirst()
                    .equals(SNR_WAYPOINT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按车站序列构造一个非循环时刻表（所有站停靠）。
     */
    public static Schedule oneOff(HolderLookup.Provider registries, List<String> stationNames) {
        return oneOff(registries, stationNames, false);
    }

    /**
     * 按车站序列构造一个非循环时刻表。
     *
     * @param passThroughIntermediate true = 中间站用 SnR 的途经点指令（通过不停车），
     *                                最后一个站仍然是正常目的地
     */
    public static Schedule oneOff(HolderLookup.Provider registries, List<String> stationNames,
                                  boolean passThroughIntermediate) {
        boolean useWaypoints = passThroughIntermediate && hasWaypointInstruction();
        List<ScheduleEntry> entries = new ArrayList<>();
        for (int i = 0; i < stationNames.size(); i++) {
            String stationName = stationNames.get(i);
            boolean last = i == stationNames.size() - 1;
            ScheduleInstruction instruction = useWaypoints && !last
                    ? waypointInstruction(registries, stationName)
                    : destinationInstruction(registries, stationName);
            entries.add(new ScheduleEntry(instruction, new ArrayList<>()));
        }
        return new Schedule(entries, false, 0);
    }

    private static DestinationInstruction destinationInstruction(HolderLookup.Provider registries,
                                                                 String stationName) {
        DestinationInstruction instruction = new DestinationInstruction();
        CompoundTag data = new CompoundTag();
        data.putString("Text", stationName);
        instruction.setData(registries, data);
        return instruction;
    }

    private static ScheduleInstruction waypointInstruction(HolderLookup.Provider registries, String stationName) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", SNR_WAYPOINT.toString());
        CompoundTag data = new CompoundTag();
        data.putString("Text", stationName);
        tag.put("Data", data);
        return ScheduleInstruction.fromTag(registries, tag);
    }
}
