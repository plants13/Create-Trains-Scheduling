// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import net.minecraft.client.Minecraft;

/** 客户端剪贴板访问（只有显式命令才会触发）。 */
public final class ClientClipboard {

    private ClientClipboard() {}

    public static void copy(String text) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.keyboardHandler != null) {
            minecraft.keyboardHandler.setClipboard(text == null ? "" : text);
        }
    }

    public static String read() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.keyboardHandler == null) {
            return "";
        }
        String text = minecraft.keyboardHandler.getClipboard();
        return text == null ? "" : text;
    }
}
