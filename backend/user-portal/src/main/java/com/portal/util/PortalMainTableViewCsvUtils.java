package com.portal.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * CSV formatting helpers for portal Main Table View exports.
 */
public final class PortalMainTableViewCsvUtils {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private PortalMainTableViewCsvUtils() {}

    public static String formatCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime dt) {
            return DT_FMT.format(dt);
        }
        return String.valueOf(value);
    }

    public static String csvEscape(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

}
