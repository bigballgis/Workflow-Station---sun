package com.portal.util;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CSV parsing and formatting helpers for portal Main Table view import/export.
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

    public static int findProcessInstanceIdColumn(String[] headers, Set<String> processInstanceIdHeaders) {
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i] != null ? headers[i].trim() : "";
            if (processInstanceIdHeaders.contains(h)) {
                return i;
            }
        }
        return -1;
    }

    public static List<String[]> parseCsvRows(byte[] csvBytes) {
        String text = new String(csvBytes, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else if (c == '\r') {
                // skip
            } else if (c == '\n') {
                fields.add(current.toString());
                current.setLength(0);
                if (!fields.isEmpty()) {
                    rows.add(fields.toArray(new String[0]));
                }
                fields = new ArrayList<>();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0 || !fields.isEmpty()) {
            fields.add(current.toString());
            if (!fields.isEmpty()) {
                rows.add(fields.toArray(new String[0]));
            }
        }
        return rows;
    }

    public static boolean isBlankRow(String[] cells) {
        for (String cell : cells) {
            if (cell != null && !cell.isBlank()) {
                return false;
            }
        }
        return true;
    }

    public static String cellValue(String[] cells, int index) {
        if (index < 0 || index >= cells.length || cells[index] == null) {
            return "";
        }
        return cells[index].trim();
    }
}
