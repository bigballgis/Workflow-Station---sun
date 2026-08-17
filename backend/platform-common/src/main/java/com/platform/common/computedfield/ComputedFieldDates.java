package com.platform.common.computedfield;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calendar-date parsing for computed fields — mirrors {@code date.ts}.
 *
 * <p>Date arithmetic is a separate path from {@code VALUE} / {@code toNumber}: {@code "2026-01-02"}
 * is not a number. Only {@code -} and {@code DATEDIFF} consult this class.
 *
 * <p>Year-first dates only ({@code YYYY} then {@code -}/{@code .} then month/day). Trailing time is
 * accepted and ignored so DATE and TIMESTAMP columns still subtract as whole days. Ambiguous
 * day-first forms such as {@code 02/06/2026} are not dates.
 */
public final class ComputedFieldDates {

    /**
     * YYYY sep M{1,2} sep D{1,2}, same separator throughout, optional time:
     * space or T, then H:MM[:SS[.fraction]] and optional Z / ±HH:mm.
     */
    private static final Pattern DATE_TEXT = Pattern.compile(
            "^(\\d{4})([./-])(\\d{1,2})\\2(\\d{1,2})"
                    + "(?:[Tt ](\\d{1,2}):(\\d{2})(?::(\\d{2})(?:\\.\\d+)?)?(?:Z|[+-]\\d{2}:\\d{2})?)?$");

    private ComputedFieldDates() {
    }

    /**
     * Epoch day when {@code value} is text that is a real calendar date; otherwise null.
     * Numbers, booleans and blank are never dates.
     *
     * @param value evaluated operand
     * @return epoch day, or null when the value is not a calendar date
     */
    public static Long epochDay(ComputedValue value) {
        if (!(value instanceof ComputedValue.Text text)) {
            return null;
        }
        return parseEpochDay(text.value().trim());
    }

    /**
     * Parses a year-first calendar date. Invalid calendar dates return null.
     *
     * @param raw trimmed text
     * @return epoch day, or null
     */
    public static Long parseEpochDay(String raw) {
        Matcher match = DATE_TEXT.matcher(raw);
        if (!match.matches()) {
            return null;
        }
        try {
            if (!clockSuffixOk(match)) {
                return null;
            }
            LocalDate date = LocalDate.of(
                    Integer.parseInt(match.group(1)),
                    Integer.parseInt(match.group(3)),
                    Integer.parseInt(match.group(4)));
            return date.toEpochDay();
        } catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean clockSuffixOk(Matcher match) {
        String hourText = match.group(5);
        if (hourText == null) {
            return true;
        }
        int hour = Integer.parseInt(hourText);
        int minute = Integer.parseInt(match.group(6));
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return false;
        }
        String secondText = match.group(7);
        if (secondText == null) {
            return true;
        }
        int second = Integer.parseInt(secondText);
        return second >= 0 && second <= 59;
    }
}
