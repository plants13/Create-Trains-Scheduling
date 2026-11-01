// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import com.plants13.createtrainsscheduling.network.ClipboardContentPayload;
import com.plants13.createtrainsscheduling.network.ClipboardRequestPayload;
import com.plants13.createtrainsscheduling.network.EditorJsonPayload;
import com.plants13.createtrainsscheduling.schedule.ScheduleJsonValidator;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端：时刻表编辑器的剪贴板交互。 */
public final class ClientScheduleEditorHandler {

    private ClientScheduleEditorHandler() {}

    public static void handleEditorJson(EditorJsonPayload payload) {
        ClientClipboard.copy(payload.json());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Component message = EditorJsonPayload.KIND_CATALOG.equals(payload.kind())
                    ? Component.translatable("create_trains_scheduling.command.getcrn.copied")
                    : Component.translatable("create_trains_scheduling.command.getsch.copied");
            minecraft.player.displayClientMessage(message, false);
        }
    }

    public static void handleClipboardRequest(ClipboardRequestPayload payload) {
        if (!ClientChannelCheck.canSendToServer(ClipboardContentPayload.TYPE)) {
            return;
        }
        String text = ClientClipboard.read();
        if (text.length() > ScheduleJsonValidator.MAX_JSON_CHARS) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "create_trains_scheduling.command.writesch.too_large",
                        ScheduleJsonValidator.MAX_JSON_CHARS), false);
            }
            return;
        }
        PacketDistributor.sendToServer(new ClipboardContentPayload(payload.token(), text));
    }
}
