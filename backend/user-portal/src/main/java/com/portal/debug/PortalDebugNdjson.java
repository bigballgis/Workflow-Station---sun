package com.portal.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Optional NDJSON append for Docker / volume mounts.
 * <ul>
 * <li>Set {@code PORTAL_DEBUG_NDJSON_FILE} to a path inside the container (e.g. {@code /tmp/portal-debug.ndjson}).</li>
 * <li>When unset and {@code SPRING_PROFILES_ACTIVE} contains {@code docker} (case-insensitive),
 * defaults to {@code /tmp/portal-debug.ndjson}.</li>
 * </ul>
 * Never log row payloads or secrets — only keys, counts, ids.
 */
public final class PortalDebugNdjson {

    private static final Logger log = LoggerFactory.getLogger(PortalDebugNdjson.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PortalDebugNdjson() {
    }

    private static String resolveNdjsonOutputPath() {
        String path = System.getenv("PORTAL_DEBUG_NDJSON_FILE");
        if (path != null && !path.isBlank()) {
            return path.trim();
        }
        String active = System.getenv("SPRING_PROFILES_ACTIVE");
        if (active != null && active.toLowerCase(Locale.ROOT).contains("docker")) {
            return "/tmp/portal-debug.ndjson";
        }
        return null;
    }

    /**
     * @param sessionId correlates with frontend debug session when set (e.g. 6a3425)
     */
    public static void append(String sessionId, String location, String message, Map<String, Object> data) {
        String path = resolveNdjsonOutputPath();
        if (path == null || path.isBlank()) {
            return;
        }
        try {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("sessionId", sessionId != null ? sessionId : "");
            line.put("location", location);
            line.put("message", message);
            line.put("data", data != null ? data : Map.of());
            line.put("timestamp", System.currentTimeMillis());
            String json = MAPPER.writeValueAsString(line);
            Files.writeString(Path.of(path), json + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("[PortalDebugNdjson] wrote ndjson entry to {} (location={})", path, location);
        } catch (Exception ignored) {
            // debug sink must never break task loading
        }
    }
}
