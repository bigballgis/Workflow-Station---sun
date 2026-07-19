package com.portal.component;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BpmnMiXmlSupport#buildMiInnerTaskNameToSubProcessName(String)}：把多实例内层 userTask 名
 * 映射到外层多实例 subProcess 名（供 My Requests 列表把 "sub form1" 显示成 "multi"）。
 */
@DisplayName("BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName")
class BpmnMiXmlSupportMiNodeNameTest {

    // 一个多实例 subProcess(name="multi")内含两个 userTask(sub form1/sub form2)，外加一个普通 userTask(assignment)。
    private static final String BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" id="defs">
          <bpmn:process id="Process_1" isExecutable="true">
            <bpmn:userTask id="assignmentTask" name="assignment"/>
            <bpmn:subProcess id="MultiInstance_1" name="multi">
              <bpmn:multiInstanceLoopCharacteristics isSequential="false"/>
              <bpmn:userTask id="subForm1Task" name="sub form1"/>
              <bpmn:userTask id="subForm2Task" name="sub form2"/>
            </bpmn:subProcess>
          </bpmn:process>
        </bpmn:definitions>
        """;

    @Test
    @DisplayName("MI 内层任务名映射到外层 MI subProcess 名；普通任务不进映射")
    void mapsInnerTasksToMiName() {
        Map<String, String> map = BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName(BPMN);
        assertThat(map).containsEntry("sub form1", "multi");
        assertThat(map).containsEntry("sub form2", "multi");
        // 普通节点 "assignment" 不在 MI 内，不应出现在映射里（调用方回退用 currentNode）。
        assertThat(map).doesNotContainKey("assignment");
    }

    @Test
    @DisplayName("MI subProcess 无 name 时回退用其 id")
    void fallsBackToSubProcessIdWhenNoName() {
        String xml = BPMN.replace(" name=\"multi\"", "");
        Map<String, String> map = BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName(xml);
        assertThat(map).containsEntry("sub form1", "MultiInstance_1");
    }

    @Test
    @DisplayName("非多实例 subProcess 的内层任务不进映射")
    void ignoresNonMiSubProcess() {
        String xml = BPMN.replace("<bpmn:multiInstanceLoopCharacteristics isSequential=\"false\"/>", "");
        Map<String, String> map = BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName(xml);
        assertThat(map).isEmpty();
    }

    @Test
    @DisplayName("空/无效 XML 返回空 map（调用方回退 currentNode）")
    void blankOrInvalidReturnsEmpty() {
        assertThat(BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName(null)).isEmpty();
        assertThat(BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName("")).isEmpty();
        assertThat(BpmnMiXmlSupport.buildMiInnerTaskNameToSubProcessName("not-xml")).isEmpty();
    }
}
