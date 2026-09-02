package com.portal.component;

import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.RecordNoteDtos.AttachmentInfo;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.enums.ChangeType;
import com.portal.service.RecordNoteService;
import com.portal.service.UserDisplayNameResolver;
import com.portal.util.RecordNoteAuditSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Record Note mutations must land in the instance's change history — the Audit History
 * panel is the only place a reviewer can see that a note was added, edited or removed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecordNoteAuditTest {

    private static final String INSTANCE_ID = "proc-1";
    private static final String ROW_ID = "row-77";

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

    @InjectMocks
    private RecordNoteComponent component;

    private final ProcessInstanceInfo instance = new ProcessInstanceInfo();

    @BeforeEach
    void setUp() {
        instance.setId(INSTANCE_ID);
        // The write gate resolves the audit grant by function unit code, so the fixture must carry
        // one for any test that actually creates a note.
        instance.setFunctionUnitCode("FU_DEMO");
    }

    private NoteTarget tableTarget() {
        return NoteTarget.builder()
                .targetType(RecordNote.TARGET_TABLE)
                .targetId(INSTANCE_ID)
                .tableKind("DW")
                .tableId("42")
                .functionUnitId("fu-7")
                .build();
    }

    private NoteTarget rowTarget() {
        return NoteTarget.builder()
                .targetType(RecordNote.TARGET_RECORD)
                .targetId(ROW_ID)
                .tableKind("DW")
                .tableId("42")
                .functionUnitId("fu-7")
                .build();
    }

    private RecordNote storedNote(String targetType, String targetId) {
        return RecordNote.builder()
                .id("note-1")
                .targetType(targetType)
                .targetId(targetId)
                .tableKind("DW")
                .tableId("42")
                .functionUnitId("fu-7")
                .noteType(RecordNote.TYPE_COMMENT)
                .subject("Subject")
                .bodyText("Original body")
                .createdBy("u1")
                .build();
    }

    @Test
    void tableScopeCreateAuditsAgainstTheTargetInstance() {
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(true);
        // Writing a note needs an audit grant on the active role; these tests are about where the
        // audit row is anchored, so the writer simply holds one.
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(eq("u1"), any())).thenReturn(true);
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT)
                        .bodyText("Hello reviewer").build());

        component.createComment("u1", tableTarget(), null, "<p>Hello reviewer</p>", List.of(), List.of(), null, null);

        verify(changeHistoryComponent).recordNoteChange(
                eq(INSTANCE_ID), eq("u1"), eq(ChangeType.RECORD_NOTE_ADD),
                isNull(), isNull(), eq("Hello reviewer"));
    }

    @Test
    void recordScopeCreateAnchorsOnTheCallerSuppliedInstanceAndKeepsTheRowId() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(eq("u1"), any())).thenReturn(true);
        // Row-id targets carry no resolvable instance, so note access adds no gate of its own.
        MockMultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_ATTACHMENT)
                        .fileName("report.pdf").build());

        component.createComment("u1", rowTarget(), null, null, List.of(file), List.of(), INSTANCE_ID, null);

        verify(changeHistoryComponent).recordNoteChange(
                eq(INSTANCE_ID), eq("u1"), eq(ChangeType.RECORD_NOTE_ADD),
                eq(ROW_ID), isNull(), eq("report.pdf"));
    }

    /**
     * A non-participant naming someone else's instance is refused outright. Previously the write
     * went through and only the audit entry was skipped, which left an unattributable note behind.
     */
    @Test
    void recordScopeCreateIsRejectedWhenTheCallerIsNotAParticipantOfTheClaimedInstance() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(false);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);

        assertThatThrownBy(() ->
                component.createComment("u1", rowTarget(), null, "<p>hi</p>", List.of(), List.of(), INSTANCE_ID, null))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("not a participant");

        verify(recordNoteService, never())
                .createComment(any(), any(), any(), any(), any(), anyString(), any());
        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    /**
     * A participant of the hosting request reads row notes even with NO function unit role.
     * That is the reported bug: an MI assignee holding HMDC_Assign_Role on a unit that only
     * lists HMDC_Index_Role was refused the notes on the row she was working.
     */
    @Test
    void rowScopeNotesAreReadableByAParticipantWithoutFunctionUnitRoleAccess() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(true);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);
        when(recordNoteService.list(any(), eq(0), eq(5), eq("u1"))).thenReturn(null);

        component.list("u1", rowTarget(), 0, 5, INSTANCE_ID);

        verify(recordNoteService).list(any(), eq(0), eq(5), eq("u1"));
        // Function unit roles must play no part in note visibility.
        verify(functionUnitAccessComponent, never()).canAccessFunctionUnit(anyString(), anyString());
    }

    /**
     * Row ids are predictable business keys (ATM-DC-PW-TRANS-000004). A non-participant who
     * guesses one must not be able to read its notes by naming the hosting instance.
     */
    @Test
    void rowScopeNotesRejectANonParticipantOfTheHostingInstance() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(false);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);

        assertThatThrownBy(() -> component.list("u1", rowTarget(), 0, 5, INSTANCE_ID))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("not a participant");

        verify(recordNoteService, never()).list(any(), anyInt(), anyInt(), anyString());
    }

    /** Without a hosting instance a row target cannot be authorized at all — deny, never fall through. */
    @Test
    void rowScopeNotesRejectWhenNoHostingInstanceIsSupplied() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);

        assertThatThrownBy(() -> component.list("u1", rowTarget(), 0, 5, null))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("Cannot resolve the request");

        verify(recordNoteService, never()).list(any(), anyInt(), anyInt(), anyString());
    }

    /** A claimed instance that does not exist must not authorize anything either. */
    @Test
    void rowScopeNotesRejectAnUnknownClaimedInstance() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail("no-such-instance")).thenReturn(null);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);

        assertThatThrownBy(() -> component.list("u1", rowTarget(), 0, 5, "no-such-instance"))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("Cannot resolve the request");

        verify(recordNoteService, never()).list(any(), anyInt(), anyInt(), anyString());
    }

    /** Instance-scope notes keep their own participant gate, unaffected by the row-scope anchor. */
    @Test
    void instanceScopeNotesStillRejectNonParticipants() {
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(false);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);

        assertThatThrownBy(() -> component.list("u1", tableTarget(), 0, 5, null))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("not a participant");

        verify(recordNoteService, never()).list(any(), anyInt(), anyInt(), anyString());
    }

    /** New-Request drafts have no instance yet; the author must still reach their own notes. */
    @Test
    void draftScopeNotesAreReadableBeforeTheRequestStarts() {
        NoteTarget draft = NoteTarget.builder()
                .targetType(RecordNote.TARGET_TABLE).targetId("draft-abc")
                .tableKind("DW").tableId("42").functionUnitId("fu-7").build();
        when(processComponent.getProcessDetail("draft-abc")).thenReturn(null);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);
        when(recordNoteService.list(any(), eq(0), eq(5), eq("u1"))).thenReturn(null);

        component.list("u1", draft, 0, 5, null);

        verify(recordNoteService).list(any(), eq(0), eq(5), eq("u1"));
    }

    @Test
    void draftCreateWithoutAnInstanceIsNotAudited() {
        NoteTarget draft = NoteTarget.builder()
                .targetType(RecordNote.TARGET_TABLE).targetId("draft-abc")
                .tableKind("DW").tableId("42").functionUnitId("fu-7").build();
        when(processComponent.getProcessDetail("draft-abc")).thenReturn(null);
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT).bodyText("hi").build());

        component.createComment("u1", draft, null, "<p>hi</p>", List.of(), List.of(), null, null);

        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    /**
     * Notes are a record of what was said at a point in time; editing one would
     * rewrite the history a request is reviewed against. Even the author is refused.
     */
    @Test
    void updateIsRefusedForEveryoneAndWritesNoAudit() {
        when(recordNoteService.getLive("note-1")).thenReturn(storedNote(RecordNote.TARGET_TABLE, INSTANCE_ID));

        assertThatThrownBy(() -> component.update("u1", "note-1", "Subject", "<p>Revised body</p>", null))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("cannot be edited");

        verify(recordNoteService, never()).update(any(), any(), any(), any());
        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void deleteIsRefusedForEveryoneAndWritesNoAudit() {
        when(recordNoteService.getLive("note-1")).thenReturn(storedNote(RecordNote.TARGET_TABLE, INSTANCE_ID));

        assertThatThrownBy(() -> component.delete("u1", "note-1", null))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("cannot be deleted");

        verify(recordNoteService, never()).softDelete(any(), any());
        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    /** A system administrator gets no exemption from immutability either. */
    @Test
    void deleteIsRefusedEvenForSystemAdministrator() {
        when(recordNoteService.getLive("note-1")).thenReturn(storedNote(RecordNote.TARGET_TABLE, INSTANCE_ID));

        assertThatThrownBy(() -> component.delete("admin", "note-1", null))
                .isInstanceOf(RecordNoteService.RecordNoteException.class)
                .hasMessageContaining("cannot be deleted");

        verify(recordNoteService, never()).softDelete(any(), any());
    }

    @Test
    void inlineImageUploadsAreNotAuditedSeparately() {
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAuditProcessDetail(eq("u1"), any())).thenReturn(true);
        when(functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(eq("u1"), any())).thenReturn(true);
        MockMultipartFile img = new MockMultipartFile(
                "file", "shot.png", "image/png", "x".getBytes(StandardCharsets.UTF_8));
        when(recordNoteService.createAttachment(any(), any(), anyBoolean(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n2").noteType(RecordNote.TYPE_ATTACHMENT)
                        .fileName("shot.png").build());

        component.createInlineImage("u1", tableTarget(), img, INSTANCE_ID, null);

        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void summaryOfAnAttachmentOnlyCreateListsEveryUploadedFile() {
        NoteItem first = NoteItem.builder().id("n1").noteType(RecordNote.TYPE_ATTACHMENT)
                .fileName("a.pdf").build();

        assertThat(RecordNoteAuditSummary.created(first, List.of("a.pdf", "b.png")))
                .isEqualTo("a.pdf, b.png");
    }

    @Test
    void summaryIgnoresInlineImagesButKeepsRealAttachments() {
        NoteItem item = NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT)
                .bodyText("see attached")
                .attachments(List.of(
                        AttachmentInfo.builder().id("a1").fileName("inline.png").isInlineImage(true).build(),
                        AttachmentInfo.builder().id("a2").fileName("spec.docx").isInlineImage(false).build()))
                .build();

        assertThat(RecordNoteAuditSummary.created(item, List.of()))
                .isEqualTo("see attached · spec.docx");
    }
}
