// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.dispatch;

import java.util.List;

import com.plants13.createtrainsscheduling.Config;
import com.plants13.createtrainsscheduling.ConfigAccess;

import net.minecraft.server.level.ServerPlayer;

/**
 * "强制放行"权限判定。
 *
 * <p>规则：OP（权限等级 2）默认允许；非 OP 玩家需被显式加入授权组
 * （{@code forceReleasePlayers} 配置项，支持玩家名或 UUID）。
 */
public final class ForceReleasePermission {

    private ForceReleasePermission() {}

    public static boolean canForceRelease(ServerPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        String name = player.getGameProfile().getName();
        String uuid = player.getStringUUID();
        for (String entry : ConfigAccess.get(Config.FORCE_RELEASE_PLAYERS, List.of())) {
            if (entry == null) {
                continue;
            }
            if (entry.equalsIgnoreCase(name) || entry.equalsIgnoreCase(uuid)) {
                return true;
            }
        }
        return false;
    }
}
