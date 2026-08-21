package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sub-table field-level permission enforcement at submit time (composite {@code bindingId:field}
 * keys), exercised through {@link TaskFormComponent#filterSubTableFieldsForTesting}, the
 * production {@code submitTaskForm} code path's private {@code filterSubTableFieldsInPlace}.
 */
class TaskFormComponentSubTableFieldPermissionTest {

    private TaskFormComponent component(JdbcTemplate jdbcTemplate) {
        return new TaskFormComponent(
                mock(ProcessFormComponent.class), mock(ChangeHistoryComponent.class),
                mock(ProcessInstanceRepository.class), mock(WorkflowEngineClient.class),
                mock(RestTemplate.class), new ObjectMapper(), jdbcTemplate,
                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
    }

    @Test
    void dropsOnlyReadonlyCompositeKeyedFieldFromNumericBindingSlice() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString(),
                eq("process-1"), eq("sub-task-stage")))
                .thenReturn(List.of(Map.of(
                        "form_id", "50192",
                        "config_json", "{}",
                        "field_permissions",
                        "{\"50544:bu_code\":\"READONLY\",\"50544:role_code\":\"READONLY\","
                                + "\"50544:name\":\"EDITABLE\",\"50544:assignee\":\"EDITABLE\"}",
                        "read_only", false)));

        Map<String, Object> editableData = new HashMap<>();
        Map<String, Object> subTables = new HashMap<>();
        subTables.put("50544", new java.util.ArrayList<>(List.of(new HashMap<>(Map.of(
                "id_idw", "ROW-1", "name", "Jane", "assignee", "user-1",
                "bu_code", "FIN", "role_code", "MANAGER")))));
        editableData.put("__subTables__", subTables);

        component(jdbcTemplate).filterSubTableFieldsForTesting(editableData, "process-1", "sub-task-stage");

        @SuppressWarnings("unchecked")
        Map<String, Object> filteredSubTables = (Map<String, Object>) editableData.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) filteredSubTables.get("50544");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row).containsEntry("name", "Jane").containsEntry("assignee", "user-1");
        assertThat(row).containsEntry("id_idw", "ROW-1");
        assertThat(row).doesNotContainKey("bu_code");
        assertThat(row).doesNotContainKey("role_code");
    }

    @Test
    void bindingWithOnlyReadonlyEntriesKeepsEveryOtherFieldEditable() {
        // Real-world shape (Form Designer only ever persists an entry when a field is toggled
        // AWAY from its EDITABLE default — see useFormSave.ts's `fieldPermissions?.[key] ||
        // 'EDITABLE'`): a designer marks just bu_code/role_code read-only and never touches
        // name/assignee at all, so field_permissions has ZERO explicit "EDITABLE" entries for
        // this binding. name/assignee must still survive — only the explicitly-READONLY fields
        // are dropped. (#1524-class regression: treating "no explicit EDITABLE entry" as
        // "not editable" wiped every field but id_idw/bu_code/role_code from every save.)
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString(),
                eq("process-3"), eq("sub-task-stage")))
                .thenReturn(List.of(Map.of(
                        "form_id", "50192",
                        "config_json", "{}",
                        "field_permissions",
                        "{\"50544:bu_code\":\"READONLY\",\"50544:role_code\":\"READONLY\"}",
                        "read_only", false)));

        Map<String, Object> editableData = new HashMap<>();
        Map<String, Object> subTables = new HashMap<>();
        subTables.put("50544", new java.util.ArrayList<>(List.of(new HashMap<>(Map.of(
                "id_idw", "ROW-1", "name", "Jane", "assignee", "user-1",
                "bu_code", "FIN", "role_code", "MANAGER")))));
        editableData.put("__subTables__", subTables);

        component(jdbcTemplate).filterSubTableFieldsForTesting(editableData, "process-3", "sub-task-stage");

        @SuppressWarnings("unchecked")
        Map<String, Object> filteredSubTables = (Map<String, Object>) editableData.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) filteredSubTables.get("50544");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row).containsEntry("name", "Jane").containsEntry("assignee", "user-1");
        assertThat(row).containsEntry("id_idw", "ROW-1");
        assertThat(row).doesNotContainKey("bu_code");
        assertThat(row).doesNotContainKey("role_code");
    }

    @Test
    void unconfiguredBindingKeepsEveryFieldUnchanged() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(org.mockito.ArgumentMatchers.anyString(),
                eq("process-2"), eq("other-stage")))
                .thenReturn(List.of(Map.of(
                        "form_id", "50193",
                        "config_json", "{}",
                        "field_permissions", "{}",
                        "read_only", false)));

        Map<String, Object> editableData = new HashMap<>();
        Map<String, Object> subTables = new HashMap<>();
        subTables.put("50544", new java.util.ArrayList<>(List.of(new HashMap<>(Map.of(
                "id_idw", "ROW-1", "bu_code", "FIN")))));
        editableData.put("__subTables__", subTables);

        component(jdbcTemplate).filterSubTableFieldsForTesting(editableData, "process-2", "other-stage");

        @SuppressWarnings("unchecked")
        Map<String, Object> filteredSubTables = (Map<String, Object>) editableData.get("__subTables__");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) filteredSubTables.get("50544");
        assertThat(rows).containsExactly(Map.of("id_idw", "ROW-1", "bu_code", "FIN"));
    }
}
