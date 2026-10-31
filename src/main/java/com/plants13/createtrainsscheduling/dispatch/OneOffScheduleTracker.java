package com.plants13.createtrainsscheduling.dispatch;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.compat.snr.SnrCompat;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.MinecraftServer;

/**
 * 一次性路线（one-off route）的完成追踪与清理。
 *
 * <p>背景：Create 原版对非循环时刻表跑完的行为是 {@code paused=true; completed=true}，时刻表仍然留在车上；
 * Steam 'n' Rails 会在同一点把 <b>auto schedule</b> 直接丢弃
 * （{@code MixinScheduleRuntime#discardAutoSchedule}）。
 *
 * <p>策略：
 * <ul>
 *   <li><b>装了 SnR</b>：优先让 SnR 的 mixin 处理，我们只等一小段时间做兜底，
 *       避免与 SnR 重复丢弃；</li>
 *   <li><b>没装 SnR</b>：由我们在 {@code completed} 后调用 {@code discardSchedule()}。</li>
 * </ul>
 * 无论哪条路径，最终行为一致：一次性路线跑完即从车上清空。
 */
public final class OneOffScheduleTracker {

    /** 装了 SnR 时，等它多少个 tick 再兜底。 */
    private static final int SNR_GRACE_TICKS = 20;

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> COMPLETED_AT = new ConcurrentHashMap<>();

    private OneOffScheduleTracker() {}

    /** 记录一条我们下发的一次性路线。 */
    public static void markDispatched(Train train) {
        if (train != null) {
            ACTIVE.add(train.id);
            COMPLETED_AT.remove(train.id);
        }
    }

    /** 玩家手动清除/重新下发时取消跟踪。 */
    public static void markCleared(Train train) {
        if (train != null) {
            ACTIVE.remove(train.id);
            COMPLETED_AT.remove(train.id);
        }
    }

    public static boolean isTracked(Train train) {
        return train != null && ACTIVE.contains(train.id);
    }

    /** 每个服务端 tick 调用：完成即清理（优先让 SnR 处理）。 */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = gameTime(server);
        boolean snr = SnrCompat.isLoaded();

        Iterator<UUID> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Train train = Create.RAILWAYS.trains.get(id);
            if (train == null || train.runtime == null) {
                forget(iterator, id);
                continue;
            }
            if (train.runtime.getSchedule() == null) {
                // 已被清除（例如 SnR 先一步丢弃），停止跟踪
                forget(iterator, id);
                continue;
            }
            if (!train.runtime.completed) {
                COMPLETED_AT.remove(id);
                continue;
            }

            if (snr) {
                long completedAt = COMPLETED_AT.computeIfAbsent(id, k -> now);
                if (now - completedAt < SNR_GRACE_TICKS) {
                    // 给 SnR 的 mixin 一个 tick 窗口
                    continue;
                }
                CreateTrainsScheduling.LOGGER.warn(
                        "[CTS] one-off route finished but SnR did not clear it; falling back for train {}",
                        trainName(train, id));
            }

            train.runtime.discardSchedule();
            forget(iterator, id);
            CreateTrainsScheduling.LOGGER.info(
                    "[CTS] one-off route finished, schedule cleared from train {} ({})",
                    trainName(train, id), snr ? "SnR present, fallback" : "self");
        }
    }

    private static void forget(Iterator<UUID> iterator, UUID id) {
        iterator.remove();
        COMPLETED_AT.remove(id);
    }

    private static String trainName(Train train, UUID id) {
        return train.name == null ? id.toString() : train.name.getString();
    }

    private static long gameTime(MinecraftServer server) {
        if (server == null || server.overworld() == null) {
            return 0L;
        }
        return server.overworld().getGameTime();
    }
}
