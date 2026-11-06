// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import com.plants13.createtrainsscheduling.network.ChannelCheck;
import com.plants13.createtrainsscheduling.network.StationDoorRadiusSyncPayload;
import com.plants13.createtrainsscheduling.station.StationDoorRadius;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 车站开门半径的服务端读写（站台 GUI 触发）。 */
public final class StationDoorRadiusService {

    private static final double MAX_DISTANCE_SQR = 64.0 * 64.0;
    private static final long MIN_INTERVAL_MS = 100L;
    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

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
        // C1：绝不使用 getBlockEntity(pos) 直接触碰未加载区块（1.21.1 会同步加载/生成区块）
        if (!player.level().isInWorldBounds(pos) || !player.level().isLoaded(pos)) {
            return null;
        }
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_DISTANCE_SQR) {
            return null;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION.get(player.getUUID());
        if (last != null && now - last < MIN_INTERVAL_MS) {
            return null;
        }
        LAST_ACTION.put(player.getUUID(), now);
        return player.level().getBlockEntity(pos) instanceof StationBlockEntity station ? station : null;
    }
    private static void sync(ServerPlayer player, BlockPos pos, int radius) {
        if (ChannelCheck.canSendTo(player, StationDoorRadiusSyncPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new StationDoorRadiusSyncPayload(pos, radius));
        }
    }
}
