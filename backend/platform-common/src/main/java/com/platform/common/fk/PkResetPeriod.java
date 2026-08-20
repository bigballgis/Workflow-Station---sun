package com.platform.common.fk;

/**
 * Sequence reset for {@code customFormat} PK allocation. Counter keys always include
 * the calendar year ({@code yyyyMMdd} / {@code yyyyMM}) when a period is selected.
 */
public enum PkResetPeriod {
    NONE("none"),
    DAY("day"),
    MONTH("month");

    private final String json;

    PkResetPeriod(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }

    public CalendarDateSequence.Period toCalendarPeriod() {
        return switch (this) {
            case DAY -> CalendarDateSequence.Period.DAY;
            case MONTH -> CalendarDateSequence.Period.MONTH;
            case NONE -> null;
        };
    }

    public String scope() {
        return switch (this) {
            case DAY -> "perDay";
            case MONTH -> "perMonth";
            case NONE -> "perTable";
        };
    }

    public static PkResetPeriod fromJson(String raw) {
        if (raw == null || raw.isBlank() || NONE.json.equalsIgnoreCase(raw)) {
            return NONE;
        }
        if (DAY.json.equalsIgnoreCase(raw)) {
            return DAY;
        }
        if (MONTH.json.equalsIgnoreCase(raw)) {
            return MONTH;
        }
        throw new IllegalArgumentException("Unknown PK resetPeriod: " + raw);
    }
}
