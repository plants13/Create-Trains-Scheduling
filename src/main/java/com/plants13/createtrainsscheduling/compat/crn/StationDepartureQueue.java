// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.crn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 车站级发车队列。
 *
 * <p>CRN 的 train_separation 只按“目标站 filter”分组；同一站台、不同目的地的列车仍然会各算各的。
 * 这里在车站维度补一个 FIFO：同一车站的列车按等待顺序排队，后车至少等前车发车后再过一个间隔。
 *
 * <p>队列只影响“扣车时间/倒计时”，实际发车仍然由 CRN 的 tickConditions 完成；队首发车时
 * {@link ScheduleRuntimeSeparationMixin} 会通知这里更新 lastDeparture。
 */
public final class StationDepartureQueue {

    private static final int MAX_QUEUE_SIZE = 64;
    private static final long MAX_HOLD_TICKS = 72000L;

    private static final Map<String, StationQueue> QUEUES = new ConcurrentHashMap<>();

    private StationDepartureQueue() {}

    private static final class StationQueue {
        private final Deque<UUID> order = new ArrayDeque<>();
        private long lastDeparture = Long.MIN_VALUE;
        private long lastInterval;
    }

    /**
     * 给 CRN 的扣车时间叠加车站队列等待。
     *
     * @param original CRN 原本的剩余扣车 tick
     * @param interval 当前列车自己的分隔间隔（tick）
     */
    public static synchronized long adjust(Train train, long original, long interval) {
        if (train == null || interval <= 0L) {
            return original;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            return original;
        }
        ScheduleRuntime runtime = train.runtime;
        if (runtime == null || runtime.schedule == null || runtime.paused || runtime.completed) {
            return original;
        }
        if (runtime.state != ScheduleRuntime.State.POST_TRANSIT) {
            return original;
        }
        GlobalStation station = train.getCurrentStation();
        if (station == null || station.name == null || station.name.isBlank()) {
            return original;
        }

        long now = server.overworld()
                .getGameTime();
        String key = station.name;
        StationQueue queue = QUEUES.computeIfAbsent(key, k -> new StationQueue());
        prune(queue, key, now);

        if (queue.order.size() >= MAX_QUEUE_SIZE) {
            return original;
        }
        if (!queue.order.contains(train.id)) {
            queue.order.addLast(train.id);
        }

        int index = 0;
        boolean found = false;
        for (UUID id : queue.order) {
            if (id.equals(train.id)) {
                found = true;
                break;
            }
            index++;
        }
        if (!found) {
            return original;
        }

        long gap = queue.lastDeparture == Long.MIN_VALUE
                ? 0L
                : Math.max(0L, queue.lastDeparture + queue.lastInterval - now);
        long base = Math.max(original, gap);
        return Math.min(MAX_HOLD_TICKS, base + (long) index * interval);
    }

    /** 队首真正发车时调用：记录车站级上次发车时间，并把该车移出队列。 */
    public static synchronized void onDeparted(Train train, long interval) {
        if (train == null) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            return;
        }
        GlobalStation station = train.getCurrentStation();
        if (station == null || station.name == null || station.name.isBlank()) {
            return;
        }
        StationQueue queue = QUEUES.computeIfAbsent(station.name, k -> new StationQueue());
        queue.order.remove(train.id);
        queue.lastDeparture = server.overworld()
                .getGameTime();
        queue.lastInterval = Math.max(1L, interval);
    }

    public static synchronized void clear() {
        QUEUES.clear();
    }

    /** 清理已经不在该站等待的列车（离开/暂停/完成/换站）。 */
    private static void prune(StationQueue queue, String stationName, long now) {
        Iterator<UUID> iterator = queue.order.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Train train = Create.RAILWAYS.trains.get(id);
            if (train == null || train.runtime == null || train.runtime.state != ScheduleRuntime.State.POST_TRANSIT
                    || train.runtime.paused || train.runtime.completed) {
                iterator.remove();
                continue;
            }
            GlobalStation station = train.getCurrentStation();
            if (station == null || !stationName.equals(station.name)) {
                iterator.remove();
            }
        }
    }
}
