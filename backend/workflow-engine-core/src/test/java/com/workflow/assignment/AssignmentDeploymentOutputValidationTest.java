package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl;
import com.workflow.client.AdminCenterClient;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentDeploymentOutputValidationTest {
    final RepositoryService repository=mock(RepositoryService.class);
    final AssignmentDeploymentCompiler compiler=new AssignmentDeploymentCompilerImpl(repository,mock(AssignmentIdentityClient.class),
            new ObjectMapper(),mock(AdminCenterClient.class));

    @Test void malformedRowMappingsAreRejectedBeforeProtectedDeployment() {
        when(repository.getBpmnModel("frozen-child")).thenReturn(legacyChild());
        assertThrows(IllegalArgumentException.class,()->compiler.compile(xml("""
                <flowable:properties><flowable:property name="callOutputMapping"
                value="[{&quot;from&quot;:&quot;returned&quot;}]"/></flowable:properties>
                """)));
    }

    @Test void ordinaryLiteralOutputTargetsRemainSupported() {
        when(repository.getBpmnModel("frozen-child")).thenReturn(legacyChild());
        var compiled=compiler.compileDeployment(xml("<flowable:out source=\"returned\" target=\"renamed_destination\"/>"));
        assertTrue(compiled.tracked()); assertTrue(compiled.xml().contains("renamed_destination"));
    }

    @Test void malformedOutputInAnExistingCalledDefinitionIsAlsoRejectedForAProtectedCaller() {
        var child=legacyChild(); var nested=new CallActivity(); nested.setId("nested");
        nested.setCalledElement("frozen-grandchild"); nested.setCalledElementType("id");
        var properties=new ExtensionElement(); properties.setName("properties");
        var property=new ExtensionElement(); property.setName("property");
        for (var pair:java.util.Map.of("name","callOutputMapping","value","[{\"from\":\"returned\"}]").entrySet()) {
            var attribute=new ExtensionAttribute(); attribute.setName(pair.getKey()); attribute.setValue(pair.getValue()); property.addAttribute(attribute);
        }
        properties.addChildElement(property); nested.addExtensionElement(properties); child.getMainProcess().addFlowElement(nested);
        when(repository.getBpmnModel("frozen-child")).thenReturn(child);
        when(repository.getBpmnModel("frozen-grandchild")).thenReturn(legacyChild());
        assertThrows(IllegalArgumentException.class,()->compiler.compile(xml("")));
    }
    @Test void unrefreshableNativeExpressionInAnExistingCalledDefinitionIsRejectedForAProtectedCaller() {
        var child=legacyChild(); var task=new UserTask(); task.setId("nativeTask"); task.setAssignee("${bean.resolve()}");
        child.getMainProcess().addFlowElement(task); when(repository.getBpmnModel("frozen-child")).thenReturn(child);
        assertThrows(IllegalArgumentException.class,()->compiler.compile(xml("")));
    }
    @Test void exactNativeVariableSourceInCalledDefinitionRemainsSupported() {
        var child=legacyChild(); var task=new UserTask(); task.setId("nativeTask"); task.setAssignee("${renamed_reviewer}");
        child.getMainProcess().addFlowElement(task); when(repository.getBpmnModel("frozen-child")).thenReturn(child);
        assertTrue(compiler.compileDeployment(xml("")).tracked());
    }

    private BpmnModel legacyChild() {
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("child");
        model.addProcess(process); return model;
    }
    private String xml(String output) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:synthetic">
                <process id="synthetic" isExecutable="true"><userTask id="review"><extensionElements><flowable:properties>
                <flowable:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,
                &quot;excludePreviousHandlers&quot;:false,&quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements></userTask>
                <callActivity id="call" calledElement="frozen-child" flowable:calledElementType="id"><extensionElements>%s</extensionElements></callActivity>
                </process></definitions>
                """.formatted(output);
    }
}
