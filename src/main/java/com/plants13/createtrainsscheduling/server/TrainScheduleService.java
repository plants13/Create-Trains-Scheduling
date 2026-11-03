// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.network.ChannelCheck;
import com.plants13.createtrainsscheduling.network.ClipboardRequestPayload;
import com.plants13.createtrainsscheduling.network.EditorJsonPayload;
import com.plants13.createtrainsscheduling.network.TrainScheduleActionPayload;
import com.plants13.createtrainsscheduling.permissions.ModPermission;
import com.plants13.createtrainsscheduling.permissions.PermissionManager;
import com.plants13.createtrainsscheduling.schedule.NbtJson;
import com.plants13.createtrainsscheduling.schedule.ScheduleJson;
import com.plants13.createtrainsscheduling.schedule.ScheduleJsonValidator;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.schedule.Schedule;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 列车时刻表的导出/导入（GUI 触发）。
 *
 * <p>与手持时刻表命令共用同一套安全边界：{@code schedule} 权限、cooldown、一次性 token、
 * 大小限制与 {@link ScheduleJsonValidator} 结构校验。剪贴板内容永远只当作 JSON 数据。
 */
public final class TrainScheduleService {

    public static final String KIND_TRAIN_SCHEDULE = EditorJsonPayload.KIND_TRAIN_SCHEDULE;

    private static final long COOLDOWN_MS = 2000L;
    private static final long TOKEN_TTL_MS = 15000L;

    private record PendingImport(UUID token, long expiresAt, UUID trainId, boolean pause) {}

    private static final Map<UUID, PendingImport> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new ConcurrentHashMap<>();

    private TrainScheduleService() {}

    public static void handleAction(ServerPlayer player, UUID trainId, int action) {
        Train train = Create.RAILWAYS.trains.get(trainId);
        if (train == null) {
            fail(player, Component.translatable("create_trains_scheduling.train_schedule.no_train"));
            return;
        }
        if (!PermissionManager.has(player, ModPermission.SCHEDULE)) {
            fail(player, Component.translatable("create_trains_scheduling.network.no_permission",
                    ModPermission.SCHEDULE.id()));
            return;
        }
        long now = System.currentTimeMillis();
        Long last = LAST_ACTION.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MS) {
            fail(player, Component.translatable("create_trains_scheduling.train_schedule.cooldown"));
            return;
        }
        LAST_ACTION.put(player.getUUID(), now);

        switch (action) {
            case TrainScheduleActionPayload.ACTION_EXPORT -> export(player, train);
            case TrainScheduleActionPayload.ACTION_IMPORT_RUN -> requestImport(player, train, false);
            case TrainScheduleActionPayload.ACTION_IMPORT_PAUSE -> requestImport(player, train, true);
            default -> fail(player, Component.translatable("create_trains_scheduling.train_schedule.unknown_action"));
        }
    }

    private static void export(ServerPlayer player, Train train) {
        Schedule schedule = train.runtime.getSchedule();
        if (schedule == null) {
            fail(player, Component.translatable("create_trains_scheduling.train_schedule.none"));
            return;
        }
        if (!ChannelCheck.canSendTo(player, EditorJsonPayload.TYPE)) {
            fail(player, Component.translatable("create_trains_scheduling.schedule_editor.client_missing"));
            return;
        }

        CompoundTag tag = schedule.write(player.getServer()
                .registryAccess());
        int current = schedule.entries.isEmpty() ? 0 : Math.max(0, Math.min(train.runtime.currentEntry, schedule.entries.size() - 1));
        if (current > 0) {
            tag.putInt("Progress", current);
        } else {
            tag.remove("Progress");
        }

        JsonObject root = new JsonObject();
        root.addProperty("format", ScheduleJson.FORMAT);
        root.addProperty("version", ScheduleJson.VERSION);
        root.addProperty("cyclic", schedule.cyclic);
        root.add("schedule", NbtJson.toJson(tag));

        JsonObject runtime = new JsonObject();
        runtime.addProperty("trainId", train.id.toString());
        runtime.addProperty("trainName", train.name == null ? train.id.toString() : train.name.getString());
        runtime.addProperty("currentEntry", current);
        runtime.addProperty("paused", train.runtime.paused);
        runtime.addProperty("completed", train.runtime.completed);
        runtime.addProperty("autoSchedule", train.runtime.isAutoSchedule);
        runtime.addProperty("state", train.runtime.state == null ? "PRE_TRANSIT" : train.runtime.state.name());
        root.add("runtime", runtime);

        PacketDistributor.sendToPlayer(player,
                new EditorJsonPayload(KIND_TRAIN_SCHEDULE, root.toString()));
    }

    private static void requestImport(ServerPlayer player, Train train, boolean pause) {
        if (!ChannelCheck.canSendTo(player, ClipboardRequestPayload.TYPE)) {
            fail(player, Component.translatable("create_trains_scheduling.schedule_editor.client_missing"));
            return;
        }
        UUID token = UUID.randomUUID();
        PENDING.put(player.getUUID(), new PendingImport(token, System.currentTimeMillis() + TOKEN_TTL_MS,
                train.id, pause));
        PacketDistributor.sendToPlayer(player, new ClipboardRequestPayload(token));
        player.displayClientMessage(
                Component.translatable("create_trains_scheduling.train_schedule.import_requested"), false);
    }

    /** @return true 表示这次剪贴板回传属于列车导入，已被消费。 */
    public static boolean handleClipboardContent(ServerPlayer player, UUID token, String text) {
        PendingImport pending = PENDING.get(player.getUUID());
        if (pending == null || !pending.token()
                .equals(token)) {
            return false;
        }
        PENDING.remove(player.getUUID());
        if (System.currentTimeMillis() > pending.expiresAt()) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.timeout"));
            return true;
        }
        if (!PermissionManager.has(player, ModPermission.SCHEDULE)) {
            fail(player, Component.translatable("create_trains_scheduling.network.no_permission",
                    ModPermission.SCHEDULE.id()));
            return true;
        }
        Train train = Create.RAILWAYS.trains.get(pending.trainId());
        if (train == null) {
            fail(player, Component.translatable("create_trains_scheduling.train_schedule.no_train"));
            return true;
        }
        if (text == null || text.length() > ScheduleJsonValidator.MAX_JSON_CHARS) {
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.too_large",
                    ScheduleJsonValidator.MAX_JSON_CHARS));
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
            boolean wasAuto = train.runtime.isAutoSchedule;
            train.runtime.setSchedule(schedule, wasAuto);
            if (pending.pause()) {
                train.runtime.paused = true;
            }
            player.displayClientMessage(Component.translatable(
                    "create_trains_scheduling.train_schedule.import_success"), false);
        } catch (ScheduleJsonValidator.ValidationException e) {
            fail(player, e.message());
        } catch (Throwable t) {
            CreateTrainsScheduling.LOGGER.debug("[CTS] rejected train schedule JSON from {}: {}", player.getName()
                    .getString(), t.getMessage());
            fail(player, Component.translatable("create_trains_scheduling.command.writesch.invalid_json"));
        }
        return true;
    }

    private static void fail(ServerPlayer player, Component message) {
        player.displayClientMessage(message, false);
    }
}
