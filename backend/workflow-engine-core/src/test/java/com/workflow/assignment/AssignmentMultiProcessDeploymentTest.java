package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl;
import com.workflow.client.AdminCenterClient;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A compiler scope covers bytes, but must not authorize an uncompiled second process within those bytes. */
class AssignmentMultiProcessDeploymentTest {
    final AssignmentDeploymentCompiler compiler=new AssignmentDeploymentCompilerImpl(mock(RepositoryService.class),
            mock(AssignmentIdentityClient.class),new ObjectMapper(),mock(AdminCenterClient.class));

    @Test void protectedSecondaryProcessCannotBorrowTheMainProcessesCompiledContract() {
        String policy="""
                <userTask id="secondary_work"><extensionElements><flowable:properties>
                <flowable:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,
                &quot;excludePreviousHandlers&quot;:false,&quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements></userTask>
                """;
        assertThrows(IllegalArgumentException.class,()->compiler.compileDeployment(xml(policy)));
    }

    @Test void portableSecondaryProcessCannotSupplyServerOwnedMetadataEvenWhenPoliciesAreOff() {
        String reserved="""
                <extensionElements><flowable:properties>
                <flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/>
                <flowable:property name="assignmentRuntimeUsers" value="{}"/>
                </flowable:properties></extensionElements>
                """;
        assertThrows(IllegalArgumentException.class,()->compiler.compileDeployment(xml(reserved)));
    }

    @Test void explicitlyUnprotectedMultiProcessResourceKeepsItsExistingDeploymentSemantics() {
        String xml=xml("<userTask id=\"secondary_work\"/>");
        var compiled=compiler.compileDeployment(xml);
        assertFalse(compiled.tracked());
        assertEquals(xml,compiled.xml());
    }

    private String xml(String secondary) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:synthetic">
                <process id="primary" isExecutable="true"><userTask id="primary_work"/></process>
                <process id="secondary" isExecutable="true">%s</process></definitions>
                """.formatted(secondary);
    }
}
