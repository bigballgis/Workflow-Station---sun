package com.developer.util;

import com.developer.entity.ActionDefinition;
import com.developer.enums.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiBpmnActionBindingWriterTest {

    @Test
    void bindStageActions_shouldResolvePersistedIdsAndPreserveGeneratedOrder() {
        ActionDefinition approve = ActionDefinition.builder()
                .id(47L)
                .actionName("approve_request")
                .actionType(ActionType.APPROVE)
                .build();
        ActionDefinition reject = ActionDefinition.builder()
                .id(48L)
                .actionName("reject_request")
                .actionType(ActionType.REJECT)
                .build();
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="Process_1">
                    <bpmn:userTask id="Task_Review" name="Review">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="formId" value="42"/>
                          <custom:property name="actionIds" value="[999]"/>
                          <custom:property name="actionNames" value="[&quot;stale&quot;]"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;
        List<Map<String, Object>> generated = List.of(
                Map.of("actionName", "reject_request", "stageIds", List.of("Task_Review")),
                Map.of("actionName", "approve_request", "stageIds", List.of("Task_Review")));

        String result = AiBpmnActionBindingWriter.bindStageActions(
                xml, List.of(approve, reject), generated);
                
        assertThat(result)
                .contains("name=\"actionIds\" value=\"[48,47]\"")
                .contains("name=\"actionNames\" value=\"[&quot;reject_request&quot;,&quot;approve_request&quot;]\"")
                .contains("name=\"formId\" value=\"42\"")
                .doesNotContain("value=\"[999]\"")
                .doesNotContain("stale");
    }

    @Test
    void bindStageActions_shouldKeepXmlWhenIdsAreNotAssigned() {
        ActionDefinition action = ActionDefinition.builder()
                .actionName("approve_request")
                .actionType(ActionType.APPROVE)
                .build();
        String xml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>";
        
        assertThat(AiBpmnActionBindingWriter.bindStageActions(xml, List.of(action),
                List.of(Map.of("actionName", "approve_request", "stageIds", List.of("Task_Review")))))
                .isEqualTo(xml);
    }

    /** 同 AiBpmnFormBindingWriter：camunda:properties 不是设计器认得的容器，不能往里塞 actionIds。 */
    @Test
    void bindStageActions_shouldNotWriteIntoAForeignNamespaceContainer() {
        ActionDefinition approve = ActionDefinition.builder()
                .id(7L)
                .actionName("approve")
                .build();

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn">
                  <bpmn:process id="Process_1">
                    <bpmn:userTask id="Task_Approve" name="Approve Request">
                      <bpmn:extensionElements>
                        <camunda:properties>
                          <camunda:property name="assigneeType" value="PROCESS_INITIATOR"/>
                        </camunda:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String result = AiBpmnActionBindingWriter.bindStageActions(xml, List.of(approve),
                List.of(Map.of("actionName", "approve", "stageIds", List.of("Task_Approve"))));

        assertThat(result).contains("name=\"actionIds\" value=\"[7]\"");
        String camundaContainer = result.substring(result.indexOf("<camunda:properties"),
                result.indexOf("</camunda:properties>"));
        assertThat(camundaContainer).doesNotContain("actionIds");
        assertThat(camundaContainer).contains("assigneeType");
        assertThat(result).contains("<custom:properties");
    }
}
