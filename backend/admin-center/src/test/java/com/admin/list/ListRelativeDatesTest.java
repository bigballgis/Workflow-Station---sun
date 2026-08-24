package com.admin.list;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListRelativeDatesTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 19);

    @Test
    void todayAndYesterdayAreSingleDays() {
        assertEquals(new ListRelativeDates.DayRange(WEDNESDAY, WEDNESDAY),
                ListRelativeDates.range("today", WEDNESDAY));
        assertEquals(new ListRelativeDates.DayRange(LocalDate.of(2026, 8, 18), LocalDate.of(2026, 8, 18)),
                ListRelativeDates.range("yesterday", WEDNESDAY));
    }

    @Test
    void rollingWindowsIncludeToday() {
        assertEquals(new ListRelativeDates.DayRange(LocalDate.of(2026, 8, 13), WEDNESDAY),
                ListRelativeDates.range("last7days", WEDNESDAY));
        assertEquals(new ListRelativeDates.DayRange(LocalDate.of(2026, 7, 21), WEDNESDAY),
                ListRelativeDates.range("last30days", WEDNESDAY));
    }

    @Test
    void calendarWindowsUseMondayWeekAndFullMonthYear() {
        assertEquals(new ListRelativeDates.DayRange(LocalDate.of(2026, 8, 17), LocalDate.of(2026, 8, 23)),
                ListRelativeDates.range("thisWeek", WEDNESDAY));
        assertEquals(new ListRelativeDates.DayRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)),
                ListRelativeDates.range("thisMonth", WEDNESDAY));
        assertEquals(new ListRelativeDates.DayRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)),
                ListRelativeDates.range("thisYear", WEDNESDAY));
    }

    @Test
    void thisWeekOnMondayDoesNotWalkBackAWeek() {
        LocalDate monday = LocalDate.of(2026, 8, 17);
        assertEquals(new ListRelativeDates.DayRange(monday, LocalDate.of(2026, 8, 23)),
                ListRelativeDates.range("thisWeek", monday));
    }

    @Test
    void unknownOperatorIsRejected() {
        assertFalse(ListRelativeDates.isRelative("on"));
        assertTrue(ListRelativeDates.isRelative("today"));
        assertThrows(IllegalArgumentException.class, () -> ListRelativeDates.range("on", WEDNESDAY));
        assertThrows(IllegalArgumentException.class, () -> ListRelativeDates.range("today", null));
    }
}
