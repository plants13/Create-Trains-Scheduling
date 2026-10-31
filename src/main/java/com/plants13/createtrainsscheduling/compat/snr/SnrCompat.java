package com.plants13.createtrainsscheduling.compat.snr;

import net.neoforged.fml.ModList;

/**
 * Steam 'n' Rails（含 1.21.1 移植版）的安装检测。
 *
 * <p>SnR 会通过 mixin 在非循环 auto schedule 完成时直接丢弃时刻表
 * （{@code MixinScheduleRuntime#discardAutoSchedule}）。检测到它就优先让它处理，
 * 我们只做兜底。
 */
public final class SnrCompat {

    /** SnR 官方版与 1.21.1 移植版使用的 mod id 相同。 */
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
