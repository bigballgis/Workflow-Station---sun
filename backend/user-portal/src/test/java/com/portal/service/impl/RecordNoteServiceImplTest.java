package com.portal.service.impl;

import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.repository.RecordNoteRepository;
import com.portal.service.RecordNoteService.RecordNoteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordNoteServiceImplTest {

    @Mock
    private RecordNoteRepository repository;

    private RecordNoteServiceImpl service;

    private final NoteTarget target = NoteTarget.builder()
            .targetType(RecordNote.TARGET_RECORD)
            .targetId("proc-1")
            .tableKind("DW")
            .tableId("42")
            .functionUnitId("fu-7")
            .build();

    @BeforeEach
    void setUp() {
        service = new RecordNoteServiceImpl(repository);
    }

    @Test
    void createCommentRejectsEmptyBodyWithoutFiles() {
        assertThatThrownBy(() -> service.createComment(target, null, "  ", List.of(), List.of(), "u1", "User One"))
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("attachment");
    }

    @Test
    void createCommentSanitizesBodyAndSavesCommentWithAttachmentChild() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "pdf-bytes".getBytes(StandardCharsets.UTF_8));

        NoteItem item = service.createComment(target, "subj",
                "<p>hi</p><script>alert(1)</script>", List.of(file), List.of(), "u1", "User One");

        assertThat(item).isNotNull();
        ArgumentCaptor<RecordNote> captor = ArgumentCaptor.forClass(RecordNote.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        RecordNote comment = captor.getAllValues().get(0);
        RecordNote child = captor.getAllValues().get(1);

        assertThat(comment.getNoteType()).isEqualTo(RecordNote.TYPE_COMMENT);
        assertThat(comment.getBodyHtml()).contains("hi").doesNotContain("script");
        assertThat(comment.getBodyText()).isEqualTo("hi");
        assertThat(comment.getTargetType()).isEqualTo(RecordNote.TARGET_RECORD);
        assertThat(child.getNoteType()).isEqualTo(RecordNote.TYPE_ATTACHMENT);
        assertThat(child.getParentNoteId()).isEqualTo(comment.getId());
        assertThat(child.getFileName()).isEqualTo("report.pdf");
        assertThat(child.getFileContent()).isNotEmpty();
    }

    @Test
    void createAttachmentRejectsOversizedFile() {
        byte[] big = new byte[(int) RecordNoteServiceImpl.MAX_FILE_SIZE_BYTES + 1];
        MockMultipartFile file = new MockMultipartFile("file", "big.bin", "application/octet-stream", big);

        assertThatThrownBy(() -> service.createAttachment(target, file, false, "u1", null))
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("10MB");
        verify(repository, never()).save(any());
    }

    @Test
    void createAttachmentSanitizesPathInFileName() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/passwd", "text/plain", "x".getBytes(StandardCharsets.UTF_8));

        service.createAttachment(target, file, false, "u1", null);

        ArgumentCaptor<RecordNote> captor = ArgumentCaptor.forClass(RecordNote.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getFileName()).isEqualTo("passwd");
    }

    @Test
    void updateRejectsNonOwner() {
        RecordNote note = RecordNote.builder()
                .id("n1").noteType(RecordNote.TYPE_COMMENT).createdBy("someone-else")
                .targetType(RecordNote.TARGET_RECORD).targetId("proc-1").tableId("42")
                .bodyHtml("<p>x</p>").isDeleted(false)
                .build();
        when(repository.findById("n1")).thenReturn(Optional.of(note));

        assertThatThrownBy(() -> service.update("n1", null, "<p>y</p>", "u1"))
                .isInstanceOf(RecordNoteException.class)
                .hasMessageContaining("author");
    }

    @Test
    void softDeletedNotesAreInvisible() {
        RecordNote deleted = RecordNote.builder()
                .id("n2").noteType(RecordNote.TYPE_COMMENT).createdBy("u1")
                .targetType(RecordNote.TARGET_RECORD).targetId("proc-1").tableId("42")
                .isDeleted(true)
                .build();
        when(repository.findById("n2")).thenReturn(Optional.of(deleted));

        assertThat(service.getLive("n2")).isNull();
    }
}
