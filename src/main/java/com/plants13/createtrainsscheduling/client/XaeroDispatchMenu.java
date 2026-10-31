// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.platform.Window;
import com.plants13.createtrainsscheduling.network.ClearSchedulePayload;
import com.plants13.createtrainsscheduling.network.GrantOverridePayload;
import com.plants13.createtrainsscheduling.network.TrainStatusPayload;
import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.entity.Train;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 列车调度右键菜单（屏幕空间绘制，不随地图缩放）。
 *
 * <p>状态机：
 * <ul>
 *   <li>{@code CLOSED} → 点地图上的列车 → {@code OPEN}</li>
 *   <li>{@code OPEN} → 选"设定目的地" → {@code PICK_STATION}（点地图车站确认，Esc 取消）</li>
 *   <li>{@code PICK_STATION} → 取消/完成 → {@code CLOSED}</li>
 * </ul>
 */
public final class XaeroDispatchMenu {

    public enum State {
        CLOSED,
        OPEN,
        PICK_STATION
    }

    private static final String PREFIX = "create_trains_scheduling.menu.";
    private static final int ITEM_HEIGHT = 13;
    private static final int PADDING = 5;
    private static final int MIN_WIDTH = 170;

    private static State state = State.CLOSED;
    private static UUID trainId;
    private static double anchorX;
    private static double anchorY;
    private static boolean showDetails;
    private static int panelX;
    private static int panelY;
    private static int panelWidth;
    private static int panelHeight;
    private static final List<Hit> HITS = new ArrayList<>();

    private record Item(Component label, boolean enabled, Runnable action) {}

    private record Hit(int x, int y, int w, int h, boolean enabled, Runnable action) {}

    private XaeroDispatchMenu() {}

    public static State state() {
        return state;
    }

    public static boolean isActive() {
        return state != State.CLOSED;
    }

    public static boolean isPicking() {
        return state == State.PICK_STATION;
    }

    public static UUID trainId() {
        return trainId;
    }

    public static void open(Train train, double mouseX, double mouseY) {
        state = State.OPEN;
        TrainStatusCache.request(train.id);
        trainId = train.id;
        anchorX = mouseX;
        anchorY = mouseY;
        showDetails = false;
    }

    public static void close() {
        state = State.CLOSED;
        trainId = null;
        showDetails = false;
        HITS.clear();
    }

    public static void beginStationPick() {
        state = State.PICK_STATION;
    }

    /** 供 overlay 在选站模式下调用：完成一次目的地设定。 */
    public static void finishStationPick() {
        close();
    }

    /** 返回 true 表示这次点击已被菜单消费（调用方应取消原事件）。 */
    public static boolean handleClick(double mouseX, double mouseY, int button) {
        if (state == State.CLOSED) {
            return false;
        }
        if (button == 1) {
            close();
            return true;
        }
        if (button != 0) {
            return true;
        }
        if (state == State.PICK_STATION) {
            return false; // 交给 XaeroOverlay 处理选站
        }
        Train train = currentTrain();
        if (train == null) {
            close();
            return true;
        }
        layout(train);
        for (Hit hit : HITS) {
            if (mouseX >= hit.x() && mouseX < hit.x() + hit.w()
                    && mouseY >= hit.y() && mouseY < hit.y() + hit.h()) {
                if (hit.enabled()) {
                    hit.action()
                            .run();
                }
                return true;
            }
        }
        close();
        return true;
    }

    public static void render(GuiGraphics graphics) {
        if (state == State.CLOSED) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        double mouseX = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();

        if (state == State.PICK_STATION) {
            Component hint = Component.translatable(PREFIX + "pick_hint");
            int w = mc.font.width(hint);
            int x = (window.getGuiScaledWidth() - w) / 2;
            int y = window.getGuiScaledHeight() - 40;
            graphics.fill(x - 4, y - 3, x + w + 4, y + 11, 0xCC000000);
            graphics.drawString(mc.font, hint, x, y, 0xFFFFF59D, true);
            return;
        }

        Train train = currentTrain();
        if (train == null) {
            close();
            return;
        }
        layout(train);

        int mouseXInt = (int) mouseX;
        int mouseYInt = (int) mouseY;

        // 面板背景 + 边框
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xE0101010);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, 0xFF808080);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, 0xFF808080);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, 0xFF808080);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF808080);

        // 标题
        graphics.drawString(mc.font, train.name == null ? train.id.toString() : train.name.getString(),
                panelX + PADDING, panelY + PADDING, 0xFFFFD54F, true);

        // 状态详情（可选）：显示服务端返回的权威状态
        if (showDetails) {
            TrainStatusPayload status = TrainStatusCache.get(trainId);
            int y = panelY + PADDING + ITEM_HEIGHT;
            y = drawLine(graphics, mc, Component.translatable(PREFIX + "status.conductor",
                    status == null ? unknown() : yesNo(status.canAutoRun())), y, 0xFFB0B0B0);
            y = drawLine(graphics, mc, Component.translatable(PREFIX + "status.schedule",
                    status == null ? unknown() : yesNo(status.hasSchedule())), y, 0xFFB0B0B0);
            Component override;
            if (status == null) {
                override = unknown();
            } else if (status.overrideMode() == TrainStatusPayload.OVERRIDE_NEXT_SIGNAL) {
                override = Component.translatable(PREFIX + "override.next_signal");
            } else if (status.overrideMode() == TrainStatusPayload.OVERRIDE_TIMED) {
                override = Component.translatable(PREFIX + "override.timed", status.overrideTicks() / 20.0);
            } else {
                override = Component.translatable(PREFIX + "override.none");
            }
            drawLine(graphics, mc, Component.translatable(PREFIX + "status.override", override), y, 0xFFB0B0B0);
        }

        // 菜单项（悬停高亮 + 标签）
        List<Item> items = buildItems(train);
        for (int i = 0; i < HITS.size() && i < items.size(); i++) {
            Hit hit = HITS.get(i);
            boolean hovered = mouseXInt >= hit.x() && mouseXInt < hit.x() + hit.w()
                    && mouseYInt >= hit.y() && mouseYInt < hit.y() + hit.h();
            if (hovered && hit.enabled()) {
                graphics.fill(hit.x(), hit.y(), hit.x() + hit.w(), hit.y() + hit.h(), 0x40FFFFFF);
            }
            int color = !hit.enabled() ? 0xFF707070 : (hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
            graphics.drawString(mc.font, items.get(i)
                    .label(), hit.x() + PADDING, hit.y() + 3, color, true);
        }
    }

    private static int drawLine(GuiGraphics graphics, Minecraft mc, Component text, int y, int color) {
        graphics.drawString(mc.font, text, panelX + PADDING, y + 2, color, true);
        return y + ITEM_HEIGHT;
    }

    private static List<Item> buildItems(Train train) {
        TrainStatusPayload status = TrainStatusCache.get(trainId);
        boolean canAutoRun = status == null || status.canAutoRun();
        boolean hasSchedule = status == null || status.hasSchedule();
        List<Item> items = new ArrayList<>();
        items.add(new Item(Component.translatable(PREFIX + "set_destination"), canAutoRun,
                XaeroDispatchMenu::beginStationPick));
        items.add(new Item(Component.translatable(PREFIX + "override_next_signal"), true, () -> {
            PacketDistributor.sendToServer(new GrantOverridePayload(train.id));
            close();
        }));
        items.add(new Item(Component.translatable(PREFIX + (showDetails ? "details_hide" : "details_show")), true,
                () -> showDetails = !showDetails));
        items.add(new Item(Component.translatable(PREFIX + "clear_schedule"), hasSchedule, () -> {
            PacketDistributor.sendToServer(new ClearSchedulePayload(train.id));
            close();
        }));
        items.add(new Item(Component.translatable(PREFIX + "close"), true, XaeroDispatchMenu::close));
        return items;
    }

    private static Component unknown() {
        return Component.translatable(PREFIX + "status.unknown");
    }

    private static void layout(Train train) {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        List<Item> items = buildItems(train);

        int width = MIN_WIDTH;
        width = Math.max(width, mc.font.width(train.name == null ? train.id.toString() : train.name.getString())
                + PADDING * 2);
        for (Item item : items) {
            width = Math.max(width, mc.font.width(item.label()) + PADDING * 3);
        }
        int lines = 1 + (showDetails ? 3 : 0);
        int height = PADDING * 2 + (lines + items.size()) * ITEM_HEIGHT;

        int x = (int) Math.max(2, Math.min(anchorX, window.getGuiScaledWidth() - width - 2));
        int y = (int) Math.max(2, Math.min(anchorY, window.getGuiScaledHeight() - height - 2));

        panelX = x;
        panelY = y;
        panelWidth = width;
        panelHeight = height;

        HITS.clear();
        int itemY = y + PADDING + lines * ITEM_HEIGHT;
        for (Item item : items) {
            HITS.add(new Hit(x + 1, itemY, width - 2, ITEM_HEIGHT, item.enabled(), item.action()));
            itemY += ITEM_HEIGHT;
        }
    }

    private static Train currentTrain() {
        if (trainId == null) {
            return null;
        }
        return CreateClient.RAILWAYS.trains.get(trainId);
    }

    private static Component yesNo(boolean value) {
        return Component.translatable("create_trains_scheduling.command." + (value ? "yes" : "no"));
    }
}
