package com.workflow.component;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

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

    @Test
    void normalizeBpmnXml_convertsSendEmailTaskToServiceTaskDelegate() throws Exception {
        ProcessDeploymentManager manager = new ProcessDeploymentManager();
        Method normalize = ProcessDeploymentManager.class.getDeclaredMethod("normalizeBpmnXml", String.class);
        normalize.setAccessible(true);

        String normalized = (String) normalize.invoke(manager, SEND_EMAIL_BPMN);

        assertFalse(normalized.contains("sendTask"), "sendTask should be converted to serviceTask");
        assertTrue(normalized.contains("serviceTask"), "expected serviceTask element");
        assertTrue(normalized.contains("sendEmailTaskDelegate"), "expected sendEmailTaskDelegate binding");
        assertTrue(normalized.contains("connectionId"), "email extension properties must be preserved");
    }
}
