// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.plants13.createtrainsscheduling.network.CircuitInfoPayload;

/** 客户端缓存的服务端 CRN 交路信息。 */
public final class CircuitInfoCache {

    private static final Map<UUID, CircuitInfoPayload> DATA = new ConcurrentHashMap<>();

    private CircuitInfoCache() {}

    public static void accept(CircuitInfoPayload payload) {
        DATA.put(payload.trainId(), payload);
    }

    public static CircuitInfoPayload get(UUID trainId) {
        return trainId == null ? null : DATA.get(trainId);
    }

    public static void clear() {
        DATA.clear();
    }
}
