// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 → 服务端：授权列车越过下一个红灯。 */
public record GrantOverridePayload(UUID trainId) implements CustomPacketPayload {

    public static final Type<GrantOverridePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "grant_override"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GrantOverridePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GrantOverridePayload::trainId,
            GrantOverridePayload::new);

    @Override
    public Type<GrantOverridePayload> type() {
        return TYPE;
    }
}
