package com.developer.component.impl;

import com.developer.entity.ActionDefinition;
import com.developer.repository.ActionDefinitionRepository;
import com.developer.repository.FormDefinitionRepository;
import com.developer.repository.TableDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessBpmnStaleIdFixerTest {

    @Mock
    private TableDefinitionRepository tableDefinitionRepository;

    @Mock
    private FormDefinitionRepository formDefinitionRepository;

    @Mock
    private ActionDefinitionRepository actionDefinitionRepository;

    private ProcessBpmnStaleIdFixer fixer;

    @BeforeEach
    void setUp() {
        fixer = new ProcessBpmnStaleIdFixer(
                tableDefinitionRepository, formDefinitionRepository, actionDefinitionRepository);
        when(tableDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
        when(formDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of());
    }

    @Test
    void remapsGlobalActionIdsByActionName() {
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of(
                ActionDefinition.builder().id(1200L).actionName("Save").build()));

        String xml = """
                <bpmn:process id="p1">
                  <custom:property name="globalActionNames" value="[&quot;Save&quot;]" />
                  <custom:property name="globalActionIds" value="[50]" />
                  <bpmn:userTask id="task1">
                    <custom:property name="actionNames" value="[&quot;Approve&quot;]" />
                    <custom:property name="actionIds" value="[51]" />
                  </bpmn:userTask>
                </bpmn:process>
                """;

        String rewritten = fixer.fixStaleIds(1L, xml);

        assertThat(rewritten)
                .as("Process Global 必须按 globalActionNames 修到本 FU 新 id，否则设计器会显示 Bind to Node 且未选节点")
                .contains("name=\"globalActionIds\" value=\"[1200]\"")
                .doesNotContain("name=\"globalActionIds\" value=\"[50]\"")
                .as("没有名称映射的节点 actionIds 保持原样")
                .contains("name=\"actionIds\" value=\"[51]\"");
    }

    @Test
    void remapsUserTaskActionIdsByActionName() {
        when(actionDefinitionRepository.findByFunctionUnitId(1L)).thenReturn(List.of(
                ActionDefinition.builder().id(1201L).actionName("Approve").build()));

        String xml = """
                <bpmn:userTask id="task1">
                  <custom:property name="actionNames" value="[&quot;Approve&quot;]" />
                  <custom:property name="actionIds" value="[51]" />
                </bpmn:userTask>
                """;

        String rewritten = fixer.fixStaleIds(1L, xml);

        assertThat(rewritten).contains("name=\"actionIds\" value=\"[1201]\"");
    }
}
