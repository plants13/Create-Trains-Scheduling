package com.plants13.createtrainsscheduling;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config of Create: Trains Scheduling.
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_LOGGING = BUILDER
            .comment("Log extra information about train schedule handling")
            .define("debugLogging", false);

    /**
     * "强制放行"授权组。
     *
     * <p>OP 始终允许；这里列出的玩家（名称或 UUID）即使不是 OP 也允许强制放行。
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> FORCE_RELEASE_PLAYERS = BUILDER
            .comment("Players (name or UUID) allowed to force-release a train through a red signal. OPs always can.")
            .defineListAllowEmpty("forceReleasePlayers", List.of(), () -> "", o -> o instanceof String);

    /**
     * 越红灯授权的默认时长（tick）。
     *
     * <p>命令可以用 {@code /cts override <train> <ticks>} 单次覆盖，例如 {@code 10} 表示只放行 0.5 秒。
     */
    public static final ModConfigSpec.IntValue FORCE_RELEASE_DURATION_TICKS = BUILDER
            .comment("Default duration (ticks) of a red-signal override grant. Overridable per command: /cts override <train> <ticks>")
            .defineInRange("forceReleaseDurationTicks", 1200, 1, 72000);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** 读取默认授权时长，配置不可用时退回 1200 ticks。 */
    public static int forceReleaseDurationTicks() {
        try {
            return FORCE_RELEASE_DURATION_TICKS.get();
        } catch (IllegalStateException ignored) {
            return 1200;
        }
    }
}
