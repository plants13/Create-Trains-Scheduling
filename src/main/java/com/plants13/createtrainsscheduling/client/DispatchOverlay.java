// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.network.SetDestinationPayload;
import com.plants13.createtrainsscheduling.network.SetRoutePayload;
import com.plants13.createtrainsscheduling.permissions.ModPermission;
import com.simibubi.create.CreateClient;
import com.simibubi.create.compat.trainmap.TrainMapSync;
import com.simibubi.create.compat.trainmap.TrainMapSyncClient;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.data.Couple;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 与具体地图 mod 无关的调度叠加层：车站标记、列车悬停高亮、选站、菜单。
 *
 * <p>Xaero 与 JourneyMap 各自只负责：算出 {@link MapProjection}、把鼠标事件转进来。
 */
public final class DispatchOverlay {

    /** 车站命中半径（世界方块）。 */
    private static final double STATION_PICK_RADIUS = 2.5;

    /** 列车命中半径（按每个车厢的中心判定）。 */
    private static final double TRAIN_PICK_RADIUS = 3.0;

    private static volatile float lastPartialTicks;

    /** 最近一次世界层渲染用到的投影，屏幕层（菜单/面板）复用它。 */
    private static volatile MapProjection lastProjection;

    // 进路规划底部面板的按钮矩形（每帧布局，点击时复用）
    private static int routeConfirmX;
    private static int routeConfirmY;
    private static int routeConfirmW;
    private static int routeConfirmH;
    private static int routeCancelX;
    private static int routeCancelY;
    private static int routeCancelW;
    private static int routeCancelH;

    private DispatchOverlay() {}

    /**
     * 跟随 Create 自带的 train map overlay 开关：它关掉时我们也不显示、不响应点击。
     *
     * <p>值由客户端 tick 每 tick 刷新一次（20 次/秒）缓存，渲染/点击路径只读 boolean，
     * 不在帧内反复访问配置对象；地图里拨开关后最多 1 tick（50ms）生效。
     */
    private static volatile boolean trainMapOverlayEnabled = true;

    public static boolean isTrainMapOverlayEnabled() {
        return trainMapOverlayEnabled;
    }

    /** 由客户端 tick 调用：从 Create 配置同步开关状态。 */
    public static void refreshConfig() {
        try {
            trainMapOverlayEnabled = AllConfigs.client().showTrainMapOverlay.get();
        } catch (Throwable ignored) {
            trainMapOverlayEnabled = true;
        }
    }

    public static void render(GuiGraphics graphics, MapProjection projection, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (!isTrainMapOverlayEnabled()) {
            // 开关关闭时顺手收起菜单，避免"看不见但还能点"的状态
            XaeroDispatchMenu.close();
            return;
        }
        if (projection == null || mc.level == null || CreateClient.RAILWAYS.trackNetworks.isEmpty()) {
            return;
        }
        lastPartialTicks = partialTicks;
        lastProjection = projection;

        ResourceKey<Level> dimension = mc.level.dimension();
        Window window = mc.getWindow();
        double mouseGuiX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseGuiY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        double worldMouseX = projection.screenToWorldX(mouseGuiX);
        double worldMouseZ = projection.screenToWorldZ(mouseGuiY);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        projection.applyTo(pose);

        // 车站：可点击提示方块
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                Vec3 position = stationPosition(graph, station, dimension);
                if (position == null) {
                    continue;
                }
                boolean near = isNear(worldMouseX, worldMouseZ, position, STATION_PICK_RADIUS);
                int color = near ? 0xFFFFC107 : 0xBB4FC3F7;
                int x = Mth.floor(position.x);
                int z = Mth.floor(position.z);
                graphics.fill(x - 1, z - 1, x + 2, z + 2, color);
            }
        }

        // 列车：Create 已逐车厢画标记，这里只在悬停时画高亮框
        for (Train train : CreateClient.RAILWAYS.trains.values()) {
            List<Vec3> carriages = trainCarriagePositions(train, dimension, partialTicks);
            if (carriages.isEmpty()) {
                continue;
            }
            boolean near = carriages.stream()
                    .anyMatch(position -> isNear(worldMouseX, worldMouseZ, position, TRAIN_PICK_RADIUS));
            if (!near) {
                continue;
            }
            for (Vec3 position : carriages) {
                int x = Mth.floor(position.x);
                int z = Mth.floor(position.z);
                graphics.fill(x - 6, z - 6, x + 7, z + 7, 0x44FFC107);
                drawFrame(graphics, x - 6, z - 6, 13, 0xFFFFC107);
                drawFrame(graphics, x - 3, z - 3, 7, 0xFFFFC107);
            }
        }

        // 选站 / 进路规划模式：所有车站加醒目黄框
        if (XaeroDispatchMenu.isPicking() || XaeroDispatchMenu.isRoutePicking()) {
            for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
                for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                    Vec3 position = stationPosition(graph, station, dimension);
                    if (position == null) {
                        continue;
                    }
                    int x = Mth.floor(position.x);
                    int z = Mth.floor(position.z);
                    drawFrame(graphics, x - 3, z - 3, 7, 0xFFFFC107);
                }
            }
        }
        pose.popPose();
    }

    /**
     * 屏幕空间层：菜单、选站提示、进路确认面板。
     *
     * <p>必须在地图 Screen 完全渲染完之后调用（{@code ScreenEvent.Render.Post}），否则会被
     * Create 自带的 train map overlay 盖住。
     */
    public static void renderScreenLayer(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!isTrainMapOverlayEnabled()) {
            XaeroDispatchMenu.close();
            return;
        }
        MapProjection projection = lastProjection;
        if (projection == null || mc.level == null) {
            return;
        }
        if (XaeroDispatchMenu.isRoutePicking()) {
            renderRoutePick(graphics, projection, mc.level.dimension());
        }
        XaeroDispatchMenu.render(graphics);
    }

    /** 返回 true 表示事件已被消费（调用方应取消原事件）。 */
    public static boolean onClick(MapProjection projection, double guiMouseX, double guiMouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (!isTrainMapOverlayEnabled()) {
            return false;
        }
        if (projection == null || mc.level == null) {
            return false;
        }

        if (XaeroDispatchMenu.isRoutePicking()) {
            return handleRoutePickClick(projection, guiMouseX, guiMouseY, button);
        }

        if (XaeroDispatchMenu.isPicking()) {
            if (button == 0 && tryPickStation(projection, guiMouseX, guiMouseY)) {
                return true;
            }
            if (button == 1) {
                XaeroDispatchMenu.close();
                return true;
            }
            return false;
        }

        if (XaeroDispatchMenu.isActive()) {
            // 菜单开着时：点菜单内部走菜单；直接点地图车站 = 快捷设定目的地
            if (XaeroDispatchMenu.isOverMenu(guiMouseX, guiMouseY)) {
                return XaeroDispatchMenu.handleClick(guiMouseX, guiMouseY, button);
            }
            if (button == 0 && mc.level != null) {
                double stationMouseX = projection.screenToWorldX(guiMouseX);
                double stationMouseZ = projection.screenToWorldZ(guiMouseY);
                GlobalStation station = findStationAt(stationMouseX, stationMouseZ, mc.level.dimension());
                UUID quickTrain = XaeroDispatchMenu.trainId();
                if (station != null && quickTrain != null
                        && PermissionCache.has(ModPermission.DISPATCH)
                        && ClientChannelCheck.canSendToServer(SetDestinationPayload.TYPE)) {
                    PacketDistributor.sendToServer(new SetDestinationPayload(quickTrain, station.id));
                    CreateTrainsScheduling.LOGGER.info(
                            "[CTS] quick destination from menu: train={} station={}", quickTrain, station.name);
                    XaeroDispatchMenu.close();
                    return true;
                }
            }
            return XaeroDispatchMenu.handleClick(guiMouseX, guiMouseY, button);
        }

        if (button != 0) {
            return false;
        }
        double worldMouseX = projection.screenToWorldX(guiMouseX);
        double worldMouseZ = projection.screenToWorldZ(guiMouseY);
        ResourceKey<Level> dimension = mc.level.dimension();

        // v1：车站暂时没有菜单；点列车打开调度菜单
        if (findStationAt(worldMouseX, worldMouseZ, dimension) != null) {
            return false;
        }
        Train train = findTrainAt(worldMouseX, worldMouseZ, dimension);
        if (train != null) {
            XaeroDispatchMenu.open(train, guiMouseX, guiMouseY);
            return true;
        }
        return false;
    }

    public static boolean onEsc() {
        if (XaeroDispatchMenu.isActive()) {
            XaeroDispatchMenu.close();
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 多站进路规划
    // ------------------------------------------------------------------

    private static void renderRoutePick(GuiGraphics graphics, MapProjection projection,
                                        ResourceKey<Level> dimension) {
        Minecraft mc = Minecraft.getInstance();
        List<XaeroDispatchMenu.PickedStation> picks = XaeroDispatchMenu.routePicks();

        // 已选车站：屏幕空间编号
        for (int i = 0; i < picks.size(); i++) {
            Vec3 position = findStationPosition(picks.get(i).id(), dimension);
            if (position == null) {
                continue;
            }
            int sx = (int) Math.round(projection.worldToScreenX(position.x));
            int sy = (int) Math.round(projection.worldToScreenY(position.z));
            graphics.fill(sx - 4, sy - 4, sx + 5, sy + 5, 0xCCFFC107);
            graphics.fill(sx - 5, sy - 5, sx + 6, sy - 4, 0xFFFFC107);
            graphics.fill(sx - 5, sy + 4, sx + 6, sy + 5, 0xFFFFC107);
            graphics.fill(sx - 5, sy - 4, sx - 4, sy + 4, 0xFFFFC107);
            graphics.fill(sx + 4, sy - 4, sx + 6, sy + 4, 0xFFFFC107);
            graphics.drawString(mc.font, String.valueOf(i + 1), sx + 7, sy - 6, 0xFFFFFFFF, true);
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int panelW = 360;
        int panelH = 62;
        int x = (screenWidth - panelW) / 2;
        int y = screenHeight - panelH - 12;
        graphics.fill(x, y, x + panelW, y + panelH, 0xE0101010);
        graphics.fill(x, y, x + panelW, y + 1, 0xFF808080);
        graphics.fill(x, y + panelH - 1, x + panelW, y + panelH, 0xFF808080);
        graphics.fill(x, y, x + 1, y + panelH, 0xFF808080);
        graphics.fill(x + panelW - 1, y, x + panelW, y + panelH, 0xFF808080);

        graphics.drawString(mc.font, net.minecraft.network.chat.Component.translatable(
                "create_trains_scheduling.menu.route.hint"), x + 10, y + 7, 0xFFB0B0B0, true);

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < picks.size(); i++) {
            if (i > 0) {
                names.append(" -> ");
            }
            names.append(picks.get(i).name());
        }
        String joined = names.length() > 48 ? names.substring(0, 48) + "…" : names.toString();
        graphics.drawString(mc.font, net.minecraft.network.chat.Component.translatable(
                "create_trains_scheduling.menu.route.summary", picks.size(), joined),
                x + 10, y + 21, 0xFFFFF59D, true);

        int buttonY = y + 38;
        routeConfirmX = x + 10;
        routeConfirmY = buttonY;
        routeConfirmW = 100;
        routeConfirmH = 16;
        routeCancelX = x + 120;
        routeCancelY = buttonY;
        routeCancelW = 80;
        routeCancelH = 16;

        boolean confirmEnabled = !picks.isEmpty();
        int mouseX = (int) (mc.mouseHandler.xpos() * screenWidth / mc.getWindow().getScreenWidth());
        int mouseY = (int) (mc.mouseHandler.ypos() * screenHeight / mc.getWindow().getScreenHeight());
        drawButton(graphics, mc, routeConfirmX, routeConfirmY, routeConfirmW, routeConfirmH,
                net.minecraft.network.chat.Component.translatable(
                        "create_trains_scheduling.menu.route.confirm"),
                confirmEnabled, mouseX, mouseY);
        drawButton(graphics, mc, routeCancelX, routeCancelY, routeCancelW, routeCancelH,
                net.minecraft.network.chat.Component.translatable(
                        "create_trains_scheduling.menu.route.cancel"),
                true, mouseX, mouseY);
    }

    private static void drawButton(GuiGraphics graphics, Minecraft mc, int x, int y, int w, int h,
                                   net.minecraft.network.chat.Component label, boolean enabled,
                                   int mouseX, int mouseY) {
        boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF4C6E30 : 0xFF2A2A2A);
        graphics.fill(x, y, x + w, y + 1, 0xFF808080);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF808080);
        graphics.fill(x, y, x + 1, y + h, 0xFF808080);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF808080);
        graphics.drawString(mc.font, label, x + (w - mc.font.width(label)) / 2, y + 4,
                enabled ? 0xFFE0E0E0 : 0xFF707070, true);
    }

    private static boolean handleRoutePickClick(MapProjection projection, double guiMouseX,
                                                double guiMouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        // 右键：撤销最后一个车站（消费，避免触发其他右键行为）
        if (button == 1) {
            if (!XaeroDispatchMenu.undoRoutePick()) {
                XaeroDispatchMenu.close();
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (inside(guiMouseX, guiMouseY, routeCancelX, routeCancelY, routeCancelW, routeCancelH)) {
            XaeroDispatchMenu.close();
            return true;
        }
        if (inside(guiMouseX, guiMouseY, routeConfirmX, routeConfirmY, routeConfirmW, routeConfirmH)) {
            sendRoute();
            return true;
        }
        double worldMouseX = projection.screenToWorldX(guiMouseX);
        double worldMouseZ = projection.screenToWorldZ(guiMouseY);
        GlobalStation station = findStationAt(worldMouseX, worldMouseZ, mc.level.dimension());
        if (station != null) {
            XaeroDispatchMenu.addRoutePick(station.id, station.name);
            return true;
        }
        // 点在空白处：不消费事件，让地图可以正常拖动
        return false;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static void sendRoute() {
        List<XaeroDispatchMenu.PickedStation> picks = XaeroDispatchMenu.routePicks();
        UUID trainId = XaeroDispatchMenu.trainId();
        if (trainId != null && !picks.isEmpty()
                && ClientChannelCheck.canSendToServer(SetRoutePayload.TYPE)) {
            List<UUID> ids = new ArrayList<>();
            for (XaeroDispatchMenu.PickedStation pick : picks) {
                ids.add(pick.id());
            }
            PacketDistributor.sendToServer(new SetRoutePayload(trainId, ids));
        }
        XaeroDispatchMenu.close();
    }

    private static Vec3 findStationPosition(UUID stationId, ResourceKey<Level> dimension) {
        if (stationId == null) {
            return null;
        }
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (stationId.equals(station.id)) {
                    return stationPosition(graph, station, dimension);
                }
            }
        }
        return null;
    }

    private static boolean tryPickStation(MapProjection projection, double guiMouseX, double guiMouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        double worldMouseX = projection.screenToWorldX(guiMouseX);
        double worldMouseZ = projection.screenToWorldZ(guiMouseY);
        GlobalStation station = findStationAt(worldMouseX, worldMouseZ, mc.level.dimension());
        if (station == null) {
            return false;
        }
        UUID targetTrain = XaeroDispatchMenu.trainId();
        if (targetTrain != null && ClientChannelCheck.canSendToServer(SetDestinationPayload.TYPE)) {
            PacketDistributor.sendToServer(new SetDestinationPayload(targetTrain, station.id));
        }
        CreateTrainsScheduling.LOGGER.info("[CTS] station picked from map: {}", station.name);
        XaeroDispatchMenu.finishStationPick();
        return true;
    }

    private static GlobalStation findStationAt(double worldMouseX, double worldMouseZ,
                                              ResourceKey<Level> dimension) {
        GlobalStation nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                Vec3 position = stationPosition(graph, station, dimension);
                if (position == null) {
                    continue;
                }
                double distance = distanceSqr(worldMouseX, worldMouseZ, position);
                if (distance <= STATION_PICK_RADIUS * STATION_PICK_RADIUS && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = station;
                }
            }
        }
        return nearest;
    }

    private static Train findTrainAt(double worldMouseX, double worldMouseZ, ResourceKey<Level> dimension) {
        Train nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Train train : CreateClient.RAILWAYS.trains.values()) {
            for (Vec3 position : trainCarriagePositions(train, dimension, lastPartialTicks)) {
                double distance = distanceSqr(worldMouseX, worldMouseZ, position);
                if (distance <= TRAIN_PICK_RADIUS * TRAIN_PICK_RADIUS && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = train;
                }
            }
        }
        return nearest;
    }

    private static boolean isNear(double worldX, double worldZ, Vec3 target, double radius) {
        return Math.abs(worldX - target.x) <= radius && Math.abs(worldZ - target.z) <= radius;
    }

    private static double distanceSqr(double worldX, double worldZ, Vec3 target) {
        double dx = worldX - target.x;
        double dz = worldZ - target.z;
        return dx * dx + dz * dz;
    }

    /** 画一个 1px 粗的方形边框。 */
    private static void drawFrame(GuiGraphics graphics, int x, int z, int size, int color) {
        graphics.fill(x, z, x + size, z + 1, color);
        graphics.fill(x, z + size - 1, x + size, z + size, color);
        graphics.fill(x, z + 1, x + 1, z + size - 1, color);
        graphics.fill(x + size - 1, z + 1, x + size, z + size - 1, color);
    }

    private static Vec3 stationPosition(TrackGraph graph, GlobalStation station, ResourceKey<Level> dimension) {
        Couple<TrackNodeLocation> edgeLocation = station.edgeLocation;
        if (edgeLocation == null) {
            return null;
        }
        TrackNode node = graph.locateNode(edgeLocation.getFirst());
        TrackNode other = graph.locateNode(edgeLocation.getSecond());
        if (node == null || other == null) {
            return null;
        }
        if (node.getLocation().dimension != dimension) {
            return null;
        }
        TrackEdge edge = graph.getConnection(Couple.create(node, other));
        if (edge == null) {
            return null;
        }
        double length = edge.getLength();
        if (length <= 0) {
            return null;
        }
        return edge.getPosition(graph, station.getLocationOn(edge) / length);
    }

    /**
     * 每节车厢的中心位置：优先用 Create 地图 overlay 的同步数据（与它逐车厢画图标同源），
     * 客户端未加载车厢实体时也能拿到。
     */
    private static List<Vec3> trainCarriagePositions(Train train, ResourceKey<Level> dimension,
                                                     float partialTicks) {
        List<Vec3> positions = new ArrayList<>();
        try {
            TrainMapSync.TrainMapSyncEntry entry = TrainMapSyncClient.currentData.get(train.id);
            if (entry != null && entry.dimensions != null) {
                int carriageCount = Math.min(train.carriages.size(), entry.dimensions.size());
                for (int i = 0; i < carriageCount; i++) {
                    if (entry.dimensions.get(i) != dimension) {
                        continue;
                    }
                    Vec3 first = entry.getPosition(i, true, partialTicks);
                    Vec3 second = entry.getPosition(i, false, partialTicks);
                    positions.add(first.add(second)
                            .scale(0.5));
                }
            }
        } catch (Throwable ignored) {
            // Create 内部实现变动时退回实体位置
        }
        if (!positions.isEmpty()) {
            return positions;
        }
        try {
            Optional<BlockPos> position = train.getPositionInDimension(dimension);
            position.ifPresent(pos -> positions.add(Vec3.atCenterOf(pos)));
        } catch (Throwable ignored) {
            // ignore
        }
        return positions;
    }
}
