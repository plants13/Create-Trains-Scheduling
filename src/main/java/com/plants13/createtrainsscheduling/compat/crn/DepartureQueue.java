// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.crn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition;
import com.simibubi.create.content.trains.station.GlobalStation;

import de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * CRN 分隔列车的“先到先发”排队扩展。
 *
 * <p>CRN 的 {@link TrainSeparationCondition#remainingHoldTicks} 只按同名车站共享“上次发车时间”，
 * 多辆列车同时等待时会同时发车。本类在该返回值上叠加队列等待：
 * <ul>
 *   <li>按车站名维护等待队列，先进入 POST_TRANSIT 的列车排在前面</li>
 *   <li>队首按 CRN 原本的扣车时间发车</li>
 *   <li>后续列车 = 上一次发车 + 队列间隔 × 队列位置，队列间隔沿用分隔列车自身配置</li>
 * </ul>
 *
 * <p>只在服务端生效；客户端（以及未加载服务端的环境）直接返回原值。
 */
public final class DepartureQueue {

    private static final Map<String, Deque<UUID>> QUEUES = new HashMap<>();
    private static final Map<String, Long> LAST_DEPARTURE = new HashMap<>();

    private DepartureQueue() {}

    /**
     * 给 CRN 的扣车时间叠加队列等待。
     *
     * @param original CRN 原本计算的剩余 tick
     * @return 叠加队列后的剩余 tick
     */
    public static long adjust(Train train, ScheduleEntry entry, long original) {
        if (train == null || entry == null) {
            return original;
        }
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return original; // 客户端 / 无服务端
        }
        if (!hasSeparation(entry)) {
            return original;
        }
        GlobalStation station = train.getCurrentStation();
        if (station == null || station.name == null) {
            return original;
        }
        ScheduleRuntime runtime = train.runtime;
        if (runtime == null || runtime.schedule == null || runtime.paused || runtime.completed) {
            return original;
        }
        if (runtime.state != ScheduleRuntime.State.POST_TRANSIT) {
            return original;
        }
        boolean allComplete = allGroupsComplete(runtime, entry);
        if (original <= 0 && !allComplete) {
            // 条件组还没完成时不要额外扣车，否则会冻结其它等待条件的进度
            return original;
        }

        long now = ServerLifecycleHooks.getCurrentServer()
                .overworld()
                .getGameTime();
        String key = station.name;
        Deque<UUID> queue = QUEUES.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(key, queue, now);

        if (queue.stream()
                .noneMatch(id -> id.equals(train.id))) {
            queue.addLast(train.id);
        }

        int index = 0;
        boolean found = false;
        for (UUID id : queue) {
            if (id.equals(train.id)) {
                found = true;
                break;
            }
            index++;
        }
        if (!found) {
            return original;
        }

        long interval = separationTicks(entry);
        if (interval <= 0) {
            return original;
        }

        long lastDeparture = LAST_DEPARTURE.getOrDefault(key, 0L);
        long gap = Math.max(0, lastDeparture + interval - now);
        long base = Math.max(original, gap);
        return base + (long) index * interval;
    }

    /** 清理已离开/暂停/完成/换站的列车；真正离开车站的队首会记录为一次发车。 */
    private static void prune(String key, Deque<UUID> queue, long now) {
        boolean departed = false;
        Iterator<UUID> iterator = queue.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Train train = Create.RAILWAYS.trains.get(id);
            if (train == null) {
                iterator.remove();
                continue;
            }
            ScheduleRuntime runtime = train.runtime;
            GlobalStation station = train.getCurrentStation();
            boolean atStation = station != null && key.equals(station.name);
            boolean waiting = runtime != null && runtime.schedule != null && !runtime.paused && !runtime.completed
                    && runtime.state == ScheduleRuntime.State.POST_TRANSIT && atStation;
            if (!waiting) {
                iterator.remove();
                if (!atStation) {
                    departed = true; // 离开车站 = 发车（或调车离开），作为下一次间隔的起点
                }
            }
        }
        if (departed) {
            LAST_DEPARTURE.put(key, now);
        }
    }

    private static boolean hasSeparation(ScheduleEntry entry) {
        if (entry.conditions == null) {
            return false;
        }
        for (var group : entry.conditions) {
            for (ScheduleWaitCondition condition : group) {
                if (condition instanceof TrainSeparationCondition) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean allGroupsComplete(ScheduleRuntime runtime, ScheduleEntry entry) {
        if (entry.conditions == null || entry.conditions.isEmpty() || !entry.instruction.supportsConditions()) {
            return true;
        }
        if (runtime.conditionProgress == null || runtime.conditionProgress.size() < entry.conditions.size()) {
            return false;
        }
        for (int i = 0; i < entry.conditions.size(); i++) {
            if (runtime.conditionProgress.get(i) < entry.conditions.get(i)
                    .size()) {
                return false;
            }
        }
        return true;
    }

    /** 读取该条目里分隔列车条件配置的间隔（tick），多个条件时取最大值。 */
    private static long separationTicks(ScheduleEntry entry) {
        long max = 0;
        if (entry.conditions == null) {
            return 0;
        }
        for (var group : entry.conditions) {
            for (ScheduleWaitCondition condition : group) {
                if (!(condition instanceof TrainSeparationCondition separation)) {
                    continue;
                }
                CompoundTag data = separation.getData();
                int ticks = data.getInt("Ticks");
                if (ticks <= 0) {
                    int value = data.getInt("Value");
                    TimedWaitCondition.TimeUnit[] units = TimedWaitCondition.TimeUnit.values();
                    int unitIndex = data.getInt("TimeUnit");
                    int ticksPer = unitIndex >= 0 && unitIndex < units.length
                            ? units[unitIndex].ticksPer
                            : 20;
                    ticks = value * ticksPer;
                }
                max = Math.max(max, ticks);
            }
        }
        return max;
    }
}
