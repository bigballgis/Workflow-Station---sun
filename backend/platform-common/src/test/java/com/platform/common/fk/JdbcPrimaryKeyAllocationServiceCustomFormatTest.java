package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcPrimaryKeyAllocationServiceCustomFormatTest {

    @Test
    void customFormat_noReset_usesGlobalPerTableCounter() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-08T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}")
                .startValue(1000L)
                .resetPeriod("none")
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026-06-08-1000");
        assertThat(jdbc.deleteSqls).isNotEmpty();
        assertThat(jdbc.updateScopeType).isEqualTo("perTable");
        assertThat(jdbc.updateScopeKey).isEqualTo("");
    }

    @Test
    void customFormat_noReset_keepsSequenceAcrossMonths() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1001L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-08T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}")
                .startValue(1000L)
                .resetPeriod("none")
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026-07-08-1001");
        assertThat(jdbc.updateScopeType).isEqualTo("perTable");
    }

    @Test
    void customFormat_dailyReset_usesPerDayScope() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-09T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}")
                .startValue(1000L)
                .resetPeriod("day")
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026-06-09-1000");
        assertThat(jdbc.deleteSqls).isEmpty();
        assertThat(jdbc.updateScopeType).isEqualTo("perDay");
        assertThat(jdbc.updateScopeKey).isEqualTo("20260609");
    }

    @Test
    void customFormat_monthlyReset_usesPerMonthScope() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-08T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}")
                .startValue(1000L)
                .resetPeriod("month")
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026-07-08-1000");
        assertThat(jdbc.updateScopeType).isEqualTo("perMonth");
        assertThat(jdbc.updateScopeKey).isEqualTo("202607");
    }

    @Test
    void customFormat_monthPatternWithDailyReset_isRejected() {
        RecordingJdbc jdbc = new RecordingJdbc();
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-08T04:00:00Z");
        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:MM-MMM}-{SEQNUM:4}")
                .startValue(1000L)
                .resetPeriod("day")
                .build();

        assertThatThrownBy(() -> service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Daily reset");
    }

    @Test
    void customFormat_usesGlobalCounterAndDefaultTemplate() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-08T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format(CustomPkFormat.DEFAULT_FORMAT)
                .startValue(1000L)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026-08-06-1000");
        assertThat(jdbc.updateScopeType).isEqualTo("perTable");
        assertThat(jdbc.deleteSqls).isNotEmpty();
    }

    @Test
    void customFormat_allocatesIndependentlyForDifferentTables() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-08T04:00:00Z");
        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:yyyy-MM-dd}-{SEQNUM:4}")
                .startValue(1000L)
                .resetPeriod("none")
                .build();

        assertThat(service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences"))
                .containsExactly("2026-06-08-1000");
        assertThat(service.allocate(99L, "order_no", config, 1, "x", "dw_pk_sequences"))
                .containsExactly("2026-06-08-1000");
    }

    @Test
    void customFormat_withoutSeqnum_isRejected() {
        RecordingJdbc jdbc = new RecordingJdbc();
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-08T04:00:00Z");
        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.STRATEGY)
                .format("{DATETIME:yyyy-MM-dd}")
                .build();

        assertThatThrownBy(() -> service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEQNUM");
    }

    @Test
    void legacyDatePrefixed_isAllocatedAsCustomFormat() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-06-09T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CustomPkFormat.LEGACY_DATE_PREFIXED_STRATEGY)
                .datePattern("yyyy-MM-dd")
                .padWidth(4)
                .startValue(1000L)
                .resetPeriod("day")
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026-06-09-1000");
        assertThat(jdbc.updateScopeType).isEqualTo("perDay");
        assertThat(jdbc.updateScopeKey).isEqualTo("20260609");
    }

    private static JdbcPrimaryKeyAllocationService serviceOn(RecordingJdbc jdbc, String utcInstant) {
        Clock clock = Clock.fixed(Instant.parse(utcInstant), CalendarDateSequence.ZONE);
        return new JdbcPrimaryKeyAllocationService(jdbc, clock, new Random(1));
    }

    private static final class RecordingJdbc extends JdbcTemplate {
        Long returningValue = 1L;
        Long coalesceValue = 0L;
        Object[] insertArgs;
        String updateScopeType;
        String updateScopeKey;
        final List<String> deleteSqls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            if (sql != null && sql.contains("DELETE FROM")) {
                deleteSqls.add(sql);
            }
            if (sql != null && sql.contains("INSERT")) {
                insertArgs = args;
            }
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (sql != null && sql.contains("RETURNING") && args.length >= 5) {
                updateScopeType = String.valueOf(args[3]);
                updateScopeKey = String.valueOf(args[4]);
                return requiredType.cast(returningValue);
            }
            if (sql != null && sql.contains("COALESCE")) {
                return requiredType.cast(coalesceValue);
            }
            return null;
        }
    }
}
