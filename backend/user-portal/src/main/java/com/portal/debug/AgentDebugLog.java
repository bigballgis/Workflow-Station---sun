package com.portal.debug;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug session NDJSON (session fdc174). Remove after verification.
 */
public final class AgentDebugLog {

    private static final ObjectMapper M = new ObjectMapper();
    private static final String SESSION = "fdc174";
    private static final String FILE = "debug-fdc174.log";

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
}
