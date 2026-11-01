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

/** 客户端 → 服务端：剪贴板内容（仅供时刻表校验，绝不作为命令执行）。 */
public record ClipboardContentPayload(UUID token, String text) implements CustomPacketPayload {

    public static final Type<ClipboardContentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "clipboard_content"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClipboardContentPayload> CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, ClipboardContentPayload::token,
                    ByteBufCodecs.STRING_UTF8, ClipboardContentPayload::text,
                    ClipboardContentPayload::new);

    @Override
    public Type<ClipboardContentPayload> type() {
        return TYPE;
    }
}
