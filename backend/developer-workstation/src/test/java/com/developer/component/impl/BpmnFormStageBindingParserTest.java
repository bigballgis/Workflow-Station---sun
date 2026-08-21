package com.developer.component.impl;

import com.developer.enums.FormScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors the frontend parser {@code parseBpmnNodeFormBindings} in
 * {@code frontend/developer-workstation/src/utils/bpmnFormBindings.ts} property-for-property, so
 * a node the Bind Process Node dialog shows as bound is exactly what this class extracts.
 */
class BpmnFormStageBindingParserTest {

    private final BpmnFormStageBindingParser parser = new BpmnFormStageBindingParser();

    private static String bpmnWithTask(String taskElementXml) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:custom="http://custom.bpmn.io/schema"
                                  id="Definitions_1">
                  <bpmn:process id="Process_1" isExecutable="true">
                """ + taskElementXml + """
                  </bpmn:process>
                </bpmn:definitions>""";
    }

    @Test
    void parsesFormIdFormNameAndReadOnlyFromAUserTask() {
        String xml = bpmnWithTask("""
                <bpmn:userTask id="Activity_1" name="Sub task">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="50192" />
                      <custom:property name="formName" value="Sub task" />
                      <custom:property name="formReadOnly" value="true" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """);

        List<BpmnFormStageBindingParser.ParsedBinding> result = parser.parse(xml);

        assertThat(result).containsExactly(
                new BpmnFormStageBindingParser.ParsedBinding(50192L, "Activity_1", "Sub task", true, FormScene.TASK));
    }

    @Test
    void defaultsReadOnlyToFalseWhenThePropertyIsAbsent() {
        String xml = bpmnWithTask("""
                <bpmn:userTask id="Activity_1" name="Sub task">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="50192" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """);

        List<BpmnFormStageBindingParser.ParsedBinding> result = parser.parse(xml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).readOnly()).isFalse();
    }

    @Test
    void parsesServiceTaskAndOtherTaskLikeElementsTooNotOnlyUserTask() {
        String xml = bpmnWithTask("""
                <bpmn:serviceTask id="Activity_2" name="Send email">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="10" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:serviceTask>
                """);

        List<BpmnFormStageBindingParser.ParsedBinding> result = parser.parse(xml);

        assertThat(result).extracting(BpmnFormStageBindingParser.ParsedBinding::stageId)
                .containsExactly("Activity_2");
    }

    @Test
    void emitsBothTaskAndRequestScenesWhenANodeCarriesBothDesigns() {
        String xml = bpmnWithTask("""
                <bpmn:userTask id="Activity_1" name="Sub task">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="50192" />
                      <custom:property name="formName" value="Sub task" />
                      <custom:property name="requestFormId" value="50193" />
                      <custom:property name="requestFormName" value="Sub task (My Request)" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """);

        List<BpmnFormStageBindingParser.ParsedBinding> result = parser.parse(xml);

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(b -> {
            assertThat(b.formId()).isEqualTo(50192L);
            assertThat(b.scene()).isEqualTo(FormScene.TASK);
        });
        assertThat(result).anySatisfy(b -> {
            assertThat(b.formId()).isEqualTo(50193L);
            assertThat(b.scene()).isEqualTo(FormScene.REQUEST);
            // My Requests designs are always read-only, regardless of any formReadOnly property.
            assertThat(b.readOnly()).isTrue();
        });
    }

    @Test
    void ignoresATaskWithNoFormIdProperty() {
        String xml = bpmnWithTask("""
                <bpmn:userTask id="Activity_1" name="Unbound task" />
                """);

        assertThat(parser.parse(xml)).isEmpty();
    }

    @Test
    void ignoresNonTaskElements() {
        String xml = bpmnWithTask("""
                <bpmn:startEvent id="StartEvent_1" />
                <bpmn:endEvent id="EndEvent_1" />
                """);

        assertThat(parser.parse(xml)).isEmpty();
    }

    @Test
    void returnsEmptyForBlankOrUnparsableXml() {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
        assertThat(parser.parse("not xml at all <<<")).isEmpty();
    }

    @Test
    void ignoresAFormIdValueThatIsNotANumber() {
        String xml = bpmnWithTask("""
                <bpmn:userTask id="Activity_1" name="Sub task">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="not-a-number" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """);

        assertThat(parser.parse(xml)).isEmpty();
    }

    @Test
    void parsesMultipleIndependentlyBoundTasks() {
        String xml = bpmnWithTask("""
                <bpmn:userTask id="Activity_1" name="First">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="1" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                <bpmn:userTask id="Activity_2" name="Second">
                  <bpmn:extensionElements>
                    <custom:properties>
                      <custom:property name="formId" value="2" />
                    </custom:properties>
                  </bpmn:extensionElements>
                </bpmn:userTask>
                """);

        List<BpmnFormStageBindingParser.ParsedBinding> result = parser.parse(xml);

        assertThat(result).extracting(BpmnFormStageBindingParser.ParsedBinding::stageId)
                .containsExactlyInAnyOrder("Activity_1", "Activity_2");
    }
}
