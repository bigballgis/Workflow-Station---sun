package com.developer.util;

import com.developer.exception.AiGenerationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 语义守门的四条规则：审批分枝必须过排他网关、审批条件只认 decision=yes/no、
 * 必须有 PROCESS_SUBMIT、stageIds 必须指向真实 userTask。
 */
class AiBpmnSemanticGuardTest {

    /** 审批任务直接挂两条条件出向流——没有网关，且条件值用的是运行时永远不会被写入的 approved/rejected。 */
    private static String ungatedApprovalBpmn(String approveCondition, String rejectCondition) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="approval_process" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="Start"/>
                    <bpmn:userTask id="Task_Review" name="Review">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="assigneeType" value="PROCESS_INITIATOR"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                    <bpmn:endEvent id="EndEvent_Approved" name="Approved"/>
                    <bpmn:endEvent id="EndEvent_Rejected" name="Rejected"/>
                    <bpmn:sequenceFlow id="Flow_start" sourceRef="StartEvent_1" targetRef="Task_Review"/>
                    <bpmn:sequenceFlow id="Flow_yes" sourceRef="Task_Review" targetRef="EndEvent_Approved">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">%s</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="Flow_no" sourceRef="Task_Review" targetRef="EndEvent_Rejected">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">%s</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="Diagram_1">
                    <bpmndi:BPMNPlane id="Plane_1" bpmnElement="approval_process">
                      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
                        <dc:Bounds x="100" y="100" width="36" height="36"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNShape id="Task_Review_di" bpmnElement="Task_Review">
                        <dc:Bounds x="200" y="78" width="100" height="80"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNShape id="EndEvent_Approved_di" bpmnElement="EndEvent_Approved">
                        <dc:Bounds x="440" y="40" width="36" height="36"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNShape id="EndEvent_Rejected_di" bpmnElement="EndEvent_Rejected">
                        <dc:Bounds x="440" y="180" width="36" height="36"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNEdge id="Flow_start_di" bpmnElement="Flow_start">
                        <di:waypoint x="136" y="118"/><di:waypoint x="200" y="118"/>
                      </bpmndi:BPMNEdge>
                      <bpmndi:BPMNEdge id="Flow_yes_di" bpmnElement="Flow_yes">
                        <di:waypoint x="300" y="118"/><di:waypoint x="440" y="58"/>
                      </bpmndi:BPMNEdge>
                      <bpmndi:BPMNEdge id="Flow_no_di" bpmnElement="Flow_no">
                        <di:waypoint x="300" y="118"/><di:waypoint x="440" y="198"/>
                      </bpmndi:BPMNEdge>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """.formatted(approveCondition, rejectCondition);
    }

    private static Map<String, Object> generatedData(List<Map<String, Object>> actions) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (actions != null) {
            data.put("actionDefinitions", new ArrayList<>(actions));
        }
        return data;
    }

    private static Map<String, Object> action(String name, String type, List<String> stageIds) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("actionName", name);
        action.put("actionType", type);
        action.put("stageIds", new ArrayList<>(stageIds));
        return action;
    }

    // ==================== R1 排他网关 ====================

    @Test
    void enforce_userTaskWithConditionalBranches_getsAnExclusiveGatewayInserted() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");

        AiBpmnSemanticGuard.Result result = AiBpmnSemanticGuard.enforce(generatedData(null), xml);

        assertThat(result.bpmnXml()).contains("exclusiveGateway");
        // 两条分枝流不再从 userTask 出发，而是从新网关出发。
        assertThat(result.bpmnXml()).doesNotContain("sourceRef=\"Task_Review\" targetRef=\"EndEvent_Approved\"");
        assertThat(result.bpmnXml()).contains("sourceRef=\"Gateway_Task_Review\" targetRef=\"EndEvent_Approved\"");
        assertThat(result.bpmnXml()).contains("sourceRef=\"Gateway_Task_Review\" targetRef=\"EndEvent_Rejected\"");
        assertThat(result.bpmnXml()).contains("sourceRef=\"Task_Review\" targetRef=\"Gateway_Task_Review\"");
        // 条件原样保留在被改挂的流上。
        assertThat(result.bpmnXml()).contains("${decision == 'yes'}").contains("${decision == 'no'}");
        assertThat(result.repairs()).anyMatch(r -> r.contains("inserted bpmn:exclusiveGateway"));
    }

    @Test
    void enforce_insertedGateway_carriesDiagramShapeAndEdge() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");

        String repaired = AiBpmnSemanticGuard.enforce(generatedData(null), xml).bpmnXml();

        assertThat(repaired).contains("bpmnElement=\"Gateway_Task_Review\"");
        assertThat(repaired).contains("bpmnElement=\"Flow_Task_Review_to_Gateway_Task_Review\"");
    }

    @Test
    void enforce_userTaskWithSingleUnconditionalFlow_isLeftAlone() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}")
                .replaceAll("(?s)<bpmn:sequenceFlow id=\"Flow_no\".*?</bpmn:sequenceFlow>", "")
                .replaceAll("(?s)<bpmn:conditionExpression.*?</bpmn:conditionExpression>", "");

        AiBpmnSemanticGuard.Result result = AiBpmnSemanticGuard.enforce(generatedData(null), xml);

        assertThat(result.bpmnXml()).isEqualTo(xml);
        assertThat(result.repairs()).noneMatch(r -> r.contains("exclusiveGateway"));
    }

    @Test
    void enforce_gatewayWithOneUnconditionalBranch_marksItAsDefault() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}")
                .replaceAll("(?s)<bpmn:conditionExpression[^>]*>\\$\\{decision == 'no'\\}</bpmn:conditionExpression>",
                        "");

        String repaired = AiBpmnSemanticGuard.enforce(generatedData(null), xml).bpmnXml();

        assertThat(repaired).contains("default=\"Flow_no\"");
    }

    /**
     * 守门在生成阶段（AiResponseParser）和落库阶段（AiWriteServiceImpl.applyGeneratedData）各跑一次，
     * 所以第二遍必须是空操作——否则每次 apply 都会再插一个网关、再补一个提交动作。
     */
    @Test
    void enforce_isIdempotent() {
        String xml = ungatedApprovalBpmn("${decision == 'approved'}", "${decision == 'rejected'}");
        Map<String, Object> data = generatedData(List.of(
                action("approve_request", "APPROVE", List.of("Task_Review"))));

        String firstPass = AiBpmnSemanticGuard.enforce(data, xml).bpmnXml();
        AiBpmnSemanticGuard.Result secondPass = AiBpmnSemanticGuard.enforce(data, firstPass);

        assertThat(secondPass.repairs()).isEmpty();
        assertThat(secondPass.bpmnXml()).isEqualTo(firstPass);
        assertThat((List<?>) data.get("actionDefinitions")).hasSize(2);
    }

    // ==================== R2 审批条件变量值 ====================

    @Test
    void enforce_approvedRejectedLiterals_areNormalizedToDecisionYesNo() {
        String xml = ungatedApprovalBpmn("${decision == 'approved'}", "${decision == 'rejected'}");

        AiBpmnSemanticGuard.Result result = AiBpmnSemanticGuard.enforce(generatedData(null), xml);

        assertThat(result.bpmnXml()).contains("${decision == 'yes'}").contains("${decision == 'no'}");
        assertThat(result.bpmnXml()).doesNotContain("approved").doesNotContain("rejected");
        assertThat(result.repairs()).anyMatch(r -> r.contains("rewrote condition on sequenceFlow 'Flow_yes'"));
    }

    @Test
    void enforce_actionApproveRejectLiterals_areNormalizedToDecisionYesNo() {
        String xml = ungatedApprovalBpmn("${action == 'APPROVE'}", "${action == 'REJECT'}");

        String repaired = AiBpmnSemanticGuard.enforce(generatedData(null), xml).bpmnXml();

        assertThat(repaired).contains("${decision == 'yes'}").contains("${decision == 'no'}");
        assertThat(repaired).doesNotContain("APPROVE").doesNotContain("REJECT");
    }

    @Test
    void enforce_negatedApprovalCondition_flipsToTheOppositeOutcome() {
        String xml = ungatedApprovalBpmn("${decision != 'rejected'}", "${approvalStatus.equals('REJECTED')}");

        String repaired = AiBpmnSemanticGuard.enforce(generatedData(null), xml).bpmnXml();

        assertThat(repaired).contains("${decision == 'yes'}").contains("${decision == 'no'}");
    }

    @Test
    void enforce_businessConditionOnANonApprovalVariable_isLeftAlone() {
        String xml = ungatedApprovalBpmn("${amount &gt; 1000}", "${decision == 'no'}");

        String repaired = AiBpmnSemanticGuard.enforce(generatedData(null), xml).bpmnXml();

        assertThat(repaired).contains("${amount &gt; 1000}");
    }

    @Test
    void enforce_decisionComparedToAnUnrecognisableLiteral_isRejected() {
        String xml = ungatedApprovalBpmn("${decision == 'escalated'}", "${decision == 'no'}");

        assertThatThrownBy(() -> AiBpmnSemanticGuard.enforce(generatedData(null), xml))
                .isInstanceOf(AiGenerationException.class)
                .hasFieldOrPropertyWithValue("errorCode", "AI_BPMN_DECISION_VALUE_INVALID");
    }

    // ==================== R3 PROCESS_SUBMIT ====================

    @Test
    void enforce_generationWithoutSubmitAction_getsOneOnTheFirstUserTask() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");
        Map<String, Object> data = generatedData(List.of(
                action("approve_request", "APPROVE", List.of("Task_Review"))));

        AiBpmnSemanticGuard.enforce(data, xml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) data.get("actionDefinitions");
        assertThat(actions).hasSize(2);
        Map<String, Object> submit = actions.get(1);
        assertThat(submit).containsEntry("actionType", "PROCESS_SUBMIT");
        assertThat(submit).containsEntry("stageIds", List.of("Task_Review"));
    }

    @Test
    void enforce_generationThatAlreadyHasSubmit_addsNothing() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");
        Map<String, Object> data = generatedData(List.of(
                action("submit_request", "PROCESS_SUBMIT", List.of("Task_Review"))));

        AiBpmnSemanticGuard.enforce(data, xml);

        assertThat((List<?>) data.get("actionDefinitions")).hasSize(1);
    }

    /** 范围化重生成只回 processDefinition：动作还在库里，不能凭空补一个。 */
    @Test
    void enforce_responseWithoutActionDefinitions_doesNotSynthesiseSubmit() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");
        Map<String, Object> data = generatedData(null);

        AiBpmnSemanticGuard.enforce(data, xml);

        assertThat(data).doesNotContainKey("actionDefinitions");
    }

    // ==================== R4 stageIds ====================

    @Test
    void enforce_stageIdsMixingRealAndImaginaryTasks_dropsTheImaginaryOnes() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");
        Map<String, Object> data = generatedData(List.of(
                action("approve_request", "APPROVE", List.of("Task_Review", "Task_Does_Not_Exist")),
                action("submit_request", "PROCESS_SUBMIT", List.of("Task_Review"))));

        AiBpmnSemanticGuard.Result result = AiBpmnSemanticGuard.enforce(data, xml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) data.get("actionDefinitions");
        assertThat(actions.get(0)).containsEntry("stageIds", List.of("Task_Review"));
        assertThat(result.repairs()).anyMatch(r -> r.contains("Task_Does_Not_Exist"));
    }

    /** 提交动作的归属由平台契约决定（第一个 userTask），所以全非法时也能确定性修复。 */
    @Test
    void enforce_submitActionBoundOnlyToAGatewayId_isReboundToTheFirstUserTask() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");
        Map<String, Object> data = generatedData(List.of(
                action("submit_request", "PROCESS_SUBMIT", List.of("StartEvent_1"))));

        AiBpmnSemanticGuard.enforce(data, xml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) data.get("actionDefinitions");
        assertThat(actions.get(0)).containsEntry("stageIds", List.of("Task_Review"));
    }

    /**
     * 非提交动作全部指向不存在的节点时不猜——原样留给 AiResponseParser 的
     * AI_ACTION_STAGE_BINDING_INVALID 拒掉并让模型重生成。把审批按钮挪到某个"看起来合理"的
     * 任务上，比直接失败更难被发现。
     */
    @Test
    void enforce_nonSubmitActionWithOnlyImaginaryStages_isLeftForTheValidatorToReject() {
        String xml = ungatedApprovalBpmn("${decision == 'yes'}", "${decision == 'no'}");
        Map<String, Object> data = generatedData(List.of(
                action("approve_request", "APPROVE", List.of("Task_Does_Not_Exist")),
                action("submit_request", "PROCESS_SUBMIT", List.of("Task_Review"))));

        AiBpmnSemanticGuard.enforce(data, xml);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> actions = (List<Map<String, Object>>) data.get("actionDefinitions");
        assertThat(actions.get(0)).containsEntry("stageIds", List.of("Task_Does_Not_Exist"));
    }
}
