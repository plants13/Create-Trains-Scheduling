// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.UUID;

import com.simibubi.create.CreateClient;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;

/**
 * 站名搜索的纯逻辑部分：从当前客户端已知的所有车站中按子串（忽略大小写）过滤。
 *
 * <p>结果保留站点 UUID：点击结果时按 UUID 下发，所以同名车站不会走错站。
 * 同名车站会各占一行，并标注所属轨道网络的序号以便区分。
 */
public final class StationSearch {

    /** 一条搜索结果。 */
    public record Hit(UUID id, String name, int networkIndex) {}

    /** 结果上限，避免大型铁路网络时列表过长。 */
    public static final int MAX_RESULTS = 8;

    private StationSearch() {}

    /** 当前搜索框内容；只在路线规划面板激活期间有效。 */
    private static String query = "";

    public static String query() {
        return query;
    }

    /** 追加一个字符（忽略控制字符）。 */
    public static void append(char c) {
        if (c >= 0x20 && c != 0x7F && query.length() < 32) {
            query += c;
        }
    }

    /** 删除最后一个字符。 */
    public static void backspace() {
        if (!query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
        }
    }

    public static void clear() {
        query = "";
    }

    /** 返回匹配 {@code query} 的车站（按名称排序，最多 {@link #MAX_RESULTS} 条）。空查询返回空列表。 */
    public static List<Hit> search(String query) {
        List<Hit> result = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return result;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        TreeMap<String, List<Hit>> sorted = new TreeMap<>();
        int networkIndex = 0;
        for (TrackGraph graph : CreateClient.RAILWAYS.trackNetworks.values()) {
            networkIndex++;
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (station.name == null || station.id == null) {
                    continue;
                }
                if (!station.name.toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                sorted.computeIfAbsent(station.name, k -> new ArrayList<>())
                        .add(new Hit(station.id, station.name, networkIndex));
            }
        }
        for (List<Hit> hits : sorted.values()) {
            for (Hit hit : hits) {
                if (result.size() >= MAX_RESULTS) {
                    return result;
                }
                result.add(hit);
            }
        }
        return result;
    }
}
