// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.permissions;

/** 可按玩家授予/剥夺的功能权限。 */
public enum ModPermission {
    /** 设定目的地 / 下发路线。 */
    DISPATCH("dispatch"),
    /** 允许越过红灯。 */
    OVERRIDE("override"),
    /** 禁用 / 恢复 / 清除时刻表。 */
    SCHEDULE("schedule");

    private final String id;

    ModPermission(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ModPermission byId(String id) {
        for (ModPermission permission : values()) {
            if (permission.id.equalsIgnoreCase(id)) {
                return permission;
            }
        }
        return null;
    }
}
