// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.schedule;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.EndTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

/**
 * 无损的 NBT &lt;-&gt; JSON 编解码。
 *
 * <p>NBT 是强类型（byte/short/int/long/float/double/string/数组/列表/复合），JSON 只有一种数字类型。
 * 若直接映射会在往返时丢类型（例如 byte 变成 double），进而让 Create 的 {@code getByte()} 读到错值。
 * 因此这里给每个值都带上类型标记，保证 {@code fromJson(toJson(x))} 与原值完全相等。
 *
 * <p>这是"外部时刻表编辑器"的地基：JSON 是给人编辑的，但底层语义必须能 1:1 还原成 Create 的 Schedule NBT。
 */
public final class NbtJson {

    private NbtJson() {}

    public static JsonElement toJson(Tag tag) {
        if (tag == null) {
            return encode("end", null);
        }
        switch (tag.getId()) {
            case Tag.TAG_BYTE:
                return encode("byte", new JsonPrimitive(((ByteTag) tag).getAsByte()));
            case Tag.TAG_SHORT:
                return encode("short", new JsonPrimitive(((ShortTag) tag).getAsShort()));
            case Tag.TAG_INT:
                return encode("int", new JsonPrimitive(((IntTag) tag).getAsInt()));
            case Tag.TAG_LONG:
                return encode("long", new JsonPrimitive(((LongTag) tag).getAsLong()));
            case Tag.TAG_FLOAT:
                return encode("float", new JsonPrimitive(((FloatTag) tag).getAsFloat()));
            case Tag.TAG_DOUBLE:
                return encode("double", new JsonPrimitive(((DoubleTag) tag).getAsDouble()));
            case Tag.TAG_STRING:
                return encode("string", new JsonPrimitive(tag.getAsString()));
            case Tag.TAG_BYTE_ARRAY: {
                JsonArray arr = new JsonArray();
                for (byte b : ((ByteArrayTag) tag).getAsByteArray()) {
                    arr.add(b);
                }
                return encode("byteArray", arr);
            }
            case Tag.TAG_INT_ARRAY: {
                JsonArray arr = new JsonArray();
                for (int i : ((IntArrayTag) tag).getAsIntArray()) {
                    arr.add(i);
                }
                return encode("intArray", arr);
            }
            case Tag.TAG_LONG_ARRAY: {
                JsonArray arr = new JsonArray();
                for (long l : ((LongArrayTag) tag).getAsLongArray()) {
                    arr.add(l);
                }
                return encode("longArray", arr);
            }
            case Tag.TAG_LIST: {
                JsonArray arr = new JsonArray();
                for (Tag element : (ListTag) tag) {
                    arr.add(toJson(element));
                }
                return encode("list", arr);
            }
            case Tag.TAG_COMPOUND: {
                JsonObject obj = new JsonObject();
                CompoundTag compound = (CompoundTag) tag;
                for (String key : compound.getAllKeys()) {
                    obj.add(key, toJson(compound.get(key)));
                }
                return encode("compound", obj);
            }
            default:
                throw new IllegalArgumentException("Unsupported NBT type: " + tag.getId());
        }
    }

    private static final int MAX_DEPTH = 24;
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_ARRAY_LENGTH = 4096;

    public static Tag fromJson(JsonElement element) {
        return fromJson(element, 0);
    }

    private static Tag fromJson(JsonElement element, int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("NBT JSON nesting is too deep");
        }
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("NBT node must be a JSON object");
        }
        JsonObject node = element.getAsJsonObject();
        String type = node.has("t") ? node.get("t").getAsString() : "compound";
        JsonElement value = node.get("v");
        switch (type) {
            case "end":
                // TAG_End 不能作为普通值/列表元素/复合键值进入 NBT，否则写盘时会截断/失步
                throw new IllegalArgumentException("TAG_End is not allowed in schedule JSON");
            case "byte":
                return ByteTag.valueOf(value.getAsByte());
            case "short":
                return ShortTag.valueOf(value.getAsShort());
            case "int":
                return IntTag.valueOf(value.getAsInt());
            case "long":
                return LongTag.valueOf(value.getAsLong());
            case "float":
                return FloatTag.valueOf(value.getAsFloat());
            case "double":
                return DoubleTag.valueOf(value.getAsDouble());
            case "string":
                return StringTag.valueOf(value.getAsString());
            case "byteArray": {
                JsonArray arr = value.getAsJsonArray();
                checkArraySize(arr);
                byte[] bytes = new byte[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    bytes[i] = arr.get(i).getAsByte();
                }
                return new ByteArrayTag(bytes);
            }
            case "intArray": {
                JsonArray arr = value.getAsJsonArray();
                checkArraySize(arr);
                int[] ints = new int[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    ints[i] = arr.get(i).getAsInt();
                }
                return new IntArrayTag(ints);
            }
            case "longArray": {
                JsonArray arr = value.getAsJsonArray();
                checkArraySize(arr);
                long[] longs = new long[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    longs[i] = arr.get(i).getAsLong();
                }
                return new LongArrayTag(longs);
            }
            case "list": {
                JsonArray arr = value.getAsJsonArray();
                checkArraySize(arr);
                ListTag list = new ListTag();
                int elementType = -1;
                for (JsonElement child : arr) {
                    Tag childTag = fromJson(child, depth + 1);
                    if (childTag.getId() == Tag.TAG_END) {
                        throw new IllegalArgumentException("TAG_End is not allowed in NBT lists");
                    }
                    if (elementType == -1) {
                        elementType = childTag.getId();
                    } else if (elementType != childTag.getId()) {
                        throw new IllegalArgumentException("NBT list elements must all have the same type");
                    }
                    list.add(childTag);
                }
                return list;
            }
            case "compound": {
                JsonObject object = value.getAsJsonObject();
                if (object.size() > MAX_ARRAY_LENGTH) {
                    throw new IllegalArgumentException("NBT compound has too many keys");
                }
                CompoundTag compound = new CompoundTag();
                for (var entry : object.entrySet()) {
                    String key = entry.getKey();
                    if (key.isEmpty() || key.length() > MAX_KEY_LENGTH) {
                        throw new IllegalArgumentException("NBT key is empty or too long");
                    }
                    Tag childTag = fromJson(entry.getValue(), depth + 1);
                    if (childTag.getId() == Tag.TAG_END) {
                        throw new IllegalArgumentException("TAG_End is not allowed in NBT compounds");
                    }
                    compound.put(key, childTag);
                }
                return compound;
            }
            default:
                throw new IllegalArgumentException("Unknown NBT type marker: " + type);
        }
    }

    private static void checkArraySize(JsonArray arr) {
        if (arr.size() > MAX_ARRAY_LENGTH) {
            throw new IllegalArgumentException("NBT array/list is too large");
        }
    }
    private static JsonObject encode(String type, JsonElement value) {
        JsonObject node = new JsonObject();
        node.addProperty("t", type);
        if (value != null) {
            node.add("v", value);
        }
        return node;
    }
}
