package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AssignmentDeploymentPreparationTest {
    final AssignmentDeploymentCompiler compiler=mock(AssignmentDeploymentCompiler.class);
    final AssignmentDeploymentPreparation preparation=new AssignmentDeploymentPreparation(compiler,new AssignmentUnavailableRuntime());

    @Test void incompleteRuntimeRejectsAnEnabledPolicyBeforeCompilingOrDeploying() {
        String xml=xml("""
                <userTask id="review"><extensionElements><properties><property name="assignmentExclusion"
                value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
                &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </properties></extensionElements></userTask>
                """);
        var failure=assertThrows(AssignmentFailure.class,()->preparation.prepare(xml));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
        verifyNoInteractions(compiler);
    }

    @Test void anIndirectTrackedCallCannotBypassTheReadinessGate() {
        when(compiler.compileDeployment(anyString())).thenReturn(new AssignmentDeploymentCompiler.Compilation("compiled",true));
        var failure=assertThrows(AssignmentFailure.class,()->preparation.prepare(xml("<callActivity id=\"child\"/>")));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
    }

    @Test void legacyDeploymentRemainsAvailableWhileReadinessIsFalse() {
        when(compiler.compileDeployment(anyString())).thenReturn(new AssignmentDeploymentCompiler.Compilation("compiled legacy",false));
        assertEquals("compiled legacy",preparation.prepare(xml("<userTask id=\"review\"/>")));
        assertFalse(new AssignmentUnavailableRuntime().capability().ready());
    }

    @Test void invalidCompiledMetadataKeepsItsTypedPolicyFailure() {
        when(compiler.compileDeployment(anyString())).thenThrow(new IllegalArgumentException("synthetic invalid contract"));
        var failure=assertThrows(AssignmentFailure.class,()->preparation.prepare(xml("<userTask id=\"review\"/>")));
        assertEquals("ASSIGNMENT_POLICY_INVALID",failure.getDetails().get("reason"));
    }

    private String xml(String nodes) {
        return "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" targetNamespace=\"urn:synthetic\">"
                +"<process id=\"synthetic\" isExecutable=\"true\">"+nodes+"</process></definitions>";
    }
}
