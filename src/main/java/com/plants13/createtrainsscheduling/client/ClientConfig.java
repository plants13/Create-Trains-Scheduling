// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置（只影响本机）。
 *
 * <p>M7 缓解：服务端可以通过本模组请求/覆盖剪贴板。第一次加入多人游戏的前 3 次会给出提示，
 * 之后不再打扰；可以在配置文件里关闭提示。
 */
public final class ClientConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CLIPBOARD_WARNING = BUILDER
            .comment("Show a chat warning when joining a multiplayer server for the first few times.",
                    "It explains that this mod lets the server read/write your clipboard when you use schedule import/export.")
            .define("clipboardAccessWarning", true);

    public static final ModConfigSpec.IntValue CLIPBOARD_WARNINGS_SHOWN = BUILDER
            .comment("Internal counter for the clipboard warning (0-3). Set to 3 to stop showing it.")
            .defineInRange("clipboardAccessWarningsShown", 0, 0, 3);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    public static boolean clipboardWarningEnabled() {
        try {
            return CLIPBOARD_WARNING.get();
        } catch (IllegalStateException ignored) {
            return true;
        }
    }

    public static int warningsShown() {
        try {
            return CLIPBOARD_WARNINGS_SHOWN.get();
        } catch (IllegalStateException ignored) {
            return 3;
        }
    }

    public static void markWarned() {
        try {
            int shown = CLIPBOARD_WARNINGS_SHOWN.get();
            if (shown < 3) {
                CLIPBOARD_WARNINGS_SHOWN.set(shown + 1);
                SPEC.save();
            }
        } catch (IllegalStateException ignored) {
            // 配置未就绪时忽略
        }
    }
}
