// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：把编辑器用 JSON 写入剪贴板。
 *
 * @param kind {@code catalog}（线路/种类/车站目录）或 {@code schedule}（时刻表 JSON）
 */
public record EditorJsonPayload(String kind, String json) implements CustomPacketPayload {

    public static final String KIND_CATALOG = "catalog";
    public static final String KIND_SCHEDULE = "schedule";

    public static final Type<EditorJsonPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "editor_json"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorJsonPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, EditorJsonPayload::kind,
            ByteBufCodecs.STRING_UTF8, EditorJsonPayload::json,
            EditorJsonPayload::new);

    @Override
    public Type<EditorJsonPayload> type() {
        return TYPE;
    }
}
