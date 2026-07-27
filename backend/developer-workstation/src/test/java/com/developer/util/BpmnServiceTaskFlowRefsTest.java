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
