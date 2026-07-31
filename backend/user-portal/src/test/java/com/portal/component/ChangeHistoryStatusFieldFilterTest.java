package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Reproduces task745testmask: status appears in approval variables but never in up_change_history.
 */
class ChangeHistoryStatusFieldFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void processFormReadonlyStatusIsExcludedFromAudit() {
        ChangeHistorySubmissionFilter filter =
                new ChangeHistorySubmissionFilter(mock(JdbcTemplate.class), objectMapper);
        Map<String, Object> processForm = Map.of(
                "formId", "50205",
                "configJson", Map.of("rule", List.of(
                        Map.of("field", "card", "type", "input"),
                        Map.of("field", "status", "type", "input",
                                "props", Map.of("readonly", true)))),
                "fieldPermissions", Map.of());
        Map<String, Object> submitted = Map.of(
                "card", "123456789",
                "status", "111");
        Map<String, Object> actual = filter.retainUserEditableSubmission(
                submitted, submitted, processForm);
        assertThat(actual).containsEntry("card", "123456789");
        assertThat(actual).doesNotContainKey("status");
    }

    @Test
    void taskFormEditableStatusIsRetainedForAudit() {
        ChangeHistorySubmissionFilter filter =
                new ChangeHistorySubmissionFilter(mock(JdbcTemplate.class), objectMapper);
        Map<String, Object> taskForm = Map.of(
                "formId", "50206",
                "configJson", Map.of("rule", List.of(
                        Map.of("field", "card", "type", "input",
                                "readonly", true,
                                "props", Map.of("readonly", true)),
                        Map.of("field", "status", "type", "input"))),
                "fieldPermissions", Map.of());
        Map<String, Object> submitted = Map.of(
                "card", "123456789",
                "status", "111");
        Map<String, Object> actual = filter.retainUserEditableSubmission(
                submitted, submitted, taskForm);
        assertThat(actual).containsEntry("status", "111");
        assertThat(actual).doesNotContainKey("card");
    }

    @Test
    void approvalAuditUsesBpmnTaskFormWhenStageBindingMissing() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ChangeHistorySubmissionFilter filter = new ChangeHistorySubmissionFilter(jdbc, objectMapper);

        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="p">
                    <bpmn:userTask id="Activity_1413vht">
                      <bpmn:extensionElements><custom:properties>
                        <custom:property name="formId" value="50206" />
                      </custom:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String encoded = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));

        when(jdbc.queryForList(anyString(), eq("a486076b"), eq("Activity_1413vht")))
                .thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(String.class), eq("a486076b")))
                .thenReturn(List.of(encoded));
        when(jdbc.queryForList(
                argThat(sql -> sql != null && sql.contains("form_type = 'TASK'")),
                eq(50206L)))
                .thenReturn(List.of(Map.of(
                        "form_id", 50206L,
                        "config_json", "{\"rule\":["
                                + "{\"field\":\"card\",\"type\":\"input\",\"readonly\":true,"
                                + "\"props\":{\"readonly\":true}},"
                                + "{\"field\":\"status\",\"type\":\"input\"}"
                                + "]}",
                        "field_permissions", "{}",
                        "read_only", false)));
        Map<String, Object> bindingRow = new HashMap<>();
        bindingRow.put("id", 50584L);
        bindingRow.put("binding_type", "PRIMARY");
        bindingRow.put("binding_mode", "EDITABLE");
        bindingRow.put("table_name", "maintable");
        bindingRow.put("table_display_name", "maintable");
        bindingRow.put("sibling_id", null);
        when(jdbc.queryForList(
                argThat(sql -> sql != null && sql.contains("dw_form_table_bindings")),
                eq(50206L)))
                .thenReturn(List.of(bindingRow));

        Map<String, Object> submitted = Map.of(
                "card", "123456789",
                "status", "111",
                "action", "APPROVE");
        Map<String, Object> actual = filter.filterTaskSubmission(
                "a486076b", "Activity_1413vht", submitted, submitted);

        assertThat(actual)
                .as("status must survive approval audit filter when BPMN points at Task Form 50206")
                .containsEntry("status", "111");
        assertThat(actual).doesNotContainKeys("card", "action");
    }

    @Test
    void emptyFormDefinitionDropsAllAuditIncludingStatus() {
        ChangeHistorySubmissionFilter filter =
                new ChangeHistorySubmissionFilter(mock(JdbcTemplate.class), objectMapper);
        Map<String, Object> submitted = Map.of("status", "111", "card", "123456789");
        Map<String, Object> actual = filter.retainUserEditableSubmission(
                submitted, submitted, Map.of());
        assertThat(actual).isEmpty();
    }

    @Test
    void emptyFormDataYieldsEmptyAuditEvenWhenVariablesCarryStatus() {
        ChangeHistorySubmissionFilter filter =
                new ChangeHistorySubmissionFilter(mock(JdbcTemplate.class), objectMapper);
        Map<String, Object> taskForm = Map.of(
                "formId", "50206",
                "configJson", Map.of("rule", List.of(Map.of("field", "status", "type", "input"))),
                "fieldPermissions", Map.of());
        Map<String, Object> actual = filter.retainUserEditableSubmission(
                Map.of(), Map.of("status", "111", "card", "123456789"), taskForm);
        assertThat(actual).isEmpty();
    }
}
