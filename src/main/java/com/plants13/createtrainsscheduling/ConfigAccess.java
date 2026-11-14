// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling;

import java.util.function.Supplier;

/**
 * 配置读取的兜底模板。
 *
 * <p>NeoForge 的配置值在配置加载之前调用 {@code get()} 会抛 {@link IllegalStateException}
 * （例如数据生成、mixin 早期、独立工具进程）。所有访问器都需要退回默认值，这里统一处理，
 * 避免每个 getter 复制一份 try/catch。
 */
public final class ConfigAccess {

    private ConfigAccess() {}

    /** 读取配置；配置未就绪时返回 {@code fallback}。 */
    public static <T> T get(Supplier<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException ignored) {
            return fallback;
        }
    }
}
