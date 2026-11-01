// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.permissions;

import java.util.Set;
import java.util.UUID;

import com.plants13.createtrainsscheduling.dispatch.ForceReleasePermission;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 权限判定入口。
 *
 * <p>规则：OP 始终拥有全部权限；一般玩家默认什么都没有，需要 OP 通过
 * {@code /cts perm grant <player> <permission>} 显式授予。
 */
public final class PermissionManager {

    private PermissionManager() {}

    public static PermissionSavedData data(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(PermissionSavedData::load, PermissionSavedData::create,
                        PermissionSavedData.DATA_NAME);
    }

    public static boolean has(ServerPlayer player, ModPermission permission) {
        if (player == null) {
            return false;
        }
        if (player.hasPermissions(2)) {
            return true;
        }
        // 兼容旧的 forceReleasePlayers 配置：视为拥有 OVERRIDE 权限
        if (permission == ModPermission.OVERRIDE && ForceReleasePermission.canForceRelease(player)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        return server != null && data(server).has(player.getUUID(), permission);
    }

    public static boolean has(MinecraftServer server, UUID player, ModPermission permission) {
        return server != null && data(server).has(player, permission);
    }

    public static Set<ModPermission> permissionsOf(MinecraftServer server, UUID player) {
        return data(server).permissionsOf(player);
    }

    public static void grant(MinecraftServer server, UUID player, ModPermission permission) {
        data(server).grant(player, permission);
    }

    public static void revoke(MinecraftServer server, UUID player, ModPermission permission) {
        data(server).revoke(player, permission);
    }
}
