// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.plants13.createtrainsscheduling.compat.snr.SnrCompat;
import com.plants13.createtrainsscheduling.dispatch.DirectDispatchTracker;
import com.plants13.createtrainsscheduling.dispatch.OneOffScheduleTracker;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务端事件：注册命令、打印启动自检信息、驱动一次性路线的完成清理。
 */
@EventBusSubscriber(modid = CreateTrainsScheduling.MODID)
public final class ServerEvents {

    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DispatchCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CreateTrainsScheduling.LOGGER.info("[CTS] server started; {}; {}", CrnBridge.describe(), SnrCompat.describe());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        OneOffScheduleTracker.tick(event.getServer());
        DirectDispatchTracker.tick(event.getServer());
    }
}
