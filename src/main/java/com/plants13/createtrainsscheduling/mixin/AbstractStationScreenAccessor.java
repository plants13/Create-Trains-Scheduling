// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.trains.station.AbstractStationScreen;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;

@Mixin(value = AbstractStationScreen.class, remap = false)
public interface AbstractStationScreenAccessor {

    @Accessor("blockEntity")
    StationBlockEntity cts$blockEntity();

    @Accessor("background")
    AllGuiTextures cts$background();
}
