// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.snr;

import net.minecraftforge.fml.ModList;

/**
 * Steam 'n' Rails 的安装检测（1.20.1 Forge 版）。
 *
 * <p>SnR 会在非循环 auto schedule 完成时直接丢弃时刻表；检测到它就优先让它处理，我们只做兜底。
 */
public final class SnrCompat {

    /** SnR 的 mod id。 */
    public static final String MOD_ID = "railways";

    private SnrCompat() {}

    public static boolean isLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(MOD_ID);
    }

    public static String describe() {
        return "SnR(railways)=" + (isLoaded() ? "present" : "absent");
    }
}