// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：设置车站的开门半径。 */
public record StationDoorRadiusSetPayload(BlockPos pos, int radius) implements CustomPacketPayload {

    public static final Type<StationDoorRadiusSetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "station_door_radius_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationDoorRadiusSetPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, StationDoorRadiusSetPayload::pos,
                    ByteBufCodecs.VAR_INT, StationDoorRadiusSetPayload::radius,
                    StationDoorRadiusSetPayload::new);

    @Override
    public Type<StationDoorRadiusSetPayload> type() {
        return TYPE;
    }
}
