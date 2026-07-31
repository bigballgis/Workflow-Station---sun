package com.developer.util;

import com.developer.entity.FormDefinition;
import com.developer.entity.FormStageBinding;
import com.developer.enums.FormType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiBpmnFormBindingWriterTest {

    @Test
    void bindStageForms_shouldResolvePersistedFormIdAndReadOnlyFlag() {
        FormDefinition form = FormDefinition.builder()
                .id(42L)
                .formName("approval_form")
                .formType(FormType.TASK)
                .stageBindings(new ArrayList<>())
                .build();
        form.getStageBindings().add(FormStageBinding.builder()
                .form(form)
                .stageId("Task_Approve")
                .stageName("Approve Request")
                .readOnly(true)
                .build());

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema">
                  <bpmn:process id="Process_1">
                    <bpmn:userTask id="Task_Approve" name="Approve Request">
                      <bpmn:extensionElements>
                        <custom:properties>
                          <custom:property name="formId" value="999"/>
                          <custom:property name="formName" value="stale_form"/>
                        </custom:properties>
                      </bpmn:extensionElements>
                    </bpmn:userTask>
                  </bpmn:process>
                </bpmn:definitions>
                """;

        String result = AiBpmnFormBindingWriter.bindStageForms(xml, List.of(form));

        assertThat(result)
                .contains("name=\"formId\" value=\"42\"")
                .contains("name=\"formName\" value=\"approval_form\"")
                .contains("name=\"formReadOnly\" value=\"true\"")
                .doesNotContain("value=\"999\"")
                .doesNotContain("value=\"stale_form\"");
    }

    @Test
    void bindStageForms_shouldKeepXmlWhenFormIdIsNotAssigned() {
        FormDefinition form = FormDefinition.builder()
                .formName("approval_form")
                .formType(FormType.TASK)
                .stageBindings(new ArrayList<>())
                .build();
        form.getStageBindings().add(FormStageBinding.builder()
                .form(form)
                .stageId("Task_Approve")
                .build());
        String xml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"/>";

        assertThat(AiBpmnFormBindingWriter.bindStageForms(xml, List.of(form))).isEqualTo(xml);
    }
}
