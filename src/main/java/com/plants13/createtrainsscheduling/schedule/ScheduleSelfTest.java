// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import java.util.ArrayList;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

/**
 * 开发期自检：验证 JSON &lt;-&gt; Schedule 往返无损。
 *
 * <p>在 {@code runData}（GatherDataEvent）里执行，因为那时能拿到真实的
 * {@link HolderLookup.Provider}，不需要开客户端/服务器。结果输出到日志。
 */
public final class ScheduleSelfTest {

    private ScheduleSelfTest() {}

    public static void run(HolderLookup.Provider registries) {
        try {
            Schedule original = buildSample(registries);
            CompoundTag originalTag = original.write(registries);

            String json = ScheduleJson.toJsonString(original, registries);
            Schedule parsed = ScheduleJson.fromJsonString(json, registries);
            CompoundTag roundTripTag = parsed.write(registries);

            boolean nbtEqual = originalTag.equals(roundTripTag);
            boolean entryCountEqual = parsed.entries.size() == original.entries.size();

            if (nbtEqual && entryCountEqual) {
                CreateTrainsScheduling.LOGGER.info(
                        "[CTS selftest] schedule JSON round-trip PASS ({} entries, {} chars)",
                        original.entries.size(), json.length());
            } else {
                CreateTrainsScheduling.LOGGER.error(
                        "[CTS selftest] schedule JSON round-trip FAIL (nbtEqual={}, entryCountEqual={})\noriginal: {}\nroundtrip: {}",
                        nbtEqual, entryCountEqual, originalTag, roundTripTag);
            }
        } catch (Throwable t) {
            CreateTrainsScheduling.LOGGER.error("[CTS selftest] schedule JSON round-trip CRASHED", t);
        }
    }

    private static Schedule buildSample(HolderLookup.Provider registries) {
        Schedule schedule = new Schedule();
        schedule.cyclic = false;

        DestinationInstruction instruction = new DestinationInstruction();
        CompoundTag data = new CompoundTag();
        data.putString("Text", "Test Station");
        instruction.setData(registries, data);

        schedule.entries.add(new ScheduleEntry(instruction, new ArrayList<>()));
        return schedule;
    }
}
