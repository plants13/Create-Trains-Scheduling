package com.plants13.createtrainsscheduling.schedule;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simibubi.create.content.trains.schedule.Schedule;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Create {@link Schedule} &lt;-&gt; JSON。
 *
 * <p>外部编辑器（单文件 HTML）产出的 JSON 由这里解析，再写入时刻表物品或直接下发列车。
 */
public final class ScheduleJson {

    public static final String FORMAT = "create-trains-scheduling/schedule";
    public static final int VERSION = 1;

    private ScheduleJson() {}

    public static JsonObject toJson(Schedule schedule, HolderLookup.Provider registries) {
        CompoundTag tag = schedule.write(registries);
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("cyclic", schedule.cyclic);
        root.add("schedule", NbtJson.toJson(tag));
        return root;
    }

    public static String toJsonString(Schedule schedule, HolderLookup.Provider registries) {
        return toJson(schedule, registries).toString();
    }

    public static Schedule fromJson(JsonObject root, HolderLookup.Provider registries) {
        if (root == null) {
            throw new IllegalArgumentException("schedule JSON is null");
        }
        if (root.has("format")) {
            String format = root.get("format").getAsString();
            if (!FORMAT.equals(format)) {
                throw new IllegalArgumentException("Unknown schedule format: " + format);
            }
        }
        if (!root.has("schedule")) {
            throw new IllegalArgumentException("schedule JSON has no 'schedule' node");
        }
        Tag tag = NbtJson.fromJson(root.get("schedule"));
        if (!(tag instanceof CompoundTag compound)) {
            throw new IllegalArgumentException("'schedule' node is not a compound");
        }
        return Schedule.fromTag(registries, compound);
    }

    public static Schedule fromJsonString(String json, HolderLookup.Provider registries) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject(), registries);
    }
}
