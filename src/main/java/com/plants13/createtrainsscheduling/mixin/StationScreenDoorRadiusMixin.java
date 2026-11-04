// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.plants13.createtrainsscheduling.client.ClientChannelCheck;
import com.plants13.createtrainsscheduling.client.StationDoorRadiusClient;
import com.plants13.createtrainsscheduling.network.StationDoorRadiusQueryPayload;
import com.plants13.createtrainsscheduling.network.StationDoorRadiusSetPayload;
import com.plants13.createtrainsscheduling.station.StationDoorRadius;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import com.simibubi.create.content.trains.station.StationScreen;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * F7：在 Create 车站 GUI 的 Door Control 右侧追加“开门半径”滚动输入框。
 */
@Mixin(value = StationScreen.class, remap = false)
public abstract class StationScreenDoorRadiusMixin {

    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void cts$addDoorRadiusWidget(CallbackInfo callback) {
        AbstractStationScreenAccessor accessor = (AbstractStationScreenAccessor) (Object) this;
        StationBlockEntity station = accessor.cts$blockEntity();
        AllGuiTextures background = accessor.cts$background();
        if (station == null || background == null) {
            return;
        }
        StationScreen screen = (StationScreen) (Object) this;
        int x = (screen.width - background.getWidth()) / 2;
        int y = (screen.height - background.getHeight()) / 2;

        Label label = new Label(x + 101, y + 108, Component.empty()).withShadow();
        ScrollInput input = new ScrollInput(x + 97, y + 102, 53, 16)
                .withRange(0, StationDoorRadius.MAX + 1)
                .titled(Component.translatable("create_trains_scheduling.station.door_radius"))
                .format(value -> Component.literal(value == 0 ? "∞" : Integer.toString(value)))
                .writingTo(label)
                .setState(0);
        input.calling(value -> {
            if (ClientChannelCheck.canSendToServer(StationDoorRadiusSetPayload.TYPE)) {
                PacketDistributor.sendToServer(new StationDoorRadiusSetPayload(station.getBlockPos(), value));
            }
        });

        ScreenWidgetAccessor screenAccessor = (ScreenWidgetAccessor) (Object) this;
        screenAccessor.cts$addRenderableWidget(input);
        screenAccessor.cts$addRenderableWidget(label);
        StationDoorRadiusClient.begin(station.getBlockPos(), input, label);

        if (ClientChannelCheck.canSendToServer(StationDoorRadiusQueryPayload.TYPE)) {
            PacketDistributor.sendToServer(new StationDoorRadiusQueryPayload(station.getBlockPos()));
        }
    }

    @Inject(method = "removed", at = @At("TAIL"), remap = false)
    private void cts$clearDoorRadiusClient(CallbackInfo callback) {
        StationDoorRadiusClient.clear();
    }
}
