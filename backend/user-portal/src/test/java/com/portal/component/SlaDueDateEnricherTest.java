package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link SlaDueDateEnricher}: due date = start + lead time, server-owned on every write. */
class SlaDueDateEnricherTest {

    private static final String FIELD_CFG =
            "{\"startDateSource\":\"FIELD\",\"startDateField\":\"received\",\"dueDateField\":\"due\"}";
    private static final String SUBMITTED_CFG =
            "{\"startDateSource\":\"SUBMITTED_AT\",\"dueDateField\":\"due\"}";

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final SlaDueDateEnricher enricher = new SlaDueDateEnricher(jdbc, new ObjectMapper());

    @SuppressWarnings("unchecked")
    private void given(String cfg, Integer leadTimeDays) {
        when(jdbc.query(contains("sla_config"), any(RowMapper.class), eq("FU1")))
                .thenReturn(cfg == null ? List.of() : List.of(cfg));
        when(jdbc.query(contains("ac_sla_policies"), any(RowMapper.class), eq("FU1")))
                .thenReturn(leadTimeDays == null ? List.of() : List.of(leadTimeDays));
    }

    @Test
    void addsLeadTimeToTheStartField() {
        given(FIELD_CFG, 30);
        Map<String, Object> vars = vars("received", "2026-01-15");
        enricher.stamp("FU1", vars, LocalDate.of(2026, 9, 1));
        assertThat(vars).containsEntry("due", "2026-02-14");
    }

    @Test
    void ignoresTimeOfDayOfATimestampStart() {
        given(FIELD_CFG, 1);
        Map<String, Object> vars = vars("received", "2026-12-31 23:59:59");
        enricher.stamp("FU1", vars, null);
        assertThat(vars).containsEntry("due", "2027-01-01");
    }

    @Test
    void usesSubmissionDateWhenConfigured() {
        given(SUBMITTED_CFG, 45);
        Map<String, Object> vars = vars("due", "1999-01-01");
        enricher.stamp("FU1", vars, LocalDate.of(2026, 9, 28));
        assertThat(vars).containsEntry("due", "2026-11-12");
    }

    @Test
    void overwritesAClientSuppliedDueDate() {
        given(FIELD_CFG, 10);
        Map<String, Object> vars = vars("received", "2026-03-01", "due", "2030-01-01");
        enricher.stamp("FU1", vars, null);
        assertThat(vars).containsEntry("due", "2026-03-11");
    }

    @Test
    void keepsAnExistingValueWhenNoDateCanBeDerived() {
        // The mapped field may already hold business data; removing it with no replacement loses it.
        given(FIELD_CFG, 10);
        Map<String, Object> noStart = vars("due", "2030-01-01");
        enricher.stamp("FU1", noStart, null);
        assertThat(noStart).containsEntry("due", "2030-01-01");

        SlaDueDateEnricher other = new SlaDueDateEnricher(jdbc, new ObjectMapper());
        given(FIELD_CFG, null);
        Map<String, Object> noLeadTime = vars("received", "2026-03-01", "due", "2030-01-01");
        other.stamp("FU1", noLeadTime, null);
        assertThat(noLeadTime).containsEntry("due", "2030-01-01");
    }

    @Test
    @SuppressWarnings("unchecked")
    void computeWithAGivenLeadTimeDoesNotQueryThePolicy() {
        given(FIELD_CFG, 99);
        SlaDueDateEnricher.Result result = enricher.compute("FU1", vars("received", "2026-01-01"), null, 5);
        assertThat(result.newDueDate()).isEqualTo("2026-01-06");
        verify(jdbc, never()).query(contains("ac_sla_policies"), any(RowMapper.class), eq("FU1"));
    }

    @Test
    void leavesVariablesAloneWhenTheFunctionUnitHasNoMapping() {
        given(null, 10);
        Map<String, Object> vars = vars("due", "2030-01-01");
        enricher.stamp("FU1", vars, LocalDate.now());
        assertThat(vars).containsEntry("due", "2030-01-01");
    }

    @Test
    void computeReportsWhyADateCannotBeDerived() {
        given(FIELD_CFG, 10);
        SlaDueDateEnricher.Result result = enricher.compute("FU1", vars("received", "not-a-date"), null);
        assertThat(result.kind()).isEqualTo(SlaDueDateEnricher.Result.Kind.START_MISSING);
        assertThat(result.reason()).contains("not-a-date");
    }

    @Test
    void brokenStoredConfigFailsLoudly() {
        given("{\"startDateSource\":\"FIELD\",\"dueDateField\":\"due\"}", 10);
        assertThatThrownBy(() -> enricher.stamp("FU1", vars(), null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void unmigratedSchemaDisablesTheFeatureInsteadOfBlockingWrites() {
        when(jdbc.query(contains("sla_config"), any(RowMapper.class), eq("FU1")))
                .thenThrow(new BadSqlGrammarException("sla", "SELECT", new SQLException("column does not exist")));
        Map<String, Object> vars = vars("due", "2030-01-01");
        enricher.stamp("FU1", vars, null);
        assertThat(vars).containsEntry("due", "2030-01-01");
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
