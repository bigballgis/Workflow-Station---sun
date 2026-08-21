package com.portal.component;

import com.portal.dto.ActionTableRowsDTO;
import com.portal.dto.TaskInfo;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Guards the invariant the design plan calls out explicitly: ACTION table rows must be
 * fetched fresh per request (never leak across requestId), and this must NOT be sourced
 * from ProcessComponent's per-functionUnitId cached content payload for row data.
 */
class ActionTableReadComponentTest {

    private JdbcTemplate jdbcTemplate;
    private ProcessInstanceRepository processInstanceRepository;
    private ProcessComponent processComponent;
    private ActionTableReadComponent component;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        processInstanceRepository = mock(ProcessInstanceRepository.class);
        processComponent = mock(ProcessComponent.class);
        component = new ActionTableReadComponent(jdbcTemplate, processInstanceRepository, processComponent);
    }

    private static TaskInfo taskFor(String processInstanceId) {
        return TaskInfo.builder().taskId("task-1").processInstanceId(processInstanceId).build();
    }

    private static ProcessInstance processInstanceWithRequestId(String piId, String requestId) {
        ProcessInstance pi = new ProcessInstance();
        pi.setId(piId);
        pi.setFunctionUnitCode("fu-20260422-23tfag");
        pi.setVariables(Map.of("__request_id", requestId));
        return pi;
    }

    private static Map<String, Object> fuContentWithOneActionBinding() {
        return Map.of("forms", List.of(
                Map.of("tableBindings", List.of(
                        Map.of(
                                "bindingType", "ACTION",
                                "bindingId", 305,
                                "tableName", "meeting_remark",
                                "foreignKeyField", "main_id"
                        )
                ))
        ));
    }

    @Test
    void returnsEmptyWhenTaskHasNoProcessInstanceId() {
        List<ActionTableRowsDTO> rows = component.getActionTableRows(TaskInfo.builder().taskId("t1").build());

        assertThat(rows).isEmpty();
        verifyNoInteractions(processInstanceRepository, jdbcTemplate);
    }

    @Test
    void returnsEmptyWhenProcessInstanceMissingRequestId() {
        ProcessInstance pi = new ProcessInstance();
        pi.setId("pi-1");
        pi.setVariables(Map.of());
        when(processInstanceRepository.findById("pi-1")).thenReturn(Optional.of(pi));

        List<ActionTableRowsDTO> rows = component.getActionTableRows(taskFor("pi-1"));

        assertThat(rows).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void queriesPhysicalTableScopedToRequestId() {
        ProcessInstance pi = processInstanceWithRequestId("pi-1", "req-abc");
        when(processInstanceRepository.findById("pi-1")).thenReturn(Optional.of(pi));
        when(processComponent.getFunctionUnitContent("fu-20260422-23tfag")).thenReturn(fuContentWithOneActionBinding());
        when(jdbcTemplate.queryForList(anyString(), eq("req-abc")))
                .thenReturn(List.of(Map.of("id", "r1", "remark_content", "hello")));

        List<ActionTableRowsDTO> rows = component.getActionTableRows(taskFor("pi-1"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBindingId()).isEqualTo(305L);
        assertThat(rows.get(0).getRows()).hasSize(1);
        verify(jdbcTemplate).queryForList(
                eq("SELECT * FROM meeting_remark WHERE main_id = ? ORDER BY created_at ASC"),
                eq("req-abc"));
    }

    @Test
    void differentRequestIdsAreIsolated() {
        // Same FU content (would be cache-shared if this were sourced from getFunctionUnitContent's
        // row-carrying payload); different process instances → different requestId → different query param.
        ProcessInstance piA = processInstanceWithRequestId("pi-A", "req-A");
        ProcessInstance piB = processInstanceWithRequestId("pi-B", "req-B");
        when(processInstanceRepository.findById("pi-A")).thenReturn(Optional.of(piA));
        when(processInstanceRepository.findById("pi-B")).thenReturn(Optional.of(piB));
        when(processComponent.getFunctionUnitContent("fu-20260422-23tfag")).thenReturn(fuContentWithOneActionBinding());
        when(jdbcTemplate.queryForList(anyString(), eq("req-A")))
                .thenReturn(List.of(Map.of("id", "rA", "remark_content", "for A only")));
        when(jdbcTemplate.queryForList(anyString(), eq("req-B")))
                .thenReturn(List.of());

        List<ActionTableRowsDTO> rowsForA = component.getActionTableRows(taskFor("pi-A"));
        List<ActionTableRowsDTO> rowsForB = component.getActionTableRows(taskFor("pi-B"));

        assertThat(rowsForA).hasSize(1);
        assertThat(rowsForA.get(0).getRows()).extracting(m -> m.get("remark_content")).containsExactly("for A only");
        assertThat(rowsForB).isEmpty();
    }

    @Test
    void skipsBindingWithNoForeignKeyFieldConfigured() {
        ProcessInstance pi = processInstanceWithRequestId("pi-1", "req-abc");
        when(processInstanceRepository.findById("pi-1")).thenReturn(Optional.of(pi));
        when(processComponent.getFunctionUnitContent("fu-20260422-23tfag")).thenReturn(Map.of("forms", List.of(
                Map.of("tableBindings", List.of(
                        Map.of("bindingType", "ACTION", "bindingId", 305, "tableName", "meeting_remark")
                ))
        )));

        List<ActionTableRowsDTO> rows = component.getActionTableRows(taskFor("pi-1"));

        assertThat(rows).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void processInstanceIdOverloadServesMyRequestPathWithoutATaskId() {
        // My Request (applicationDetail) never has a taskId — only processInstanceId.
        ProcessInstance pi = processInstanceWithRequestId("pi-1", "req-abc");
        when(processInstanceRepository.findById("pi-1")).thenReturn(Optional.of(pi));
        when(processComponent.getFunctionUnitContent("fu-20260422-23tfag")).thenReturn(fuContentWithOneActionBinding());
        when(jdbcTemplate.queryForList(anyString(), eq("req-abc")))
                .thenReturn(List.of(Map.of("id", "r1", "remark_content", "hello")));

        List<ActionTableRowsDTO> rows = component.getActionTableRows("pi-1");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getBindingId()).isEqualTo(305L);
    }

    @Test
    void processInstanceIdOverloadReturnsEmptyForBlankId() {
        List<ActionTableRowsDTO> rows = component.getActionTableRows("");

        assertThat(rows).isEmpty();
        verifyNoInteractions(processInstanceRepository, jdbcTemplate);
    }

    @Test
    void omitsBindingsWithNoRows() {
        ProcessInstance pi = processInstanceWithRequestId("pi-1", "req-abc");
        when(processInstanceRepository.findById("pi-1")).thenReturn(Optional.of(pi));
        when(processComponent.getFunctionUnitContent("fu-20260422-23tfag")).thenReturn(fuContentWithOneActionBinding());
        when(jdbcTemplate.queryForList(anyString(), any(Object.class))).thenReturn(List.of());

        List<ActionTableRowsDTO> rows = component.getActionTableRows(taskFor("pi-1"));

        assertThat(rows).isEmpty();
    }
}
