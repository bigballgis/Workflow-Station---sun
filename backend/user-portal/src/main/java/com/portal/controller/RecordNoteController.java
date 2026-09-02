package com.portal.controller;

import com.platform.common.dto.ApiResponse;
import com.portal.component.RecordNoteArchiveComponent;
import com.portal.component.RecordNoteArchiveComponent.ArchiveResult;
import com.portal.component.RecordNoteComponent;
import com.portal.dto.PageResponse;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.security.CurrentUserId;
import com.portal.service.RecordNoteService.RecordNoteException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RecordNote endpoints (rich-text comments + attachments on tables / records).
 * Full paths are prefixed by the portal context path /api/portal.
 */
@Slf4j
@RestController
@RequestMapping("/record-notes")
@RequiredArgsConstructor
@Tag(name = "Record Notes", description = "Rich-text comments and file attachments on tables and records")
public class RecordNoteController {

    /** Content types safe to render inline; everything else is forced to download. */
    private static final Set<String> INLINE_SAFE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp",
            "application/pdf", "text/plain");

    private final RecordNoteComponent recordNoteComponent;
    private final RecordNoteArchiveComponent recordNoteArchiveComponent;

    @Operation(summary = "List notes of a target, newest first (summaries only)")
    @GetMapping
    public ApiResponse<PageResponse<NoteItem>> list(
            @CurrentUserId String userId,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String tableKind,
            @RequestParam String tableId,
            @RequestParam(required = false) String functionUnitId,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size) {
        int safeSize = size < 1 ? 5 : Math.min(size, 100);
        NoteTarget target = target(targetType, targetId, tableKind, tableId, functionUnitId);
        return ApiResponse.success(
                recordNoteComponent.list(userId, target, Math.max(page, 0), safeSize, processInstanceId));
    }

    /**
     * Separate from the list so the panel can hide its Add button up front. Reading and writing
     * notes follow different rules (see {@code RecordNoteComponent#checkWriteAccess}), and letting
     * the user compose a note only to have the POST rejected is the worst way to convey that.
     */
    @Operation(summary = "Whether the caller may add a note to this target")
    @GetMapping("/can-add")
    public ApiResponse<Boolean> canAdd(
            @CurrentUserId String userId,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String tableKind,
            @RequestParam String tableId,
            @RequestParam(required = false) String functionUnitId,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String taskId) {
        NoteTarget target = target(targetType, targetId, tableKind, tableId, functionUnitId);
        return ApiResponse.success(recordNoteComponent.canAddNote(userId, target, processInstanceId, taskId));
    }

    @Operation(summary = "Note detail including sanitized rich-text body")
    @GetMapping("/{noteId}")
    public ApiResponse<NoteDetail> detail(@CurrentUserId String userId, @PathVariable String noteId,
                                          @RequestParam(required = false) String processInstanceId) {
        return ApiResponse.success(recordNoteComponent.detail(userId, noteId, processInstanceId));
    }

    @Operation(summary = "Create a comment with optional attachments; adopts pre-uploaded inline images")
    @PostMapping
    public ApiResponse<NoteItem> create(
            @CurrentUserId String userId,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String tableKind,
            @RequestParam String tableId,
            @RequestParam(required = false) String functionUnitId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String bodyHtml,
            @RequestParam(required = false) List<String> inlineImageIds,
            @RequestParam(required = false) List<MultipartFile> files,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String taskId) {
        NoteTarget target = target(targetType, targetId, tableKind, tableId, functionUnitId);
        return ApiResponse.success(recordNoteComponent.createComment(
                userId, target, subject, bodyHtml, files, inlineImageIds, processInstanceId, taskId));
    }

    @Operation(summary = "Upload an inline image referenced from a rich-text body")
    @PostMapping("/inline-images")
    public ApiResponse<NoteItem> uploadInlineImage(
            @CurrentUserId String userId,
            @RequestParam String targetType,
            @RequestParam String targetId,
            @RequestParam(required = false) String tableKind,
            @RequestParam String tableId,
            @RequestParam(required = false) String functionUnitId,
            @RequestParam(required = false) String processInstanceId,
            @RequestParam(required = false) String taskId,
            @RequestParam MultipartFile file) {
        NoteTarget target = target(targetType, targetId, tableKind, tableId, functionUnitId);
        return ApiResponse.success(
                recordNoteComponent.createInlineImage(userId, target, file, processInstanceId, taskId));
    }

    @Operation(summary = "Re-anchor New-Request draft notes onto the started process instance")
    @PostMapping("/adopt")
    public ApiResponse<Integer> adoptDrafts(@CurrentUserId String userId,
                                            @RequestParam String draftTargetId,
                                            @RequestParam String processInstanceId) {
        return ApiResponse.success(recordNoteComponent.adoptDraftNotes(userId, draftTargetId, processInstanceId));
    }

    @Operation(summary = "Edit own comment")
    @PutMapping("/{noteId}")
    public ApiResponse<NoteDetail> update(@CurrentUserId String userId, @PathVariable String noteId,
                                          @RequestBody Map<String, String> body) {
        // processInstanceId only anchors the change-history entry for RECORD-scope notes
        // (sub-table rows); it is re-validated against the participant gate server-side.
        return ApiResponse.success(recordNoteComponent.update(
                userId, noteId, body.get("subject"), body.get("bodyHtml"), body.get("processInstanceId")));
    }

    @Operation(summary = "Soft-delete own note (admin may delete any)")
    @DeleteMapping("/{noteId}")
    public ApiResponse<Void> delete(@CurrentUserId String userId, @PathVariable String noteId,
                                    @RequestParam(required = false) String processInstanceId) {
        recordNoteComponent.delete(userId, noteId, processInstanceId);
        return ApiResponse.success();
    }

    @Operation(summary = "Download a single attachment / inline image")
    @GetMapping("/{noteId}/content")
    public ResponseEntity<byte[]> content(@CurrentUserId String userId, @PathVariable String noteId,
                                          @RequestParam(required = false) String processInstanceId) {
        RecordNote note;
        try {
            note = recordNoteComponent.getForDownload(userId, noteId, processInstanceId);
        } catch (RecordNoteException e) {
            return ResponseEntity.status(binaryStatus(e)).build();
        }
        String contentType = note.getMimeType();
        boolean inlineSafe = contentType != null && INLINE_SAFE_CONTENT_TYPES.contains(contentType);
        if (!inlineSafe) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        ContentDisposition disposition = (inlineSafe ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(note.getFileName() != null ? note.getFileName() : "attachment", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(note.getFileContent());
    }

    @Operation(summary = "Archive zip of a process instance: history + notes + attachments")
    @GetMapping("/archive/{processInstanceId}")
    public ResponseEntity<byte[]> archive(@CurrentUserId String userId, @PathVariable String processInstanceId) {
        ProcessInstanceInfo detail;
        try {
            detail = recordNoteComponent.requireArchiveAccess(userId, processInstanceId);
        } catch (RecordNoteException e) {
            return ResponseEntity.status(binaryStatus(e)).build();
        }
        ArchiveResult archive = recordNoteArchiveComponent.buildArchive(detail);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(archive.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(archive.content());
    }

    @ExceptionHandler(RecordNoteException.class)
    public ApiResponse<Void> handleRecordNoteException(RecordNoteException e) {
        String code = switch (e.getCode()) {
            // Notes are immutable, so edit/delete are refusals of permission, not bad input.
            case "FORBIDDEN", "NOT_OWNER", "NOT_EDITABLE", "NOT_DELETABLE" -> "403";
            case "NOT_FOUND" -> "404";
            default -> "400";
        };
        return ApiResponse.error(code, e.getMessage());
    }

    private static HttpStatus binaryStatus(RecordNoteException e) {
        return switch (e.getCode()) {
            case "FORBIDDEN", "NOT_OWNER", "NOT_EDITABLE", "NOT_DELETABLE" -> HttpStatus.FORBIDDEN;
            case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static NoteTarget target(String targetType, String targetId, String tableKind,
                                     String tableId, String functionUnitId) {
        return NoteTarget.builder()
                .targetType(targetType)
                .targetId(targetId)
                .tableKind(tableKind)
                .tableId(tableId)
                .functionUnitId(functionUnitId)
                .build();
    }
}
