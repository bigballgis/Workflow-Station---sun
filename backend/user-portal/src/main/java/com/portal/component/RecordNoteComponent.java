package com.portal.component;

import com.portal.dto.PageResponse;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.service.RecordNoteService;
import com.portal.service.RecordNoteService.RecordNoteException;
import com.portal.service.UserDisplayNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RecordNote orchestration: target validation + access control, then delegates
 * persistence to {@link RecordNoteService}.
 *
 * Notes never cross process instances. Stream identity = (targetType, targetId,
 * tableId): TABLE scope anchors on targetId = process instance id (the hosting
 * table's shared stream within that one process); RECORD scope anchors on a
 * sub-table row id.
 *
 * Access model:
 * - targetId resolving to a process instance: participants only (initiator /
 *   assignee / candidates), probed server-side against the instance store.
 * - otherwise (sub-table row ids): falls back to function unit access —
 *   row-level involvement is not resolvable from a bare row id.
 * - SYS_ADMIN bypasses all checks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordNoteComponent {

    private final RecordNoteService recordNoteService;
    private final ProcessComponent processComponent;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final UserDisplayNameResolver userDisplayNameResolver;

    public PageResponse<NoteItem> list(String userId, NoteTarget target, int page, int size) {
        checkTargetShape(target);
        checkAccess(userId, target);
        return recordNoteService.list(target, page, size, userId);
    }

    public NoteDetail detail(String userId, String noteId) {
        RecordNote note = requireLive(noteId);
        checkAccess(userId, targetOf(note));
        return recordNoteService.detail(noteId, userId);
    }

    /** Returns the live note after verifying read access; used by the download endpoint. */
    public RecordNote getForDownload(String userId, String noteId) {
        RecordNote note = requireLive(noteId);
        if (!RecordNote.TYPE_ATTACHMENT.equals(note.getNoteType())) {
            throw new RecordNoteException("NOT_ATTACHMENT", "Note has no downloadable content");
        }
        checkAccess(userId, targetOf(note));
        return note;
    }

    public NoteItem createComment(String userId, NoteTarget target, String subject, String bodyHtml,
                                  List<MultipartFile> files, List<String> adoptInlineIds) {
        checkTargetShape(target);
        checkAccess(userId, target);
        return recordNoteService.createComment(target, subject, bodyHtml, files, adoptInlineIds,
                userId, resolveName(userId));
    }

    public NoteItem createInlineImage(String userId, NoteTarget target, MultipartFile file) {
        checkTargetShape(target);
        checkAccess(userId, target);
        return recordNoteService.createAttachment(target, file, true, userId, resolveName(userId));
    }

    public NoteDetail update(String userId, String noteId, String subject, String bodyHtml) {
        RecordNote note = requireLive(noteId);
        checkAccess(userId, targetOf(note));
        return recordNoteService.update(noteId, subject, bodyHtml, userId);
    }

    public void delete(String userId, String noteId) {
        RecordNote note = requireLive(noteId);
        checkAccess(userId, targetOf(note));
        boolean owner = note.getCreatedBy().equals(userId);
        if (!owner && !functionUnitAccessComponent.isSystemAdministrator(userId)) {
            throw new RecordNoteException("NOT_OWNER", "Only the author or an administrator can delete a note");
        }
        recordNoteService.softDelete(noteId, userId);
    }

    /**
     * Re-anchors New-Request draft notes onto the instance the user just started.
     * Draft ids carry a fixed prefix so an arbitrary instance/row id can never be
     * mass-re-targeted; only the author's own rows move.
     */
    public int adoptDraftNotes(String userId, String draftTargetId, String processInstanceId) {
        if (userId == null || userId.isBlank()) {
            throw new RecordNoteException("FORBIDDEN", "Missing user identity");
        }
        if (draftTargetId == null || !draftTargetId.startsWith("draft-") || draftTargetId.length() > 64) {
            throw new RecordNoteException("BAD_TARGET", "draftTargetId must start with 'draft-'");
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processInstanceId);
        if (detail == null) {
            throw new RecordNoteException("NOT_FOUND", "Process instance not found: " + processInstanceId);
        }
        if (!functionUnitAccessComponent.isSystemAdministrator(userId)
                && !processComponent.isProcessParticipant(userId, detail)) {
            throw new RecordNoteException("FORBIDDEN", "You are not a participant of this process");
        }
        return recordNoteService.adoptDraftNotes(draftTargetId, processInstanceId, userId);
    }

    /** Participant-or-admin gate for the per-instance archive download. */
    public ProcessInstanceInfo requireArchiveAccess(String userId, String processInstanceId) {
        ProcessInstanceInfo detail = processComponent.getProcessDetail(processInstanceId);
        if (detail == null) {
            throw new RecordNoteException("NOT_FOUND", "Process instance not found: " + processInstanceId);
        }
        if (!functionUnitAccessComponent.isSystemAdministrator(userId)
                && !processComponent.isProcessParticipant(userId, detail)) {
            throw new RecordNoteException("FORBIDDEN", "You are not a participant of this process");
        }
        return detail;
    }

    private void checkAccess(String userId, NoteTarget target) {
        if (userId == null || userId.isBlank()) {
            throw new RecordNoteException("FORBIDDEN", "Missing user identity");
        }
        if (functionUnitAccessComponent.isSystemAdministrator(userId)) {
            return;
        }
        // Both scopes anchor on the process instance when one matches the target id
        // (TABLE = per-instance table stream; legacy RECORD-on-instance rows too).
        ProcessInstanceInfo detail = processComponent.getProcessDetail(target.getTargetId());
        if (detail != null) {
            if (!processComponent.isProcessParticipant(userId, detail)) {
                throw new RecordNoteException("FORBIDDEN", "You are not a participant of this process");
            }
            return;
        }
        // Sub-table row targets: fall back to function unit access.
        String functionUnitId = target.getFunctionUnitId();
        if (functionUnitId == null || functionUnitId.isBlank()
                || !functionUnitAccessComponent.canAccessFunctionUnit(userId, functionUnitId)) {
            throw new RecordNoteException("FORBIDDEN", "No access to this function unit's notes");
        }
    }

    private void checkTargetShape(NoteTarget target) {
        if (target == null
                || target.getTargetType() == null || target.getTargetId() == null || target.getTargetId().isBlank()
                || target.getTableId() == null || target.getTableId().isBlank()) {
            throw new RecordNoteException("BAD_TARGET", "targetType, targetId and tableId are required");
        }
        if (!RecordNote.TARGET_TABLE.equals(target.getTargetType())
                && !RecordNote.TARGET_RECORD.equals(target.getTargetType())) {
            throw new RecordNoteException("BAD_TARGET", "targetType must be TABLE or RECORD");
        }
        String kind = target.getTableKind();
        if (kind != null && !"DW".equals(kind) && !"RT".equals(kind)) {
            throw new RecordNoteException("BAD_TARGET", "tableKind must be DW or RT");
        }
    }

    private NoteTarget targetOf(RecordNote note) {
        return NoteTarget.builder()
                .targetType(note.getTargetType())
                .targetId(note.getTargetId())
                .tableKind(note.getTableKind())
                .tableId(note.getTableId())
                .functionUnitId(note.getFunctionUnitId())
                .build();
    }

    private RecordNote requireLive(String noteId) {
        RecordNote note = recordNoteService.getLive(noteId);
        if (note == null) {
            throw new RecordNoteException("NOT_FOUND", "Note not found: " + noteId);
        }
        return note;
    }

    private String resolveName(String userId) {
        try {
            return userDisplayNameResolver.resolve(userId);
        } catch (Exception e) {
            log.debug("Display name resolution failed for {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
