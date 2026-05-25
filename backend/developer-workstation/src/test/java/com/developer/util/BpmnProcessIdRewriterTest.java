package com.developer.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnProcessIdRewriterTest {

    private static final String SAMPLE_BPMN = """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
              xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
              id="Definitions_1">
              <bpmn:process id="Process_1_xx" isExecutable="true" name="Demo">
                <bpmn:startEvent id="StartEvent_1" />
              </bpmn:process>
              <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1_xx">
                  <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1" />
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </bpmn:definitions>
            """;

    @Test
    void extractProcessId_readsFirstProcessElement() {
        assertThat(BpmnProcessIdRewriter.extractProcessId(SAMPLE_BPMN)).isEqualTo("Process_1_xx");
    }

    @Test
    void rewriteToFunctionUnitCode_updatesProcessAndDiagramReference() {
        String newCode = "multi-instance-subtask-demo-20260526-abc123";
        String rewritten = BpmnProcessIdRewriter.rewriteToFunctionUnitCode(SAMPLE_BPMN, newCode);

        assertThat(rewritten).contains("<bpmn:process id=\"" + newCode + "\"");
        assertThat(rewritten).contains("bpmnElement=\"" + newCode + "\"");
        assertThat(rewritten).contains("bpmnElement=\"StartEvent_1\"");
        assertThat(rewritten).doesNotContain("Process_1_xx");
    }

    @Test
    void rewriteToFunctionUnitCode_isNoOpWhenAlreadyAligned() {
        String code = "demo-flow-20260526-xyz789";
        String aligned = SAMPLE_BPMN.replace("Process_1_xx", code);
        assertThat(BpmnProcessIdRewriter.rewriteToFunctionUnitCode(aligned, code)).isEqualTo(aligned);
    }

    @Test
    void rewriteToFunctionUnitCode_preservesBase64Encoding() {
        String encoded = XmlEncodingUtil.encode(SAMPLE_BPMN);
        String newCode = "cloned-flow-20260526-qwerty";
        String rewritten = BpmnProcessIdRewriter.rewriteToFunctionUnitCode(encoded, newCode);
        String decoded = XmlEncodingUtil.smartDecode(rewritten);

        assertThat(decoded).contains("<bpmn:process id=\"" + newCode + "\"");
        assertThat(rewritten).isEqualTo(XmlEncodingUtil.encode(decoded));
    }
}
