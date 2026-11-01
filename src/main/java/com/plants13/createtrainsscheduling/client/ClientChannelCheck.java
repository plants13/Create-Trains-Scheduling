// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 客户端侧通道检查：服务端没装本 mod 时，C2S 通道不存在，发送会被 NeoForge 拒绝，
 * 所以所有请求发送前都先查这里的通道。
 */
public final class ClientChannelCheck {

    private ClientChannelCheck() {}

    public static boolean canSendToServer(CustomPacketPayload.Type<?> type) {
        ClientPacketListener connection = Minecraft.getInstance()
                .getConnection();
        if (connection == null) {
            return false;
        }
        return NetworkRegistry.hasChannel(connection.getConnection(), null, type.id());
    }
}
