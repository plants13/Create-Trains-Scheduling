// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Startup config（在注册事件之前加载）。
 *
 * <p>Create 的 {@code display_source} 是同步注册表：只要本模组注册了条目，服务端就会把它同步给客户端；
 * 未安装本模组的客户端会因为未知注册表键而无法加入。这里允许在注册前决定是否注册倒计时数据源，
 * 从而让纯服务端使用（或允许未安装本模组的客户端）成为可能。
 *
 * <p>注意：该选项在启动时读取，修改后需要重启游戏/服务器；关闭时倒计时功能不可用。
 */
public final class StartupConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue REGISTER_DEPARTURE_COUNTDOWN = BUILDER
            .comment(
                    "Register the station departure countdown display source (create:display_source).",
                    "Create's display_source registry is synced, so a registered entry is sent to every client.",
                    "Disable this when the server must accept clients without this mod; the countdown feature",
                    "is then unavailable. This is read at startup and requires a restart.")
            .define("registerDepartureCountdown", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StartupConfig() {}

    /** 注册前读取；配置未就绪时保守地按 true 处理。 */
    public static boolean registerDepartureCountdown() {
        return ConfigAccess.get(REGISTER_DEPARTURE_COUNTDOWN, true);
    }
}
