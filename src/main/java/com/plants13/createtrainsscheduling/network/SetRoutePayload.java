// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：下发多站进路（按站点 UUID 顺序）。
 *
 * <p>编解码层硬上限 {@link #MAX_STATIONS}，防止超长列表占用内存；实际生效的上限由服务端配置
 * {@code maxRouteStations} 决定（不超过该硬上限）。
 */
public record SetRoutePayload(UUID trainId, List<UUID> stationIds) implements CustomPacketPayload {

    /** 编解码层的硬上限，与配置项 {@code maxRouteStations} 的最大值一致。 */
    public static final int MAX_STATIONS = 512;

    public static final Type<SetRoutePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "set_route"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetRoutePayload> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SetRoutePayload::trainId,
            ByteBufCodecs.collection(ArrayList::new, UUIDUtil.STREAM_CODEC, MAX_STATIONS), SetRoutePayload::stationIds,
            SetRoutePayload::new);

    @Override
    public Type<SetRoutePayload> type() {
        return TYPE;
    }
}
