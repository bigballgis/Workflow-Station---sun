package com.workflow.component;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessDeploymentManagerSendEmailTest {

    private static final String SEND_EMAIL_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:flowable="http://flowable.org/bpmn"
              targetNamespace="http://flowable.org/test">
              <process id="Process_Test" name="Test" isExecutable="true">
                <startEvent id="start"/>
                <sendTask id="Activity_Email" name="Send Email">
                  <extensionElements>
                    <flowable:properties>
                      <flowable:property name="connectionId" value="42"/>
                      <flowable:property name="emailTo" value="user@example.com"/>
                    </flowable:properties>
                  </extensionElements>
                </sendTask>
                <endEvent id="end"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="Activity_Email"/>
                <sequenceFlow id="f2" sourceRef="Activity_Email" targetRef="end"/>
              </process>
            </definitions>
            """;

    /**
     * Regression: SendTask inside Multi-Instance SubProcess must be replaced in-place.
     * Previously process.remove/add put a duplicate ServiceTask at process root (cvc-id.2).
     */
    private static final String SEND_EMAIL_IN_MI_SUBPROCESS_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:flowable="http://flowable.org/bpmn"
              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
              targetNamespace="http://flowable.org/test">
              <process id="Process_MI" name="MI Test" isExecutable="true">
                <startEvent id="start"/>
                <subProcess id="Activity_MI" name="Multi Instance">
                  <multiInstanceLoopCharacteristics isSequential="false"
                    flowable:collection="${assignees}" flowable:elementVariable="assignee"/>
                  <startEvent id="mi_start"/>
                  <sendTask id="Activity_00auiyo" name="Send Email">
                    <extensionElements>
                      <flowable:properties>
                        <flowable:property name="connectionId" value="42"/>
                        <flowable:property name="emailTo" value="user@example.com"/>
                      </flowable:properties>
                    </extensionElements>
                  </sendTask>
                  <endEvent id="mi_end"/>
                  <sequenceFlow id="mi_f1" sourceRef="mi_start" targetRef="Activity_00auiyo"/>
                  <sequenceFlow id="mi_f2" sourceRef="Activity_00auiyo" targetRef="mi_end"/>
                </subProcess>
                <endEvent id="end"/>
                <sequenceFlow id="f1" sourceRef="start" targetRef="Activity_MI"/>
                <sequenceFlow id="f2" sourceRef="Activity_MI" targetRef="end"/>
              </process>
            </definitions>
            """;

    @Test
    void normalizeBpmnXml_convertsSendEmailTaskToServiceTaskDelegate() throws Exception {
        String normalized = normalize(SEND_EMAIL_BPMN);

        assertFalse(normalized.contains("sendTask"), "sendTask should be converted to serviceTask");
        assertTrue(normalized.contains("serviceTask"), "expected serviceTask element");
        assertTrue(normalized.contains("sendEmailTaskDelegate"), "expected sendEmailTaskDelegate binding");
        assertTrue(normalized.contains("connectionId"), "email extension properties must be preserved");
    }

    @Test
    void normalizeBpmnXml_convertsSendEmailTaskInsideMultiInstanceSubProcessWithoutDuplicateId()
            throws Exception {
        String normalized = normalize(SEND_EMAIL_IN_MI_SUBPROCESS_BPMN);

        assertFalse(normalized.contains("<sendTask"), "nested sendTask should be converted");
        assertTrue(normalized.contains("sendEmailTaskDelegate"), "expected delegate binding");
        assertEquals(1, countOccurrences(normalized, "id=\"Activity_00auiyo\""),
                "SendTask id must appear exactly once after conversion (no duplicate at process root)");
        assertTrue(normalized.contains("<subProcess"), "subProcess structure must be preserved");
        // Converted serviceTask must still sit inside the subProcess, not be hoisted to process root
        int subProcessStart = normalized.indexOf("<subProcess");
        int subProcessEnd = normalized.indexOf("</subProcess>");
        assertTrue(subProcessStart >= 0 && subProcessEnd > subProcessStart);
        String subProcessXml = normalized.substring(subProcessStart, subProcessEnd);
        assertTrue(subProcessXml.contains("id=\"Activity_00auiyo\""),
                "converted serviceTask must remain inside the Multi-Instance SubProcess");
        assertTrue(subProcessXml.contains("serviceTask"),
                "converted element inside subProcess must be serviceTask");
    }

    private static String normalize(String bpmnXml) throws Exception {
        ProcessDeploymentManager manager = new ProcessDeploymentManager();
        Method normalize = ProcessDeploymentManager.class.getDeclaredMethod("normalizeBpmnXml", String.class);
        normalize.setAccessible(true);
        return (String) normalize.invoke(manager, bpmnXml);
    }

    private static int countOccurrences(String haystack, String needle) {
        Matcher matcher = Pattern.compile(Pattern.quote(needle)).matcher(haystack);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
