// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;

import net.minecraft.core.BlockPos;

/** 客户端：等待/接收车站开门半径，并更新已打开的车站 GUI 控件。 */
public final class StationDoorRadiusClient {

    private static BlockPos pendingPos;
    private static ScrollInput pendingInput;
    private static Label pendingLabel;

    private StationDoorRadiusClient() {}

    public static void begin(BlockPos pos, ScrollInput input, Label label) {
        pendingPos = pos;
        pendingInput = input;
        pendingLabel = label;
    }

    public static void clear() {
        pendingPos = null;
        pendingInput = null;
        pendingLabel = null;
    }

    public static void accept(BlockPos pos, int radius) {
        if (pendingPos == null || !pendingPos.equals(pos)) {
            return;
        }
        if (pendingInput != null) {
            // setState 会通过 writingTo(label) 自动刷新标签，不需要 onChanged，避免回传导致循环
            pendingInput.setState(radius);
        }
    }
}
