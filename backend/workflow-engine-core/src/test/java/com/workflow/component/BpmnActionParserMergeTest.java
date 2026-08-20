package com.workflow.component;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.task.api.Task;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BpmnActionParser merge regressions")
class BpmnActionParserMergeTest {

    @Mock
    private RepositoryService repositoryService;

    @InjectMocks
    private BpmnActionParser parser;

    @Test
    @DisplayName("sibling userTasks only merge their own actionIds with process Global")
    void siblingUserTasksDoNotLeakNodeActions() {
        stubXml("pd:sib", TWO_USER_TASKS_XML);
        when(repositoryService.getBpmnModel("pd:sib")).thenReturn(null);

        assertThat(parser.extractActionIds(task("pd:sib", "ut1"))).containsExactly("1223", "1225");
        assertThat(parser.extractActionIds(task("pd:sib", "ut2"))).containsExactly("1224", "1225");
    }

    @Test
    @DisplayName("complete BpmnModel is not overwritten by different XML action ids")
    void modelBindingsWinWhenBothSourcesPresent() {
        when(repositoryService.getBpmnModel("pd:win")).thenReturn(bpmnModelWithActions("[1223]", "[1225]"));

        assertThat(parser.extractActionIds(task("pd:win", "ut1"))).containsExactly("1223", "1225");
        verify(repositoryService, never()).createProcessDefinitionQuery();
    }

    @Test
    @DisplayName("model Global is kept when XML also has a different globalActionIds")
    void modelGlobalWinsOverXmlGlobalWhileNodeComesFromXml() {
        when(repositoryService.getBpmnModel("pd:inv")).thenReturn(bpmnModelWithActions(null, "[1225]"));
        stubXml("pd:inv", bpmnXml("[1223]", "[8888]"));

        assertThat(parser.extractActionIds(task("pd:inv", "ut1"))).containsExactly("1223", "1225");
    }

    @Test
    @DisplayName("quoted JSON action ids merge the same as numeric arrays")
    void quotedJsonActionIdsMerge() {
        stubXml("pd:q", QUOTED_JSON_XML);
        when(repositoryService.getBpmnModel("pd:q")).thenReturn(null);

        assertThat(parser.extractActionIds(task("pd:q", "ut1")))
                .containsExactly("node-save", "global-save");
        assertThat(parser.parseActionIds("[\"node-save\",\"global-save\"]"))
                .containsExactly("node-save", "global-save");
    }

    @Test
    @DisplayName("empty action arrays yield no buttons")
    void emptyArraysReturnNull() {
        stubXml("pd:empty", bpmnXml("[]", "[]"));
        when(repositoryService.getBpmnModel("pd:empty")).thenReturn(null);

        assertThat(parser.extractActionIds(task("pd:empty", "ut1"))).isNull();
    }

    @Test
    @DisplayName("missing process definition returns null instead of throwing")
    void missingProcessDefinitionReturnsNull() {
        when(repositoryService.getBpmnModel("pd:miss")).thenReturn(null);
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId("pd:miss")).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThat(parser.extractActionIds(task("pd:miss", "ut1"))).isNull();
    }

    @Test
    @DisplayName("BpmnModel lookup failure still merges node + Global from XML")
    void modelExceptionFallsBackToXmlMerge() {
        when(repositoryService.getBpmnModel("pd:boom")).thenThrow(new IllegalStateException("model unavailable"));
        stubXml("pd:boom", bpmnXml("[1223]", "[1225]"));

        assertThat(parser.extractActionIds(task("pd:boom", "ut1"))).containsExactly("1223", "1225");
    }

    @Test
    @DisplayName("bpmn: prefixed userTask and reversed name/value attributes still merge")
    void namespacedAndReversedAttributesMerge() {
        stubXml("pd:ns", NAMESPACED_REVERSED_XML);
        when(repositoryService.getBpmnModel("pd:ns")).thenReturn(null);

        assertThat(parser.extractActionIds(task("pd:ns", "ut1"))).containsExactly("1223", "1225");
    }

    private static final String QUOTED_JSON_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:custom="http://workflow.platform/schema/custom"
                         targetNamespace="http://example.org">
              <process id="p1" isExecutable="true">
                <extensionElements>
                  <custom:properties>
                    <custom:property name="globalActionIds" value='["global-save"]'/>
                  </custom:properties>
                </extensionElements>
                <userTask id="ut1" name="A">
                  <extensionElements>
                    <custom:properties>
                      <custom:property name="actionIds" value='["node-save"]'/>
                    </custom:properties>
                  </extensionElements>
                </userTask>
              </process>
            </definitions>
            """;

    private static final String TWO_USER_TASKS_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:custom="http://workflow.platform/schema/custom"
                         targetNamespace="http://example.org">
              <process id="p1" isExecutable="true">
                <extensionElements>
                  <custom:properties>
                    <custom:property name="globalActionIds" value="[1225]"/>
                  </custom:properties>
                </extensionElements>
                <userTask id="ut1" name="A">
                  <extensionElements>
                    <custom:properties>
                      <custom:property name="actionIds" value="[1223]"/>
                    </custom:properties>
                  </extensionElements>
                </userTask>
                <userTask id="ut2" name="B">
                  <extensionElements>
                    <custom:properties>
                      <custom:property name="actionIds" value="[1224]"/>
                    </custom:properties>
                  </extensionElements>
                </userTask>
              </process>
            </definitions>
            """;

    private static final String NAMESPACED_REVERSED_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:custom="http://workflow.platform/schema/custom"
                         targetNamespace="http://example.org">
              <bpmn:process id="p1" isExecutable="true">
                <bpmn:extensionElements>
                  <custom:property value="[1225]" name="globalActionIds"/>
                </bpmn:extensionElements>
                <bpmn:userTask id="ut1" name="A">
                  <bpmn:extensionElements>
                    <custom:property value="[1223]" name="actionIds"/>
                  </bpmn:extensionElements>
                </bpmn:userTask>
              </bpmn:process>
            </definitions>
            """;

    private static Task task(String processDefinitionId, String taskDefinitionKey) {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getProcessDefinitionId()).thenReturn(processDefinitionId);
        when(task.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        return task;
    }

    private void stubXml(String processDefinitionId, String xml) {
        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn(processDefinitionId);
        when(pd.getResourceName()).thenReturn("p.bpmn20.xml");
        when(pd.getDeploymentId()).thenReturn("d-" + processDefinitionId);
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId(processDefinitionId)).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(repositoryService.getResourceAsStream("d-" + processDefinitionId, "p.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bpmnXml(String nodeActionIds, String globalActionIds) {
        String globalBlock = globalActionIds == null ? "" : """
                          <extensionElements>
                            <custom:properties>
                              <custom:property name="globalActionIds" value="%s"/>
                            </custom:properties>
                          </extensionElements>
                """.formatted(globalActionIds);
        String nodeBlock = nodeActionIds == null ? "" : """
                          <extensionElements>
                            <custom:properties>
                              <custom:property name="actionIds" value="%s"/>
                            </custom:properties>
                          </extensionElements>
                """.formatted(nodeActionIds);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:custom="http://workflow.platform/schema/custom"
                             targetNamespace="http://example.org">
                  <process id="p1" isExecutable="true">
                %s                    <userTask id="ut1" name="A">
                %s                    </userTask>
                  </process>
                </definitions>
                """.formatted(globalBlock, nodeBlock);
    }

    private static BpmnModel bpmnModelWithActions(String nodeActionIds, String globalActionIds) {
        BpmnModel model = new BpmnModel();
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("p1");
        UserTask userTask = new UserTask();
        userTask.setId("ut1");
        if (nodeActionIds != null) {
            userTask.addExtensionElement(namedProperty("actionIds", nodeActionIds));
        }
        if (globalActionIds != null) {
            process.addExtensionElement(namedProperty("globalActionIds", globalActionIds));
        }
        process.addFlowElement(userTask);
        model.addProcess(process);
        return model;
    }

    private static ExtensionElement namedProperty(String name, String value) {
        ExtensionElement properties = new ExtensionElement();
        properties.setName("properties");
        ExtensionElement property = new ExtensionElement();
        property.setName("property");
        property.addAttribute(new ExtensionAttribute("name", name));
        property.addAttribute(new ExtensionAttribute("value", value));
        properties.addChildElement(property);
        return properties;
    }
}
