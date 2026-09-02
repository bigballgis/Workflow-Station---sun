package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.service.RecordNoteService;
import com.portal.service.RecordNoteService.RecordNoteException;
import com.portal.service.UserDisplayNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading a request and commenting on it are different acts, so notes gate them differently.
 * Anyone who can open the request reads its notes; adding one is gated by the surface it is
 * written from.
 *
 * <p><b>Request form</b> — the note is an audit opinion, so it needs an audit grant held by the
 * role the user is currently working as (or SYS_ADMIN). Participation grants nothing: an initiator
 * or assignee whose active role holds no audit grant reads the notes and may not add one.
 *
 * <p><b>To Do form</b> — the note is a comment by whoever works the task, so holding the task is
 * the whole qualification and no audit grant is required. The claim is verified rather than
 * trusted; {@link #aTaskOnAnotherProcessDoesNotUnlockTheseNotes()} is the case that matters.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordNoteWriteAccessTest {

    private static final String INSTANCE_ID = "proc-1";
    private static final String FU_CODE = "FU_DEMO";
    private static final String USER = "u1";
    private static final String TASK_ID = "task-9";

    @Mock
    private RecordNoteService recordNoteService;
    @Mock
    private ProcessComponent processComponent;
    @Mock
    private FunctionUnitAccessComponent functionUnitAccessComponent;
    @Mock
    private UserDisplayNameResolver userDisplayNameResolver;
    @Mock
    private ChangeHistoryComponent changeHistoryComponent;
    @Mock
    private WorkflowEngineClient workflowEngineClient;
    @Mock
    private TaskPermissionEvaluator taskPermissionEvaluator;

    @InjectMocks
    private RecordNoteComponent component;

    private final ProcessInstanceInfo instance = new ProcessInstanceInfo();

    /** Set by the To Do tests; null on the request-form tests, which name no task. */
    private String taskId;

    @BeforeEach
    void setUp() {
        taskId = null;
        instance.setId(INSTANCE_ID);
        instance.setFunctionUnitCode(FU_CODE);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        // Read access: everyone in these tests can open the request and see its notes.
        when(processComponent.canAuditProcessDetail(eq(USER), any())).thenReturn(true);
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT).build());
    }

    /**
     * A live engine task on {@code onInstance}, as the raw engine payload the component maps.
     * {@code processInstanceId} is what the cross-check compares, so it is the interesting field.
     */
    private void engineTask(String id, String onInstance) {
        when(workflowEngineClient.getTaskById(id)).thenReturn(Optional.of(Map.of(
                "taskId", id,
                "processInstanceId", onInstance,
                "name", "Review")));
    }

    private NoteTarget target() {
        return NoteTarget.builder()
                .targetType(RecordNote.TARGET_TABLE)
                .targetId(INSTANCE_ID)
                .tableKind("DW")
                .tableId("42")
                .functionUnitId("fu-7")
                .build();
    }

    private void createNote() {
        component.createComment(USER, target(), null, "<p>note</p>", List.of(), List.of(), null, taskId);
    }

    /**
     * Participation grants nothing: writing a note is done <em>as</em> a role, so the audit
     * configuration decides and not who happens to be working the request. This is the reported
     * case — a Department Manager on the Submit task of a unit that grants audit to another role
     * only, who could previously edit notes through the participant bypass.
     */
    @Test
    void participantWithoutAnAuditGrantMayNotAddANote() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote)
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("not granted audit access");

        verify(recordNoteService, never())
                .createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /** Participation is neither required nor sufficient — the audit grant alone decides. */
    @Test
    void participantWithAnAuditGrantMayAddANote() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(true);

        createNote();

        verify(recordNoteService).createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void activeRoleWithAnAuditGrantMayAddANote() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(true);

        createNote();

        verify(recordNoteService).createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /**
     * The behaviour change. This user can still open the request and read its notes — the read gate
     * above passes — but writing is refused because their active role holds no audit grant.
     */
    @Test
    void viewOnlyReaderWithoutAnAuditGrantMayNotAddANote() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote)
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("not granted audit access");

        verify(recordNoteService, never())
                .createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /**
     * Holding an audit-granted role that is not the one selected must not be enough: writing a note
     * is done *as* a role, so it follows the role the user actually switched to. This is what
     * separates the new predicate from {@code canAuditFunctionUnit}, which spans all roles.
     */
    @Test
    void anAuditGrantOnANonActiveRoleIsNotEnough() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnit(USER, FU_CODE)).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote).isInstanceOf(RecordNoteException.class);
    }

    @Test
    void systemAdministratorMayAlwaysAddANote() {
        when(functionUnitAccessComponent.isSystemAdministrator(USER)).thenReturn(true);

        createNote();

        verify(recordNoteService).createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /** Inline images are part of composing a note, so they follow the same rule. */
    @Test
    void inlineImageUploadFollowsTheWriteGate() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(() -> component.createInlineImage(USER, target(), null, null, null))
                .isInstanceOf(RecordNoteException.class);
    }

    /** Drives the panel's Add button, so it must agree with what a write would actually do. */
    @Test
    void canAddNoteMirrorsTheWriteGate() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);
        assertThat(component.canAddNote(USER, target(), null, null)).isFalse();

        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(true);
        assertThat(component.canAddNote(USER, target(), null, null)).isTrue();
    }

    // ---- To Do form: the task's handler comments without an audit grant ----

    /**
     * The To Do case. This user works the task and their active role holds no audit grant on the
     * function unit — under the audit-only gate they were refused, which is what made task comments
     * impossible for anyone but auditors.
     */
    @Test
    void theHolderOfTheTaskMayCommentWithoutAnAuditGrant() {
        taskId = TASK_ID;
        engineTask(TASK_ID, INSTANCE_ID);
        when(taskPermissionEvaluator.canProcessTask(any(), eq(USER), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        createNote();

        verify(recordNoteService).createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /**
     * Naming a task the user does not hold is no better than naming none: it is the engine's
     * verdict that authorizes, never the id the client sent.
     */
    @Test
    void namingATaskTheUserDoesNotHoldGrantsNothing() {
        taskId = TASK_ID;
        engineTask(TASK_ID, INSTANCE_ID);
        when(taskPermissionEvaluator.canProcessTask(any(), eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote)
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("not granted audit access");

        verify(recordNoteService, never())
                .createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /**
     * The cross-check that keeps the To Do path from becoming a way around the audit gate: holding
     * a task on some <em>other</em> request must not unlock note-writing on this one. Without it,
     * anyone with a task anywhere could write audit notes on every request in the system.
     */
    @Test
    void aTaskOnAnotherProcessDoesNotUnlockTheseNotes() {
        taskId = TASK_ID;
        engineTask(TASK_ID, "some-other-process");
        // The user genuinely holds that task — it is simply not a task on this request.
        when(taskPermissionEvaluator.canProcessTask(any(), eq(USER), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote)
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("not granted audit access");

        verify(recordNoteService, never())
                .createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /** An id that resolves to no task authorizes nothing; the audit gate still decides. */
    @Test
    void anUnresolvableTaskIdFallsThroughToTheAuditGate() {
        taskId = TASK_ID;
        when(workflowEngineClient.getTaskById(TASK_ID)).thenReturn(Optional.empty());
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote).isInstanceOf(RecordNoteException.class);

        verify(recordNoteService, never())
                .createComment(any(), any(), any(), any(), any(), anyString(), any());
    }

    /**
     * An engine outage must not become an authorization. The probe swallows the failure so the
     * request-form path still works, but it may never answer "allowed" on the strength of it.
     */
    @Test
    void anEngineFailureDoesNotAuthorize() {
        taskId = TASK_ID;
        when(workflowEngineClient.getTaskById(TASK_ID)).thenThrow(new RuntimeException("engine down"));
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        assertThatThrownBy(this::createNote).isInstanceOf(RecordNoteException.class);
    }

    /** The Add button on a task form must agree with what the write would decide. */
    @Test
    void canAddNoteReflectsTheTaskGrant() {
        engineTask(TASK_ID, INSTANCE_ID);
        when(taskPermissionEvaluator.canProcessTask(any(), eq(USER), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        // Same user, same request: writable from the task form, refused from the request form.
        assertThat(component.canAddNote(USER, target(), null, TASK_ID)).isTrue();
        assertThat(component.canAddNote(USER, target(), null, null)).isFalse();
    }

    /** Reading is unaffected: the tightening applies to writing only. */
    @Test
    void readingNotesStaysOpenToAnyoneWhoCanOpenTheRequest() {
        when(processComponent.isProcessParticipant(eq(USER), any())).thenReturn(false);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(USER, FU_CODE)).thenReturn(false);

        component.list(USER, target(), 0, 5, null);

        verify(recordNoteService).list(any(), eq(0), eq(5), eq(USER));
    }
}
