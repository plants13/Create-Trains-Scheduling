// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 通道检查：我们的 payload 全部注册为 optional，因此客户端可以不装本 mod。
 * 向没有该通道的客户端发送会被 NeoForge 拒绝（抛异常），所以发送前必须先查。
 */
public final class ChannelCheck {

    private ChannelCheck() {}

    /** 服务端 → 该玩家：这个 payload 通道是否已协商成功。 */
    public static boolean canSendTo(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        if (player == null || player.connection == null) {
            return false;
        }
        return NetworkRegistry.hasChannel(player.connection.getConnection(), null, type.id());
    }
}
