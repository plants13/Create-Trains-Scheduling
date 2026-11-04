// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：查询车站的开门半径。 */
public record StationDoorRadiusQueryPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<StationDoorRadiusQueryPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "station_door_radius_query"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationDoorRadiusQueryPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationDoorRadiusQueryPayload::pos,
                    StationDoorRadiusQueryPayload::new);

    @Override
    public Type<StationDoorRadiusQueryPayload> type() {
        return TYPE;
    }
}
