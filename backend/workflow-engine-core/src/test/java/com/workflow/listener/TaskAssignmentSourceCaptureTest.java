package com.workflow.listener;

import com.workflow.assignment.AssignmentCreationBuffer;
import com.workflow.assignment.AssignmentSourceSpec;
import com.workflow.component.BpmnActionParser;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskAssignmentSourceCaptureTest {
    final BpmnActionParser parser=mock(BpmnActionParser.class);
    final Task task=mock(Task.class);
    @BeforeEach void scope() {
        when(task.getId()).thenReturn("task"); when(task.getProcessDefinitionId()).thenReturn("frozen"); when(task.getTaskDefinitionKey()).thenReturn("node");
        TransactionSynchronizationManager.setActualTransactionActive(true); TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void cleanup() {
        for (var synchronization:TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization(); TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    @Test void preassignedMiKeepsConfiguredRowSourceInsteadOfFreezingTheEvaluatedUser() {
        AssignmentCreationBuffer.begin("task");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeType")).thenReturn("ELEMENT_VARIABLE");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeField")).thenReturn("reviewer_code");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","roleField")).thenReturn("role_codes");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeMode")).thenReturn("user");
        TaskAssignmentSourceCapture.preassigned(parser,task);
        var source=AssignmentCreationBuffer.source("task",List.of("evaluated-user"),false);
        assertEquals(AssignmentSourceSpec.Kind.ROW,source.kind()); assertEquals("reviewer_code",source.assigneeField()); assertNull(source.roleField());
        AssignmentCreationBuffer.finish("task",Set.of());
    }
    @Test void legacyTaskDoesNotReadNewRuntimeMetadata() {
        TaskAssignmentSourceCapture.preassigned(parser,task); verifyNoInteractions(parser);
    }
    @Test void unrelatedNativeAssignmentKeepsItsOriginalSource() {
        AssignmentCreationBuffer.begin("task");
        TaskAssignmentSourceCapture.preassigned(parser,task);
        assertEquals(AssignmentSourceSpec.Kind.FIXED,AssignmentCreationBuffer.source("task",List.of("native"),false).kind());
        AssignmentCreationBuffer.finish("task",Set.of());
    }
    @Test void preassignedInitiatorKeepsItsConfiguredSourceInsteadOfFreezingAnEvaluatedBusinessValue() {
        AssignmentCreationBuffer.begin("task");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeType")).thenReturn("INITIATOR");
        TaskAssignmentSourceCapture.preassigned(parser,task);
        var source=AssignmentCreationBuffer.source("task",List.of("forged-evaluated-user"),false);
        assertEquals(AssignmentSourceSpec.Kind.RESOLVER,source.kind()); assertEquals("INITIATOR",source.type());
        AssignmentCreationBuffer.finish("task",Set.of());
    }
    @Test void preassignedVariableKeepsItsConfiguredFieldForLaterQualification() {
        AssignmentCreationBuffer.begin("task");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeType")).thenReturn("ASSIGNEE_FROM_VARIABLE");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeVariable")).thenReturn("renamed_reviewer");
        TaskAssignmentSourceCapture.preassigned(parser,task);
        var source=AssignmentCreationBuffer.source("task",List.of("old-evaluated-user"),false);
        assertEquals(AssignmentSourceSpec.Kind.VARIABLE,source.kind()); assertEquals("renamed_reviewer",source.variable());
        AssignmentCreationBuffer.finish("task",Set.of());
    }
    @Test void preassignedManualKeepsItsConfiguredVariablesForLaterQualification() {
        AssignmentCreationBuffer.begin("task");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","assigneeType")).thenReturn("MANUAL_ASSIGN");
        when(parser.getUserTaskExtensionPropertyValue("frozen","node","manualAssignVariable")).thenReturn("selected_processor");
        TaskAssignmentSourceCapture.preassigned(parser,task);
        var source=AssignmentCreationBuffer.source("task",List.of("old-evaluated-user"),false);
        assertEquals(AssignmentSourceSpec.Kind.MANUAL,source.kind()); assertEquals("selected_processor",source.manualUserVariable());
        AssignmentCreationBuffer.finish("task",Set.of());
    }
}
