package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.platform.common.functionunit.FunctionUnitVersionException;
import com.portal.repository.ProcessInstanceRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link RequestIdEnricher#stampRequestId(Map)} is the server-side authority for the
 * derived Request ID: every path that persists process variables calls it so the browser can never
 * own the value. A client may send nothing, a value computed from a config that has since changed,
 * or one built before an auto-generated PK was allocated — none may reach the database.
 *
 * <p>The config is the main table's in the request's own release ({@code functionUnitId}).
 */
class RequestIdStampTest {

    private static final Map<String, Object> CFG = Map.of("fieldNames", List.of("I", "idncfm"), "separator", "_");

    private RequestIdEnricher enricherReturning(Map<String, Object> cfg) {
        CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
        Map<String, Object> main = new LinkedHashMap<>(Map.of("tableType", "MAIN"));
        if (cfg != null) {
            main.put("requestIdConfig", cfg);
        }
        when(snapshots.contents("rel-1", "DATA_TABLE")).thenReturn(List.of(main));
        RequestIdEnricher enricher = new RequestIdEnricher(
                mock(JdbcTemplate.class), new ObjectMapper(), mock(ProcessInstanceRepository.class));
        ReflectionTestUtils.setField(enricher, "catalogSnapshots", snapshots);
        return enricher;
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("functionUnitId", "rel-1");
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void buildsRequestIdWhenClientSentNone() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "Meeting-000003");
        enricherReturning(CFG).stampRequestId(v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1_Meeting-000003");
    }

    /** The regression this targets: the client value lacks the late-allocated PK segment. */
    @Test
    void overwritesIncompleteClientSuppliedValue() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "Meeting-000003",
                RequestIdEnricher.REQUEST_ID_FIELD, "RID1");
        enricherReturning(CFG).stampRequestId(v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1_Meeting-000003");
    }

    @Test
    void overwritesForgedClientSuppliedValue() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "Meeting-000003",
                RequestIdEnricher.REQUEST_ID_FIELD, "TOTALLY-MADE-UP");
        enricherReturning(CFG).stampRequestId(v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1_Meeting-000003");
    }

    /** A later task editing a contributing field must re-derive, not keep the stored value. */
    @Test
    void refreshesStaleValueAfterContributingFieldChanged() {
        Map<String, Object> v = vars("I", "RENAMED", "idncfm", "Meeting-000003",
                RequestIdEnricher.REQUEST_ID_FIELD, "RID1_Meeting-000003");
        enricherReturning(CFG).stampRequestId(v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RENAMED_Meeting-000003");
    }

    @Test
    void skipsEmptyContributingFields() {
        Map<String, Object> v = vars("I", "RID1", "idncfm", "");
        enricherReturning(CFG).stampRequestId(v);
        assertThat(v.get(RequestIdEnricher.REQUEST_ID_FIELD)).isEqualTo("RID1");
    }

    /** An unconfigured main table must not let a client-supplied identifier survive. */
    @Test
    void dropsClientValueWhenMainTableHasNoConfig() {
        Map<String, Object> v = vars("I", "RID1", RequestIdEnricher.REQUEST_ID_FIELD, "CLIENT-INVENTED");
        enricherReturning(null).stampRequestId(v);
        assertThat(v).doesNotContainKey(RequestIdEnricher.REQUEST_ID_FIELD);
    }

    @Test
    void dropsClientValueWhenEveryContributingFieldIsEmpty() {
        Map<String, Object> v = vars("I", "", "idncfm", "", RequestIdEnricher.REQUEST_ID_FIELD, "STALE");
        enricherReturning(CFG).stampRequestId(v);
        assertThat(v).doesNotContainKey(RequestIdEnricher.REQUEST_ID_FIELD);
    }

    @Test
    void toleratesNullVariables() {
        enricherReturning(CFG).stampRequestId(null);
    }

    /** Without its release the request has no config to derive from; nothing falls back to the designer. */
    @Test
    void variablesWithoutTheirReleaseNeedRecovery() {
        Map<String, Object> v = new LinkedHashMap<>(Map.of("I", "RID1"));
        assertThatThrownBy(() -> enricherReturning(CFG).stampRequestId(v))
                .isInstanceOf(FunctionUnitVersionException.class);
    }
}
