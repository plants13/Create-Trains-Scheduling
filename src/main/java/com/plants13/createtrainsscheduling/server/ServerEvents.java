// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.dispatch.DirectDispatchTracker;
import com.plants13.createtrainsscheduling.dispatch.OneOffScheduleTracker;
import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.Create;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 服务端事件（1.20.1 Forge 版）。 */
@Mod.EventBusSubscriber(modid = CreateTrainsScheduling.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerEvents {

    private ServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DispatchCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        CreateTrainsScheduling.LOGGER.info("[CTS] server started (1.20.1 Forge / Create 0.5.1); railways={}",
                Create.RAILWAYS != null);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        SignalOverrideManager.clearAll();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        OneOffScheduleTracker.tick(event.getServer());
        DirectDispatchTracker.tick(event.getServer());
    }
}