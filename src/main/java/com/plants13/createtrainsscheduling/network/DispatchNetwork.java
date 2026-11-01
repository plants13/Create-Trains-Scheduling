// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.client.CircuitInfoCache;
import com.plants13.createtrainsscheduling.client.ClientScheduleEditorHandler;
import com.plants13.createtrainsscheduling.client.ClientClipboard;
import com.plants13.createtrainsscheduling.client.PermissionCache;
import com.plants13.createtrainsscheduling.client.TrainStatusCache;
import com.plants13.createtrainsscheduling.compat.crn.CrnBridge;
import com.plants13.createtrainsscheduling.dispatch.DispatchService;
import com.plants13.createtrainsscheduling.dispatch.ForceReleasePermission;
import com.plants13.createtrainsscheduling.dispatch.SignalOverrideManager;
import com.plants13.createtrainsscheduling.permissions.ModPermission;
import com.plants13.createtrainsscheduling.server.ScheduleEditorService;
import com.plants13.createtrainsscheduling.permissions.PermissionManager;
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
 * 权限模型见 {@link PermissionManager}：OP 全权，一般玩家默认无权限，需 OP 按功能授予。
 */
@EventBusSubscriber(modid = CreateTrainsScheduling.MODID)
public final class DispatchNetwork {

    private static final String PREFIX = "create_trains_scheduling.network.";

    private DispatchNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(SetDestinationPayload.TYPE, SetDestinationPayload.CODEC,
                DispatchNetwork::handleSetDestination);
        registrar.playToServer(SetRoutePayload.TYPE, SetRoutePayload.CODEC,
                DispatchNetwork::handleSetRoute);
        registrar.playToServer(GrantOverridePayload.TYPE, GrantOverridePayload.CODEC,
                DispatchNetwork::handleGrantOverride);
        registrar.playToServer(SetScheduleEnabledPayload.TYPE, SetScheduleEnabledPayload.CODEC,
                DispatchNetwork::handleSetScheduleEnabled);
        registrar.playToServer(RequestTrainStatusPayload.TYPE, RequestTrainStatusPayload.CODEC,
                DispatchNetwork::handleRequestStatus);
        registrar.playToClient(TrainStatusPayload.TYPE, TrainStatusPayload.CODEC,
                DispatchNetwork::handleTrainStatus);
        registrar.playToClient(CircuitInfoPayload.TYPE, CircuitInfoPayload.CODEC,
                DispatchNetwork::handleCircuitInfo);
        registrar.playToClient(PlayerPermissionsPayload.TYPE, PlayerPermissionsPayload.CODEC,
                DispatchNetwork::handlePlayerPermissions);
        registrar.playToClient(EditorJsonPayload.TYPE, EditorJsonPayload.CODEC,
                DispatchNetwork::handleEditorJson);
        registrar.playToClient(ClipboardRequestPayload.TYPE, ClipboardRequestPayload.CODEC,
                DispatchNetwork::handleClipboardRequest);
        registrar.playToServer(ClipboardContentPayload.TYPE, ClipboardContentPayload.CODEC,
                DispatchNetwork::handleClipboardContent);
    }

    // ------------------------------------------------------------------
    // 客户端请求
    // ------------------------------------------------------------------

    private static void handleSetDestination(SetDestinationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            if (!require(player, ModPermission.DISPATCH)) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
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

    private static void handleSetRoute(SetRoutePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            if (!require(player, ModPermission.DISPATCH)) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
                return;
            }
            java.util.List<GlobalStation> stations = new java.util.ArrayList<>();
            for (java.util.UUID stationId : payload.stationIds()) {
                GlobalStation station = findStation(train, stationId);
                if (station == null) {
                    reply(player, Component.translatable(PREFIX + "station_not_found"));
                    return;
                }
                stations.add(station);
            }
            DispatchService.Result result = DispatchService.setRoute(
                    player.getServer()
                            .registryAccess(),
                    train, stations);
            reply(player, result.message());
        });
    }

    private static void handleGrantOverride(GrantOverridePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            if (!require(player, ModPermission.OVERRIDE)) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
                return;
            }
            SignalOverrideManager.grantNextSignal(train);
            reply(player, Component.translatable(PREFIX + "override.success"));
        });
    }

    private static void handleSetScheduleEnabled(SetScheduleEnabledPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            if (!require(player, ModPermission.SCHEDULE)) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                reply(player, Component.translatable(PREFIX + "train_not_found"));
                return;
            }
            DispatchService.Result result = DispatchService.setScheduleEnabled(train, payload.enabled());
            reply(player, result.message());
        });
    }

    private static void handleRequestStatus(RequestTrainStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player == null) {
                return;
            }
            Train train = Create.RAILWAYS.trains.get(payload.trainId());
            if (train == null) {
                return;
            }
            int flags = 0;
            if (train.hasForwardConductor() || train.hasBackwardConductor()) {
                flags |= TrainStatusPayload.FLAG_CAN_AUTO_RUN;
            }
            if (train.runtime.getSchedule() != null) {
                flags |= TrainStatusPayload.FLAG_HAS_SCHEDULE;
            }
            if (train.runtime.paused) {
                flags |= TrainStatusPayload.FLAG_PAUSED;
            }
            if (train.runtime.isAutoSchedule) {
                flags |= TrainStatusPayload.FLAG_AUTO_SCHEDULE;
            }
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
            if (ChannelCheck.canSendTo(player, TrainStatusPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new TrainStatusPayload(
                        train.id, flags, overrideMode, overrideTicks));
            }
            // CRN 交路数据（线路 / 交路终点 / 种类）；CRN 未跟踪该列车时发空串清空旧值
            CrnBridge.CircuitInfo circuit = CrnBridge.circuitInfo(train.id)
                    .orElse(new CrnBridge.CircuitInfo("", "", ""));
            if (ChannelCheck.canSendTo(player, CircuitInfoPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, new CircuitInfoPayload(
                        train.id, circuit.line(), circuit.terminus(), circuit.category()));
            }
        });
    }

    // ------------------------------------------------------------------
    // 服务端 → 客户端
    // ------------------------------------------------------------------

    private static void handleTrainStatus(TrainStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrainStatusCache.accept(payload));
    }

    private static void handlePlayerPermissions(PlayerPermissionsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> PermissionCache.accept(payload));
    }

    private static void handleCircuitInfo(CircuitInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CircuitInfoCache.accept(payload));
    }

    private static void handleEditorJson(EditorJsonPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientScheduleEditorHandler.handleEditorJson(payload));
    }

    private static void handleClipboardRequest(ClipboardRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientScheduleEditorHandler.handleClipboardRequest(payload));
    }

    private static void handleClipboardContent(ClipboardContentPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = serverPlayer(context);
            if (player != null) {
                ScheduleEditorService.handleClipboardContent(player, payload.token(), payload.text());
            }
        });
    }

    /** 把玩家的权限同步给客户端（登录、授权/剥夺后调用）。 */
    public static void syncPermissions(ServerPlayer player) {
        if (player != null && ChannelCheck.canSendTo(player, PlayerPermissionsPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, PlayerPermissionsPayload.of(permissionsFor(player)));
        }
    }

    public static Set<ModPermission> permissionsFor(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return EnumSet.allOf(ModPermission.class);
        }
        EnumSet<ModPermission> permissions = EnumSet.noneOf(ModPermission.class);
        if (player.getServer() != null) {
            permissions.addAll(PermissionManager.permissionsOf(player.getServer(), player.getUUID()));
        }
        // 兼容旧的 forceReleasePlayers 配置
        if (ForceReleasePermission.canForceRelease(player)) {
            permissions.add(ModPermission.OVERRIDE);
        }
        return permissions;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static boolean require(ServerPlayer player, ModPermission permission) {
        if (PermissionManager.has(player, permission)) {
            return true;
        }
        reply(player, Component.translatable(PREFIX + "no_permission", permission.id()));
        return false;
    }

    private static ServerPlayer serverPlayer(IPayloadContext context) {
        return context.player() instanceof ServerPlayer player ? player : null;
    }

    private static GlobalStation findStation(Train train, UUID stationId) {
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

    private static void reply(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
