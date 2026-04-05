package com.admin.debug;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug-session NDJSON append (session 864cd4). Do not log secrets/PII.
 * <p>
 * <b>Docker / 路径：</b>{@code user.dir} 多为 {@code /app}，且仅 {@code /app/logs} 常挂载到宿主机。
 * 请设置环境变量 {@code WORKFLOW_DEBUG_NDJSON_PATH}（推荐 {@code /app/logs/debug-864cd4.log}），
 * 或 {@code WORKFLOW_DEBUG_NDJSON_DIR}（文件名固定为 {@code debug-864cd4.log}）。
 * JVM 系统属性 {@code workflow.debug.ndjson.path} 也可指定完整文件路径。
 */
public final class AgentNdjsonLog {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String SESSION = "864cd4";
    private static final String FILE_NAME = "debug-864cd4.log";

    private AgentNdjsonLog() {
    }

    private static List<Path> resolveLogTargets() {
        List<Path> targets = new ArrayList<>();
        String explicitPath = getenv("WORKFLOW_DEBUG_NDJSON_PATH");
        if (explicitPath != null && !explicitPath.isBlank()) {
            targets.add(Path.of(explicitPath.trim()));
            return targets;
        }
        String explicitDir = getenv("WORKFLOW_DEBUG_NDJSON_DIR");
        if (explicitDir != null && !explicitDir.isBlank()) {
            targets.add(Path.of(explicitDir.trim()).resolve(FILE_NAME));
            return targets;
        }
        String prop = System.getProperty("workflow.debug.ndjson.path");
        if (prop != null && !prop.isBlank()) {
            targets.add(Path.of(prop.trim()));
            return targets;
        }
        Path cwd = Path.of(System.getProperty("user.dir"));
        targets.add(cwd.resolve(FILE_NAME));
        if (cwd.getFileName() != null && "admin-center".equalsIgnoreCase(cwd.getFileName().toString())
                && cwd.getParent() != null && cwd.getParent().getParent() != null) {
            targets.add(cwd.getParent().getParent().resolve(FILE_NAME));
        }
        return targets;
    }

    private static String getenv(String name) {
        try {
            return System.getenv(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean nonBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void ensureParentDir(Path filePath) {
        Path parent = filePath.getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (Exception ignored) {
            // debug-only
        }
    }

    public static void append(String hypothesisId, String location, String message, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", SESSION);
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("message", message);
            payload.put("data", data != null ? data : Map.of());
            Path cwd = Path.of(System.getProperty("user.dir"));
            payload.put("cwd", cwd.toString());
            List<Path> targetPaths = resolveLogTargets();
            List<String> targetStrs = new ArrayList<>();
            for (Path p : targetPaths) {
                targetStrs.add(p.toAbsolutePath().toString());
            }
            payload.put("logFiles", targetStrs);
            payload.put("resolvedBy", resolveMode());
            payload.put("timestamp", System.currentTimeMillis());
            String line = M.writeValueAsString(payload) + System.lineSeparator();
            for (Path t : targetPaths) {
                try {
                    ensureParentDir(t);
                    Files.writeString(t, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (Exception ignored) {
                    // debug-only
                }
            }
        } catch (Exception ignored) {
            // debug-only
        }
    }

    private static String resolveMode() {
        if (nonBlank(getenv("WORKFLOW_DEBUG_NDJSON_PATH"))) {
            return "WORKFLOW_DEBUG_NDJSON_PATH";
        }
        if (nonBlank(getenv("WORKFLOW_DEBUG_NDJSON_DIR"))) {
            return "WORKFLOW_DEBUG_NDJSON_DIR";
        }
        if (nonBlank(System.getProperty("workflow.debug.ndjson.path"))) {
            return "workflow.debug.ndjson.path";
        }
        return "user.dir_fallback";
    }
}
