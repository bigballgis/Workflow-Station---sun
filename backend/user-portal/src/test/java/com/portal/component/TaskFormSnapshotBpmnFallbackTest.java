package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskFormSnapshotBpmnFallbackTest {

    @Test
    void mergeUsesBpmnProcessFormFieldsWhenStageBindingMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        String xml = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:custom="http://workflow.platform/schema/custom">
                  <bpmn:process id="atm">
                    <bpmn:userTask id="Activity_092hlui">
                      <bpmn:extensionElements><custom:properties>
                        <custom:property name="formId" value="320" />
                      </custom:properties></bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        String encoded = Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8));
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("process-atm")))
                .thenReturn(List.of(encoded));
        when(jdbcTemplate.queryForList(anyString(), eq(320L))).thenReturn(List.of(Map.of(
                "form_id", "320",
                "config_json", "{\"rule\":[{\"field\":\"case_number\",\"readonly\":true}]}",
                "field_permissions", "{}",
                "read_only", false)));

        TaskFormComponent component = new TaskFormComponent(
                mock(ProcessFormComponent.class), mock(ChangeHistoryComponent.class),
                mock(ProcessInstanceRepository.class), mock(WorkflowEngineClient.class),
                mock(RestTemplate.class), new ObjectMapper(), jdbcTemplate,
                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());

        Map<String, Object> variables = new HashMap<>();
        variables.put("case_number", "ATM-DC-PW-000001");
        variables.put("__subTables__", Map.of(
                "1135", List.of(Map.of("arn", "1", "row_id", "ATM-DC-PW-TRANS-000001")),
                "ATM Transaction", List.of(Map.of("arn", "1", "row_id", "ATM-DC-PW-TRANS-000001"))));

        Set<String> captured = component.mergeCompletedTaskSnapshotIntoVariables(
                "task-1", "user-1", "Activity_092hlui", "process-atm", variables);

        assertThat(captured).containsExactlyInAnyOrder("case_number", "__subTables__");
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) variables.get("_snapshot_task-1");
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldValues = (Map<String, Object>) snapshot.get("fieldValues");
        assertThat(fieldValues).containsEntry("case_number", "ATM-DC-PW-000001");
        @SuppressWarnings("unchecked")
        Map<String, Object> frozenSub = (Map<String, Object>) fieldValues.get("__subTables__");
        assertThat(frozenSub).containsOnlyKeys("1135");
    }
}
