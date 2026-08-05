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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
        when(processComponent.canAccessProcessDetail(eq("u1"), any())).thenReturn(true);
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT)
                        .bodyText("Hello reviewer").build());

        component.createComment("u1", tableTarget(), null, "<p>Hello reviewer</p>", List.of(), List.of(), null);

        verify(changeHistoryComponent).recordNoteChange(
                eq(INSTANCE_ID), eq("u1"), eq(ChangeType.RECORD_NOTE_ADD),
                isNull(), isNull(), eq("Hello reviewer"));
    }

    @Test
    void recordScopeCreateAnchorsOnTheCallerSuppliedInstanceAndKeepsTheRowId() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAccessProcessDetail(eq("u1"), any())).thenReturn(true);
        // Row-id targets fall back to function-unit access for the note itself.
        when(functionUnitAccessComponent.canAccessFunctionUnit("u1", "fu-7")).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "x".getBytes(StandardCharsets.UTF_8));
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_ATTACHMENT)
                        .fileName("report.pdf").build());

        component.createComment("u1", rowTarget(), null, null, List.of(file), List.of(), INSTANCE_ID);

        verify(changeHistoryComponent).recordNoteChange(
                eq(INSTANCE_ID), eq("u1"), eq(ChangeType.RECORD_NOTE_ADD),
                eq(ROW_ID), isNull(), eq("report.pdf"));
    }

    @Test
    void recordScopeCreateSkipsAuditWhenTheCallerIsNotAParticipantOfTheClaimedInstance() {
        when(processComponent.getProcessDetail(ROW_ID)).thenReturn(null);
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAccessProcessDetail(eq("u1"), any())).thenReturn(false);
        when(functionUnitAccessComponent.isSystemAdministrator("u1")).thenReturn(false);
        when(functionUnitAccessComponent.canAccessFunctionUnit("u1", "fu-7")).thenReturn(true);
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT).bodyText("hi").build());

        component.createComment("u1", rowTarget(), null, "<p>hi</p>", List.of(), List.of(), INSTANCE_ID);

        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void draftCreateWithoutAnInstanceIsNotAudited() {
        NoteTarget draft = NoteTarget.builder()
                .targetType(RecordNote.TARGET_TABLE).targetId("draft-abc")
                .tableKind("DW").tableId("42").functionUnitId("fu-7").build();
        when(processComponent.getProcessDetail("draft-abc")).thenReturn(null);
        when(functionUnitAccessComponent.canAccessFunctionUnit("u1", "fu-7")).thenReturn(true);
        when(recordNoteService.createComment(any(), any(), any(), any(), any(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n1").noteType(RecordNote.TYPE_COMMENT).bodyText("hi").build());

        component.createComment("u1", draft, null, "<p>hi</p>", List.of(), List.of(), null);

        verify(changeHistoryComponent, never()).recordNoteChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateAuditsBothSidesOfTheEdit() {
        when(recordNoteService.getLive("note-1")).thenReturn(storedNote(RecordNote.TARGET_TABLE, INSTANCE_ID));
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAccessProcessDetail(eq("u1"), any())).thenReturn(true);
        when(recordNoteService.update(eq("note-1"), any(), anyString(), eq("u1")))
                .thenReturn(NoteDetail.builder().id("note-1").subject("Subject")
                        .bodyHtml("<p>Revised body</p>").build());

        component.update("u1", "note-1", "Subject", "<p>Revised body</p>", null);

        verify(changeHistoryComponent).recordNoteChange(
                eq(INSTANCE_ID), eq("u1"), eq(ChangeType.RECORD_NOTE_UPDATE),
                isNull(), eq("Subject · Original body"), eq("Subject · Revised body"));
    }

    @Test
    void deleteAuditsTheRemovedNoteAsOldValue() {
        when(recordNoteService.getLive("note-1")).thenReturn(storedNote(RecordNote.TARGET_TABLE, INSTANCE_ID));
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAccessProcessDetail(eq("u1"), any())).thenReturn(true);

        component.delete("u1", "note-1", null);

        verify(recordNoteService).softDelete("note-1", "u1");
        verify(changeHistoryComponent).recordNoteChange(
                eq(INSTANCE_ID), eq("u1"), eq(ChangeType.RECORD_NOTE_DELETE),
                isNull(), eq("Subject · Original body"), isNull());
    }

    @Test
    void inlineImageUploadsAreNotAuditedSeparately() {
        when(processComponent.getProcessDetail(INSTANCE_ID)).thenReturn(instance);
        when(processComponent.canAccessProcessDetail(eq("u1"), any())).thenReturn(true);
        MockMultipartFile img = new MockMultipartFile(
                "file", "shot.png", "image/png", "x".getBytes(StandardCharsets.UTF_8));
        when(recordNoteService.createAttachment(any(), any(), anyBoolean(), anyString(), any()))
                .thenReturn(NoteItem.builder().id("n2").noteType(RecordNote.TYPE_ATTACHMENT)
                        .fileName("shot.png").build());

        component.createInlineImage("u1", tableTarget(), img);

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
