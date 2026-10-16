package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.component.FunctionUnitAccessComponent;
import com.portal.component.ProcessComponent;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.RecordNoteDtos.*;
import com.portal.service.RecordNoteService.RecordNoteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordNoteUploadSourceResolverTest {
    final WorkflowEngineClient engine=mock(WorkflowEngineClient.class);
    final ProcessComponent processes=mock(ProcessComponent.class);
    final FunctionUnitAccessComponent access=mock(FunctionUnitAccessComponent.class);
    final RecordNoteUploadSourceResolver resolver=new RecordNoteUploadSourceResolver(engine,processes,access);
    NoteTarget target;
    @BeforeEach void setup() {
        target=NoteTarget.builder().targetType("TABLE").targetId("request").processInstanceId("request").build();
        when(processes.getProcessDetail("request")).thenReturn(ProcessInstanceInfo.builder()
                .id("request").startUserId("initiator").functionUnitCode("generic-fu").build());
    }
    void task(String request,String status) {
        when(engine.getTaskById("upload-task")).thenReturn(Optional.of(Map.of(
                "taskId","upload-task","processInstanceId",request,"taskName","<b>Handle</b>",
                "currentStepName","Credit Card Transaction","status",status)));
        target.setTaskId("upload-task");
    }
    @Test void taskSourceUsesActualTaskAndStepNames() {
        task("request","ACTIVE");
        var source=resolver.resolve("handler",target);
        assertEquals(UploadSourceKind.TASK,source.kind());
        assertEquals("upload-task",source.taskId());
        assertEquals("Credit Card Transaction / Handle",source.taskName());
    }
    @Test void anotherRequestTaskIsRejectedEvenForAdministrator() {
        task("another-request","ACTIVE");
        assertThrows(RecordNoteException.class,()->resolver.resolve("admin",target));
    }
    @Test void completedTaskRemainsAValidSourceForAnAlreadyAuthorizedAuditWriter() {
        task("request","COMPLETED");
        assertEquals(UploadSourceKind.TASK,resolver.resolve("authorized-auditor",target).kind());
    }
    @Test void missingTaskAndConflictingSurfaceAreRejected() {
        target.setTaskId("missing"); when(engine.getTaskById("missing")).thenReturn(Optional.empty());
        assertThrows(RecordNoteException.class,()->resolver.resolve("handler",target));
        target.setSourceView("AUDIT");
        assertThrows(RecordNoteException.class,()->resolver.resolve("handler",target));
    }
    @Test void initiatorAndAuditorAreDifferentSourcesIncludingInitiatorOpeningAudit() {
        target.setSourceView("REQUEST");
        assertEquals(UploadSourceKind.MY_REQUEST,resolver.resolve("initiator",target).kind());
        target.setSourceView("AUDIT");
        assertThrows(RecordNoteException.class,()->resolver.resolve("initiator",target));
        when(access.canAuditFunctionUnitAsActiveRole("initiator","generic-fu")).thenReturn(true);
        assertEquals(UploadSourceKind.AUDIT,resolver.resolve("initiator",target).kind());
        target.setSourceView("REQUEST");
        when(access.canAuditFunctionUnitAsActiveRole("auditor","generic-fu")).thenReturn(true);
        assertEquals(UploadSourceKind.AUDIT,resolver.resolve("auditor",target).kind());
    }
    @Test void auditHintCannotGrantPermissionOrInventSourceNames() {
        target.setSourceView("AUDIT");
        assertThrows(RecordNoteException.class,()->resolver.resolve("stranger",target));
        target.setSourceView("Fake Task Name");
        assertThrows(RecordNoteException.class,()->resolver.resolve("initiator",target));
    }
    @Test void oldClientWithoutSurfaceDoesNotInventMyRequestOrAuditOrigin() {
        assertNull(resolver.resolve("initiator",target));
        assertNull(resolver.resolve("auditor",target));
    }
    @Test void draftSourceIsNewRequest() {
        target.setTargetId("draft-123");
        assertEquals(UploadSourceKind.NEW_REQUEST,resolver.resolve("initiator",target).kind());
        target.setSourceView("AUDIT");
        assertThrows(RecordNoteException.class,()->resolver.resolve("initiator",target));
    }
}
