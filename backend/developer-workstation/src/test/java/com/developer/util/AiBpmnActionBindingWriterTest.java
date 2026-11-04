package com.developer.util;

import com.developer.entity.ActionDefinition;
import com.developer.enums.ActionType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiBpmnActionBindingWriterTest {

    @Test
    void rebind_shouldResolvePersistedIdsAndPreserveGeneratedOrder() {
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

        String result = AiBpmnActionBindingWriter.rebind(
                xml, AiBpmnActionBindingWriter.Snapshot.EMPTY, List.of(approve, reject), generated);
                
        assertThat(result)
                .contains("name=\"actionIds\" value=\"[48,47]\"")
                .contains("name=\"actionNames\" value=\"[&quot;reject_request&quot;,&quot;approve_request&quot;]\"")
                .contains("name=\"formId\" value=\"42\"")
                .doesNotContain("value=\"[999]\"")
                .doesNotContain("stale");
    }

    @Test
    void rebind_shouldKeepXmlWhenIdsAreNotAssigned() {
        ActionDefinition action = ActionDefinition.builder()
                .actionName("approve_request")
                .actionType(ActionType.APPROVE)
                .build();
        String xml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>";
        
        assertThat(AiBpmnActionBindingWriter.rebind(xml, AiBpmnActionBindingWriter.Snapshot.EMPTY, List.of(action),
                List.of(Map.of("actionName", "approve_request", "stageIds", List.of("Task_Review")))))
                .isEqualTo(xml);
    }

    /** 同 AiBpmnFormBindingWriter：camunda:properties 不是设计器认得的容器，不能往里塞 actionIds。 */
    @Test
    void rebind_shouldNotWriteIntoAForeignNamespaceContainer() {
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

        String result = AiBpmnActionBindingWriter.rebind(xml, AiBpmnActionBindingWriter.Snapshot.EMPTY, List.of(approve),
                List.of(Map.of("actionName", "approve", "stageIds", List.of("Task_Approve"))));

        assertThat(result).contains("name=\"actionIds\" value=\"[7]\"");
        String camundaContainer = result.substring(result.indexOf("<camunda:properties"),
                result.indexOf("</camunda:properties>"));
        assertThat(camundaContainer).doesNotContain("actionIds");
        assertThat(camundaContainer).contains("assigneeType");
        assertThat(result).contains("<custom:properties");
    }

    private static final String TWO_TASKS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:custom="http://custom.bpmn.io/schema">
              <bpmn:process id="Process_1">
                %s
                <bpmn:userTask id="Task_Submit" name="Submit">%s</bpmn:userTask>
                <bpmn:userTask id="Task_Review" name="Review">%s</bpmn:userTask>
              </bpmn:process>
            </bpmn:definitions>
            """;

    private static String bound(String idsKey, String ids, String names) {
        return "<bpmn:extensionElements><custom:properties>"
                + "<custom:property name=\"" + idsKey + "\" value=\"" + ids + "\"/>"
                + "<custom:property name=\"" + idsKey.replace("Ids", "Names") + "\" value=\"" + names.replace("\"", "&quot;") + "\"/>"
                + "</custom:properties></bpmn:extensionElements>";
    }

    private static ActionDefinition action(long id, String name) {
        return ActionDefinition.builder().id(id).actionName(name).build();
    }

    private static Map<String, List<String>> placement(String xml, List<ActionDefinition> actions) {
        return AiBpmnActionBindingWriter.capture(xml, actions).byTask();
    }

    /** Scope ACTIONS: actions are rebuilt under new ids while the stored BPMN stays. */
    @Test
    void rebind_rebuiltActions_declaredStagesWinUndeclaredFollowByNameAndDeadIdsGo() {
        String stored = TWO_TASKS.formatted("",
                bound("actionIds", "[10,11]", "[\"submit\",\"save_draft\"]"),
                bound("actionIds", "[12]", "[\"approve\"]"));
        var before = AiBpmnActionBindingWriter.capture(stored,
                List.of(action(10, "submit"), action(11, "save_draft"), action(12, "approve")));
        // save_draft was removed; approve now declares Task_Submit only; submit declares nothing
        List<ActionDefinition> rebuilt = List.of(action(20, "submit"), action(22, "approve"));
        List<Map<String, Object>> generated = List.of(
                Map.of("actionName", "submit"),
                Map.of("actionName", "approve", "stageIds", List.of("Task_Submit")));

        String result = AiBpmnActionBindingWriter.rebind(stored, before, rebuilt, generated);

        assertThat(placement(result, rebuilt))
                .containsEntry("Task_Submit", List.of("approve", "submit"))
                .doesNotContainKey("Task_Review");
        assertThat(result).contains("value=\"[22,20]\"").doesNotContain("[10").doesNotContain("[12]")
                .doesNotContain("save_draft");
    }

    /** Scope PROCESS: the model's new XML has no ids; bindings follow the user-task id. */
    @Test
    void rebind_replacedProcess_carriesBindingsByTaskIdIncludingGlobal() {
        String stored = TWO_TASKS.formatted(bound("globalActionIds", "[9]", "[\"withdraw\"]"),
                bound("actionIds", "[7]", "[\"submit\"]"),
                bound("actionIds", "[8]", "[\"approve\"]"));
        List<ActionDefinition> actions = List.of(action(7, "submit"), action(8, "approve"), action(9, "withdraw"));
        var before = AiBpmnActionBindingWriter.capture(stored, actions);
        String fresh = """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="Process_1">
                    <bpmn:userTask id="Task_Submit" name="Submit"/>
                    <bpmn:userTask id="Task_Approve" name="Review"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String result = AiBpmnActionBindingWriter.rebind(fresh, before, actions, null);

        var after = AiBpmnActionBindingWriter.capture(result, actions);
        assertThat(after.byTask()).containsEntry("Task_Submit", List.of("submit"))
                .doesNotContainKey("Task_Approve");
        assertThat(after.global()).containsExactly("withdraw");
    }

    /** AI Generate undo replays a snapshot BPMN whose ids were deleted since; the paired names still resolve. */
    @Test
    void rebind_staleIdsInWrittenBpmnResolveThroughPairedNames() {
        String snapshot = TWO_TASKS.formatted("", bound("actionIds", "[1]", "[\"submit\"]"), "");
        var before = AiBpmnActionBindingWriter.capture(snapshot, List.of(action(50, "submit")));
        List<ActionDefinition> replayed = List.of(action(60, "submit"));

        String result = AiBpmnActionBindingWriter.rebind(snapshot, before, replayed, List.of(Map.of("actionName", "submit")));

        assertThat(result).contains("value=\"[60]\"").doesNotContain("value=\"[1]\"");
    }

    @Test
    void rebind_returnsInputUntouchedWhenNothingChanges() {
        String stored = TWO_TASKS.formatted("", bound("actionIds", "[7]", "[\"submit\"]"), "");
        List<ActionDefinition> actions = List.of(action(7, "submit"));
        var before = AiBpmnActionBindingWriter.capture(stored, actions);

        assertThat(AiBpmnActionBindingWriter.rebind(stored, before, actions, null)).isSameAs(stored);
    }

    @Test
    void plan_reportsEveryUserTaskByName() {
        String stored = TWO_TASKS.formatted("", "", "");
        var plan = AiBpmnActionBindingWriter.plan(stored, AiBpmnActionBindingWriter.capture(stored, List.of()),
                List.of("submit", "approve"),
                List.of(Map.of("actionName", "submit", "stageIds", List.of("Task_Submit", "Task_Missing"))));

        assertThat(plan.byTask()).containsEntry("Task_Submit", List.of("submit"))
                .containsEntry("Task_Review", List.of());
        assertThat(plan.global()).isNull();
    }
}
