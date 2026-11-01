// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：CRN 提供的交路信息（线路 / 交路终点 / 种类）。 */
public record CircuitInfoPayload(UUID trainId, String line, String terminus,
                                 String category) implements CustomPacketPayload {

    public static final Type<CircuitInfoPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "circuit_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CircuitInfoPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, CircuitInfoPayload::trainId,
            ByteBufCodecs.STRING_UTF8, CircuitInfoPayload::line,
            ByteBufCodecs.STRING_UTF8, CircuitInfoPayload::terminus,
            ByteBufCodecs.STRING_UTF8, CircuitInfoPayload::category,
            CircuitInfoPayload::new);

    @Override
    public Type<CircuitInfoPayload> type() {
        return TYPE;
    }
}
