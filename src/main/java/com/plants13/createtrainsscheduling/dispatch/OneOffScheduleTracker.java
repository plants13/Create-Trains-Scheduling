package com.plants13.createtrainsscheduling.dispatch;

import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.server.MinecraftServer;

/**
 * 一次性路线（one-off route）的完成追踪与清理。
 *
 * <p>背景：Create 原版对非循环时刻表跑完的行为是 {@code paused=true; completed=true}，
 * 时刻表仍然留在车上；而 Steam 'n' Rails 会在同一点把 <b>auto schedule</b> 直接丢弃
 * （{@code MixinScheduleRuntime#discardAutoSchedule}）。这导致"一次性路线跑完是否自动清空"
 * 取决于装了哪些 mod。
 *
 * <p>这里由我们自己保证确定性：只要是我们下发的一次性路线，到达终点（completed）后统一清空，
 * 与是否存在 SnR 无关。清理是幂等的——如果 SnR 已经先一步丢弃，这里只会把记录移除。
 */
public final class OneOffScheduleTracker {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private OneOffScheduleTracker() {}

    /** 记录一条我们下发的一次性路线。 */
    public static void markDispatched(Train train) {
        if (train != null) {
            ACTIVE.add(train.id);
        }
    }

    /** 玩家手动清除/重新下发时取消跟踪。 */
    public static void markCleared(Train train) {
        if (train != null) {
            ACTIVE.remove(train.id);
        }
    }

    public static boolean isTracked(Train train) {
        return train != null && ACTIVE.contains(train.id);
    }

    /** 每个服务端 tick 调用：完成即清理。 */
    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<UUID> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Train train = Create.RAILWAYS.trains.get(id);
            if (train == null || train.runtime == null) {
                iterator.remove();
                continue;
            }
            if (train.runtime.getSchedule() == null) {
                // 已经没时刻表了（例如 SnR 先清掉了），停止跟踪即可
                iterator.remove();
                continue;
            }
            if (train.runtime.completed) {
                train.runtime.discardSchedule();
                iterator.remove();
                CreateTrainsScheduling.LOGGER.info(
                        "[CTS] one-off route finished, schedule cleared from train {}",
                        train.name == null ? id : train.name.getString());
            }
        }
    }
}
