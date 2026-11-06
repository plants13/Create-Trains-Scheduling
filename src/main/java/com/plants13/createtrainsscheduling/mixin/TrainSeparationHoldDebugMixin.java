// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.Config;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.ScheduleEntry;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;

import de.mrjulsen.crn.api.core.RailwayBackendApi;
import de.mrjulsen.crn.data.schedule.condition.ETrainFilter;
import de.mrjulsen.crn.data.schedule.condition.TrainSeparationCondition;
import de.mrjulsen.crn.data.schedule.instruction.PrioritizedDestinationInstruction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * 临时诊断：打印每次 CRN 扣车计算的输入（间隔、过滤条件、上次发车时间、当前 game time）。
 */
@Mixin(value = TrainSeparationCondition.class, remap = false)
public abstract class TrainSeparationHoldDebugMixin {

    private static final Map<UUID, Long> LAST_LOG = new ConcurrentHashMap<>();

    @Inject(method = "remainingHoldTicks", at = @At("RETURN"), remap = false)
    private static void cts$logHold(Train train, ScheduleEntry entry, CallbackInfoReturnable<Long> cir) {
        if (!Config.debugLogging() || train == null || entry == null || train.name == null) {
            return;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.overworld() == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        Long last = LAST_LOG.get(train.id);
        if (last != null && now - last < 10L) {
            return;
        }
        LAST_LOG.put(train.id, now);

        TrainSeparationCondition condition = findCondition(entry);
        if (condition == null) {
            return;
        }
        CompoundTag data = condition.getData();
        int sepTicks = data.getInt(TrainSeparationCondition.NBT_TICKS);
        String stationFilter = data.getString(TrainSeparationCondition.NBT_STATION_FILTER);
        ETrainFilter trainFilter = ETrainFilter.getByIndex(data.getByte(TrainSeparationCondition.NBT_TRAIN_FILTER));
        String effectiveFilter = stationFilter;
        if (effectiveFilter == null || effectiveFilter.isBlank()) {
            if (entry.instruction instanceof com.simibubi.create.content.trains.schedule.destination.DestinationInstruction instruction) {
                effectiveFilter = instruction.getFilter();
            } else if (entry.instruction instanceof PrioritizedDestinationInstruction instruction) {
                effectiveFilter = String.join(",", instruction.getFilters());
            } else {
                effectiveFilter = "<none>";
            }
        }
        long lastDeparture = effectiveFilter == null || effectiveFilter.isBlank()
                ? -1L
                : RailwayBackendApi.getLastDepartureTime(effectiveFilter, trainFilter, train.id, train.name.getString());

        CreateTrainsScheduling.LOGGER.info(
                "[CTS separation] hold train={} sepTicks={} trainFilter={} stationFilter={} effectiveFilter={} lastDeparture={} now={} hold={}",
                train.name.getString(),
                sepTicks,
                trainFilter,
                stationFilter,
                effectiveFilter,
                lastDeparture,
                now,
                cir.getReturnValue());
    }

    private static TrainSeparationCondition findCondition(ScheduleEntry entry) {
        if (entry.conditions == null) {
            return null;
        }
        for (var group : entry.conditions) {
            for (ScheduleWaitCondition condition : group) {
                if (condition instanceof TrainSeparationCondition separation) {
                    return separation;
                }
            }
        }
        return null;
    }
}
