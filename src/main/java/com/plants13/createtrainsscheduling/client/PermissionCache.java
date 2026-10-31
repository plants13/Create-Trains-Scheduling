// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import com.plants13.createtrainsscheduling.network.PlayerPermissionsPayload;
import com.plants13.createtrainsscheduling.permissions.ModPermission;

/** 客户端缓存的"我有哪些功能权限"，用于菜单灰显。 */
public final class PermissionCache {

    private static volatile PlayerPermissionsPayload current = new PlayerPermissionsPayload(0);

    private PermissionCache() {}

    public static void accept(PlayerPermissionsPayload payload) {
        current = payload;
    }

    public static boolean has(ModPermission permission) {
        return current.has(permission);
    }

    public static void clear() {
        current = new PlayerPermissionsPayload(0);
    }
}
