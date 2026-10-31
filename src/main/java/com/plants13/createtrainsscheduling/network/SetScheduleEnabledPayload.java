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

/**
 * 客户端 → 服务端：禁用/恢复列车时刻表。
 *
 * <p>服务端决定语义：手动放置的时刻表只"打断"（暂停、保留），自动/本 mod 下发的时刻表才会被清除。
 */
public record SetScheduleEnabledPayload(UUID trainId, boolean enabled) implements CustomPacketPayload {

    public static final Type<SetScheduleEnabledPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "set_schedule_enabled"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetScheduleEnabledPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, SetScheduleEnabledPayload::trainId,
                    ByteBufCodecs.BOOL, SetScheduleEnabledPayload::enabled,
                    SetScheduleEnabledPayload::new);

    @Override
    public Type<SetScheduleEnabledPayload> type() {
        return TYPE;
    }
}
