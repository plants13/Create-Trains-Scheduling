// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import java.util.function.Supplier;

import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.condition.ScheduleWaitCondition;
import com.simibubi.create.content.trains.schedule.destination.ScheduleInstruction;

import net.createmod.catnip.data.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 时刻表 JSON/NBT 的安全校验（服务端权威）。
 *
 * <p>输入来自玩家剪贴板，属于不可信数据。这里只做**结构校验**，绝不把内容当作命令执行：
 * <ul>
 *   <li>限制条目数、条件数、字符串长度、NBT 深度与节点数</li>
 *   <li>指令/条件 id 必须已注册（拒绝未知 id，避免 Create 的 fallback 行为）</li>
 *   <li>字符串拒绝 {@code §} 格式化符与控制字符（防止文本注入/显示异常）</li>
 * </ul>
 */
public final class ScheduleJsonValidator {

    public static final int MAX_JSON_CHARS = 64 * 1024;
    public static final int MAX_ENTRIES = 64;
    public static final int MAX_CONDITION_COLUMNS = 8;
    public static final int MAX_CONDITIONS_PER_COLUMN = 16;
    public static final int MAX_STRING_LENGTH = 256;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_NODES = 20000;

    private ScheduleJsonValidator() {}

    /** 校验失败：携带可直接发给玩家的翻译消息。 */
    public static final class ValidationException extends Exception {
        private final Component message;

        public ValidationException(Component message) {
            super(message.getString());
            this.message = message;
        }

        public Component message() {
            return message;
        }
    }

    /**
     * 校验一个时刻表 NBT（{@code Schedule.write} 的结构），通过后才可以交给
     * {@code Schedule.fromTag} 反序列化。
     */
    public static void validate(CompoundTag scheduleTag) throws ValidationException {
        if (scheduleTag == null) {
            throw invalid("empty");
        }

        ListTag entries = scheduleTag.getList("Entries", Tag.TAG_COMPOUND);
        if (entries.isEmpty()) {
            throw invalid("entries.empty");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw invalid("entries.too_many", MAX_ENTRIES);
        }

        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.contains("Instruction", Tag.TAG_COMPOUND)) {
                throw invalid("entry.no_instruction", i + 1);
            }
            validateInstruction(entry.getCompound("Instruction"), i + 1);
            validateConditions(entry, i + 1);
        }

        // 全量深度/节点/字符串扫描（覆盖 Data 与条件里的任意字段）
        checkTag(scheduleTag, 0, new int[] { 0 }, "schedule");
    }

    private static void validateInstruction(CompoundTag instruction, int entryIndex)
            throws ValidationException {
        String idText = instruction.getString("Id");
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(idText);
        } catch (Throwable t) {
            throw invalid("instruction.bad_id", entryIndex, idText);
        }
        if (!knownInstruction(id)) {
            throw invalid("instruction.unknown", entryIndex, id.toString());
        }
    }

    private static void validateConditions(CompoundTag entry, int entryIndex) throws ValidationException {
        if (!entry.contains("Conditions", Tag.TAG_LIST)) {
            return;
        }
        ListTag columns = entry.getList("Conditions", Tag.TAG_LIST);
        if (columns.size() > MAX_CONDITION_COLUMNS) {
            throw invalid("conditions.too_many_columns", entryIndex, MAX_CONDITION_COLUMNS);
        }
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            ListTag column = (ListTag) columns.get(columnIndex);
            if (column.size() > MAX_CONDITIONS_PER_COLUMN) {
                throw invalid("conditions.too_many", entryIndex, columnIndex + 1, MAX_CONDITIONS_PER_COLUMN);
            }
            for (int i = 0; i < column.size(); i++) {
                if (!(column.get(i) instanceof CompoundTag condition)) {
                    throw invalid("condition.invalid", entryIndex);
                }
                String idText = condition.getString("Id");
                ResourceLocation id;
                try {
                    id = ResourceLocation.parse(idText);
                } catch (Throwable t) {
                    throw invalid("condition.bad_id", entryIndex, idText);
                }
                if (!knownCondition(id)) {
                    throw invalid("condition.unknown", entryIndex, id.toString());
                }
            }
        }
    }

    private static void checkTag(Tag tag, int depth, int[] nodes, String path) throws ValidationException {
        if (tag == null) {
            return;
        }
        nodes[0]++;
        if (nodes[0] > MAX_NODES) {
            throw invalid("too_large");
        }
        if (depth > MAX_DEPTH) {
            throw invalid("too_deep");
        }
        if (tag instanceof StringTag stringTag) {
            checkString(stringTag.getAsString(), path);
        } else if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                checkTag(compound.get(key), depth + 1, nodes, path + "/" + key);
            }
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                checkTag(list.get(i), depth + 1, nodes, path + "/" + i);
            }
        }
    }

    private static void checkString(String value, String path) throws ValidationException {
        if (value.length() > MAX_STRING_LENGTH) {
            throw invalid("string.too_long", path, MAX_STRING_LENGTH);
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u00a7' || c < 0x20) {
                throw invalid("string.illegal", path);
            }
        }
    }

    private static boolean knownInstruction(ResourceLocation id) {
        for (Pair<ResourceLocation, Supplier<? extends ScheduleInstruction>> pair : Schedule.INSTRUCTION_TYPES) {
            if (pair.getFirst()
                    .equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean knownCondition(ResourceLocation id) {
        for (Pair<ResourceLocation, Supplier<? extends ScheduleWaitCondition>> pair : Schedule.CONDITION_TYPES) {
            if (pair.getFirst()
                    .equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static ValidationException invalid(String key, Object... args) {
        return new ValidationException(Component.translatable(
                "create_trains_scheduling.schedule_editor." + key, args));
    }
}
