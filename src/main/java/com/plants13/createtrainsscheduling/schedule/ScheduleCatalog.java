// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 外部时刻表编辑器用的"目录"数据：CRN 线路 / 种类 + 世界内所有车站名。
 *
 * <p>只读；作为 JSON 发给客户端写入剪贴板，供单文件 HTML 编辑器做下拉选项。
 */
public final class ScheduleCatalog {

    public static final String FORMAT = "create-trains-scheduling/editor-catalog";
    public static final int VERSION = 1;

    private ScheduleCatalog() {}

    public static JsonObject build() {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);

        root.add("lines", entries(CrnBridge.allLines()));
        root.add("categories", entries(CrnBridge.allCategories()));
        root.add("stations", stations());
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
