package com.developer.component.impl;

import com.developer.dto.ValidationResult;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import com.platform.common.i18n.I18nService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ProcessBpmnValidatorSendEmailTest {

    @Mock
    private TableDefinitionRepository tableDefinitionRepository;
    @Mock
    private FormDefinitionRepository formDefinitionRepository;
    @Mock
    private I18nService i18nService;

    private ProcessBpmnValidator validator;

    @BeforeEach
    void setUp() {
        lenient().when(i18nService.getMessage(anyString())).thenAnswer(inv -> inv.getArgument(0));
        validator = new ProcessBpmnValidator(
                tableDefinitionRepository, formDefinitionRepository, i18nService);
    }

    @Test
    void validate_requiresEmailTemplate_notFromOrSubject() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="P1" isExecutable="true">
                    <bpmn:startEvent id="start"/>
                    <bpmn:sendTask id="Activity_Email" name="Send Email">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="sendMode" value="email"/>
                          <custom:property name="connectionId" value="conn-1"/>
                          <custom:property name="emailTo" value="a@example.com"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:sendTask>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="Activity_Email"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="Activity_Email" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validate(bpmn);

        assertThat(result.getErrors()).anyMatch(e -> "SEND_TASK_MISSING_TEMPLATE".equals(e.getCode()));
        assertThat(result.getErrors()).noneMatch(e -> "SEND_TASK_MISSING_FROM".equals(e.getCode()));
        assertThat(result.getErrors()).noneMatch(e -> "SEND_TASK_MISSING_SUBJECT".equals(e.getCode()));
    }

    @Test
    void validate_acceptsSendTaskWithTemplateAndNoFrom() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="P1" isExecutable="true">
                    <bpmn:startEvent id="start"/>
                    <bpmn:sendTask id="Activity_Email" name="Send Email">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="sendMode" value="email"/>
                          <custom:property name="connectionId" value="conn-1"/>
                          <custom:property name="emailTo" value="a@example.com"/>
                          <custom:property name="emailTemplateId" value="12"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:sendTask>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="Activity_Email"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="Activity_Email" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validate(bpmn);

        assertThat(result.getErrors()).noneMatch(e -> e.getCode() != null && e.getCode().startsWith("SEND_TASK_"));
    }

    @Test
    void validate_rejectsLegacyFreeFormAttachments() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="P1" isExecutable="true">
                    <bpmn:startEvent id="start"/>
                    <bpmn:sendTask id="Activity_Email" name="Send Email">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="sendMode" value="email"/>
                          <custom:property name="connectionId" value="conn-1"/>
                          <custom:property name="emailTo" value="a@example.com"/>
                          <custom:property name="emailTemplateId" value="12"/>
                          <custom:property name="emailAttachments" value="[{&quot;name&quot;:&quot;a.pdf&quot;,&quot;content&quot;:&quot;AAAA&quot;}]"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:sendTask>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="Activity_Email"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="Activity_Email" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validate(bpmn);

        assertThat(result.getErrors()).anyMatch(e -> "SEND_TASK_INVALID_ATTACHMENTS".equals(e.getCode()));
    }

    @Test
    void validate_acceptsUploadFieldAttachments() {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://workflow.platform/schema/bpmn">
                  <bpmn:process id="P1" isExecutable="true">
                    <bpmn:startEvent id="start"/>
                    <bpmn:sendTask id="Activity_Email" name="Send Email">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="sendMode" value="email"/>
                          <custom:property name="connectionId" value="conn-1"/>
                          <custom:property name="emailTo" value="a@example.com"/>
                          <custom:property name="emailTemplateId" value="12"/>
                          <custom:property name="emailAttachments" value="[{&quot;source&quot;:&quot;main&quot;,&quot;fieldName&quot;:&quot;invoice_file&quot;}]"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:sendTask>
                    <bpmn:endEvent id="end"/>
                    <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="Activity_Email"/>
                    <bpmn:sequenceFlow id="f2" sourceRef="Activity_Email" targetRef="end"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ValidationResult result = validator.validate(bpmn);

        assertThat(result.getErrors()).noneMatch(e -> "SEND_TASK_INVALID_ATTACHMENTS".equals(e.getCode()));
    }
}
