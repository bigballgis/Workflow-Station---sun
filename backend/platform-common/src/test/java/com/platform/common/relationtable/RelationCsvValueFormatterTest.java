package com.platform.common.relationtable;

import com.platform.common.enums.RelationDataType;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RelationCsvValueFormatterTest {

    @Test
    void timestampString_withFractionalSeconds_normalizedToSeconds() {
        // java.sql.Timestamp#toString() form that spreadsheets misread as "26:31.0"
        assertThat(RelationCsvValueFormatter.format("2026-06-28 13:25:31.123", RelationDataType.TIMESTAMP))
                .isEqualTo("2026-06-28 13:25:31");
    }

    @Test
    void timestampString_isoTWithFraction_normalized() {
        assertThat(RelationCsvValueFormatter.format("2026-06-28T13:25:31.5", RelationDataType.TIMESTAMP))
                .isEqualTo("2026-06-28 13:25:31");
    }

    @Test
    void timestampObject_normalized() {
        Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 6, 28, 13, 25, 31));
        assertThat(RelationCsvValueFormatter.format(ts, RelationDataType.TIMESTAMP))
                .isEqualTo("2026-06-28 13:25:31");
    }

    @Test
    void timestampString_withoutSeconds_normalized() {
        assertThat(RelationCsvValueFormatter.format("2026-06-28 13:25", RelationDataType.TIMESTAMP))
                .isEqualTo("2026-06-28 13:25:00");
    }

    @Test
    void dateField_keepsDateOnly() {
        assertThat(RelationCsvValueFormatter.format("2026-06-28 13:25:31.0", RelationDataType.DATE))
                .isEqualTo("2026-06-28");
        assertThat(RelationCsvValueFormatter.format("2026-06-28", RelationDataType.DATE))
                .isEqualTo("2026-06-28");
    }

    @Test
    void nonDateTypes_passThrough() {
        assertThat(RelationCsvValueFormatter.format("hello", RelationDataType.VARCHAR)).isEqualTo("hello");
        assertThat(RelationCsvValueFormatter.format(42L, RelationDataType.BIGINT)).isEqualTo("42");
    }

    @Test
    void nullAndUnparseable_safe() {
        assertThat(RelationCsvValueFormatter.format(null, RelationDataType.TIMESTAMP)).isEmpty();
        // Unparseable string is passed through unchanged rather than dropped.
        assertThat(RelationCsvValueFormatter.format("not-a-date", RelationDataType.TIMESTAMP))
                .isEqualTo("not-a-date");
    }

    @Test
    void nullType_usesToString() {
        assertThat(RelationCsvValueFormatter.format(123, null)).isEqualTo("123");
    }
}
