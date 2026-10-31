package com.plants13.createtrainsscheduling.dispatch;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 一次性"越过前方红灯"授权。
 *
 * <p>授权按列车 UUID 记录，带到期时间；由调度器（或授权组内的玩家）发放。
 * 过期后自动失效，不会长期挂着。
 */
public final class SignalOverrideManager {

    /** 默认授权时长：60 秒。 */
    public static final int DEFAULT_DURATION_TICKS = 20 * 60;

    /** 正在被 Navigation#tick 处理的列车（mixin 用）。 */
    public static final ThreadLocal<Train> TICKING = new ThreadLocal<>();

    private static final Map<UUID, Long> GRANTS = new ConcurrentHashMap<>();

    private SignalOverrideManager() {}

    /** 给列车发放一次越红灯授权。 */
    public static void grant(Train train, long durationTicks) {
        if (train == null) {
            return;
        }
        GRANTS.put(train.id, currentGameTime() + Math.max(1L, durationTicks));
    }

    public static void revoke(UUID trainId) {
        if (trainId != null) {
            GRANTS.remove(trainId);
        }
    }

    public static void clearAll() {
        GRANTS.clear();
    }

    /** 该列车当前是否持有有效授权。 */
    public static boolean isOverriding(Train train) {
        if (train == null) {
            return false;
        }
        Long expiry = GRANTS.get(train.id);
        if (expiry == null) {
            return false;
        }
        if (currentGameTime() >= expiry) {
            GRANTS.remove(train.id);
            return false;
        }
        return true;
    }

    /** 授权剩余时长（tick），无授权返回 0。 */
    public static long remaining(Train train) {
        if (train == null) {
            return 0L;
        }
        Long expiry = GRANTS.get(train.id);
        if (expiry == null) {
            return 0L;
        }
        return Math.max(0L, expiry - currentGameTime());
    }

    private static long currentGameTime() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            return 0L;
        }
        return server.overworld().getGameTime();
    }
}
