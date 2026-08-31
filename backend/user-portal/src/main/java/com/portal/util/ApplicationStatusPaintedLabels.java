package com.portal.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.platform.common.util.JsonUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Painted Audit/My Request status text. The JSON is the same file the portal
 * i18n modules import; Maven copies it onto this classpath.
 */
public final class ApplicationStatusPaintedLabels {

    private static final Map<String, Map<String, String>> BY_CODE = load();

    private ApplicationStatusPaintedLabels() {
    }

    public static List<String> storedCodesForKeyword(String keyword) {
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        List<String> codes = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> row : BY_CODE.entrySet()) {
            if (matches(row.getKey(), row.getValue(), needle) && !codes.contains(row.getKey())) {
                codes.add(row.getKey());
            }
        }
        return codes;
    }

    private static boolean matches(String code, Map<String, String> labels, String needle) {
        if (code.toLowerCase(Locale.ROOT).contains(needle)) {
            return true;
        }
        for (String label : labels.values()) {
            if (label != null && label.toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Map<String, String>> load() {
        try (InputStream in = ApplicationStatusPaintedLabels.class.getResourceAsStream(
                "/list/application-status-labels.json")) {
            if (in == null) {
                throw new IllegalStateException("missing classpath list/application-status-labels.json");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Map<String, String>> parsed = JsonUtils.fromJson(
                    json, new TypeReference<Map<String, Map<String, String>>>() { });
            if (parsed == null || parsed.isEmpty()) {
                throw new IllegalStateException("application-status-labels.json has no status codes");
            }
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read application-status-labels.json", e);
        }
    }
}
