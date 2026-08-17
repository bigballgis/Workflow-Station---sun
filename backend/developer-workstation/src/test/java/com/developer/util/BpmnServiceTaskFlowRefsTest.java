package com.developer.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BPMN 里 service task 的 Automation flow 引用提取。
 */
class BpmnServiceTaskFlowRefsTest {

    private static final String BPMN_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:flowable="http://flowable.org/bpmn">
              <process id="p1">
                %s
              </process>
            </definitions>
            """;

    private static String serviceTask(String id, String... properties) {
        return "<serviceTask id=\"" + id + "\"><extensionElements><flowable:properties>"
                + String.join("", properties)
                + "</flowable:properties></extensionElements></serviceTask>";
    }

    @Test
    void extractsFlowIdFromServiceTask() {
        String bpmn = BPMN_TEMPLATE.formatted(serviceTask("t1",
                "<flowable:property name=\"ap:flowId\" value=\"flow-abc\" />",
                "<flowable:property name=\"ap:timeoutSeconds\" value=\"120\" />"));

        assertEquals(List.of("flow-abc"), BpmnServiceTaskFlowRefs.extract(bpmn));
    }

    @Test
    void extractsFlowKeyFromServiceTask() {
        String bpmn = BPMN_TEMPLATE.formatted(serviceTask("t1",
                "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />",
                "<flowable:property name=\"ap:timeoutSeconds\" value=\"120\" />"));

        assertEquals(List.of("invoice-sync"), BpmnServiceTaskFlowRefs.extract(bpmn));
    }

    @Test
    void prefersFlowKeyOverLegacyFlowIdWithinTheSameServiceTask() {
        // 业务键才是可移植引用；旧 flowId 是源环境实值，两者并存时只认业务键，
        // 否则跨环境导入会拿着源环境 id 去解析、被误判为缺失。
        String bpmn = BPMN_TEMPLATE.formatted(serviceTask("t1",
                "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />",
                "<flowable:property name=\"ap:flowId\" value=\"source-env-id\" />"));

        assertEquals(List.of("invoice-sync"), BpmnServiceTaskFlowRefs.extract(bpmn));
    }

    @Test
    void mixedTasksYieldKeyForKeyedTaskAndIdForLegacyTask() {
        String bpmn = BPMN_TEMPLATE.formatted(
                serviceTask("t1", "<flowable:property name=\"ap:flowKey\" value=\"invoice-sync\" />")
                        + serviceTask("t2", "<flowable:property name=\"ap:flowId\" value=\"flow-legacy\" />"));

        assertEquals(List.of("invoice-sync", "flow-legacy"), BpmnServiceTaskFlowRefs.extract(bpmn));
    }

    @Test
    void blankFlowKeyFallsBackToFlowIdWithinTheTask() {
        String bpmn = BPMN_TEMPLATE.formatted(serviceTask("t1",
                "<flowable:property name=\"ap:flowKey\" value=\"\" />",
                "<flowable:property name=\"ap:flowId\" value=\"flow-abc\" />"));

        assertEquals(List.of("flow-abc"), BpmnServiceTaskFlowRefs.extract(bpmn));
    }

    @Test
    void extractsFromBase64EncodedBpmn() {
        String bpmn = BPMN_TEMPLATE.formatted(serviceTask("t1",
                "<flowable:property name=\"ap:flowId\" value=\"flow-abc\" />"));

        assertEquals(List.of("flow-abc"),
                BpmnServiceTaskFlowRefs.extract(XmlEncodingUtil.encode(bpmn)));
    }

    @Test
    void handlesReversedAttributeOrderAndDeduplicates() {
        String bpmn = BPMN_TEMPLATE.formatted(
                serviceTask("t1", "<flowable:property value=\"flow-abc\" name=\"ap:flowId\" />")
                        + serviceTask("t2", "<flowable:property name=\"ap:flowId\" value=\"flow-abc\" />")
                        + serviceTask("t3", "<flowable:property name=\"ap:flowId\" value=\"flow-xyz\" />"));

        assertEquals(List.of("flow-abc", "flow-xyz"), BpmnServiceTaskFlowRefs.extract(bpmn));
    }

    @Test
    void ignoresWebhookOnlyAndBlankConfigurations() {
        String bpmn = BPMN_TEMPLATE.formatted(
                serviceTask("t1", "<flowable:property name=\"ap:webhookUrl\" value=\"http://ap/webhook\" />")
                        + serviceTask("t2", "<flowable:property name=\"ap:flowId\" value=\"\" />"));

        assertTrue(BpmnServiceTaskFlowRefs.extract(bpmn).isEmpty());
    }

    @Test
    void returnsEmptyForNullBlankOrFlowlessBpmn() {
        assertTrue(BpmnServiceTaskFlowRefs.extract(null).isEmpty());
        assertTrue(BpmnServiceTaskFlowRefs.extract("   ").isEmpty());
        assertTrue(BpmnServiceTaskFlowRefs.extract(BPMN_TEMPLATE.formatted("<userTask id=\"u1\" />")).isEmpty());
    }
}
