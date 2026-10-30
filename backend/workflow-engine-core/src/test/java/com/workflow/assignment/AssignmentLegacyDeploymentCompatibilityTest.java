package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl;
import com.workflow.client.AdminCenterClient;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentLegacyDeploymentCompatibilityTest {
    final RepositoryService repository=mock(RepositoryService.class);
    final AssignmentIdentityClient identities=mock(AssignmentIdentityClient.class);
    final AssignmentDeploymentCompiler compiler=new AssignmentDeploymentCompilerImpl(repository,identities,new ObjectMapper(),mock(AdminCenterClient.class));
    @Test void disabledLegacyDeploymentKeepsDynamicCallsAndItsOriginalXml() {
        String xml=xml("${selectedChild}");
        var compiled=compiler.compileDeployment(xml);
        assertFalse(compiled.tracked()); assertEquals(xml,compiled.xml()); verifyNoInteractions(identities);
    }
    @Test void disabledLegacyDeploymentKeepsItsExistingKeyBasedCallBehavior() {
        var query=mock(org.flowable.engine.repository.ProcessDefinitionQuery.class,RETURNS_SELF);
        var child=mock(org.flowable.engine.repository.ProcessDefinition.class);
        when(repository.createProcessDefinitionQuery()).thenReturn(query); when(query.list()).thenReturn(java.util.List.of(child));
        when(child.getId()).thenReturn("existing-child");
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("child"); model.addProcess(process);
        when(repository.getBpmnModel("existing-child")).thenReturn(model);
        String xml=xml("child-key"); var compiled=compiler.compileDeployment(xml);
        assertFalse(compiled.tracked()); assertEquals(xml,compiled.xml()); verifyNoInteractions(identities);
    }
    private String xml(String called) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:synthetic">
                <process id="legacy" isExecutable="true"><callActivity id="call" calledElement="%s"/></process></definitions>
                """.formatted(called);
    }
}
