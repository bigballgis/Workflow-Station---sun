package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

/**
 * {@link MiParticipantEnrichmentComponent} row-id backfill: the in-memory meeting lookup must mirror the
 * SQL fallback priority (email → name+department → name) and keep the lowest id per key (ORDER BY id LIMIT 1),
 * while issuing a single preload query for the whole meeting instead of one query per row.
 */
class MiParticipantEnrichmentComponentTest {

    /** One synthetic participants row for the preload result set. */
    private record P(long id, String email, String name, String department) {}

    @SuppressWarnings("unchecked")
    private MiParticipantEnrichmentComponent componentWithMeeting(List<P> participants) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        // Stub the meeting preload: SELECT id, email, name, department FROM participants WHERE meeting_id = ?
        doAnswer((InvocationOnMock inv) -> {
            RowCallbackHandler handler = inv.getArgument(1);
            for (P p : participants) {
                ResultSet rs = mock(ResultSet.class);
                org.mockito.Mockito.when(rs.getLong("id")).thenReturn(p.id());
                org.mockito.Mockito.when(rs.getString("email")).thenReturn(p.email());
                org.mockito.Mockito.when(rs.getString("name")).thenReturn(p.name());
                org.mockito.Mockito.when(rs.getString("department")).thenReturn(p.department());
                handler.processRow(rs);
            }
            return null;
        }).when(jdbc).query(contains("WHERE meeting_id = ?"), any(RowCallbackHandler.class), eq(7L));
        return new MiParticipantEnrichmentComponent(jdbc);
    }

    private static Map<String, Object> row(String email, String name, String department) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (email != null) m.put("email", email);
        if (name != null) m.put("name", name);
        if (department != null) m.put("department", department);
        return m;
    }

    private static Map<String, Object> variablesWith(List<Map<String, Object>> rows) {
        Map<String, Object> subTables = new LinkedHashMap<>();
        subTables.put("participants", rows);
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("__subTables__", subTables);
        vars.put("meeting_id", 7L);
        return vars;
    }

    @Test
    void resolvesByEmailFirstAndCaseInsensitive() {
        MiParticipantEnrichmentComponent c = componentWithMeeting(List.of(
                new P(10L, "Alice@Corp.com", "Alice", "HR"),
                new P(20L, "bob@corp.com", "Bob", "IT")));
        Map<String, Object> r = row("  alice@corp.com ", "Whatever", "Whatever");
        c.enrichMissingParticipantRowIdsInSubTables(variablesWith(List.of(r)));
        assertThat(r.get("id")).isEqualTo(10L);
    }

    @Test
    void fallsBackToNamePlusDepartmentWhenNoEmail() {
        MiParticipantEnrichmentComponent c = componentWithMeeting(List.of(
                new P(30L, "", "Carol", "Finance"),
                new P(31L, "", "Carol", "Legal")));
        Map<String, Object> r = row("", "carol", "legal");
        c.enrichMissingParticipantRowIdsInSubTables(variablesWith(List.of(r)));
        assertThat(r.get("id")).isEqualTo(31L);
    }

    @Test
    void fallsBackToNameOnlyAndKeepsLowestId() {
        // Two rows share the same name; ORDER BY id LIMIT 1 → lowest id wins.
        MiParticipantEnrichmentComponent c = componentWithMeeting(List.of(
                new P(50L, "", "Dave", null),
                new P(40L, "", "Dave", null)));
        Map<String, Object> r = row("", "dave", "");
        c.enrichMissingParticipantRowIdsInSubTables(variablesWith(List.of(r)));
        assertThat(r.get("id")).isEqualTo(40L);
    }

    @Test
    void preloadsOnceForManyRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer((InvocationOnMock inv) -> {
            RowCallbackHandler handler = inv.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            org.mockito.Mockito.when(rs.getLong("id")).thenReturn(99L);
            org.mockito.Mockito.when(rs.getString("email")).thenReturn("x@y.com");
            org.mockito.Mockito.when(rs.getString("name")).thenReturn("X");
            org.mockito.Mockito.when(rs.getString("department")).thenReturn("D");
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("WHERE meeting_id = ?"), any(RowCallbackHandler.class), eq(7L));
        MiParticipantEnrichmentComponent c = new MiParticipantEnrichmentComponent(jdbc);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(row("x@y.com", "X", "D"));
        }
        c.enrichMissingParticipantRowIdsInSubTables(variablesWith(rows));

        // Single preload query regardless of row count (was up to 3 queries per row).
        verify(jdbc, times(1)).query(contains("WHERE meeting_id = ?"), any(RowCallbackHandler.class), eq(7L));
        assertThat(rows).allSatisfy(r -> assertThat(r.get("id")).isEqualTo(99L));
    }

    @Test
    void noMatchLeavesRowUnchanged() {
        MiParticipantEnrichmentComponent c = componentWithMeeting(List.of(
                new P(60L, "known@corp.com", "Known", "HR")));
        Map<String, Object> r = row("missing@corp.com", "Missing", "Nowhere");
        c.enrichMissingParticipantRowIdsInSubTables(variablesWith(List.of(r)));
        assertThat(r.get("id")).isNull();
    }
}
