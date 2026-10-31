// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.permissions;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 世界存档内的权限授予记录（OP 通过命令修改）。
 *
 * <p>OP 始终拥有全部权限，这里只记录被显式授权的一般玩家。
 */
public class PermissionSavedData extends SavedData {

    public static final String DATA_NAME = "create_trains_scheduling_permissions";

    private final Map<UUID, EnumSet<ModPermission>> grants = new HashMap<>();

    public static PermissionSavedData create() {
        return new PermissionSavedData();
    }

    public static PermissionSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        PermissionSavedData data = new PermissionSavedData();
        ListTag list = tag.getList("Grants", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID player = UUID.fromString(entry.getString("Player"));
                EnumSet<ModPermission> permissions = EnumSet.noneOf(ModPermission.class);
                int mask = entry.getInt("Mask");
                for (ModPermission permission : ModPermission.values()) {
                    if ((mask & (1 << permission.ordinal())) != 0) {
                        permissions.add(permission);
                    }
                }
                if (!permissions.isEmpty()) {
                    data.grants.put(player, permissions);
                }
            } catch (IllegalArgumentException ignored) {
                // 损坏的条目跳过
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, EnumSet<ModPermission>> entry : grants.entrySet()) {
            CompoundTag item = new CompoundTag();
            item.putString("Player", entry.getKey()
                    .toString());
            int mask = 0;
            for (ModPermission permission : entry.getValue()) {
                mask |= 1 << permission.ordinal();
            }
            item.putInt("Mask", mask);
            list.add(item);
        }
        tag.put("Grants", list);
        return tag;
    }

    public boolean has(UUID player, ModPermission permission) {
        EnumSet<ModPermission> permissions = grants.get(player);
        return permissions != null && permissions.contains(permission);
    }

    public Set<ModPermission> permissionsOf(UUID player) {
        EnumSet<ModPermission> permissions = grants.get(player);
        return permissions == null ? EnumSet.noneOf(ModPermission.class) : EnumSet.copyOf(permissions);
    }

    public Map<UUID, Set<ModPermission>> all() {
        Map<UUID, Set<ModPermission>> result = new HashMap<>();
        grants.forEach((player, permissions) -> result.put(player, EnumSet.copyOf(permissions)));
        return result;
    }

    public void grant(UUID player, ModPermission permission) {
        grants.computeIfAbsent(player, key -> EnumSet.noneOf(ModPermission.class))
                .add(permission);
        setDirty();
    }

    public void revoke(UUID player, ModPermission permission) {
        EnumSet<ModPermission> permissions = grants.get(player);
        if (permissions == null) {
            return;
        }
        permissions.remove(permission);
        if (permissions.isEmpty()) {
            grants.remove(player);
        }
        setDirty();
    }
}
