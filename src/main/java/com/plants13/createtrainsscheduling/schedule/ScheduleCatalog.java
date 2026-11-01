// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.createmod.catnip.data.Pair;
import net.minecraft.resources.ResourceLocation;

/**
 * 外部时刻表编辑器用的"目录"数据：CRN 线路 / 种类 + 世界内所有车站名 +
 * 已注册的指令/条件 id（供编辑器下拉选择）。
 *
 * <p>只读；作为 JSON 发给客户端写入剪贴板，供单文件 HTML 编辑器做下拉选项。
 */
public final class ScheduleCatalog {

    public static final String FORMAT = "create-trains-scheduling/editor-catalog";
    public static final int VERSION = 2;

    private ScheduleCatalog() {}

    public static JsonObject build() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);

        root.add("lines", entries(CrnBridge.allLines()));
        root.add("categories", entries(CrnBridge.allCategories()));
        root.add("stations", stations());
        root.add("instructions", instructionIds());
        root.add("conditions", conditionIds());
        return root;
    }

    private static JsonArray entries(List<CrnBridge.NamedEntry> entries) {
        JsonArray array = new JsonArray();
        for (CrnBridge.NamedEntry entry : entries) {
            JsonObject item = new JsonObject();
            item.addProperty("id", entry.id());
            item.addProperty("name", entry.name());
            item.addProperty("color", entry.color());
            array.add(item);
        }
        return array;
    }

    private static JsonArray instructionIds() {
        JsonArray array = new JsonArray();
        for (Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> pair : Schedule.INSTRUCTION_TYPES) {
            array.add(pair.getFirst()
                    .toString());
        }
        return array;
    }

    private static JsonArray conditionIds() {
        JsonArray array = new JsonArray();
        for (Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> pair : Schedule.CONDITION_TYPES) {
            array.add(pair.getFirst()
                    .toString());
        }
        return array;
    }

    private static JsonArray stations() {
        Set<String> names = new TreeSet<>(CrnBridge.knownStations());
        try {
            for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
                for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                    if (station.name != null && !station.name.isBlank()) {
                        names.add(station.name);
                    }
                }
            }
        } catch (Throwable ignored) {
            // 轨道网络未就绪时只返回 CRN 已知车站
        }
        JsonArray array = new JsonArray();
        for (String name : names) {
            array.add(name);
        }
        return array;
    }
}
