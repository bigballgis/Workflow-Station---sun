package com.portal.component;

import com.portal.dto.PageResponse;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.enums.ChangeType;
import com.portal.service.RecordNoteService;
import com.portal.service.RecordNoteService.RecordNoteException;
import com.portal.service.UserDisplayNameResolver;
import com.portal.util.RecordNoteAuditSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
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
 * Access model — visibility follows the hosting request, never function unit roles:
 * - targetId resolving to a process instance: participants only (initiator /
 *   assignee / candidates), probed server-side against the instance store.
 * - sub-table row ids: the caller names the hosting instance and the same participant
 *   check runs against it. Row ids are predictable business keys, so this anchor is
 *   what stops enumeration; it is verified server-side, never taken on trust.
 * - New-Request drafts ("draft-" prefix): no instance exists yet; adoptDraftNotes
 *   re-anchors them onto the instance once the request starts.
 * - SYS_ADMIN bypasses all checks.
 *
 * Deliberately NOT a function unit role check: that would deny the very participants
 * working a row (e.g. an MI assignee whose task role was never granted FU access).
 *
 * Writing is gated separately and more tightly — see {@link #checkWriteAccess}: adding a
 * note requires an audit grant on the user's currently selected role (or SYS_ADMIN),
 * and participation in the request grants nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordNoteComponent {

    private final RecordNoteService recordNoteService;
    private final ProcessComponent processComponent;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final ChangeHistoryComponent changeHistoryComponent;

    public PageResponse<NoteItem> list(String userId, NoteTarget target, int page, int size,
                                       String processInstanceId) {
        checkTargetShape(target);
        checkAccess(userId, target, processInstanceId);
        return recordNoteService.list(target, page, size, userId);
    }

    public NoteDetail detail(String userId, String noteId, String processInstanceId) {
        RecordNote note = requireLive(noteId);
        checkAccess(userId, targetOf(note), processInstanceId);
        return recordNoteService.detail(noteId, userId);
    }

    /** Returns the live note after verifying read access; used by the download endpoint. */
    public RecordNote getForDownload(String userId, String noteId, String processInstanceId) {
        RecordNote note = requireLive(noteId);
        if (!RecordNote.TYPE_ATTACHMENT.equals(note.getNoteType())) {
            throw new RecordNoteException("NOT_ATTACHMENT", "Note has no downloadable content");
        }
        checkAccess(userId, targetOf(note), processInstanceId);
        return note;
    }

    public NoteItem createComment(String userId, NoteTarget target, String subject, String bodyHtml,
                                  List<MultipartFile> files, List<String> adoptInlineIds,
                                  String processInstanceId) {
        checkTargetShape(target);
        checkAccess(userId, target, processInstanceId);
        checkWriteAccess(userId, target, processInstanceId);
        NoteItem created = recordNoteService.createComment(target, subject, bodyHtml, files, adoptInlineIds,
                userId, resolveName(userId));
        audit(userId, target, processInstanceId, ChangeType.RECORD_NOTE_ADD,
                null, RecordNoteAuditSummary.created(created, uploadedFileNames(files)));
        return created;
    }

    public NoteItem createInlineImage(String userId, NoteTarget target, MultipartFile file,
                                      String processInstanceId) {
        checkTargetShape(target);
        checkAccess(userId, target, processInstanceId);
        checkWriteAccess(userId, target, processInstanceId);
        return recordNoteService.createAttachment(target, file, true, userId, resolveName(userId));
    }

    /** Whether this user may add notes to the given target; drives the UI's Add button. */
    public boolean canAddNote(String userId, NoteTarget target, String processInstanceId) {
        try {
            checkAccess(userId, target, processInstanceId);
            checkWriteAccess(userId, target, processInstanceId);
            return true;
        } catch (RecordNoteException e) {
            return false;
        }
    }

    /**
     * Notes are immutable once written — nobody may edit them, not even the author.
     * They are a record of what was said at a point in time, and the request they
     * belong to is reviewed on that basis; a later edit would rewrite that record.
     * Corrections are made by adding a further note.
     */
    public NoteDetail update(String userId, String noteId, String subject, String bodyHtml,
                             String processInstanceId) {
        requireLive(noteId);
        throw new RecordNoteException("NOT_EDITABLE", "Notes cannot be edited once written; add a new note instead");
    }

    /**
     * Notes are immutable once written — nobody may delete them, author and system
     * administrator alike. See {@link #update} for the reasoning.
     */
    public void delete(String userId, String noteId, String processInstanceId) {
        requireLive(noteId);
        throw new RecordNoteException("NOT_DELETABLE", "Notes cannot be deleted once written");
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
                && !processComponent.canAccessProcessDetail(userId, detail)) {
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
                && !processComponent.canAccessProcessDetail(userId, detail)) {
            throw new RecordNoteException("FORBIDDEN", "You are not a participant of this process");
        }
        return detail;
    }

    /**
     * Mirror the note mutation into the hosting instance's change history.
     * Silent no-op when no instance can be established — a New-Request draft has none yet
     * (its notes are re-anchored by {@link #adoptDraftNotes}), and change history is
     * per-instance by definition.
     */
    private void audit(String userId, NoteTarget target, String claimedProcessInstanceId,
                       ChangeType changeType, String oldValue, String newValue) {
        String processInstanceId = resolveAuditInstanceId(userId, target, claimedProcessInstanceId);
        if (processInstanceId == null) {
            return;
        }
        // RECORD scope: keep the entry pinned to its sub-table row so the multi-instance
        // row filter in ChangeHistoryComponent does not leak it onto sibling rows.
        String rowIdentifier = RecordNote.TARGET_RECORD.equals(target.getTargetType())
                && !processInstanceId.equals(target.getTargetId())
                ? target.getTargetId()
                : null;
        changeHistoryComponent.recordNoteChange(processInstanceId, userId, changeType,
                rowIdentifier, oldValue, newValue);
    }

    /**
     * The instance an audit row belongs to. A TABLE-scope target id *is* the instance id
     * (also true of legacy RECORD-on-instance rows). RECORD-scope targets are sub-table row
     * ids, so the caller supplies the hosting instance — accepted only after the same
     * gate that guards note access, never on the client's word alone.
     *
     * <p>Tracks {@code requireParticipant} deliberately: a reviewer who may add the note
     * must also be able to anchor it, otherwise the note lands with no audit trail.
     *
     * <p>Stays on the read gate rather than {@code checkWriteAccess}, and must remain at least as
     * permissive as it: every writer already clears this check (system administrators are exempt
     * outright, and an active-role audit holder passes {@code canAuditProcessDetail} through
     * {@code canAuditFunctionUnit}, which spans all of the user's roles and so is implied by a
     * grant on the active one). Tightening this to the write gate would gain nothing and risk
     * silently dropping an audit row.
     */
    private String resolveAuditInstanceId(String userId, NoteTarget target, String claimedProcessInstanceId) {
        if (target != null && processComponent.getProcessDetail(target.getTargetId()) != null) {
            return target.getTargetId();
        }
        if (claimedProcessInstanceId == null || claimedProcessInstanceId.isBlank()) {
            return null;
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(claimedProcessInstanceId);
        if (detail == null) {
            return null;
        }
        if (functionUnitAccessComponent.isSystemAdministrator(userId)
                || processComponent.canAuditProcessDetail(userId, detail)) {
            return claimedProcessInstanceId;
        }
        return null;
    }

    private static List<String> uploadedFileNames(List<MultipartFile> files) {
        List<String> names = new ArrayList<>();
        if (files == null) {
            return names;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty() && file.getOriginalFilename() != null) {
                names.add(file.getOriginalFilename());
            }
        }
        return names;
    }

    /**
     * Notes are visible to whoever can open the hosting request — no function unit role check.
     *
     * <p>A sub-table row target carries a bare row id, so the instance cannot be derived from it;
     * the caller supplies {@code hostProcessInstanceId} and it is verified here, never trusted.
     * Row ids are predictable business keys (ATM-DC-PW-TRANS-000004), so without that anchor any
     * authenticated user could enumerate other people's row notes.
     */
    private void checkAccess(String userId, NoteTarget target, String hostProcessInstanceId) {
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
            requireParticipant(userId, detail);
            return;
        }
        // New-Request drafts have no instance yet; only the author's own rows are ever reachable
        // (adoptDraftNotes re-anchors them once the request starts).
        if (isDraftTarget(target.getTargetId())) {
            return;
        }
        // Sub-table row target: authorize against the hosting instance the caller named.
        ProcessInstanceInfo host = hostProcessInstanceId == null || hostProcessInstanceId.isBlank()
                ? null
                : processComponent.getProcessDetail(hostProcessInstanceId);
        if (host == null) {
            throw new RecordNoteException("FORBIDDEN", "Cannot resolve the request hosting this row's notes");
        }
        requireParticipant(userId, host);
    }

    /**
     * Extra gate for <em>adding</em> a note, on top of the read gate.
     *
     * <p>Reading a request and commenting on it are different acts. Anyone who can open the
     * request may read its notes; writing one is an act of review, so it needs one of:
     * <ul>
     *   <li>system administrator;</li>
     *   <li>an audit grant on the function unit held by the user's <em>currently selected</em>
     *       role (admin-center → Function Unit → Access Config → Audit).</li>
     * </ul>
     *
     * <p>Participation in the request deliberately grants nothing here. Writing a note is done
     * <em>as</em> a role, so it is the audit configuration that decides — never who happens to be
     * working the request. An initiator or assignee whose active role holds no audit grant reads
     * the notes and may not add one; granting their role audit access in admin-center is the
     * supported way to let them write.
     */
    private void checkWriteAccess(String userId, NoteTarget target, String hostProcessInstanceId) {
        if (functionUnitAccessComponent.isSystemAdministrator(userId)) {
            return;
        }
        ProcessInstanceInfo detail = processComponent.getProcessDetail(target.getTargetId());
        if (detail == null) {
            // New-Request drafts have no instance yet; checkAccess already limited these to the
            // author's own rows, and the notes are re-anchored once the request starts.
            if (isDraftTarget(target.getTargetId())) {
                return;
            }
            detail = hostProcessInstanceId == null || hostProcessInstanceId.isBlank()
                    ? null
                    : processComponent.getProcessDetail(hostProcessInstanceId);
        }
        if (detail == null) {
            throw new RecordNoteException("FORBIDDEN", "Cannot resolve the request hosting this row's notes");
        }
        String functionUnitCode = detail.getFunctionUnitCode();
        if (functionUnitCode != null && !functionUnitCode.isBlank()
                && functionUnitAccessComponent.canAuditFunctionUnitAsActiveRole(userId, functionUnitCode)) {
            return;
        }
        log.debug("Note write denied for user {} on process {}: active role holds no audit grant "
                + "on function unit {}", userId, detail.getId(), functionUnitCode);
        throw new RecordNoteException("FORBIDDEN",
                "Your current role is not granted audit access to this function unit");
    }

    /**
     * Gate for reading and adding notes. Reviewers holding an audit grant pass here
     * — commenting is the point of the audit view — but deliberately not at
     * {@code requireArchiveAccess} (bulk export) or {@code adoptDraftNotes} (a write
     * that re-anchors someone else's drafts).
     */
    private void requireParticipant(String userId, ProcessInstanceInfo detail) {
        if (!processComponent.canAuditProcessDetail(userId, detail)) {
            throw new RecordNoteException("FORBIDDEN", "You are not a participant of this process");
        }
    }

    private static boolean isDraftTarget(String targetId) {
        return targetId != null && targetId.startsWith("draft-");
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
