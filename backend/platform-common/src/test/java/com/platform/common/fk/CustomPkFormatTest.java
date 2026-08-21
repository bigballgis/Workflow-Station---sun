package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomPkFormatTest {

    private static Clock shanghai(String utcInstant) {
        return Clock.fixed(Instant.parse(utcInstant), CalendarDateSequence.ZONE);
    }

    @Test
    void defaultTemplate_usesYearDayMonthInShanghai() {
        Clock june8 = shanghai("2026-06-08T04:00:00Z");
        assertThat(CustomPkFormat.render(CustomPkFormat.DEFAULT_FORMAT, june8, 1000, new Random(1)))
                .isEqualTo("2026-08-06-1000");
    }

    @Test
    void datetime_usesAsiaShanghaiNotUtc() {
        Clock stillJune7 = shanghai("2026-06-07T15:30:00Z");
        assertThat(CustomPkFormat.render(
                "{DATETIME:yyyy-MM-dd}-{SEQNUM:4}", stillJune7, 1, new Random(1)))
                .isEqualTo("2026-06-07-0001");
        Clock june8 = shanghai("2026-06-07T16:30:00Z");
        assertThat(CustomPkFormat.render(
                "{DATETIME:yyyy-MM-dd}-{SEQNUM:4}", june8, 1, new Random(1)))
                .isEqualTo("2026-06-08-0001");
    }

    @Test
    void seqnum_isSharedAcrossRepeatedTokens() {
        Clock june8 = shanghai("2026-06-08T04:00:00Z");
        assertThat(CustomPkFormat.render(
                "{SEQNUM:4}-{SEQNUM:4}", june8, 12, new Random(1)))
                .isEqualTo("0012-0012");
    }

    @Test
    void randstring_usesFixedAlphabetAndLength() {
        Clock june8 = shanghai("2026-06-08T04:00:00Z");
        String value = CustomPkFormat.render(
                "{SEQNUM:2}-{RANDSTRING:4}", june8, 7, new Random(1));
        assertThat(value).matches("07-[A-Z0-9]{4}");
    }

    @Test
    void missingSeqnum_isRejected() {
        assertThatThrownBy(() -> CustomPkFormat.parse("{DATETIME:yyyy-MM-dd}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEQNUM");
    }

    @Test
    void unknownTokenAndUnclosedPlaceholder_areRejected() {
        assertThatThrownBy(() -> CustomPkFormat.parse("{DATETIMEUTC:yyyy-MM-dd}-{SEQNUM:4}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown placeholder");
        assertThatThrownBy(() -> CustomPkFormat.parse("{SEQNUM:4"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unclosed");
    }

    @Test
    void illegalDatetimePattern_isRejected() {
        assertThatThrownBy(() -> CustomPkFormat.parse("{DATETIME:yyyy'Q'}-{SEQNUM:4}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void datetimePatternWithoutDay_rejectsDailyReset() {
        CustomPkFormat.Parsed parsed = CustomPkFormat.parse("{DATETIME:MM-MMM}-{SEQNUM:4}");
        assertThatThrownBy(() -> CustomPkFormat.validateReset(parsed, PkResetPeriod.DAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Daily reset");
        CustomPkFormat.validateReset(parsed, PkResetPeriod.MONTH);
        CustomPkFormat.validateReset(
                CustomPkFormat.parse("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}"), PkResetPeriod.DAY);
    }

    @Test
    void seqOnly_rejectsPeriodReset() {
        CustomPkFormat.Parsed parsed = CustomPkFormat.parse("{SEQNUM:4}");
        assertThatThrownBy(() -> CustomPkFormat.validateReset(parsed, PkResetPeriod.DAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CustomPkFormat.validateReset(parsed, PkResetPeriod.MONTH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizeConfig_migratesLegacyDatePrefixed() {
        PkGenerationConfig migrated = CustomPkFormat.normalizeConfig(PkGenerationConfig.builder()
                .strategy(CustomPkFormat.LEGACY_DATE_PREFIXED_STRATEGY)
                .datePattern("yyyy-MM-dd")
                .padWidth(4)
                .startValue(1000L)
                .resetPeriod("day")
                .build());
        assertThat(migrated.getStrategy()).isEqualTo(CustomPkFormat.STRATEGY);
        assertThat(migrated.getFormat()).isEqualTo("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}");
        assertThat(migrated.getResetPeriod()).isEqualTo("day");
        assertThat(migrated.getStartValue()).isEqualTo(1000L);
    }
}
