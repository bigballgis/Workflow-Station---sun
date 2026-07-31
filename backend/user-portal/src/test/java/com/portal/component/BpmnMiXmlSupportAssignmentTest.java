package com.portal.component;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BpmnMiXmlSupportAssignmentTest {

    @Test
    void parsesUserRoleAndBothWithoutFieldDefaults() {
        String bpmn = definitions(
                miSubProcess("sp-user", "task-user", "people", "user", "owner_user_id", null, null)
                        + miSubProcess("sp-role", "task-role", "roles", "role", null, "approver_role", null)
                        + miSubProcess("sp-both", "task-both", "mixed", "both",
                                "assignee_id", "role_code", "department_code"));

        Map<String, Map<String, Object>> result =
                BpmnMiXmlSupport.buildMiAssignmentsBySubTableName(bpmn);

        assertThat(result.get("people")).containsEntry("allowUser", true)
                .containsEntry("allowRole", false)
                .containsEntry("assigneeField", "owner_user_id")
                .doesNotContainKeys("roleField", "buField");
        assertThat(result.get("roles")).containsEntry("allowUser", false)
                .containsEntry("allowRole", true)
                .containsEntry("roleField", "approver_role")
                .doesNotContainKeys("assigneeField", "buField");
        assertThat(result.get("mixed")).containsEntry("allowUser", true)
                .containsEntry("allowRole", true)
                .containsEntry("assigneeField", "assignee_id")
                .containsEntry("roleField", "role_code")
                .containsEntry("buField", "department_code");
    }

    @Test
    void rejectsConflictingContractsForSameSubTable() {
        String bpmn = definitions(
                miSubProcess("sp-1", "task-1", "participants", "user", "user_id", null, null)
                        + miSubProcess("sp-2", "task-2", "participants", "role", null, "role_code", null));

        assertThatThrownBy(() -> BpmnMiXmlSupport.buildMiAssignmentsBySubTableName(bpmn))
                .isInstanceOf(BpmnMiXmlSupport.MiAssignmentConfigurationException.class)
                .hasMessageContaining("CONFLICTING_MI_ASSIGNMENT_CONFIG")
                .hasMessageContaining("participants");
    }

    private static String definitions(String body) {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="process">%s</bpmn:process>
                </bpmn:definitions>
                """.formatted(body);
    }

    private static String miSubProcess(
            String subProcessId,
            String taskId,
            String subTableName,
            String mode,
            String assigneeField,
            String roleField,
            String buField) {
        return """
                <bpmn:subProcess id="%s">
                  <bpmn:multiInstanceLoopCharacteristics flowable:collection="rows"/>
                  <bpmn:userTask id="%s">
                    <bpmn:extensionElements><custom:properties>
                      %s
                    </custom:properties></bpmn:extensionElements>
                  </bpmn:userTask>
                </bpmn:subProcess>
                """.formatted(
                subProcessId,
                taskId,
                property("subTableName", subTableName)
                        + property("assigneeMode", mode)
                        + property("assigneeField", assigneeField)
                        + property("roleField", roleField)
                        + property("buField", buField));
    }

    private static String property(String name, String value) {
        return value == null ? "" : "<custom:property name=\"" + name + "\" value=\"" + value + "\"/>";
    }
}
