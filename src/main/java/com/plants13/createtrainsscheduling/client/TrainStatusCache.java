// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.plants13.createtrainsscheduling.network.RequestTrainStatusPayload;
import com.plants13.createtrainsscheduling.network.TrainStatusPayload;

import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端缓存的服务端列车状态。
 *
 * <p>客户端自己的 {@code Train} 状态（conductor / schedule）不一定及时同步，
 * 所以菜单打开时向服务端查询，显示服务端给出的权威结果。
 */
public final class TrainStatusCache {

    private static final Map<UUID, TrainStatusPayload> STATUS = new ConcurrentHashMap<>();

    private TrainStatusCache() {}

    public static void request(UUID trainId) {
        if (trainId != null && ClientChannelCheck.canSendToServer(RequestTrainStatusPayload.TYPE)) {
            PacketDistributor.sendToServer(new RequestTrainStatusPayload(trainId));
        }
    }

    public static void accept(TrainStatusPayload payload) {
        STATUS.put(payload.trainId(), payload);
    }

    public static TrainStatusPayload get(UUID trainId) {
        return trainId == null ? null : STATUS.get(trainId);
    }

    public static void clear() {
        STATUS.clear();
    }
}
