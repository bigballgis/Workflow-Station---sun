package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.SubTableBindingData;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The Process Form panel's sub-table Add/Edit dialog (SubTableAddDialog) only renders the
 * MI Assignment Mode block when its binding carries assignmentConfig. Regression coverage for
 * the fix that stamps it from the deployed BPMN, keyed by table name — mirrors ProcessComponent's
 * enrichMiAssignments so the Process Form panel reaches parity with the Task Form (portal-design-parity).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessFormComponentMiAssignmentTest {

    @Mock private ProcessInstanceRepository processInstanceRepository;
    @Mock private ChangeHistoryComponent changeHistoryComponent;
    @Mock private RestTemplate restTemplate;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private WorkflowEngineClient workflowEngineClient;

    private ProcessFormComponent newComponent() {
        ProcessFormComponent component = new ProcessFormComponent(
                processInstanceRepository, changeHistoryComponent, restTemplate,
                new ObjectMapper(), jdbcTemplate,
                com.portal.testsupport.PortalTransactionTestSupport.noopPlatformTransactionManager());
        ReflectionTestUtils.setField(component, "workflowEngineClient", workflowEngineClient);
        return component;
    }

    private static SubTableBindingData binding(String tableName) {
        return SubTableBindingData.builder().bindingId(1L).tableName(tableName).build();
    }

    @Test
    void stampsAssignmentConfigOnMatchingBindingByTableName() {
        when(workflowEngineClient.getBpmnXml("proc-key")).thenReturn(Optional.of(bpmnWithMiUserTask(
                "subtable", "both", "assignee", "role_code", "bu_code")));

        List<SubTableBindingData> bindings = new ArrayList<>();
        bindings.add(binding("subtable"));
        bindings.add(binding("attachment"));

        ReflectionTestUtils.invokeMethod(newComponent(), "attachMiAssignmentConfigs", bindings, "proc-key");

        assertThat(bindings.get(0).getAssignmentConfig())
                .containsEntry("allowUser", true)
                .containsEntry("allowRole", true)
                .containsEntry("assigneeField", "assignee")
                .containsEntry("roleField", "role_code")
                .containsEntry("buField", "bu_code");
        assertThat(bindings.get(1).getAssignmentConfig()).isNull();
    }

    @Test
    void leavesBindingsUnstampedWhenBpmnFetchFails() {
        when(workflowEngineClient.getBpmnXml("proc-key")).thenReturn(Optional.empty());

        List<SubTableBindingData> bindings = new ArrayList<>();
        bindings.add(binding("subtable"));

        ReflectionTestUtils.invokeMethod(newComponent(), "attachMiAssignmentConfigs", bindings, "proc-key");

        assertThat(bindings.get(0).getAssignmentConfig()).isNull();
    }

    @Test
    void leavesBindingsUnstampedWhenProcessDefinitionKeyMissing() {
        List<SubTableBindingData> bindings = new ArrayList<>();
        bindings.add(binding("subtable"));

        ReflectionTestUtils.invokeMethod(newComponent(), "attachMiAssignmentConfigs", bindings, (String) null);

        assertThat(bindings.get(0).getAssignmentConfig()).isNull();
        verifyNoInteractions(workflowEngineClient);
    }

    private static String bpmnWithMiUserTask(
            String subTableName, String mode, String assigneeField, String roleField, String buField) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="p"><bpmn:subProcess id="sp-1">
                    <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows"/>
                    <bpmn:userTask id="task-1"><bpmn:extensionElements><custom:properties>
                      <custom:property name="subTableName" value="%s"/>
                      <custom:property name="assigneeMode" value="%s"/>
                      <custom:property name="assigneeField" value="%s"/>
                      <custom:property name="roleField" value="%s"/>
                      <custom:property name="buField" value="%s"/>
                    </custom:properties></bpmn:extensionElements></bpmn:userTask>
                  </bpmn:subProcess></bpmn:process>
                </bpmn:definitions>
                """.formatted(subTableName, mode, assigneeField, roleField, buField);
    }
}
