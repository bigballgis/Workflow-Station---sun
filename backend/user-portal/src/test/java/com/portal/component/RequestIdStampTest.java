package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.repository.ProcessInstanceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * {@link RequestIdEnricher#stampRequestId(String, Map)} is the server-side authority for the
 * derived Request ID: every path that persists process variables calls it so the browser can never
 * own the value. A client may send nothing, a value computed from a config that has since changed,
 * or one built before an auto-generated PK was allocated — none may reach the database.
 *
 * <p>The PRIMARY-table config lookup is stubbed at the JdbcTemplate seam, as in
 * {@link RequestIdEnricherTest}.
 */
class RequestIdStampTest {

    private static final String CFG = "{\"fieldNames\":[\"I\",\"idncfm\"],\"separator\":\"_\"}";

    @SuppressWarnings("unchecked")
    private RequestIdEnricher enricherReturning(String cfgJson) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(any(String.class), any(RowMapper.class), eq("FU1")))
                .thenReturn(cfgJson == null ? List.of() : List.of(cfgJson));
        return new RequestIdEnricher(jdbc, new ObjectMapper(), mock(ProcessInstanceRepository.class));
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void buildsRequestIdWhenClientSentNone() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "Meeting-000003");
        enricherReturning(CFG).stampRequestId("FU1", v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1_Meeting-000003");
    }

    /** The regression this targets: the client value lacks the late-allocated PK segment. */
    @Test
    void overwritesIncompleteClientSuppliedValue() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "Meeting-000003",
                RequestIdEnricher.REQUEST_ID_FIELD, "RID1");
        enricherReturning(CFG).stampRequestId("FU1", v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1_Meeting-000003");
    }

    @Test
    void overwritesForgedClientSuppliedValue() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "Meeting-000003",
                RequestIdEnricher.REQUEST_ID_FIELD, "TOTALLY-MADE-UP");
        enricherReturning(CFG).stampRequestId("FU1", v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1_Meeting-000003");
    }

    /** A later task editing a contributing field must re-derive, not keep the stored value. */
    @Test
    void refreshesStaleValueAfterContributingFieldChanged() {
        Map<String, Object> v = vars("I", "RENAMED", "idncfm", "Meeting-000003",
                RequestIdEnricher.REQUEST_ID_FIELD, "RID1_Meeting-000003");
        enricherReturning(CFG).stampRequestId("FU1", v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RENAMED_Meeting-000003");
    }

    @Test
    void skipsEmptyContributingFields() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "");
        enricherReturning(CFG).stampRequestId("FU1", v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1");
    }

    /** An unconfigured main table must not let a client-supplied identifier survive. */
    @Test
    void dropsClientValueWhenMainTableHasNoConfig() {
        Map<String, Object> v = vars("I", "RID1", RequestIdEnricher.REQUEST_ID_FIELD, "CLIENT-INVENTED");
        enricherReturning(null).stampRequestId("FU1", v);
        assertThat(v).doesNotContainKey(RequestIdEnricher.REQUEST_ID_FIELD);
    }

    @Test
    void dropsClientValueWhenEveryContributingFieldIsEmpty() {
        Map<String, Object> v = vars("I", "", "idncfm", "", RequestIdEnricher.REQUEST_ID_FIELD, "STALE");
        enricherReturning(CFG).stampRequestId("FU1", v);
        assertThat(v).doesNotContainKey(RequestIdEnricher.REQUEST_ID_FIELD);
    }

    @Test
    void toleratesNullVariables() {
        enricherReturning(CFG).stampRequestId("FU1", null);
    }
}
