// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
 */
public final class ScheduleEditorService {

    private static final long WRITE_COOLDOWN_MS = 2000L;
    private static final long TOKEN_TTL_MS = 15000L;

    private record PendingWrite(UUID token, long expiresAt) {}

    private static final Map<UUID, PendingWrite> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_WRITE = new ConcurrentHashMap<>();

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
        String json = ScheduleJson.toJsonString(schedule, player.getServer()
                .registryAccess());
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
        long now = System.currentTimeMillis();
        Long last = LAST_WRITE.get(player.getUUID());
        if (last != null && now - last < WRITE_COOLDOWN_MS) {
            source.sendFailure(Component.translatable("create_trains_scheduling.command.writesch.cooldown"));
            return 0;
        }
        LAST_WRITE.put(player.getUUID(), now);

        UUID token = UUID.randomUUID();
        PENDING.put(player.getUUID(), new PendingWrite(token, now + TOKEN_TTL_MS));
        PacketDistributor.sendToPlayer(player, new ClipboardRequestPayload(token));
        source.sendSuccess(() -> Component.translatable("create_trains_scheduling.command.writesch.requested"), false);
        return 1;
    }

    // ------------------------------------------------------------------
    // 客户端回传剪贴板
    // ------------------------------------------------------------------

    public static void handleClipboardContent(ServerPlayer player, UUID token, String text) {
        PendingWrite pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.token()
                .equals(token)) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.timeout"));
            return;
        }
        PENDING.remove(player.getUUID());
        if (System.currentTimeMillis() > pending.expiresAt()) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.timeout"));
            return;
        }
        if (!PermissionManager.has(player, ModPermission.SCHEDULE)) {
            fail(player, Component.translatable("create_trains_scheduling.network.no_permission",
                    ModPermission.SCHEDULE.id()));
            return;
        }
        if (text == null || text.length() > ScheduleJsonValidator.MAX_JSON_CHARS) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.too_large",
                    ScheduleJsonValidator.MAX_JSON_CHARS));
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ScheduleItem)) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.no_item"));
            return;
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
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
