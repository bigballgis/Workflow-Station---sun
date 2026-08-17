package com.platform.common.fk;

import com.platform.common.dto.PkGenerationConfig;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPrimaryKeyAllocationServiceDailyDateTest {

    @Test
    void dailyDateSequence_firstOfDay_usesPerDayScopeAndDoesNotDeleteOtherDays() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-14T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.DAY.strategy())
                .padWidth(4)
                .startValue(1L)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "ignored", "dw_pk_sequences");

        assertThat(ids).containsExactly("202607140001");
        assertThat(jdbc.deleteSqls).isEmpty();
        assertThat(jdbc.insertArgs).containsExactly(10L, "req_no", "perDay", "20260714", "20260714", 4, 0L);
        assertThat(jdbc.updateScopeType).isEqualTo("perDay");
        assertThat(jdbc.updateScopeKey).isEqualTo("20260714");
    }

    @Test
    void dailyDateSequence_nextCalendarDay_resetsTo0001() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-14T16:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.DAY.strategy())
                .padWidth(4)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("202607150001");
        assertThat(jdbc.updateScopeKey).isEqualTo("20260715");
    }

    @Test
    void dailyDateSequence_overflowExpandsDigits() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 10000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-14T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.DAY.strategy())
                .padWidth(4)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026071410000");
    }

    @Test
    void dailyDateSequence_padWidth2_overflowToThreeDigits() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 100L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-14T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.DAY.strategy())
                .padWidth(2)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("20260714100");
    }

    @Test
    void monthlyDateSequence_firstOfMonth_usesPerMonthScopeAndDoesNotDeleteOtherRows() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-08-17T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.MONTH.strategy())
                .padWidth(4)
                .startValue(1L)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "ignored", "dw_pk_sequences");

        assertThat(ids).containsExactly("2026080001");
        assertThat(jdbc.deleteSqls).isEmpty();
        assertThat(jdbc.insertArgs).containsExactly(10L, "req_no", "perMonth", "202608", "202608", 4, 0L);
        assertThat(jdbc.updateScopeType).isEqualTo("perMonth");
        assertThat(jdbc.updateScopeKey).isEqualTo("202608");
    }

    @Test
    void monthlyDateSequence_nextCalendarMonth_resetsTo0001() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-08-31T16:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.MONTH.strategy())
                .padWidth(4)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("2026090001");
        assertThat(jdbc.updateScopeKey).isEqualTo("202609");
    }

    @Test
    void monthlyDateSequence_overflowExpandsDigits() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 10000L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-08-17T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy(CalendarDateSequence.Period.MONTH.strategy())
                .padWidth(4)
                .build();

        List<String> ids = service.allocate(10L, "req_no", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("20260810000");
    }

    @Test
    void prefixedSequence_stillConsolidatesGlobalCounter() {
        RecordingJdbc jdbc = new RecordingJdbc();
        jdbc.returningValue = 1L;
        jdbc.coalesceValue = 0L;
        JdbcPrimaryKeyAllocationService service = serviceOn(jdbc, "2026-07-14T04:00:00Z");

        PkGenerationConfig config = PkGenerationConfig.builder()
                .strategy("prefixedSequence")
                .prefix("ORD-")
                .padWidth(6)
                .startValue(1L)
                .build();

        List<String> ids = service.allocate(10L, "id", config, 1, "x", "dw_pk_sequences");
        assertThat(ids).containsExactly("ORD-000001");
        assertThat(jdbc.deleteSqls).isNotEmpty();
        assertThat(jdbc.updateScopeType).isEqualTo("perTable");
        assertThat(jdbc.updateScopeKey).isEqualTo("");
    }

    private static JdbcPrimaryKeyAllocationService serviceOn(RecordingJdbc jdbc, String utcInstant) {
        Clock clock = Clock.fixed(Instant.parse(utcInstant), CalendarDateSequence.ZONE);
        return new JdbcPrimaryKeyAllocationService(jdbc, clock);
    }

    /** Records sequence SQL without Mockito (JdbcTemplate cannot be inlined-mocked on this JDK). */
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
