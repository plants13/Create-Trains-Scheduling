// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：设定列车目的地（按站点 UUID，天然无重名歧义）。 */
public record SetDestinationPayload(UUID trainId, UUID stationId) implements CustomPacketPayload {

    public static final Type<SetDestinationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "set_destination"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetDestinationPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetDestinationPayload::trainId,
            UUIDUtil.STREAM_CODEC, SetDestinationPayload::stationId,
            SetDestinationPayload::new);

    @Override
    public Type<SetDestinationPayload> type() {
        return TYPE;
    }
}
