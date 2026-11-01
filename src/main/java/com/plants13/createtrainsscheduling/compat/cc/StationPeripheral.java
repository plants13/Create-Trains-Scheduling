// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.cc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;

/**
 * 挂在 Create 车站方块实体上的 CC:Tweaked 外设（只读，**只提供调度专属数据**）。
 *
 * <p>去重说明：车站名、停靠列车、时刻表、{@code train_arrival}/{@code train_departure} 事件
 * 已经由 Create 自带的 {@code Create_Station} 外设提供，本外设不再重复暴露。
 *
 * <p>Lua 侧用法：
 * <pre>
 * local station = peripheral.find("cts_station")
 * station.getTrains()          -- 全服被跟踪列车 + 调度状态
 * station.getTrain("车名或UUID") -- 单列车调度状态（含越红灯授权）
 * station.getCircuit("车名或UUID") -- CRN 交路：线路 / 交路终点 / 种类
 * station.getVersion()
 * </pre>
 *
 * <p>只读：不提供下发/改时刻表等写操作。
 */
public final class StationPeripheral implements IPeripheral {

    private static final String TYPE = "cts_station";

    private final StationBlockEntity stationBlock;

    public StationPeripheral(StationBlockEntity stationBlock) {
        this.stationBlock = stationBlock;
    }

    // ------------------------------------------------------------------
    // IPeripheral
    // ------------------------------------------------------------------

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other || (other instanceof StationPeripheral peripheral
                && peripheral.stationBlock == stationBlock);
    }

    // ------------------------------------------------------------------
    // Lua 方法（仅调度专属数据，不与 Create_Station 重复）
    // ------------------------------------------------------------------

    /** 服务器上所有被跟踪的列车，含调度状态。 */
    @LuaFunction(mainThread = true)
    public final List<Object> getTrains() {
        List<Object> result = new ArrayList<>();
        for (Train train : Create.RAILWAYS.trains.values()) {
            result.add(trainInfo(train));
        }
        return result;
    }

    /** 按车名或 UUID 查询单列车调度状态。 */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTrain(String reference) {
        Train train = resolveTrain(reference);
        return train == null ? null : trainInfo(train);
    }

    /** CRN 交路数据：线路 / 交路终点 / 种类。 */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getCircuit(String reference) {
        Train train = resolveTrain(reference);
        if (train == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        CrnBridge.CircuitInfo circuit = CrnBridge.circuitInfo(train.id)
                .orElse(new CrnBridge.CircuitInfo("", "", ""));
        map.put("line", circuit.line());
        map.put("terminus", circuit.terminus());
        map.put("category", circuit.category());
        map.put("id", train.id.toString());
        map.put("name", trainName(train));
        return map;
    }

    @LuaFunction(mainThread = true)
    public final String getVersion() {
        return CreateTrainsScheduling.MODID;
    }

    // ------------------------------------------------------------------
    // 数据工具
    // ------------------------------------------------------------------

    private static Train resolveTrain(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String trimmed = reference.trim();
        try {
            Train byId = Create.RAILWAYS.trains.get(UUID.fromString(trimmed));
            if (byId != null) {
                return byId;
            }
        } catch (IllegalArgumentException ignored) {
            // 不是 UUID，按名字查
        }
        for (Train train : Create.RAILWAYS.trains.values()) {
            if (trainName(train)
                    .equalsIgnoreCase(trimmed)) {
                return train;
            }
        }
        return null;
    }

    private static Map<String, Object> trainInfo(Train train) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", train.id.toString());
        map.put("name", trainName(train));
        map.put("automatic", train.hasForwardConductor() || train.hasBackwardConductor());
        map.put("manual", train.manualTick);
        map.put("paused", train.runtime.paused);
        map.put("completed", train.runtime.completed);
        map.put("hasSchedule", train.runtime.getSchedule() != null);
        map.put("autoSchedule", train.runtime.isAutoSchedule);
        map.put("speed", Math.round(train.speed * 1000.0) / 1000.0);
        map.put("overrideNextSignal", SignalOverrideManager.hasNextSignalGrant(train));
        map.put("overrideSeconds", Math.round(SignalOverrideManager.remaining(train) / 20.0 * 10.0) / 10.0);
        try {
            CrnBridge.circuitInfo(train.id)
                    .ifPresent(circuit -> {
                        map.put("line", circuit.line());
                        map.put("terminus", circuit.terminus());
                        map.put("category", circuit.category());
                    });
        } catch (Throwable ignored) {
            // CRN 未就绪时忽略
        }
        return map;
    }

    private static String trainName(Train train) {
        return train.name == null ? train.id.toString() : train.name.getString();
    }
}
