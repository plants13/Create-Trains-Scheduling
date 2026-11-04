// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：同步车站的开门半径。 */
public record StationDoorRadiusSyncPayload(BlockPos pos, int radius) implements CustomPacketPayload {

    public static final Type<StationDoorRadiusSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "station_door_radius_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationDoorRadiusSyncPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationDoorRadiusSyncPayload::pos,
                    ByteBufCodecs.VAR_INT, StationDoorRadiusSyncPayload::radius,
                    StationDoorRadiusSyncPayload::new);

    @Override
    public Type<StationDoorRadiusSyncPayload> type() {
        return TYPE;
    }
}
