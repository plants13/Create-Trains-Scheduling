// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import java.util.UUID;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.network.ChannelCheck;
import com.plants13.createtrainsscheduling.network.ClipboardRequestPayload;
import com.plants13.createtrainsscheduling.network.EditorJsonPayload;
import com.plants13.createtrainsscheduling.permissions.ModPermission;
import com.plants13.createtrainsscheduling.permissions.PermissionManager;
import com.plants13.createtrainsscheduling.schedule.ScheduleCatalog;
import com.plants13.createtrainsscheduling.schedule.ScheduleJson;
import com.plants13.createtrainsscheduling.schedule.ScheduleJsonValidator;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleItem;

import com.mojang.brigadier.context.CommandContext;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 外部时刻表编辑器的服务端逻辑：/cts getcrn、getsch、writesch。
 *
 * <p><b>安全边界</b>：剪贴板内容一律当作不可信 JSON 数据，只做解析 + 结构校验 + 写入手持物品，
 * 任何情况下都不会被当作命令/代码执行；写入前必须通过权限、token、大小与结构校验。
 * 冷却/TTL/token 机制由 {@link ClipboardExchange} 统一提供。
 */
public final class ScheduleEditorService {

    private static final ClipboardExchange<Void> EXCHANGE = new ClipboardExchange<>();

    private ScheduleEditorService() {}

    // ------------------------------------------------------------------
    // 命令入口
    // ------------------------------------------------------------------

    public static int getCatalog(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.player_only"));
            return 0;
        }
        if (!ChannelCheck.canSendTo(player, EditorJsonPayload.TYPE)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.client_missing"));
            return 0;
        }
        String json = ScheduleCatalog.build()
                .toString();
        if (json.length() > ScheduleJsonValidator.MAX_JSON_CHARS) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.json_too_large",
                    ScheduleJsonValidator.MAX_JSON_CHARS));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new EditorJsonPayload(EditorJsonPayload.KIND_CATALOG, json));
        source.sendSuccess(() -> Component.translatable("create_trains_scheduling.command.getcrn.sent"), false);
        return 1;
    }

    public static int getHeldSchedule(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.player_only"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ScheduleItem)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.command.getsch.no_item"));
            return 0;
        }
        Schedule schedule = ScheduleItem.getSchedule(player.getServer()
                .registryAccess(), stack);
        if (schedule == null) {
            source.sendFailure(Component.translatable("create_trains_scheduling.command.getsch.empty"));
            return 0;
        }
        if (!ChannelCheck.canSendTo(player, EditorJsonPayload.TYPE)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.client_missing"));
            return 0;
        }
        String json;
        try {
            json = ScheduleJson.toJsonString(schedule, player.getServer()
                    .registryAccess());
        } catch (Throwable t) {
            source.sendFailure(Component.translatable("create_trains_scheduling.command.writesch.invalid_json"));
            return 0;
        }
        if (json.length() > ScheduleJsonValidator.MAX_JSON_CHARS) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.json_too_large",
                    ScheduleJsonValidator.MAX_JSON_CHARS));
            return 0;
        }
        PacketDistributor.sendToPlayer(player, new EditorJsonPayload(EditorJsonPayload.KIND_SCHEDULE, json));
        source.sendSuccess(() -> Component.translatable("create_trains_scheduling.command.getsch.sent"), false);
        return 1;
    }

    public static int requestWrite(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.player_only"));
            return 0;
        }
        if (!PermissionManager.has(player, ModPermission.SCHEDULE)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.network.no_permission",
                    ModPermission.SCHEDULE.id()));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ScheduleItem)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.command.writesch.no_item"));
            return 0;
        }
        if (!ChannelCheck.canSendTo(player, ClipboardRequestPayload.TYPE)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.schedule_editor.client_missing"));
            return 0;
        }
        if (!EXCHANGE.tryBegin(player)) {
            source.sendFailure(Component.translatable("create_trains_scheduling.command.writesch.cooldown"));
            return 0;
        }

        UUID token = EXCHANGE.issue(player.getUUID(), null);
        PacketDistributor.sendToPlayer(player, new ClipboardRequestPayload(token));
        source.sendSuccess(() -> Component.translatable("create_trains_scheduling.command.writesch.requested"), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // 客户端回传剪贴板
    // ------------------------------------------------------------------

    public static void notifyTimeout(ServerPlayer player) {
        fail(player, Component.translatable("create_trains_scheduling.command.writesch.timeout"));
    }

    public static boolean handleClipboardContent(ServerPlayer player, UUID token, String text) {
        ClipboardExchange.Pending<Void> pending = EXCHANGE.consume(player.getUUID(), token);
        if (pending == null) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.timeout"));
            return false;
        }
        if (pending.isExpired()) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.timeout"));
            return true;
        }
        if (!PermissionManager.has(player, ModPermission.SCHEDULE)) {
            fail(player, Component.translatable("create_trains_scheduling.network.no_permission",
                    ModPermission.SCHEDULE.id()));
            return true;
        }
        if (text == null || text.length() > ScheduleJsonValidator.MAX_JSON_CHARS) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.too_large",
                    ScheduleJsonValidator.MAX_JSON_CHARS));
            return true;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ScheduleItem)) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.no_item"));
            return true;
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("not a JSON object");
            }
            CompoundTag scheduleTag = ScheduleJson.readScheduleTag(element.getAsJsonObject());
            ScheduleJsonValidator.validate(scheduleTag);
            Schedule schedule = Schedule.fromTag(player.getServer()
                    .registryAccess(), scheduleTag);
            if (schedule == null || schedule.entries.isEmpty()) {
                throw new ScheduleJsonValidator.ValidationException(
                        Component.translatable("create_trains_scheduling.schedule_editor.entries.empty"));
            }
            CompoundTag canonical = schedule.write(player.getServer()
                    .registryAccess());
            stack.set(AllDataComponents.TRAIN_SCHEDULE, canonical);
            player.getInventory()
                    .setChanged();
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable("create_trains_scheduling.command.writesch.success"), false);
        } catch (ScheduleJsonValidator.ValidationException e) {
            fail(player, e.message());
        } catch (Throwable t) {
            CreateTrainsScheduling.LOGGER.debug("[CTS] rejected schedule JSON from {}: {}", player.getName()
                    .getString(), t.getMessage());
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.invalid_json"));
        }
        return true;
    }

    /** 服务器停止时清空待处理 token 与冷却记录。 */
    public static void clearExchange() {
        EXCHANGE.clear();
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
