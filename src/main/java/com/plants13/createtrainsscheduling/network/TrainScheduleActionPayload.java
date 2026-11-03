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
 * 客户端 → 服务端：对某列车的时刻表执行操作（地图菜单触发，不注册命令）。
 */
public record TrainScheduleActionPayload(UUID trainId, int action) implements CustomPacketPayload {

    public static final int ACTION_EXPORT = 0;
    public static final int ACTION_IMPORT_RUN = 1;
    public static final int ACTION_IMPORT_PAUSE = 2;

    public static final Type<TrainScheduleActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "train_schedule_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrainScheduleActionPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, TrainScheduleActionPayload::trainId,
                    ByteBufCodecs.VAR_INT, TrainScheduleActionPayload::action,
                    TrainScheduleActionPayload::new);

    @Override
    public Type<TrainScheduleActionPayload> type() {
        return TYPE;
    }
}
