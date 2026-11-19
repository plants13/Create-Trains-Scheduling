// SPDX-License-Identifier: MPL-2.0

package com.plants13.createtrainsscheduling.audit;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.UUID;

import com.plants13.createtrainsscheduling.CreateTrainsScheduling;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 调度操作审计日志：每行一条 JSON，追加写入 {@code <world>/create_trains_scheduling/audit.log}。
 *
 * <p>只记录"谁、何时、对哪列车、做了什么、结果如何"。写入失败只打日志，不影响游戏逻辑。
 * 日志文件位于世界目录下，随存档一起备份/删除，不写入 Create 的 SavedData。
 */
public final class AuditLog {

    private static volatile Path file;

    private AuditLog() {}

    /** 服务器启动时绑定日志文件路径。 */
    public static void bind(MinecraftServer server) {
        try {
            Path dir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("create_trains_scheduling");
            Files.createDirectories(dir);
            file = dir.resolve("audit.log");
        } catch (IOException e) {
            file = null;
            CreateTrainsScheduling.LOGGER.warn("[CTS] audit log disabled: {}", e.getMessage());
        }
    }

    /** 服务器停止时解除绑定。 */
    public static void unbind() {
        file = null;
    }

    /**
     * 记录一次操作。
     *
     * @param player  操作的玩家（控制台/命令方块为 null）
     * @param action  操作类型，如 {@code set_destination}、{@code override}
     * @param trainId 列车 UUID，可为 null
     * @param detail  补充说明，已去掉换行
     * @param ok      是否成功
     */
    public static void record(ServerPlayer player, String action, UUID trainId, String detail, boolean ok) {
        record(player == null ? "console" : player.getName().getString(),
                player == null ? null : player.getUUID(), action, trainId, detail, ok);
    }

    /** 记录一次操作（玩家名与 UUID 显式传入，便于在非玩家上下文中使用）。 */
    public static void record(String actor, UUID actorId, String action, UUID trainId, String detail, boolean ok) {
        Path target = file;
        if (target == null) {
            return;
        }
        String line = "{\"time\":\"" + Instant.now() + "\""
                + ",\"actor\":\"" + escape(actor) + "\""
                + ",\"actorId\":\"" + (actorId == null ? "" : actorId) + "\""
                + ",\"action\":\"" + escape(action) + "\""
                + ",\"train\":\"" + (trainId == null ? "" : trainId) + "\""
                + ",\"detail\":\"" + escape(detail) + "\""
                + ",\"ok\":" + ok + "}\n";
        synchronized (AuditLog.class) {
            try (BufferedWriter writer = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                writer.write(line);
            } catch (IOException e) {
                CreateTrainsScheduling.LOGGER.warn("[CTS] audit log write failed: {}", e.getMessage());
            }
        }
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
