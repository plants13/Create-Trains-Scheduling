// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.level.ServerPlayer;

/**
 * 剪贴板导入的公共状态机：一次性 token、TTL、按玩家的请求冷却。
 *
 * <p>列车时刻表导入和手持时刻表编辑器的导入流程本来各自复制了一份同样的逻辑
 * （冷却 2 秒、token 15 秒有效、按玩家记录 pending）。这里收拢后，两个业务类只保留
 * “token 对应什么数据”以及“校验通过后写到哪里”这两段策略代码。
 *
 * <p>实例按 Feature 各持有一份，{@link #clear()} 由 {@code ServerEvents} 在服务器停止时调用。
 *
 * @param <T> token 关联的业务数据（列车导入用 trainId+pause，编辑器导入不需要数据）
 */
public final class ClipboardExchange<T> {

    /** 两次请求之间的最小间隔。 */
    public static final long COOLDOWN_MS = 2000L;

    /** token 的有效期。 */
    public static final long TOKEN_TTL_MS = 15000L;

    private final Map<UUID, Pending<T>> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    /** 一次未消费的导入请求。 */
    public record Pending<T>(UUID token, long expiresAt, T data) {

        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * 冷却检查；通过时记录本次请求时间。
     *
     * @return false 表示还在冷却中，调用方应提示玩家稍后再试
     */
    public boolean tryBegin(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = lastRequest.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        lastRequest.put(player.getUUID(), now);
        return true;
    }

    /** 生成一次性 token 并登记 TTL。 */
    public UUID issue(UUID player, T data) {
        UUID token = UUID.randomUUID();
        pending.put(player, new Pending<>(token, System.currentTimeMillis() + TOKEN_TTL_MS, data));
        return token;
    }

    /**
     * 取出并移除该玩家的 pending 请求。
     *
     * @return token 不匹配时返回 null（调用方应继续尝试其它 Exchange）
     */
    public Pending<T> consume(UUID player, UUID token) {
        Pending<T> current = pending.get(player);
        if (current == null || !current.token().equals(token)) {
            return null;
        }
        pending.remove(player);
        return current;
    }

    /** 服务器停止时清空状态。 */
    public void clear() {
        pending.clear();
        lastRequest.clear();
    }
}
