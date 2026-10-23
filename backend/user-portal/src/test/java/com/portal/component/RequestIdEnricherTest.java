package com.portal.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link RequestIdEnricher} core join logic: ordered fields + separator, empty-field skip,
 * and unconfigured → null, from the main table of the request's release.
 */
class RequestIdEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RequestIdEnricher enricherReturning(Map<String, Object> cfg) {
        CatalogSnapshotReader snapshots = mock(CatalogSnapshotReader.class);
        Map<String, Object> main = new LinkedHashMap<>(Map.of("tableType", "MAIN"));
        if (cfg != null) {
            main.put("requestIdConfig", cfg);
        }
        when(snapshots.contents("rel-1", "DATA_TABLE")).thenReturn(List.of(main));
        RequestIdEnricher enricher = new RequestIdEnricher(
                mock(JdbcTemplate.class), objectMapper, mock(ProcessInstanceRepository.class));
        ReflectionTestUtils.setField(enricher, "catalogSnapshots", snapshots);
        return enricher;
    }

    private static Map<String, Object> cfg(String separator, String... fieldNames) {
        return Map.of("fieldNames", List.of(fieldNames), "separator", separator);
    }

    private static Map<String, Object> vars(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void joinsOrderedFieldsWithSeparator() {
        RequestIdEnricher enricher = enricherReturning(cfg("-", "dept", "year", "seq"));
        String id = enricher.buildCatalogRequestId("rel-1", vars("dept", "HR", "year", 2026, "seq", "001"));
        assertThat(id).isEqualTo("HR-2026-001");
    }

    @Test
    void skipsEmptyFieldsToAvoidDoubleSeparator() {
        RequestIdEnricher enricher = enricherReturning(cfg("-", "dept", "year", "seq"));
        // year missing → must not produce HR--001
        String id = enricher.buildCatalogRequestId("rel-1", vars("dept", "HR", "seq", "001"));
        assertThat(id).isEqualTo("HR-001");
    }

    @Test
    void supportsEmptySeparator() {
        RequestIdEnricher enricher = enricherReturning(cfg("", "a", "b"));
        assertThat(enricher.buildCatalogRequestId("rel-1", vars("a", "X", "b", "Y"))).isEqualTo("XY");
    }

    @Test
    void returnsNullWhenUnconfigured() {
        RequestIdEnricher enricher = enricherReturning(null);
        assertThat(enricher.buildCatalogRequestId("rel-1", vars("dept", "HR"))).isNull();
    }

    @Test
    void returnsNullWhenAllSelectedFieldsEmpty() {
        RequestIdEnricher enricher = enricherReturning(cfg("-", "dept", "seq"));
        assertThat(enricher.buildCatalogRequestId("rel-1", vars("other", "x"))).isNull();
    }

    /** Lists and details show the value stamped from the request's own release, never a live recompute. */
    @Test
    void storedRequestIdIsTheValueOnTheRequest() {
        assertThat(RequestIdEnricher.storedRequestId(vars("dept", "IT", "__request_id", "HR"))).isEqualTo("HR");
        assertThat(RequestIdEnricher.storedRequestId(vars("dept", "HR"))).isNull();
        assertThat(RequestIdEnricher.storedRequestId(null)).isNull();
    }

    @Test
    void enrichTaskRequestIdsSetsFunctionUnitNameAndCode() throws Exception {
        ProcessInstance instance = ProcessInstance.builder()
                .id("pi-1")
                .processDefinitionKey("Process_1")
                .functionUnitCode("help_pr")
                .functionUnitCatalogId("cat-help-pr")
                .variables(vars("dept", "HR", "__request_id", "HR"))
                .build();
        ProcessInstanceRepository repo = mock(ProcessInstanceRepository.class);
        when(repo.findAllById(any())).thenReturn(List.of(instance));

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("code")).thenReturn("help_pr");
            when(rs.getString("name")).thenReturn("Purchase Request");
            handler.processRow(rs);
            return null;
        }).when(jdbc).query(contains("sys_function_units"), any(RowCallbackHandler.class), any(Object[].class));

        RequestIdEnricher enricher = new RequestIdEnricher(jdbc, objectMapper, repo);
        TaskInfo task = TaskInfo.builder().taskId("t1").processInstanceId("pi-1").build();
        enricher.enrichTaskRequestIds(List.of(task));

        assertThat(task.getRequestId()).isEqualTo("HR");
        assertThat(task.getFunctionUnitCode()).isEqualTo("help_pr");
        assertThat(task.getFunctionUnitCatalogId()).isEqualTo("cat-help-pr");
        assertThat(task.getFunctionUnitName()).isEqualTo("Purchase Request");
    }

    /** A called Function Unit's task is work on the caller's request, so it shows that request's ID. */
    @Test
    void calledUnitTaskCarriesTheCallingRequestsId() {
        ProcessInstance request = ProcessInstance.builder()
                .id("purchase").processDefinitionKey("fu-purchase").functionUnitCode("fu-purchase")
                .variables(vars("dept", "HR", "__request_id", "HR"))
                .build();
        ProcessInstance vendorCheck = ProcessInstance.builder()
                .id("vendor-check").processDefinitionKey("fu-vendor").functionUnitCode("fu-vendor")
                .parentProcessInstanceId("purchase")
                .variables(vars())
                .build();
        ProcessInstanceRepository repo = mock(ProcessInstanceRepository.class);
        when(repo.findAllById(any())).thenAnswer(inv -> {
            Iterable<?> ids = inv.getArgument(0);
            java.util.List<ProcessInstance> out = new java.util.ArrayList<>();
            for (Object id : ids) {
                if ("purchase".equals(id)) out.add(request);
                if ("vendor-check".equals(id)) out.add(vendorCheck);
            }
            return out;
        });

        RequestIdEnricher enricher = new RequestIdEnricher(mock(JdbcTemplate.class), objectMapper, repo);
        TaskInfo task = TaskInfo.builder().taskId("t1").processInstanceId("vendor-check").build();
        enricher.enrichTaskRequestIds(List.of(task));

        assertThat(task.getRequestId()).isEqualTo("HR");
        // The task still belongs to the called unit.
        assertThat(task.getFunctionUnitCode()).isEqualTo("fu-vendor");
    }
}
