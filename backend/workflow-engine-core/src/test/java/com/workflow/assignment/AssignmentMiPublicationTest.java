package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentMiPublicationTest {
    final RepositoryService repository=mock(RepositoryService.class);
    final AssignmentDeploymentCompiler compiler=new AssignmentDeploymentCompilerImpl(repository,
            mock(AssignmentIdentityClient.class),new ObjectMapper(),mock(com.workflow.client.AdminCenterClient.class));
    @ParameterizedTest @ValueSource(strings={"userTask","subProcess"})
    void scalarMiWithoutADeclaredRowSourceIsRejectedBeforeDeployment(String type) {
        assertThrows(IllegalArgumentException.class,()->compiler.compile(xml("<"+type+" id=\"mi\"><multiInstanceLoopCharacteristics flowable:collection=\"people\" flowable:elementVariable=\"person\"/></"+type+">")));
    }
    @Test void aRowSourceWithoutAnItemVariableIsRejectedBeforeDeployment() {
        assertThrows(IllegalArgumentException.class,()->compiler.compile(xml("""
                <userTask id="mi"><extensionElements><flowable:properties><flowable:property name="subTableName" value="renamed_rows"/>
                </flowable:properties></extensionElements><multiInstanceLoopCharacteristics flowable:collection="rows"/></userTask>
                """)));
    }
    @Test void anExplicitRowSourceAndDeclaredItemRemainSupported() {
        assertTrue(compiler.compileDeployment(xml("""
                <userTask id="mi"><extensionElements><flowable:properties><flowable:property name="subTableName" value="renamed_rows"/>
                </flowable:properties></extensionElements><multiInstanceLoopCharacteristics flowable:collection="rows" flowable:elementVariable="row"/></userTask>
                """)).tracked());
    }
    @Test void unsupportedMiInsideAnExistingFrozenCalledDefinitionIsAlsoRejected() {
        var model=new org.flowable.bpmn.model.BpmnModel();
        var process=new org.flowable.bpmn.model.Process(); process.setId("called"); model.addProcess(process);
        var subprocess=new org.flowable.bpmn.model.SubProcess(); subprocess.setId("nested_mi");
        var loop=new org.flowable.bpmn.model.MultiInstanceLoopCharacteristics();
        loop.setElementVariable("person"); loop.setInputDataItem("people"); subprocess.setLoopCharacteristics(loop);
        process.addFlowElement(subprocess); when(repository.getBpmnModel("frozen-called")).thenReturn(model);
        assertThrows(IllegalArgumentException.class,()->compiler.compile(xml("<callActivity id=\"call\" calledElement=\"frozen-called\" flowable:calledElementType=\"id\"/>")));
    }
    private String xml(String mi) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="synthetic"><userTask id="work"><extensionElements><flowable:properties>
                <flowable:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
                &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements></userTask>%s</process></definitions>
                """.formatted(mi);
    }
}
