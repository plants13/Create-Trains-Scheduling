// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.cc;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.AllBlockEntityTypes;

import dan200.computercraft.api.peripheral.PeripheralCapability;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * CC:Tweaked 桥接入口（软依赖，仅当 {@code computercraft} 已加载时才会加载本类）。
 *
 * <p>不新增方块/物品：把 peripheral capability 挂到 Create 已有的车站方块实体
 * （{@code AllBlockEntityTypes.TRACK_STATION}）上，电脑放在车站旁边即可读取调度数据。
 */
public final class CcCompat {

    private CcCompat() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(CcCompat::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(CcCompat::onServerTick);
        NeoForge.EVENT_BUS.addListener(CcCompat::onServerStopped);
        CreateTrainsScheduling.LOGGER.info("[CTS] ComputerCraft bridge enabled (station peripheral)");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(PeripheralCapability.get(),
                AllBlockEntityTypes.TRACK_STATION.get(),
                (station, side) -> new StationPeripheral(station));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        StationPeripheral.tick(event.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        StationPeripheral.clear();
    }
}
