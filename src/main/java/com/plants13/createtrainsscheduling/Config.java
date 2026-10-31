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

    static final ModConfigSpec SPEC = BUILDER.build();
}
