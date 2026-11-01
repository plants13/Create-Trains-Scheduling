// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.dispatch;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * "越过前方红灯"授权。
 *
 * <p>支持两种模式：
 * <ul>
 *   <li><b>事件制</b>（默认）：授权在下一次<em>实际通过信号</em>后立即失效，与距离/时间无关；</li>
 *   <li><b>时间制</b>：授权在指定 tick 数后过期。</li>
 * </ul>
 */
public final class SignalOverrideManager {

    /** 时间制默认时长：60 秒（仅作兜底，实际以配置为准）。 */
    public static final int DEFAULT_DURATION_TICKS = 20 * 60;

    /** 正在被 Navigation#tick 处理的列车（mixin 用）。 */
    public static final ThreadLocal<Train> TICKING = new ThreadLocal<>();

    private static final Map<UUID, Long> TIMED = new ConcurrentHashMap<>();
    private static final Set<UUID> NEXT_SIGNAL = ConcurrentHashMap.newKeySet();

    private SignalOverrideManager() {}

    /** 时间制授权：durationTicks 后自动失效。 */
    public static void grantTimed(Train train, long durationTicks) {
        if (train == null) {
            return;
        }
        NEXT_SIGNAL.remove(train.id);
        TIMED.put(train.id, currentGameTime() + Math.max(1L, durationTicks));
    }

    /** 事件制授权：下一次实际通过信号后失效。 */
    public static void grantNextSignal(Train train) {
        if (train == null) {
            return;
        }
        TIMED.remove(train.id);
        NEXT_SIGNAL.add(train.id);
    }

    /** 列车通过信号时调用，消费事件制授权。 */
    public static void consumeNextSignal(Train train) {
        if (train == null) {
            return;
        }
        NEXT_SIGNAL.remove(train.id);
    }

    public static void revoke(UUID trainId) {
        if (trainId != null) {
            TIMED.remove(trainId);
            NEXT_SIGNAL.remove(trainId);
        }
    }

    public static void clearAll() {
        TIMED.clear();
        NEXT_SIGNAL.clear();
    }

    /** 该列车当前是否持有有效授权（任一模式）。 */
    public static boolean isOverriding(Train train) {
        if (train == null) {
            return false;
        }
        return hasNextSignalGrant(train) || remaining(train) > 0L;
    }

    /** 是否持有"下一信号"授权。 */
    public static boolean hasNextSignalGrant(Train train) {
        return train != null && NEXT_SIGNAL.contains(train.id);
    }

    /** 时间制授权剩余 tick，无授权返回 0。 */
    public static long remaining(Train train) {
        if (train == null) {
            return 0L;
        }
        Long expiry = TIMED.get(train.id);
        if (expiry == null) {
            return 0L;
        }
        long left = expiry - currentGameTime();
        if (left <= 0L) {
            TIMED.remove(train.id);
            return 0L;
        }
        return left;
    }

    private static long currentGameTime() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            return 0L;
        }
        return server.overworld().getGameTime();
    }
}
