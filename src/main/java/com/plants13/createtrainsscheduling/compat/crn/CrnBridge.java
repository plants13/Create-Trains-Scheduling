// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.compat.crn;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import de.mrjulsen.crn.api.core.RailwayBackendApi;
import de.mrjulsen.crn.data.settings.GlobalSettings;
import de.mrjulsen.crn.data.settings.TrainCategory;
import de.mrjulsen.crn.data.settings.TrainLine;

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

    /**
     * CRN 提供的交路信息。
     *
     * @param line     线路名（CRN 的 line，可能为空）
     * @param terminus 交路终点（当前区间的 destination）
     * @param category 种类（CRN 的 category，可能为空）
     */
    public record CircuitInfo(String line, String terminus, String category) {

        public boolean isEmpty() {
            return line.isBlank() && terminus.isBlank() && category.isBlank();
        }
    }

    /** 编辑器目录用的命名条目（线路 / 种类）。 */
    public record NamedEntry(String id, String name, int color) {}

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
    public static Set<String> knownStations() {
        return isActive() ? RailwayBackendApi.getKnownStations() : Set.of();
    }

    /**
     * 读取列车的交路信息（线路 / 交路终点 / 种类）。
     *
     * <p>只使用 CRN 的公开 API 快照，不在客户端做任何解析；CRN 未就绪或该列车未被跟踪时返回空。
     */
    public static Optional<CircuitInfo> circuitInfo(UUID trainId) {
        if (!isActive() || trainId == null) {
            return Optional.empty();
        }
        return RailwayBackendApi.getTrain(trainId)
                .map(snapshot -> new CircuitInfo(
                        snapshot.line()
                                .hasName() ? snapshot.line()
                                        .name() : "",
                        snapshot.destination()
                                .isKnown() ? snapshot.destination()
                                        .displayName() : "",
                        snapshot.category()
                                .hasName() ? snapshot.category()
                                        .name() : ""));
    }

    /**
     * 世界里配置的全部列车线路（编辑器目录用）。
     *
     * <p>CRN 没有在公开 API 暴露"全部线路"列表，这里读取它自己的全局设置（同一个数据源，
     * 每条线路/种类的 id 与名字都是配置里的稳定值）；CRN 内部结构变动时安全降级为空列表。
     */
    public static List<NamedEntry> allLines() {
        try {
            List<TrainLine> lines = GlobalSettings.getInstance()
                    .getAllTrainLines();
            List<NamedEntry> result = new ArrayList<>(lines.size());
            for (TrainLine line : lines) {
                result.add(new NamedEntry(line.getId()
                        .toString(), line.getLineName(), line.getColor()
                                .getAsARGB()));
            }
            return result;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    /** 世界里配置的全部列车种类（编辑器目录用）。 */
    public static List<NamedEntry> allCategories() {
        try {
            List<TrainCategory> categories = GlobalSettings.getInstance()
                    .getAllTrainCategories();
            List<NamedEntry> result = new ArrayList<>(categories.size());
            for (TrainCategory category : categories) {
                result.add(new NamedEntry(category.getId()
                        .toString(), category.getCategoryName(), category.getColor()
                                .getAsARGB()));
            }
            return result;
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static String describe() {
        return "CRN active=" + isActive() + ", trackedTrains=" + trackedTrainCount();
    }
}
