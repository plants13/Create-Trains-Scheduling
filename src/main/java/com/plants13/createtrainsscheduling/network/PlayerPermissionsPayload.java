// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.network;

import java.util.EnumSet;
import java.util.Set;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;
import com.plants13.createtrainsscheduling.permissions.ModPermission;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：该玩家拥有的功能权限（用于菜单灰显）。 */
public record PlayerPermissionsPayload(int mask) implements CustomPacketPayload {

    public static final Type<PlayerPermissionsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateTrainsScheduling.MODID, "player_permissions"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerPermissionsPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, PlayerPermissionsPayload::mask,
                    PlayerPermissionsPayload::new);

    public static int maskOf(Set<ModPermission> permissions) {
        int mask = 0;
        for (ModPermission permission : permissions) {
            mask |= 1 << permission.ordinal();
        }
        return mask;
    }

    public static PlayerPermissionsPayload of(Set<ModPermission> permissions) {
        return new PlayerPermissionsPayload(maskOf(permissions));
    }

    public boolean has(ModPermission permission) {
        return (mask & (1 << permission.ordinal())) != 0;
    }

    public Set<ModPermission> permissions() {
        Set<ModPermission> result = EnumSet.noneOf(ModPermission.class);
        for (ModPermission permission : ModPermission.values()) {
            if (has(permission)) {
                result.add(permission);
            }
        }
        return result;
    }

    @Override
    public Type<PlayerPermissionsPayload> type() {
        return TYPE;
    }
}
