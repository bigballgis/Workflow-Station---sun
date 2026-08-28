package com.platform.common.list;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * Expands valueless DATETIME filter operators into inclusive calendar-day bounds.
 * The filter dialog sends the operator name only; SQL compares {@code left(stored, 10)}.
 *
 * <p>Bounds are computed in {@link #ZONE} so "today" is the same calendar day the user sees.
 */
public final class ListRelativeDates {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final Set<String> RELATIVE = Set.of(
            "today", "yesterday", "last7days", "last30days",
            "thisWeek", "thisMonth", "thisYear");

    public record DayRange(LocalDate start, LocalDate end) {
        public DayRange {
            if (start == null || end == null) {
                throw new IllegalArgumentException("relative date range bounds are required");
            }
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("relative date range start is after end");
            }
        }
    }

    private ListRelativeDates() {
    }

    public static boolean isRelative(String operator) {
        return operator != null && RELATIVE.contains(operator);
    }

    public static DayRange range(String operator, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("today is required to expand a relative date filter");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("relative date operator is required");
        }
        return switch (operator) {
            case "today" -> new DayRange(today, today);
            case "yesterday" -> {
                LocalDate day = today.minusDays(1);
                yield new DayRange(day, day);
            }
            case "last7days" -> new DayRange(today.minusDays(6), today);
            case "last30days" -> new DayRange(today.minusDays(29), today);
            case "thisWeek" -> {
                LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new DayRange(monday, monday.plusDays(6));
            }
            case "thisMonth" -> new DayRange(
                    today.with(TemporalAdjusters.firstDayOfMonth()),
                    today.with(TemporalAdjusters.lastDayOfMonth()));
            case "thisYear" -> new DayRange(
                    today.with(TemporalAdjusters.firstDayOfYear()),
                    today.with(TemporalAdjusters.lastDayOfYear()));
            default -> throw new IllegalArgumentException("not a relative date operator: " + operator);
        };
    }
}
