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
 * 服务端 → 客户端：列车真实状态。
 *
 * @param overrideMode 0=无授权，1=下一信号，2=时间制
 * @param overrideTicks 时间制剩余 tick
 */
public record TrainStatusPayload(UUID trainId, boolean canAutoRun, boolean hasSchedule,
                                 int overrideMode, int overrideTicks) implements CustomPacketPayload {

    public static final int OVERRIDE_NONE = 0;
    public static final int OVERRIDE_NEXT_SIGNAL = 1;
    public static final int OVERRIDE_TIMED = 2;

    public static final Type<TrainStatusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "train_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainStatusPayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TrainStatusPayload::trainId,
            ByteBufCodecs.BOOL, TrainStatusPayload::canAutoRun,
            ByteBufCodecs.BOOL, TrainStatusPayload::hasSchedule,
            ByteBufCodecs.VAR_INT, TrainStatusPayload::overrideMode,
            ByteBufCodecs.VAR_INT, TrainStatusPayload::overrideTicks,
            TrainStatusPayload::new);

    @Override
    public Type<TrainStatusPayload> type() {
        return TYPE;
    }
}
