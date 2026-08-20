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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        // Production caches deployed XML by ProcessDefinition.getId(); a null id would NPE inside
        // ConcurrentHashMap.get and be swallowed as "no XML".
        when(pd.getId()).thenReturn("procDef:1:abc");
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
        when(pd.getId()).thenReturn("pd:1");
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

    @Test
    @DisplayName("extractActionIds merges XML node actionIds with process globalActionIds")
    void extractActionIdsMergesNodeAndGlobalFromXml() {
        stubXml("pd:mcy", bpmnXml("[1223]", "[1225]"));
        when(repositoryService.getBpmnModel("pd:mcy")).thenReturn(null);

        List<String> ids = parser.extractActionIds(task("pd:mcy", "ut1"));
        assertThat(ids).containsExactly("1223", "1225");
    }

    @Test
    @DisplayName("extractActionIds returns only globalActionIds when the userTask has none")
    void extractActionIdsUsesGlobalWhenNodeMissing() {
        stubXml("pd:guide", bpmnXml(null, "[1249,1250]"));
        when(repositoryService.getBpmnModel("pd:guide")).thenReturn(null);

        List<String> ids = parser.extractActionIds(task("pd:guide", "ut1"));
        assertThat(ids).containsExactly("1249", "1250");
    }

    @Test
    @DisplayName("extractActionIds keeps node order and drops Global IDs already on the node")
    void extractActionIdsDedupesOverlapKeepingNodeFirst() {
        stubXml("pd:overlap", bpmnXml("[1223,1225]", "[1225,1249]"));
        when(repositoryService.getBpmnModel("pd:overlap")).thenReturn(null);

        List<String> ids = parser.extractActionIds(task("pd:overlap", "ut1"));
        assertThat(ids).containsExactly("1223", "1225", "1249");
    }

    @Test
    @DisplayName("extractActionIds merges node + Global from the in-memory BpmnModel")
    void extractActionIdsMergesFromBpmnModel() {
        BpmnModel model = bpmnModelWithActions("[1223]", "[1225]");
        when(repositoryService.getBpmnModel("pd:model")).thenReturn(model);

        List<String> ids = parser.extractActionIds(task("pd:model", "ut1"));
        assertThat(ids).containsExactly("1223", "1225");
        verify(repositoryService, never()).createProcessDefinitionQuery();
    }

    @Test
    @DisplayName("extractActionIds still reads XML Global when the model only has node actionIds")
    void extractActionIdsMergesModelNodeWithXmlGlobal() {
        BpmnModel model = bpmnModelWithActions("[1223]", null);
        when(repositoryService.getBpmnModel("pd:mixed")).thenReturn(model);
        stubXml("pd:mixed", bpmnXml("[1223]", "[1225]"));

        List<String> ids = parser.extractActionIds(task("pd:mixed", "ut1"));
        assertThat(ids).containsExactly("1223", "1225");
    }

    @Test
    @DisplayName("mergeActionIds appends Global after node and skips blanks")
    void mergeActionIdsPreservesOrderAndSkipsBlank() {
        assertThat(parser.mergeActionIds(List.of("1223"), List.of("1225")))
                .containsExactly("1223", "1225");
        assertThat(parser.mergeActionIds(List.of("1223", " "), List.of("1223", "1225")))
                .containsExactly("1223", "1225");
        assertThat(parser.mergeActionIds(null, null)).isNull();
    }

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
