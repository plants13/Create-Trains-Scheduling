// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.cc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import net.minecraft.network.chat.Component;

/**
 * 挂在 Create 车站方块实体上的 CC:Tweaked 外设（只读）。
 *
 * <p>Lua 侧用法：
 * <pre>
 * local station = peripheral.find("cts_station")
 * station.getStationName()
 * station.getTrains()          -- 本轨道网络上的列车列表
 * station.getTrain("车名或UUID") -- 单列车详情（含 CRN 线路/终点/种类、越红灯状态）
 * station.getSchedule("车名")   -- 时刻表站序列表
 * station.getCircuit("车名")    -- CRN 交路：线路 / 交路终点 / 种类
 * station.getTrainsAtStation()  -- 当前停靠在本站的列车
 * </pre>
 *
 * <p>事件（{@code os.pullEvent}）：{@code cts_train_arrival}、{@code cts_train_departure}，
 * 参数均为 {@code (车名, 站名)}。
 *
 * <p>只读：不提供下发/改时刻表等写操作，避免 CC 程序影响调度安全。
 */
public final class StationPeripheral implements IPeripheral {

    private static final String TYPE = "cts_station";
    private static final Set<StationPeripheral> INSTANCES = ConcurrentHashMap.newKeySet();

    private final StationBlockEntity stationBlock;
    private final Set<IComputerAccess> computers = ConcurrentHashMap.newKeySet();
    private UUID lastPresentTrainId;
    private String lastPresentTrainName = "";

    public StationPeripheral(StationBlockEntity stationBlock) {
        this.stationBlock = stationBlock;
        INSTANCES.add(this);
    }

    // ------------------------------------------------------------------
    // IPeripheral
    // ------------------------------------------------------------------

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public void attach(IComputerAccess computer) {
        computers.add(computer);
    }

    @Override
    public void detach(IComputerAccess computer) {
        computers.remove(computer);
    }

    @Override
    public boolean equals(IPeripheral other) {
        return this == other || (other instanceof StationPeripheral peripheral
                && peripheral.stationBlock == stationBlock);
    }

    // ------------------------------------------------------------------
    // Lua 方法
    // ------------------------------------------------------------------

    @LuaFunction(mainThread = true)
    public final String getStationName() {
        GlobalStation station = station();
        return station == null ? "" : station.name;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStation() {
        Map<String, Object> map = new LinkedHashMap<>();
        GlobalStation station = station();
        map.put("name", station == null ? "" : station.name);
        map.put("x", stationBlock.getBlockPos()
                .getX());
        map.put("y", stationBlock.getBlockPos()
                .getY());
        map.put("z", stationBlock.getBlockPos()
                .getZ());
        if (stationBlock.getLevel() != null) {
            map.put("dimension", stationBlock.getLevel()
                    .dimension()
                    .location()
                    .toString());
        }
        return map;
    }

    /** 当前服务器上所有被跟踪的列车。 */
    @LuaFunction(mainThread = true)
    public final List<Object> getTrains() {
        List<Object> result = new ArrayList<>();
        for (Train train : Create.RAILWAYS.trains.values()) {
            result.add(trainInfo(train));
        }
        return result;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTrain(String reference) {
        Train train = resolveTrain(reference);
        return train == null ? null : trainInfo(train);
    }

    /** 时刻表站序（text 字段是 Create/CRN/SnR 给出的显示文本）。 */
    @LuaFunction(mainThread = true)
    public final List<Object> getSchedule(String reference) {
        List<Object> result = new ArrayList<>();
        Train train = resolveTrain(reference);
        if (train == null) {
            return result;
        }
        Schedule schedule = train.runtime.getSchedule();
        if (schedule == null) {
            return result;
        }
        for (int i = 0; i < schedule.entries.size(); i++) {
            ScheduleEntry entry = schedule.entries.get(i);
            if (entry == null || entry.instruction == null) {
                continue;
            }
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("index", i);
            map.put("type", entry.instruction.getId()
                    .toString());
            Component summary = entry.instruction.getSummary()
                    .getSecond();
            map.put("text", summary.getString());
            map.put("conditions", entry.conditions == null ? 0 : entry.conditions.size());
            result.add(map);
        }
        return result;
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

    /** 当前停靠在本站的列车（通常 0 或 1 列）。 */
    @LuaFunction(mainThread = true)
    public final List<Object> getTrainsAtStation() {
        List<Object> result = new ArrayList<>();
        GlobalStation station = station();
        if (station != null && station.getPresentTrain() != null) {
            result.add(trainInfo(station.getPresentTrain()));
        }
        return result;
    }

    @LuaFunction(mainThread = true)
    public final String getVersion() {
        return com.plants13.createtrainsscheduling.CreateTrainsScheduling.MODID;
    }

    // ------------------------------------------------------------------
    // 事件轮询
    // ------------------------------------------------------------------

    static void tick(net.minecraft.server.MinecraftServer server) {
        for (StationPeripheral peripheral : INSTANCES) {
            try {
                if (peripheral.stationBlock.isRemoved()) {
                    INSTANCES.remove(peripheral);
                    continue;
                }
                peripheral.pollArrival();
            } catch (Throwable ignored) {
                // 单站异常不影响其他站
            }
        }
    }

    private void pollArrival() {
        GlobalStation station = station();
        Train present = station == null ? null : station.getPresentTrain();
        UUID presentId = present == null ? null : present.id;
        if (Objects.equals(presentId, lastPresentTrainId)) {
            return;
        }
        String stationName = station == null ? "" : station.name;
        if (lastPresentTrainId != null) {
            queueAll("cts_train_departure", lastPresentTrainName, stationName);
        }
        if (presentId != null) {
            lastPresentTrainName = trainName(present);
            queueAll("cts_train_arrival", lastPresentTrainName, stationName);
        }
        lastPresentTrainId = presentId;
    }

    private void queueAll(String event, Object... args) {
        for (IComputerAccess computer : computers) {
            try {
                computer.queueEvent(event, args);
            } catch (Throwable ignored) {
                // 电脑可能已离线
            }
        }
    }

    static void clear() {
        INSTANCES.clear();
    }

    // ------------------------------------------------------------------
    // 数据工具
    // ------------------------------------------------------------------

    private GlobalStation station() {
        try {
            return stationBlock.getStation();
        } catch (Throwable ignored) {
            return null;
        }
    }

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
