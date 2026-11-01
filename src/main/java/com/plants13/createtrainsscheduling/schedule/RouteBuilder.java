// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.foundation.utility.Pair;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * 构造"一次性路线"用的时刻表（Create 0.5.1 / 1.20.1 版）。
 *
 * <p>与 Create 6 的差异：
 * <ul>
 *   <li>{@code Schedule} 只有无参构造，字段 {@code entries/cyclic/savedProgress} 公开</li>
 *   <li>{@code ScheduleEntry} 只有无参构造，字段公开</li>
 *   <li>{@code ScheduleInstruction.setData(CompoundTag)} / {@code fromTag(CompoundTag)} 不带 registries</li>
 *   <li>{@code Pair} 位于 {@code com.simibubi.create.foundation.utility}</li>
 * </ul>
 *
 * <p>途经站"通过不停车"：装了 Steam 'n' Rails 时用它的 {@code railways:waypoint_destination} 指令，
 * 通过 Create 的指令表按 id 构造，无编译期依赖；没装则退回普通停靠。
 */
public final class RouteBuilder {

    private static final ResourceLocation SNR_WAYPOINT =
            new ResourceLocation("railways", "waypoint_destination");

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

    /** 按车站序列构造一个非循环时刻表（所有站停靠）。 */
    public static Schedule oneOff(List<String> stationNames) {
        return oneOff(stationNames, false);
    }

    /**
     * 按车站序列构造一个非循环时刻表。
     *
     * @param passThroughIntermediate true = 中间站用 SnR 的途经点指令（通过不停车），最后一站正常停靠
     */
    public static Schedule oneOff(List<String> stationNames, boolean passThroughIntermediate) {
        boolean useWaypoints = passThroughIntermediate && hasWaypointInstruction();
        List<ScheduleEntry> entries = new ArrayList<>();
        for (int i = 0; i < stationNames.size(); i++) {
            String stationName = stationNames.get(i);
            boolean last = i == stationNames.size() - 1;
            ScheduleInstruction instruction = useWaypoints && !last
                    ? waypointInstruction(stationName)
                    : destinationInstruction(stationName);
            ScheduleEntry entry = new ScheduleEntry();
            entry.instruction = instruction;
            entry.conditions = new ArrayList<>();
            entries.add(entry);
        }
        Schedule schedule = new Schedule();
        schedule.entries = entries;
        schedule.cyclic = false;
        schedule.savedProgress = 0;
        return schedule;
    }

    private static DestinationInstruction destinationInstruction(String stationName) {
        DestinationInstruction instruction = new DestinationInstruction();
        CompoundTag data = new CompoundTag();
        data.putString("Text", stationName);
        instruction.setData(data);
        return instruction;
    }

    private static ScheduleInstruction waypointInstruction(String stationName) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", SNR_WAYPOINT.toString());
        CompoundTag data = new CompoundTag();
        data.putString("Text", stationName);
        tag.put("Data", data);
        return ScheduleInstruction.fromTag(tag);
    }
}
