package com.platform.common.fk;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarDateSequenceTest {

    @Test
    void padWidth4_firstValueIsFourDigits() {
        assertThat(CalendarDateSequence.format("20260714", 1, 4)).isEqualTo("202607140001");
        assertThat(CalendarDateSequence.format("20260714", 9999, 4)).isEqualTo("202607149999");
        assertThat(CalendarDateSequence.format("202608", 1, 4)).isEqualTo("2026080001");
    }

    @Test
    void padWidth4_overflowGrowsToFiveDigits() {
        assertThat(CalendarDateSequence.format("20260714", 10000, 4)).isEqualTo("2026071410000");
        assertThat(CalendarDateSequence.format("202608", 10000, 4)).isEqualTo("20260810000");
    }

    @Test
    void padWidth2_overflowGrowsToThreeDigits() {
        assertThat(CalendarDateSequence.format("20260714", 1, 2)).isEqualTo("2026071401");
        assertThat(CalendarDateSequence.format("20260714", 100, 2)).isEqualTo("20260714100");
        assertThat(CalendarDateSequence.format("202608", 100, 2)).isEqualTo("202608100");
    }

    @Test
    void nullOrNonPositivePadWidth_defaultsToFour() {
        assertThat(CalendarDateSequence.format("20260715", 1, 0)).isEqualTo("202607150001");
        assertThat(CalendarDateSequence.resolvePadWidth(null)).isEqualTo(4);
    }

    @Test
    void dayKey_matchesTableAuditFieldZoneAsiaShanghai() {
        Clock still14 = Clock.fixed(Instant.parse("2026-07-14T15:59:59Z"), CalendarDateSequence.ZONE);
        assertThat(CalendarDateSequence.periodKey(still14, CalendarDateSequence.Period.DAY))
                .isEqualTo("20260714");

        Clock just15 = Clock.fixed(Instant.parse("2026-07-14T16:00:00Z"), CalendarDateSequence.ZONE);
        assertThat(CalendarDateSequence.periodKey(just15, CalendarDateSequence.Period.DAY))
                .isEqualTo("20260715");
    }

    @Test
    void monthKey_resetsOnShanghaiMonthBoundary() {
        Clock stillAugust = Clock.fixed(Instant.parse("2026-08-31T15:59:59Z"), CalendarDateSequence.ZONE);
        assertThat(CalendarDateSequence.periodKey(stillAugust, CalendarDateSequence.Period.MONTH))
                .isEqualTo("202608");

        Clock september = Clock.fixed(Instant.parse("2026-08-31T16:00:00Z"), CalendarDateSequence.ZONE);
        assertThat(CalendarDateSequence.periodKey(september, CalendarDateSequence.Period.MONTH))
                .isEqualTo("202609");
    }

    @Test
    void forStrategy_mapsDailyAndMonthly() {
        assertThat(CalendarDateSequence.Period.forStrategy("dailyDateSequence"))
                .isEqualTo(CalendarDateSequence.Period.DAY);
        assertThat(CalendarDateSequence.Period.forStrategy("monthlyDateSequence"))
                .isEqualTo(CalendarDateSequence.Period.MONTH);
        assertThat(CalendarDateSequence.Period.forStrategy("uuid")).isNull();
    }
}
