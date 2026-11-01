// SPDX-License-Identifier: MPL-2.0

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
 * "精确模式"（EXACT）下发的目的地追踪。
 *
 * <p>EXACT 模式绕过时刻表，直接 {@code Navigation.startNavigation}，所以 Create 的
 * {@code ScheduleRuntime.completed} 不会触发。这里在服务端 tick 里观察导航是否已经结束
 * （{@code navigation.destination == null}），结束时记录日志并停止追踪。
 */
public final class DirectDispatchTracker {

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    private DirectDispatchTracker() {}

    public static void mark(Train train) {
        if (train != null) {
            ACTIVE.add(train.id);
        }
    }

    public static void markCleared(Train train) {
        if (train != null) {
            ACTIVE.remove(train.id);
        }
    }

    public static boolean isTracked(Train train) {
        return train != null && ACTIVE.contains(train.id);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<UUID> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Train train = Create.RAILWAYS.trains.get(id);
            if (train == null || train.navigation == null) {
                iterator.remove();
                continue;
            }
            if (train.navigation.destination == null) {
                iterator.remove();
                CreateTrainsScheduling.LOGGER.info(
                        "[CTS] exact dispatch finished for train {}",
                        train.name == null ? id : train.name.getString());
            }
        }
    }
}
