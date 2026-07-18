package com.portal.service.impl;

import com.portal.dto.PageResponse;
import com.portal.dto.RecordNoteDtos.AttachmentInfo;
import com.portal.dto.RecordNoteDtos.NoteDetail;
import com.portal.dto.RecordNoteDtos.NoteItem;
import com.portal.dto.RecordNoteDtos.NoteTarget;
import com.portal.entity.RecordNote;
import com.portal.repository.RecordNoteRepository;
import com.portal.repository.RecordNoteRepository.RecordNoteSummary;
import com.portal.service.RecordNoteService;
import com.portal.util.RecordNoteHtmlSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecordNoteServiceImpl implements RecordNoteService {

    static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final RecordNoteRepository recordNoteRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<NoteItem> list(NoteTarget target, int page, int size, String currentUserId) {
        Page<RecordNoteSummary> result = recordNoteRepository
                .findByTargetTypeAndTargetIdAndTableIdAndParentNoteIdIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
                        target.getTargetType(), target.getTargetId(), target.getTableId(),
                        PageRequest.of(page, size));

        List<String> commentIds = result.getContent().stream()
                .filter(s -> RecordNote.TYPE_COMMENT.equals(s.getNoteType()))
                .map(RecordNoteSummary::getId)
                .collect(Collectors.toList());
        Map<String, List<RecordNoteSummary>> childrenByParent = commentIds.isEmpty()
                ? Map.of()
                : recordNoteRepository.findByParentNoteIdInAndIsDeletedFalseOrderByCreatedAtAsc(commentIds).stream()
                        .collect(Collectors.groupingBy(RecordNoteSummary::getParentNoteId));

        List<NoteItem> items = result.getContent().stream()
                .map(s -> toItem(s, childrenByParent.getOrDefault(s.getId(), List.of()), currentUserId))
                .collect(Collectors.toList());
        return PageResponse.of(items, page, size, result.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public NoteDetail detail(String noteId, String currentUserId) {
        RecordNote note = requireLive(noteId);
        List<RecordNoteSummary> children = recordNoteRepository
                .findByParentNoteIdInAndIsDeletedFalseOrderByCreatedAtAsc(List.of(note.getId()));
        return NoteDetail.builder()
                .id(note.getId())
                .noteType(note.getNoteType())
                .subject(note.getSubject())
                .bodyHtml(note.getBodyHtml())
                .createdBy(note.getCreatedBy())
                .createdByName(note.getCreatedByName())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .editable(note.getCreatedBy().equals(currentUserId))
                .attachments(children.stream().map(RecordNoteServiceImpl::toAttachmentInfo).collect(Collectors.toList()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public RecordNote getLive(String noteId) {
        return recordNoteRepository.findById(noteId)
                .filter(n -> !Boolean.TRUE.equals(n.getIsDeleted()))
                .orElse(null);
    }

    @Override
    @Transactional
    public NoteItem createComment(NoteTarget target, String subject, String bodyHtml,
                                  List<MultipartFile> files, List<String> adoptInlineIds,
                                  String userId, String userName) {
        String sanitized = RecordNoteHtmlSupport.sanitize(bodyHtml);
        boolean hasFiles = files != null && files.stream().anyMatch(f -> f != null && !f.isEmpty());
        if (sanitized == null && !hasFiles) {
            throw new RecordNoteException("EMPTY_NOTE", "Note requires a body or at least one attachment");
        }

        if (sanitized == null) {
            // Attachment-only entry: first file becomes the standalone top-level row,
            // extra files become their own standalone rows as well.
            NoteItem first = null;
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                NoteItem item = createAttachment(target, file, false, userId, userName);
                if (first == null) {
                    first = item;
                }
            }
            return first;
        }

        RecordNote comment = baseNote(target, userId, userName);
        comment.setNoteType(RecordNote.TYPE_COMMENT);
        comment.setSubject(trimTo(subject, 255));
        comment.setBodyHtml(sanitized);
        comment.setBodyText(RecordNoteHtmlSupport.extractText(sanitized));
        recordNoteRepository.save(comment);

        List<AttachmentInfo> attachments = new ArrayList<>();
        if (hasFiles) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                RecordNote child = attachmentNote(target, file, false, userId, userName);
                child.setParentNoteId(comment.getId());
                recordNoteRepository.save(child);
                attachments.add(toAttachmentInfo(child));
            }
        }
        attachments.addAll(adoptInlineImages(comment, adoptInlineIds, userId));

        return NoteItem.builder()
                .id(comment.getId())
                .noteType(comment.getNoteType())
                .subject(comment.getSubject())
                .bodyText(comment.getBodyText())
                .createdBy(userId)
                .createdByName(userName)
                .createdAt(comment.getCreatedAt() != null ? comment.getCreatedAt() : Instant.now())
                .editable(true)
                .attachments(attachments)
                .build();
    }

    @Override
    @Transactional
    public NoteItem createAttachment(NoteTarget target, MultipartFile file, boolean inlineImage,
                                     String userId, String userName) {
        RecordNote note = attachmentNote(target, file, inlineImage, userId, userName);
        recordNoteRepository.save(note);
        return NoteItem.builder()
                .id(note.getId())
                .noteType(note.getNoteType())
                .fileName(note.getFileName())
                .mimeType(note.getMimeType())
                .fileSize(note.getFileSize())
                .createdBy(userId)
                .createdByName(userName)
                .createdAt(note.getCreatedAt() != null ? note.getCreatedAt() : Instant.now())
                .editable(true)
                .attachments(List.of())
                .build();
    }

    @Override
    @Transactional
    public NoteDetail update(String noteId, String subject, String bodyHtml, String userId) {
        RecordNote note = requireLive(noteId);
        if (!note.getCreatedBy().equals(userId)) {
            throw new RecordNoteException("NOT_OWNER", "Only the author can edit a note");
        }
        if (!RecordNote.TYPE_COMMENT.equals(note.getNoteType())) {
            throw new RecordNoteException("NOT_EDITABLE", "Attachments cannot be edited");
        }
        String sanitized = RecordNoteHtmlSupport.sanitize(bodyHtml);
        if (sanitized == null) {
            throw new RecordNoteException("EMPTY_NOTE", "Note body cannot be empty");
        }
        note.setSubject(trimTo(subject, 255));
        note.setBodyHtml(sanitized);
        note.setBodyText(RecordNoteHtmlSupport.extractText(sanitized));
        note.setUpdatedBy(userId);
        recordNoteRepository.save(note);
        return detail(noteId, userId);
    }

    @Override
    @Transactional
    public void softDelete(String noteId, String userId) {
        requireLive(noteId);
        recordNoteRepository.softDeleteWithChildren(noteId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecordNote> findRecordNotesForArchive(String processInstanceId) {
        // Notes are instance-scoped: TABLE streams anchor on the instance id too,
        // so everything with this targetId belongs to the archived process.
        return recordNoteRepository.findByTargetIdAndIsDeletedFalseOrderByCreatedAtAsc(processInstanceId);
    }

    @Override
    @Transactional
    public int adoptDraftNotes(String draftTargetId, String processInstanceId, String userId) {
        return recordNoteRepository.adoptDraftTarget(draftTargetId, processInstanceId, userId);
    }

    private List<AttachmentInfo> adoptInlineImages(RecordNote comment, List<String> adoptInlineIds, String userId) {
        if (adoptInlineIds == null || adoptInlineIds.isEmpty()) {
            return List.of();
        }
        List<AttachmentInfo> adopted = new ArrayList<>();
        for (String inlineId : adoptInlineIds) {
            recordNoteRepository.findById(inlineId)
                    .filter(n -> !Boolean.TRUE.equals(n.getIsDeleted()))
                    .filter(n -> n.getParentNoteId() == null)
                    .filter(n -> Boolean.TRUE.equals(n.getIsInlineImage()))
                    .filter(n -> n.getCreatedBy().equals(userId))
                    .filter(n -> n.getTargetType().equals(comment.getTargetType())
                            && n.getTargetId().equals(comment.getTargetId()))
                    .ifPresent(n -> {
                        n.setParentNoteId(comment.getId());
                        recordNoteRepository.save(n);
                        adopted.add(toAttachmentInfo(n));
                    });
        }
        return adopted;
    }

    private RecordNote attachmentNote(NoteTarget target, MultipartFile file, boolean inlineImage,
                                      String userId, String userName) {
        if (file == null || file.isEmpty()) {
            throw new RecordNoteException("FILE_EMPTY", "Uploaded file is empty");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new RecordNoteException("FILE_TOO_LARGE", "File exceeds the 10MB limit");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new RecordNoteException("FILE_READ_FAILED", "Failed to read uploaded file");
        }
        RecordNote note = baseNote(target, userId, userName);
        note.setNoteType(RecordNote.TYPE_ATTACHMENT);
        note.setFileName(sanitizeFileName(file.getOriginalFilename()));
        note.setMimeType(trimTo(file.getContentType(), 255));
        note.setFileSize(file.getSize());
        note.setFileContent(content);
        note.setIsInlineImage(inlineImage);
        return note;
    }

    private RecordNote baseNote(NoteTarget target, String userId, String userName) {
        return RecordNote.builder()
                .id(UUID.randomUUID().toString())
                .targetType(target.getTargetType())
                .targetId(target.getTargetId())
                .tableKind(target.getTableKind() == null ? "DW" : target.getTableKind())
                .tableId(target.getTableId())
                .functionUnitId(target.getFunctionUnitId())
                .createdBy(userId)
                .createdByName(trimTo(userName, 100))
                .build();
    }

    private RecordNote requireLive(String noteId) {
        RecordNote note = getLive(noteId);
        if (note == null) {
            throw new RecordNoteException("NOT_FOUND", "Note not found: " + noteId);
        }
        return note;
    }

    private NoteItem toItem(RecordNoteSummary s, List<RecordNoteSummary> children, String currentUserId) {
        return NoteItem.builder()
                .id(s.getId())
                .noteType(s.getNoteType())
                .subject(s.getSubject())
                .bodyText(s.getBodyText())
                .fileName(s.getFileName())
                .mimeType(s.getMimeType())
                .fileSize(s.getFileSize())
                .createdBy(s.getCreatedBy())
                .createdByName(s.getCreatedByName())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .editable(s.getCreatedBy().equals(currentUserId))
                .attachments(children.stream()
                        .filter(c -> !Boolean.TRUE.equals(c.getIsInlineImage()))
                        .map(RecordNoteServiceImpl::toAttachmentInfo)
                        .collect(Collectors.toList()))
                .build();
    }

    private static AttachmentInfo toAttachmentInfo(RecordNoteSummary s) {
        return AttachmentInfo.builder()
                .id(s.getId())
                .fileName(s.getFileName())
                .mimeType(s.getMimeType())
                .fileSize(s.getFileSize())
                .isInlineImage(s.getIsInlineImage())
                .build();
    }

    private static AttachmentInfo toAttachmentInfo(RecordNote n) {
        return AttachmentInfo.builder()
                .id(n.getId())
                .fileName(n.getFileName())
                .mimeType(n.getMimeType())
                .fileSize(n.getFileSize())
                .isInlineImage(n.getIsInlineImage())
                .build();
    }

    private static String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "attachment";
        }
        String name = original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1);
        return trimTo(name.isBlank() ? "attachment" : name, 255);
    }

    private static String trimTo(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
