package com.workflow.component;

import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.xml.stream.XMLInputFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BpmnActionParserPrecedenceTest {
    @Mock RepositoryService repositoryService;
    @InjectMocks BpmnActionParser parser;
    private static final String OPEN = "<b:definitions xmlns:b=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:c=\"http://workflow.platform/schema/custom\" xmlns:l=\"http://custom.bpmn.io/schema\" targetNamespace=\"precedence\"><b:process id=\"p\" isExecutable=\"true\">";
    private static String property(String prefix, String key, String value) {
        return "<" + prefix + ":properties><" + prefix + ":property name=\"" + key + "\" value='" + value + "'/></" + prefix + ":properties>";
    }
    private static String xml(String globals, String nodes) {
        return OPEN + "<b:extensionElements>" + globals + "</b:extensionElements><b:userTask id=\"a\"><b:extensionElements>" + nodes + "</b:extensionElements></b:userTask></b:process></b:definitions>";
    }
    private Task task() {
        Task task = mock(Task.class);
        when(task.getProcessDefinitionId()).thenReturn("pd:precedence");
        when(task.getTaskDefinitionKey()).thenReturn("a");
        return task;
    }
    private void source(String xml, boolean withModel) throws Exception {
        if (withModel) {
            var factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            var model = new BpmnXMLConverter().convertToBpmnModel(factory.createXMLStreamReader(new StringReader(xml)));
            when(repositoryService.getBpmnModel("pd:precedence")).thenReturn(model);
        }
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        lenient().when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        lenient().when(query.processDefinitionId("pd:precedence")).thenReturn(query);
        lenient().when(query.singleResult()).thenReturn(definition);
        lenient().when(definition.getId()).thenReturn("pd:precedence");
        lenient().when(definition.getDeploymentId()).thenReturn("dep:precedence");
        lenient().when(definition.getResourceName()).thenReturn("review.bpmn");
        lenient().when(repositoryService.getResourceAsStream("dep:precedence", "review.bpmn"))
                .thenAnswer(invocation -> new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void platformNamespaceWinsInTheModel() throws Exception { assertPlatform(true); }
    @Test void platformNamespaceWinsInXml() throws Exception { assertPlatform(false); }
    private void assertPlatform(boolean model) throws Exception {
        source(xml(property("l", "globalActionIds", "[48,12]") + property("c", "globalActionIds", "[35]"), property("c", "actionIds", "[22,12]")), model);
        assertThat(parser.extractActionIds(task())).containsExactly("22", "12", "35");
    }
    @Test void lastPlatformValueWinsInTheModel() throws Exception { assertLast(true); }
    @Test void lastPlatformValueWinsInXml() throws Exception { assertLast(false); }
    private void assertLast(boolean model) throws Exception {
        source(xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[12,22]") + property("c", "actionIds", "[35,12]")), model);
        assertThat(parser.extractActionIds(task())).containsExactly("35", "12", "48");
    }
    @Test void explicitEmptyModelListDoesNotReviveTheDeployedOldList() throws Exception {
        source(xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[12]")), false);
        var factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        var model = new BpmnXMLConverter().convertToBpmnModel(factory.createXMLStreamReader(new StringReader(xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[]")))));
        when(repositoryService.getBpmnModel("pd:precedence")).thenReturn(model);
        assertThat(parser.extractActionIds(task())).containsExactly("48");
    }
    @Test void emptyPlatformGlobalListDoesNotReviveLegacyGlobals() throws Exception {
        source(xml(property("l", "globalActionIds", "[48]") + property("c", "globalActionIds", "[]"), property("c", "actionIds", "[12]")), false);
        assertThat(parser.extractActionIds(task())).containsExactly("12");
    }
    @Test void malformedEffectiveValueDoesNotReturnOldActions() throws Exception {
        source(xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[12]") + property("c", "actionIds", "[bad,,id]")), false);
        assertThatThrownBy(() -> parser.extractActionIds(task())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void modelUsesXmlOrderWhenDirectAndContainerDuplicatesAreInterleaved() throws Exception {
        source(xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[12]")
                + "<c:property name=\"actionIds\" value=\"[22]\"/>" + property("c", "actionIds", "[35]")), true);
        assertThat(parser.extractActionIds(task())).containsExactly("35", "48");
    }
    @Test void valuesHaveTheSameEstablishedPrecedenceAsInTheDesigner() throws Exception {
        source(xml(property("c", "globalActionIds", "[48]"), "<c:properties><c:values name=\"actionIds\" value=\"[35,12]\"/><c:property name=\"actionIds\" value=\"[22]\"/></c:properties>"), true);
        assertThat(parser.extractActionIds(task())).containsExactly("35", "12", "48");
    }
    @Test void malformedModelValueIsNotMaskedByXmlFallback() throws Exception {
        source(xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[bad,,id]")), true);
        assertThatThrownBy(() -> parser.extractActionIds(task())).isInstanceOf(IllegalArgumentException.class);
        verify(repositoryService, never()).createProcessDefinitionQuery();
    }
    @Test void deployedXmlRejectsDoctypeBeforeResolvingEntities() throws Exception {
        source("<!DOCTYPE b:definitions SYSTEM 'file:///tmp/no-action-binding.dtd'>" + xml(property("c", "globalActionIds", "[48]"), property("c", "actionIds", "[12]")), false);
        assertThatThrownBy(() -> parser.extractActionIds(task())).isInstanceOf(IllegalStateException.class);
    }
}
