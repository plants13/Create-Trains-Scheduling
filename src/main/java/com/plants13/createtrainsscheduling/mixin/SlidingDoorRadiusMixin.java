// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.plants13.createtrainsscheduling.station.StationDoorRadius;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.DoorControlBehaviour;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import com.simibubi.create.content.trains.station.StationBlockEntity;

import net.minecraft.core.BlockPos;

/**
 * F7：列车停靠车站时，离车站方块水平距离超过半径的车门不自动打开。
 *
 * <p>仅影响车站 DoorControl 的自动开门；玩家手动开关门不受影响，电梯不处理。
 */
@Mixin(value = SlidingDoorMovementBehaviour.class, remap = false)
public class SlidingDoorRadiusMixin {

    @Inject(method = "shouldOpenAt", at = @At("RETURN"), cancellable = true, remap = false)
    private void cts$limitDoorByRadius(DoorControlBehaviour controller, MovementContext context,
            CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) {
            return;
        }
        if (!(controller.blockEntity instanceof StationBlockEntity station)) {
            return;
        }
        int radius = StationDoorRadius.get(station);
        if (radius <= 0) {
            return;
        }
        BlockPos stationPos = station.getBlockPos();
        double dx = context.position.x - (stationPos.getX() + 0.5);
        double dz = context.position.z - (stationPos.getZ() + 0.5);
        if (dx * dx + dz * dz > (double) radius * radius) {
            callback.setReturnValue(false);
        }
    }
}
