package com.portal.debug;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug session NDJSON (session fdc174). Remove after verification.
 */
public final class AgentDebugLog {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String SESSION = "fdc174";
    private static final String FILE = "debug-fdc174.log";
    /** Session 97dc8c: auto-complete first task path (ProcessComponent.startProcess). */
    private static final String SESSION_97 = "97dc8c";
    private static final String FILE_97 = "debug-97dc8c.log";

    private AgentDebugLog() {
    }

    /** @deprecated Legacy session; delegates to {@link #fdc174} */
    @Deprecated
    public static void ff0c74(String location, String hypothesisId, String message, Map<String, Object> data) {
        fdc174(location, hypothesisId, message, data);
    }

    public static void fdc174(String location, String hypothesisId, String message, Map<String, Object> data) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("sessionId", SESSION);
            root.put("location", location);
            root.put("hypothesisId", hypothesisId);
            root.put("message", message);
            root.put("data", data != null ? data : Map.of());
            root.put("timestamp", System.currentTimeMillis());
            String line = M.writeValueAsString(root) + "\n";
            Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            for (Path p : new Path[]{
                    cwd.resolve("../../" + FILE).normalize(),
                    cwd.resolve("../" + FILE).normalize(),
                    cwd.resolve(FILE).normalize()
            }) {
                try {
                    Files.writeString(p, line, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    return;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** NDJSON for debug session 97dc8c → {@code debug-97dc8c.log} (see candidate paths). */
    public static void ndjson97dc8c(String location, String hypothesisId, String message, Map<String, Object> data) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("sessionId", SESSION_97);
            root.put("location", location);
            root.put("hypothesisId", hypothesisId);
            root.put("message", message);
            root.put("data", data != null ? data : Map.of());
            root.put("timestamp", System.currentTimeMillis());
            String runId = System.getenv("WORKFLOW_DEBUG_RUN_ID");
            if (runId != null && !runId.isBlank()) {
                root.put("runId", runId.trim());
            }
            String line = M.writeValueAsString(root) + "\n";
            for (Path p : candidatePathsNdjson97()) {
                try {
                    Path parent = p.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(p, line, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    return;
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Order: WORKFLOW_DEBUG_NDJSON_PATH (file or dir), java.io.tmpdir, then user.dir relatives.
     * Docker-friendly: tmpdir is usually writable when repo root is not.
     */
    private static List<Path> candidatePathsNdjson97() {
        List<Path> list = new ArrayList<>();
        String env = System.getenv("WORKFLOW_DEBUG_NDJSON_PATH");
        if (env != null && !env.isBlank()) {
            Path ep = Path.of(env.trim());
            if (Files.isDirectory(ep)) {
                list.add(ep.resolve(FILE_97));
            } else {
                list.add(ep);
            }
        }
        list.add(Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve(FILE_97));
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        list.add(cwd.resolve("../../" + FILE_97).normalize());
        list.add(cwd.resolve("../" + FILE_97).normalize());
        list.add(cwd.resolve(FILE_97));
        return list;
    }
}
