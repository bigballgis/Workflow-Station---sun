package com.platform.common.relationtable;

import com.platform.common.enums.RelationDataType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * Formats a Relation Table data cell value into a stable, human/spreadsheet-friendly string for CSV
 * export. Shared by Admin Center and User Portal so both emit identical output.
 *
 * <p>Row values live in a JSONB blob, so a {@code created_at}/{@code updated_at} cell can arrive in
 * several shapes depending on who wrote it and how Jackson round-tripped it: a {@code java.sql.Timestamp}
 * / {@code Date} object, an epoch-millis {@code Number}, or a string such as
 * {@code "2026-06-28 13:25:31.123"} (Timestamp#toString) or {@code "2026-06-28T13:25:31"}
 * (LocalDateTime#toString). Spreadsheets misread the fractional-second / 'T' variants (e.g. showing
 * {@code 26:31.0}). For TIMESTAMP / DATE / TIME fields we therefore normalize to a canonical pattern
 * ({@code yyyy-MM-dd HH:mm:ss} / {@code yyyy-MM-dd} / {@code HH:mm:ss}); anything we cannot parse is
 * passed through unchanged.
 */
public final class RelationCsvValueFormatter {

    private static final DateTimeFormatter TS_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_OUT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private RelationCsvValueFormatter() {
    }

    /** Convert a raw cell value to its CSV string form, normalizing date/time columns. */
    public static String format(Object value, RelationDataType type) {
        if (value == null) {
            return "";
        }
        if (type == null) {
            return value.toString();
        }
        switch (type) {
            case TIMESTAMP: {
                LocalDateTime dt = toLocalDateTime(value);
                return dt != null ? dt.format(TS_OUT) : value.toString();
            }
            case DATE: {
                LocalDate d = toLocalDate(value);
                return d != null ? d.format(DATE_OUT) : value.toString();
            }
            case TIME: {
                LocalTime tm = toLocalTime(value);
                return tm != null ? tm.format(TIME_OUT) : value.toString();
            }
            default:
                return value.toString();
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZONE);
        }
        if (value instanceof Number n) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(n.longValue()), ZONE);
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        // Normalize: 'T' separator -> space, drop fractional seconds, drop trailing zone offset.
        String norm = s.replace('T', ' ');
        int dot = norm.indexOf('.');
        if (dot > 0) {
            norm = norm.substring(0, dot);
        }
        norm = norm.replaceAll("[+Zz].*$", "").trim();
        try {
            if (norm.length() <= 10) {
                // date-only -> midnight
                return LocalDate.parse(norm, DATE_OUT).atStartOfDay();
            }
            if (norm.length() == 16) {
                // yyyy-MM-dd HH:mm
                return LocalDateTime.parse(norm, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }
            return LocalDateTime.parse(norm, TS_OUT);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZONE).toLocalDate();
        }
        if (value instanceof Number n) {
            return Instant.ofEpochMilli(n.longValue()).atZone(ZONE).toLocalDate();
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        LocalDateTime dt = toLocalDateTime(s);
        if (dt != null) {
            return dt.toLocalDate();
        }
        try {
            return LocalDate.parse(s.substring(0, Math.min(10, s.length())), DATE_OUT);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalTime toLocalTime(Object value) {
        if (value instanceof Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), ZONE).toLocalTime();
        }
        String s = value.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        LocalDateTime dt = toLocalDateTime(s);
        if (dt != null) {
            return dt.toLocalTime();
        }
        String norm = s;
        int dot = norm.indexOf('.');
        if (dot > 0) {
            norm = norm.substring(0, dot);
        }
        try {
            return LocalTime.parse(norm, norm.length() == 5
                    ? DateTimeFormatter.ofPattern("HH:mm")
                    : TIME_OUT);
        } catch (Exception e) {
            return null;
        }
    }
}
