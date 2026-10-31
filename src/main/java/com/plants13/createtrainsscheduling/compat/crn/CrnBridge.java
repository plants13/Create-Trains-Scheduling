// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.crn;

import de.mrjulsen.crn.api.core.RailwayBackendApi;

/**
 * CRN（Create Railways Navigator）集成入口。
 *
 * <p><b>项目铁律</b>：整个项目只有这个包允许 {@code import de.mrjulsen.crn.*}。
 * 业务代码通过我们自己的接口消费数据，将来 CRN API 变动时只需要改这里。
 *
 * <p>CRN 是硬依赖，所以这里不做 {@code isLoaded} 判断；若依赖缺失，FML 会在加载阶段就拒绝启动，
 * 不会走到这个类。
 */
public final class CrnBridge {

    private CrnBridge() {}

    /** 铁路后端是否已就绪（只在服务器运行时为 true）。 */
    public static boolean isActive() {
        return RailwayBackendApi.isActive();
    }

    /** CRN 正在跟踪的列车数量，后端未就绪返回 -1。 */
    public static int trackedTrainCount() {
        return isActive() ? RailwayBackendApi.getTrackedTrainCount() : -1;
    }

    /** 已知车站名集合，后端未就绪返回空集合。 */
    public static java.util.Set<String> knownStations() {
        return isActive() ? RailwayBackendApi.getKnownStations() : java.util.Set.of();
    }

    public static String describe() {
        return "CRN active=" + isActive() + ", trackedTrains=" + trackedTrainCount();
    }
}
