package com.portal.service;

import com.portal.dto.PageResponse;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Persistence-level operations for RecordNote (comments + attachments).
 * Permission checks live in {@code RecordNoteComponent}; this layer assumes
 * the caller is already authorized for the target.
 */
public interface RecordNoteService {

    PageResponse<NoteItem> list(NoteTarget target, int page, int size, String currentUserId);

    NoteDetail detail(String noteId, String currentUserId);

    /** Live (not soft-deleted) note by id, attachment content included; null when absent. */
    RecordNote getLive(String noteId);

    /**
     * Creates a COMMENT row plus one ATTACHMENT child per file; previously uploaded
     * inline-image attachments (ids in {@code adoptInlineIds}) are re-parented to it.
     */
    NoteItem createComment(NoteTarget target, String subject, String bodyHtml,
                           List<MultipartFile> files, List<String> adoptInlineIds,
                           String userId, String userName);

    /** Creates a standalone ATTACHMENT row (inline image or attachment-only entry). */
    NoteItem createAttachment(NoteTarget target, MultipartFile file, boolean inlineImage,
                              String userId, String userName);

    /** Updates own comment body/subject; re-sanitizes. */
    NoteDetail update(String noteId, String subject, String bodyHtml, String userId);

    /** Soft-deletes a note and its children. */
    void softDelete(String noteId, String userId);

    /** All live notes anchored to a process instance, oldest first (archive). */
    List<RecordNote> findRecordNotesForArchive(String processInstanceId);

    /** Re-anchors the author's draft notes onto a started instance; returns rows moved. */
    int adoptDraftNotes(String draftTargetId, String processInstanceId, String userId);

    class RecordNoteException extends RuntimeException {
        private final String code;

        public RecordNoteException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
