package com.workflow.component;

import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BpmnActionParser")
class BpmnActionParserTest {

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private BpmnActionParser parser;

    @Test
    @DisplayName("getUserTaskExtensionPropertyValue reads assigneeType from deployed BPMN XML (DOM)")
    void shouldReadAssigneeTypeFromXml() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:custom="http://workflow.platform/schema/custom"
                             targetNamespace="http://example.org">
                  <process id="p1" isExecutable="true">
                    <userTask id="Task_SubmitApplication" name="Submit">
                      <extensionElements>
                        <custom:properties>
                          <custom:property name="assigneeType" value="INITIATOR"/>
                        </custom:properties>
                      </extensionElements>
                    </userTask>
                  </process>
                </definitions>
                """;

        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getResourceName()).thenReturn("proc.bpmn20.xml");
        when(pd.getDeploymentId()).thenReturn("dep-1");

        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId("procDef:1:abc")).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(repositoryService.getResourceAsStream("dep-1", "proc.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        assertThat(parser.getUserTaskExtensionPropertyValue("procDef:1:abc", "Task_SubmitApplication", "assigneeType"))
                .isEqualTo("INITIATOR");
    }

    @Test
    @DisplayName("getUserTaskExtensionPropertyValue returns null when process definition missing")
    void shouldReturnNullWhenNoDefinition() {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId(anyString())).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThat(parser.getUserTaskExtensionPropertyValue("x", "Task_A", "assigneeType")).isNull();
    }

    @Test
    @DisplayName("extractActionIds falls back to XML when model has no actionIds")
    void extractActionIdsUsesXmlFallback() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="http://example.org">
                  <process id="p1" isExecutable="true">
                    <userTask id="ut1" name="A">
                      <extensionElements>
                        <properties>
                          <property name="actionIds" value="a1,a2"/>
                        </properties>
                      </extensionElements>
                    </userTask>
                  </process>
                </definitions>
                """;

        org.flowable.task.api.Task task = org.mockito.Mockito.mock(org.flowable.task.api.Task.class);
        when(task.getProcessDefinitionId()).thenReturn("pd:1");
        when(task.getTaskDefinitionKey()).thenReturn("ut1");

        when(repositoryService.getBpmnModel("pd:1")).thenReturn(null);

        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getResourceName()).thenReturn("p.bpmn20.xml");
        when(pd.getDeploymentId()).thenReturn("d1");
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId("pd:1")).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(repositoryService.getResourceAsStream("d1", "p.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

        List<String> ids = parser.extractActionIds(task);
        assertThat(ids).containsExactly("a1", "a2");
    }
}
