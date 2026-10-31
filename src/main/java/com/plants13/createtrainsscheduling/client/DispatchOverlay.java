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

    private DispatchOverlay() {}

    public static void render(GuiGraphics graphics, MapProjection projection, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (projection == null || mc.level == null || CreateClient.RAILWAYS.trackNetworks.isEmpty()) {
            return;
        }
        lastPartialTicks = partialTicks;

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

        // 选站模式：所有车站加醒目黄框
        if (XaeroDispatchMenu.isPicking()) {
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

        // 菜单 / 选站提示（屏幕空间）
        XaeroDispatchMenu.render(graphics);
    }

    /** 返回 true 表示事件已被消费（调用方应取消原事件）。 */
    public static boolean onClick(MapProjection projection, double guiMouseX, double guiMouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (projection == null || mc.level == null) {
            return false;
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
        if (targetTrain != null) {
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
