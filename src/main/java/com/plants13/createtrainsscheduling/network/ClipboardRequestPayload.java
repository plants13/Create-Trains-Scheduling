// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：请求把当前剪贴板内容发回（用于 /cts writesch）。 */
public record ClipboardRequestPayload(UUID token) implements CustomPacketPayload {

    public static final Type<ClipboardRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "clipboard_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardRequestPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ClipboardRequestPayload::token,
                    ClipboardRequestPayload::new);

    @Override
    public Type<ClipboardRequestPayload> type() {
        return TYPE;
    }
}
