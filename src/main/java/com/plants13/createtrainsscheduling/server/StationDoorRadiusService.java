// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import com.plants13.createtrainsscheduling.network.ChannelCheck;
import com.plants13.createtrainsscheduling.network.StationDoorRadiusSyncPayload;
import com.plants13.createtrainsscheduling.station.StationDoorRadius;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 车站开门半径的服务端读写（站台 GUI 触发）。 */
public final class StationDoorRadiusService {

    private StationDoorRadiusService() {}

    public static void handleQuery(ServerPlayer player, BlockPos pos) {
        StationBlockEntity station = resolve(player, pos);
        if (station == null) {
            return;
        }
        sync(player, pos, StationDoorRadius.get(station));
    }

    public static void handleSet(ServerPlayer player, BlockPos pos, int radius) {
        StationBlockEntity station = resolve(player, pos);
        if (station == null) {
            return;
        }
        StationDoorRadius.set(station, radius);
        sync(player, pos, StationDoorRadius.get(station));
    }

    private static StationBlockEntity resolve(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return null;
        }
        return player.level()
                .getBlockEntity(pos) instanceof StationBlockEntity station ? station : null;
    }

    private static void sync(ServerPlayer player, BlockPos pos, int radius) {
        if (ChannelCheck.canSendTo(player, StationDoorRadiusSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new StationDoorRadiusSyncPayload(pos, radius));
        }
    }
}
