// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：清除列车时刻表。 */
public record ClearSchedulePayload(UUID trainId) implements CustomPacketPayload {

    public static final Type<ClearSchedulePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "clear_schedule"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearSchedulePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ClearSchedulePayload::trainId,
            ClearSchedulePayload::new);

    @Override
    public Type<ClearSchedulePayload> type() {
        return TYPE;
    }
}
