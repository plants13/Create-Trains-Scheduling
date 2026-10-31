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
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import org.lwjgl.glfw.GLFW;

/**
 * Xaero 世界地图上的调度层（spike 版本）。
 *
 * <p>编译期不依赖 Xaero 的任何类；相机/缩放由 {@code GuiMapMixin} 通过 {@code @Shadow}
 * 读出后作为普通参数传进来（普通类不能直接引用 mixin 类）。
 *
 * <p>注意：Create 本体已经在地图上画了车站图标和**逐车厢**的列车标记（颜色可能被 SnR 等改变），
 * 所以这里不再重复画列车标记，只在鼠标靠近时画高亮框；车站保留我们自己的小方块作为可点击提示。
 */
public final class XaeroOverlay {

    private static final String XAERO_MAP_CLASS = "xaero.map.gui.GuiMap";

    /** 车站命中半径（世界方块）。 */
    private static final double STATION_PICK_RADIUS = 2.5;

    /** 列车命中半径（按每个车厢的中心判定）。 */
    private static final double TRAIN_PICK_RADIUS = 3.0;

    // 最近一帧渲染时的地图参数，供点击事件使用（地图每帧都会渲染，所以总是新鲜的）
    private static volatile boolean hasProjection = false;
    private static volatile double lastCameraX;
    private static volatile double lastCameraZ;
    private static volatile double lastScale;
    private static volatile double lastScreenWidth;
    private static volatile double lastScreenHeight;
    private static volatile float lastPartialTicks;
    private static volatile long lastHoverLogMillis = 0L;

    private XaeroOverlay() {}

    /**
     * 由 mixin 每帧调用。
     *
     * @param mapScale Xaero 的原始缩放值（GuiMap#scale 字段）
     */
    public static void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks,
                              double cameraX, double cameraZ, double mapScale) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CreateClient.RAILWAYS.trackNetworks.isEmpty()) {
            return;
        }
        Projection projection = Projection.create(cameraX, cameraZ, mapScale);
        if (projection == null) {
            return;
        }
        remember(projection, partialTicks);

        ResourceKey<Level> dimension = mc.level.dimension();
        Window window = mc.getWindow();
        double screenMouseX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double screenMouseY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        double worldMouseX = projection.screenToWorldX(screenMouseX);
        double worldMouseZ = projection.screenToWorldZ(screenMouseY);

        PoseStack pose = graphics.pose();
        pose.pushPose();
        projection.applyTo(pose);

        // 车站：我们自己的可点击提示方块
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

        // 列车：不画标记（Create 已经逐车厢画了），只在悬停时高亮所有车厢
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
            maybeLogHover(train, carriages);
            for (Vec3 position : carriages) {
                int x = Mth.floor(position.x);
                int z = Mth.floor(position.z);
                // 画得比 Create 的车厢图标更大，避免被它盖住：外框 + 四角
                graphics.fill(x - 6, z - 6, x + 7, z + 7, 0x44FFC107);
                drawFrame(graphics, x - 6, z - 6, 13, 0xFFFFC107);
                drawFrame(graphics, x - 3, z - 3, 7, 0xFFFFC107);
            }
        }
        pose.popPose();

        // 选站模式：给所有车站加醒目黄框，提示"点一个"
        if (XaeroDispatchMenu.isPicking()) {
            PoseStack pickPose = graphics.pose();
            pickPose.pushPose();
            projection.applyTo(pickPose);
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
            pickPose.popPose();
        }

        // 菜单 / 选站提示（屏幕空间）
        XaeroDispatchMenu.render(graphics);
    }

    /** 画一个 1px 粗的方形边框（世界坐标内）。 */
    private static void drawFrame(GuiGraphics graphics, int x, int z, int size, int color) {
        graphics.fill(x, z, x + size, z + 1, color);
        graphics.fill(x, z + size - 1, x + size, z + size, color);
        graphics.fill(x, z + 1, x + 1, z + size - 1, color);
        graphics.fill(x + size - 1, z + 1, x + size, z + size - 1, color);
    }

    /** 悬停列车时每秒最多打一条日志，用于确认"检测到了但没画出来"还是"根本没检测到"。 */
    private static void maybeLogHover(Train train, List<Vec3> carriages) {
        long now = System.currentTimeMillis();
        if (now - lastHoverLogMillis < 1000L) {
            return;
        }
        lastHoverLogMillis = now;
        Vec3 first = carriages.get(0);
        CreateTrainsScheduling.LOGGER.info("[CTS] hover train '{}' carriages={} first=({}, {})",
                trainName(train), carriages.size(), (int) first.x, (int) first.z);
    }

    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        // Pre 事件按下/抬起都会触发，只处理"按下"，否则一次点击会执行两遍
        if (!isXaeroMapScreen(mc.screen) || event.getAction() != GLFW.GLFW_PRESS || mc.level == null) {
            return;
        }
        Window window = mc.getWindow();
        double screenMouseX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double screenMouseY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        int button = event.getButton();

        // 选站模式：左键选站、右键取消
        if (XaeroDispatchMenu.isPicking()) {
            if (button == 0 && tryPickStationAt(screenMouseX, screenMouseY)) {
                event.setCanceled(true);
            } else if (button == 1) {
                XaeroDispatchMenu.close();
                event.setCanceled(true);
            }
            return;
        }

        // 菜单打开：交给菜单处理（点击外部会关闭菜单）
        if (XaeroDispatchMenu.isActive()) {
            if (XaeroDispatchMenu.handleClick(screenMouseX, screenMouseY, button)) {
                event.setCanceled(true);
            }
            return;
        }

        if (button != 0 || !hasProjection) {
            return;
        }
        Projection projection = currentProjection();
        if (projection == null) {
            return;
        }
        double worldMouseX = projection.screenToWorldX(screenMouseX);
        double worldMouseZ = projection.screenToWorldZ(screenMouseY);
        ResourceKey<Level> dimension = mc.level.dimension();
        float partialTicks = lastPartialTicks;

        // v1：车站暂时没有菜单；点列车打开调度菜单
        if (findStationAt(worldMouseX, worldMouseZ, dimension) != null) {
            return;
        }
        Train train = findTrainAt(worldMouseX, worldMouseZ, dimension, partialTicks);
        if (train != null) {
            XaeroDispatchMenu.open(train, screenMouseX, screenMouseY);
            event.setCanceled(true);
        }
    }

    /** 选站模式下：把鼠标位置的车站作为目的地发给服务端。 */
    private static boolean tryPickStationAt(double screenMouseX, double screenMouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!hasProjection || mc.level == null) {
            return false;
        }
        Projection projection = currentProjection();
        if (projection == null) {
            return false;
        }
        double worldMouseX = projection.screenToWorldX(screenMouseX);
        double worldMouseZ = projection.screenToWorldZ(screenMouseY);
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

    private static Train findTrainAt(double worldMouseX, double worldMouseZ,
                                     ResourceKey<Level> dimension, float partialTicks) {
        Train nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Train train : CreateClient.RAILWAYS.trains.values()) {
            for (Vec3 position : trainCarriagePositions(train, dimension, partialTicks)) {
                double distance = distanceSqr(worldMouseX, worldMouseZ, position);
                if (distance <= TRAIN_PICK_RADIUS * TRAIN_PICK_RADIUS && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = train;
                }
            }
        }
        return nearest;
    }

    /** 通过类名判断是否是 Xaero 全屏地图（含其子类），避免编译期引用 Xaero 类型。 */
    public static boolean isXaeroMapScreen(Object screen) {
        return screen != null && screen.getClass().getName().startsWith(XAERO_MAP_CLASS);
    }

    private static boolean isNear(double worldX, double worldZ, Vec3 target, double radius) {
        return Math.abs(worldX - target.x) <= radius && Math.abs(worldZ - target.z) <= radius;
    }

    private static double distanceSqr(double worldX, double worldZ, Vec3 target) {
        double dx = worldX - target.x;
        double dz = worldZ - target.z;
        return dx * dx + dz * dz;
    }

    private static String trainName(Train train) {
        return train.name == null ? train.id.toString() : train.name.getString();
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
     * 每节车厢的中心位置（用于命中与高亮）。
     *
     * <p>优先用 Create 地图 overlay 的同步数据（与它逐车厢画图标用的是同一份数据，
     * 客户端未加载车厢实体时也能拿到）；拿不到再退回实体位置。
     */
    private static List<Vec3> trainCarriagePositions(Train train, ResourceKey<Level> dimension, float partialTicks) {
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
            // Create 内部实现变动时退回下面的实体位置
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

    private static void remember(Projection projection, float partialTicks) {
        lastCameraX = projection.cameraX;
        lastCameraZ = projection.cameraZ;
        lastScale = projection.scale;
        lastScreenWidth = projection.screenWidth;
        lastScreenHeight = projection.screenHeight;
        lastPartialTicks = partialTicks;
        hasProjection = true;
    }

    private static Projection currentProjection() {
        if (lastScale <= 0 || Double.isNaN(lastScale) || Double.isInfinite(lastScale)) {
            return null;
        }
        return new Projection(lastCameraX, lastCameraZ, lastScale, lastScreenWidth, lastScreenHeight);
    }

    /** 世界坐标与地图屏幕坐标之间的换算（与 Create 本体的 Xaero 集成同款公式）。 */
    private record Projection(double cameraX, double cameraZ, double scale,
                              double screenWidth, double screenHeight) {

        static Projection create(double cameraX, double cameraZ, double mapScale) {
            Minecraft mc = Minecraft.getInstance();
            Window window = mc.getWindow();
            double guiScale = (double) window.getScreenWidth() / window.getGuiScaledWidth();
            double interfaceScale = (double) window.getWidth() / window.getScreenWidth();
            double scale = mapScale / guiScale / interfaceScale;
            if (scale <= 0 || Double.isNaN(scale) || Double.isInfinite(scale)) {
                return null;
            }
            return new Projection(cameraX, cameraZ, scale,
                    window.getGuiScaledWidth(), window.getGuiScaledHeight());
        }

        void applyTo(PoseStack pose) {
            pose.translate(screenWidth / 2.0f, screenHeight / 2.0f, 0);
            pose.scale((float) scale, (float) scale, 1);
            pose.translate(-cameraX, -cameraZ, 0);
        }

        double screenToWorldX(double screenX) {
            return cameraX + (screenX - screenWidth / 2.0) / scale;
        }

        double screenToWorldZ(double screenY) {
            return cameraZ + (screenY - screenHeight / 2.0) / scale;
        }
    }
}
