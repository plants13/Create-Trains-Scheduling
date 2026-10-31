// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.client.TrainStatusCache;
import com.plants13.createtrainsscheduling.dispatch.DispatchService;
import com.plants13.createtrainsscheduling.dispatch.ForceReleasePermission;
import com.plants13.createtrainsscheduling.dispatch.OneOffScheduleTracker;
import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 调度操作的客户端 → 服务端网络层。
 *
 * <p>服务端权威：所有校验（权限、可调度性、站点存在）都在服务端做，客户端只发意图。
 */
@EventBusSubscriber(modid = CreateTrainsScheduling.MODID)
public final class DispatchNetwork {

    private static final String PREFIX = "create_trains_scheduling.network.";

    private DispatchNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetDestinationPayload.TYPE, SetDestinationPayload.CODEC,
                DispatchNetwork::handleSetDestination);
        registrar.playToServer(GrantOverridePayload.TYPE, GrantOverridePayload.CODEC,
                DispatchNetwork::handleGrantOverride);
        registrar.playToServer(ClearSchedulePayload.TYPE, ClearSchedulePayload.CODEC,
                DispatchNetwork::handleClearSchedule);
        registrar.playToServer(RequestTrainStatusPayload.TYPE, RequestTrainStatusPayload.CODEC,
                DispatchNetwork::handleRequestStatus);
        registrar.playToClient(TrainStatusPayload.TYPE, TrainStatusPayload.CODEC,
                DispatchNetwork::handleTrainStatus);
    }

    private static void handleSetDestination(SetDestinationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
                return;
            }
            if (!canDispatch(player, train)) {
                reply(player, Component.translatable(PREFIX + "no_permission"));
                return;
            }
            GlobalStation station = findStation(train, payload.stationId());
            if (station == null) {
                reply(player, Component.translatable(PREFIX + "station_not_found"));
                return;
            }
            DispatchService.Result result = DispatchService.setDestination(
                    player.getServer()
                            .registryAccess(),
                    train, station);
            reply(player, result.message());
        });
    }

    private static void handleGrantOverride(GrantOverridePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
                return;
            }
            if (!ForceReleasePermission.canForceRelease(player)) {
                reply(player, Component.translatable(PREFIX + "no_override_permission"));
                return;
            }
            SignalOverrideManager.grantNextSignal(train);
            reply(player, Component.translatable(PREFIX + "override.success"));
        });
    }

    private static void handleClearSchedule(ClearSchedulePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
                return;
            }
            if (!canDispatch(player, train)) {
                reply(player, Component.translatable(PREFIX + "no_permission"));
                return;
            }
            train.runtime.discardSchedule();
            OneOffScheduleTracker.markCleared(train);
            reply(player, Component.translatable(PREFIX + "clear.success"));
        });
    }

    /** v1 权限：OP 或列车车主。 */
    private static boolean canDispatch(ServerPlayer player, Train train) {
        if (player.hasPermissions(2)) {
            return true;
        }
        return train.owner != null && train.owner.equals(player.getUUID());
    }

    private static GlobalStation findStation(Train train, java.util.UUID stationId) {
        if (train.graph == null || stationId == null) {
            return null;
        }
        for (GlobalStation station : train.graph.getPoints(EdgePointType.STATION)) {
            if (stationId.equals(station.id)) {
                return station;
            }
        }
        return null;
    }

    private static void handleRequestStatus(RequestTrainStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = context.player() instanceof ServerPlayer serverPlayer ? serverPlayer : null;
            if (player == null) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                return;
            }
            boolean canAutoRun = train.hasForwardConductor() || train.hasBackwardConductor();
            boolean hasSchedule = train.runtime.getSchedule() != null;
            int overrideMode = TrainStatusPayload.OVERRIDE_NONE;
            int overrideTicks = 0;
            if (SignalOverrideManager.hasNextSignalGrant(train)) {
                overrideMode = TrainStatusPayload.OVERRIDE_NEXT_SIGNAL;
            } else {
                long remaining = SignalOverrideManager.remaining(train);
                if (remaining > 0L) {
                    overrideMode = TrainStatusPayload.OVERRIDE_TIMED;
                    overrideTicks = (int) Math.min(Integer.MAX_VALUE, remaining);
                }
            }
            PacketDistributor.sendToPlayer(player, new TrainStatusPayload(
                    train.id, canAutoRun, hasSchedule, overrideMode, overrideTicks));
        });
    }

    private static void handleTrainStatus(TrainStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrainStatusCache.accept(payload));
    }

    private static void reply(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
