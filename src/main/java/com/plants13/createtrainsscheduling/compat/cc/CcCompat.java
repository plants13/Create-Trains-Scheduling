// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.cc;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.AllBlockEntityTypes;

import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * CC:Tweaked 桥接入口（软依赖，仅当 {@code computercraft} 已加载时才会加载本类）。
 *
 * <p>不新增方块/物品：把 peripheral capability 挂到 Create 已有的车站方块实体
 * （{@code AllBlockEntityTypes.TRACK_STATION}）上。
 *
 * <p>只注册外设，不注册到站/离站事件：那部分 Create 自带的 {@code Create_Station} 外设已经提供
 * （{@code train_imminent} / {@code train_arrival} / {@code train_departure}）。
 */
public final class CcCompat {

    private CcCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CcCompat::registerCapabilities);
        CreateTrainsScheduling.LOGGER.info("[CTS] ComputerCraft bridge enabled (dispatch-only station peripheral)");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(),
                AllBlockEntityTypes.TRACK_STATION.get(),
                (station, side) -> new StationPeripheral(station));
    }
}
