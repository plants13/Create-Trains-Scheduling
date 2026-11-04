// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.display;

import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.condition.ScheduledDelay;
import com.simibubi.create.content.trains.schedule.condition.TimeOfDayCondition;
import com.simibubi.create.content.trains.schedule.condition.TimedWaitCondition;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/**
 * 计算列车当前条目的发车倒计时（tick）。
 *
 * <p>仅对可确定的条件给出精确值：调度延迟、一天中的时间、CRN 动态延迟，以及 CRN 列车分离
 * （CRN 用服务端 gameTime 直接计算）。物品/流体/玩家/红石等世界状态条件无法预测，返回未知。
 */
public final class DepartureCountdown {

    /**
     * @param ticks   剩余 tick；未知时为 -1
     * @param exact   是否所有条件都可精确计算
     * @param blocker 第一个无法计算的条件 id（精确时为空串）
     */
    public record Result(long ticks, boolean exact, String blocker) {}

    private DepartureCountdown() {}

    /** @return 倒计时；列车不在站台等待时返回 null */
    public static Result compute(Level level, Train train) {
        if (level == null || train == null || train.derailed || train.runtime == null) {
            return null;
        }
        ScheduleRuntime runtime = train.runtime;
        Schedule schedule = runtime.schedule;
        if (schedule == null || runtime.paused || runtime.completed) {
            return null;
        }
        if (runtime.state != ScheduleRuntime.State.POST_TRANSIT) {
            return null;
        }
        if (runtime.currentEntry < 0 || runtime.currentEntry >= schedule.entries.size()) {
            return null;
        }
        ScheduleEntry entry = schedule.entries.get(runtime.currentEntry);
        if (entry.instruction == null || !entry.instruction.supportsConditions()) {
            return new Result(0, true, "");
        }
        if (entry.conditions == null || entry.conditions.isEmpty()) {
            return new Result(0, true, "");
        }

        long maxTicks = 0;
        boolean exact = true;
        String blocker = "";
        for (int column = 0; column < entry.conditions.size(); column++) {
            ScheduleWaitCondition condition = currentCondition(runtime, entry, column);
            if (condition == null) {
                continue; // 该列条件已完成
            }
            CompoundTag context = conditionContext(runtime, column);
            Long remaining = remainingTicks(level, train, entry, condition, context);
            if (remaining == null) {
                exact = false;
                if (blocker.isEmpty()) {
                    blocker = String.valueOf(condition.getId());
                }
                continue;
            }
            maxTicks = Math.max(maxTicks, remaining);
        }
        // CRN 的列车分离是"到点才允许发车"的墙钟约束：条件组完成后 CRN 会取消 tickConditions，
        // 此时 conditionProgress 已越过该组，必须单独把剩余 hold 计入，否则会误判为 0。
        maxTicks = Math.max(maxTicks, CrnBridge.trainSeparationHoldTicks(train, entry));

        return exact ? new Result(maxTicks, true, "") : new Result(-1, false, blocker);
    }

    private static ScheduleWaitCondition currentCondition(ScheduleRuntime runtime, ScheduleEntry entry, int column) {
        if (column >= entry.conditions.size()) {
            return null;
        }
        int progress = runtime.conditionProgress != null && column < runtime.conditionProgress.size()
                ? runtime.conditionProgress.get(column)
                : 0;
        if (progress >= entry.conditions.get(column)
                .size()) {
            return null;
        }
        return entry.conditions.get(column)
                .get(progress);
    }

    private static CompoundTag conditionContext(ScheduleRuntime runtime, int column) {
        if (runtime.conditionContext != null && column < runtime.conditionContext.size()) {
            return runtime.conditionContext.get(column);
        }
        return new CompoundTag();
    }

    private static Long remainingTicks(Level level, Train train, ScheduleEntry entry, ScheduleWaitCondition condition,
            CompoundTag context) {
        // CRN 列车分离：必须放在 ScheduledDelay 之前判断（它是 ScheduledDelay 的子类）
        if (CrnBridge.isTrainSeparationCondition(condition)) {
            return CrnBridge.trainSeparationHoldTicks(train, entry);
        }
        if (condition instanceof ScheduledDelay delay) {
            int total = context.getInt("TotalTicks");
            int time = context.getInt("Time");
            long totalTicks = total > 0 ? total : delay.totalWaitTicks();
            return Math.max(0, totalTicks - time);
        }
        if (condition instanceof TimeOfDayCondition timeOfDay) {
            return timeOfDayRemaining(level, timeOfDay);
        }
        if (condition instanceof TimedWaitCondition timed) {
            return (long) Math.max(0, timed.totalWaitTicks() - context.getInt("Time"));
        }
        return null;
    }

    private static long timeOfDayRemaining(Level level, TimeOfDayCondition condition) {
        int rotation = condition.getRotation();
        if (rotation <= 0) {
            return 0;
        }
        int dayTime = (int) (level.getDayTime() % rotation);
        CompoundTag data = condition.getData();
        int hour = data.getInt("Hour");
        int minute = data.getInt("Minute");
        int target = (int) ((((hour + 18) % 24) * 1000 + Math.ceil(minute / 60f * 1000)) % rotation);
        int diff = target - dayTime;
        if (diff < 0) {
            diff += rotation;
        }
        return diff;
    }
}
