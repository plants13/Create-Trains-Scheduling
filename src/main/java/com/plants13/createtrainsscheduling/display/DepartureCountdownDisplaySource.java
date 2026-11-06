// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.display;

import java.util.List;

import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.simibubi.create.Create;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * 显示链接数据源：列车站点发车倒计时。
 *
 * <p>目标为 CRN 高级显示器（simple_text / rich_text 模式）时：
 * <ul>
 *   <li>列车在站台等待期间：居中显示红色的倒计时秒数（纯数字，14 秒内会逐秒减少）</li>
 *   <li>发车后 10 秒内：显示一条绿色横线</li>
 *   <li>其余时间：清空</li>
 * </ul>
 *
 * <p>倒计时只对可确定的条件精确（调度延迟 / 一天中的时间 / 动态延迟 / CRN 列车分离）；
 * 遇到物品、流体、玩家、红石等世界状态条件时不显示数字。
 */
public final class DepartureCountdownDisplaySource extends DisplaySource {

    private static final String KEY_PRESENT = "CtsTrainPresent";
    private static final String KEY_DEPARTED_AT = "CtsDepartedAt";
    private static final int DEPARTURE_LINE_TICKS = 200; // 10 秒
    private static final String LINE = "━".repeat(3);
    private static final long DISPLAY_LEAD_TICKS = 20;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        Level level = context.level();
        if (!(level instanceof ServerLevel)) {
            return EMPTY;
        }
        if (!CrnBridge.isAdvancedDisplayTextTarget(context.getTargetBlockEntity())) {
            return EMPTY;
        }
        if (!(context.getSourceBlockEntity() instanceof StationBlockEntity stationBlockEntity)) {
            return EMPTY;
        }
        GlobalStation station = stationBlockEntity.getStation();
        if (station == null) {
            return EMPTY;
        }

        CompoundTag config = context.sourceConfig();
        long now = level.getGameTime();
        Train presentTrain = findNextTrain(level, station);

        if (presentTrain != null) {
            config.putBoolean(KEY_PRESENT, true);
            DepartureCountdown.Result result = DepartureCountdown.compute(level, presentTrain);
            if (result == null || !result.exact()) {
                config.remove(KEY_DEPARTED_AT);
                return EMPTY;
            }
            // 整体提前 1 秒：倒计时少读一秒，并在最后 1 秒直接切到绿色横线
            long adjusted = result.ticks() - DISPLAY_LEAD_TICKS;
            if (adjusted <= 0) {
                if (!config.contains(KEY_DEPARTED_AT)) {
                    config.putLong(KEY_DEPARTED_AT, now);
                }
                return List.of(Component.literal(LINE)
                        .withStyle(ChatFormatting.GREEN));
            }
            config.remove(KEY_DEPARTED_AT);
            long seconds = Math.max(1, (adjusted + 19) / 20);
            return List.of(Component.literal(Long.toString(seconds))
                    .withStyle(ChatFormatting.RED));
        }

        // 列车刚离开站台：记录时间，并在 10 秒内显示绿色横线
        if (config.getBoolean(KEY_PRESENT)) {
            config.putBoolean(KEY_PRESENT, false);
            if (!config.contains(KEY_DEPARTED_AT)) {
                config.putLong(KEY_DEPARTED_AT, now);
            }
        }
        long departedAt = config.getLong(KEY_DEPARTED_AT);
        if (departedAt > 0 && now >= departedAt && now - departedAt <= DEPARTURE_LINE_TICKS) {
            return List.of(Component.literal(LINE)
                    .withStyle(ChatFormatting.GREEN));
        }
        config.remove(KEY_DEPARTED_AT);
        return EMPTY;
    }

    /**
     * 车站可能有多个列车等待；倒计时显示“下一辆要发车的车”，而不是 Create 的最近一列车。
     */
    private static Train findNextTrain(Level level, GlobalStation station) {
        Train best = null;
        long bestTicks = Long.MAX_VALUE;
        for (Train candidate : Create.RAILWAYS.trains.values()) {
            if (candidate == null || candidate.derailed || candidate.runtime == null) {
                continue;
            }
            if (candidate.getCurrentStation() != station) {
                continue;
            }
            DepartureCountdown.Result result = DepartureCountdown.compute(level, candidate);
            if (result == null || !result.exact()) {
                continue;
            }
            if (result.ticks() < bestTicks) {
                bestTicks = result.ticks();
                best = candidate;
            }
        }
        return best != null ? best : station.getPresentTrain();
    }
    /** 倒计时需要 1 秒刷新一次。 */
    @Override
    public int getPassiveRefreshTicks() {
        return 20;
    }
}
