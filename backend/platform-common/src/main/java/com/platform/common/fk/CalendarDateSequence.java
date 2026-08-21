package com.platform.common.fk;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Date-prefixed PK sequence that resets on a calendar period ({@code YYYYMMDD} daily or
 * {@code YYYYMM} monthly).
 *
 * <p>The calendar period uses {@link #ZONE} — the same Asia/Shanghai (UTC+8) zone as table-design
 * audit fields ({@code SystemAuditFieldFiller} / {@code created_at}).
 *
 * <p>{@code padWidth} is a <em>minimum</em> digit count: value {@code 10000} with width 4 becomes
 * {@code 10000} (five digits), matching {@code prefixedSequence} padding.
 */
public final class CalendarDateSequence {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static final int DEFAULT_PAD_WIDTH = 4;

    public enum Period {
        DAY("dailyDateSequence", "perDay", "yyyyMMdd"),
        MONTH("monthlyDateSequence", "perMonth", "yyyyMM");

        private final String strategy;
        private final String scopeType;
        private final DateTimeFormatter keyFormat;

        Period(String strategy, String scopeType, String pattern) {
            this.strategy = strategy;
            this.scopeType = scopeType;
            this.keyFormat = DateTimeFormatter.ofPattern(pattern);
        }

        public String strategy() {
            return strategy;
        }

        public String scopeType() {
            return scopeType;
        }

        public static Period forStrategy(String strategy) {
            if (strategy == null) {
                return null;
            }
            for (Period period : values()) {
                if (period.strategy.equals(strategy)) {
                    return period;
                }
            }
            return null;
        }
    }

    private CalendarDateSequence() {
    }

    public static Clock systemClock() {
        return Clock.system(ZONE);
    }

    public static String periodKey(Clock clock, Period period) {
        Clock effective = clock != null ? clock : systemClock();
        return LocalDate.now(effective).format(period.keyFormat);
    }

    public static int resolvePadWidth(Integer padWidth) {
        return padWidth != null && padWidth > 0 ? padWidth : DEFAULT_PAD_WIDTH;
    }

    public static String format(String periodKey, long value, int padWidth) {
        int width = resolvePadWidth(padWidth);
        return periodKey + String.format(Locale.ROOT, "%0" + width + "d", value);
    }
}
