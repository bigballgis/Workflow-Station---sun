package com.portal.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * DTOs for the RecordNote feature (rich-text comments + attachments).
 */
public final class RecordNoteDtos {

    private RecordNoteDtos() {
    }

    /** One attachment chip in a list entry. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentInfo {
        private String id;
        private String fileName;
        private String mimeType;
        private Long fileSize;
        private Boolean isInlineImage;
    }

    /** Top-level timeline entry: a comment (with its attachments) or a standalone attachment. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteItem {
        private String id;
        private String noteType;
        private String subject;
        private String bodyText;
        /** Sanitized rich-text body — rendered directly in the list. */
        private String bodyHtml;
        private String fileName;
        private String mimeType;
        private Long fileSize;
        private String createdBy;
        private String createdByName;
        private Instant createdAt;
        private Instant updatedAt;
        private Boolean editable;
        private List<AttachmentInfo> attachments;
    }

    /** Detail view of a comment, includes the sanitized rich-text body. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteDetail {
        private String id;
        private String noteType;
        private String subject;
        private String bodyHtml;
        private String createdBy;
        private String createdByName;
        private Instant createdAt;
        private Instant updatedAt;
        private Boolean editable;
        private List<AttachmentInfo> attachments;
    }

    /** Target descriptor shared by create/list calls. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NoteTarget {
        private String targetType;
        private String targetId;
        private String tableKind;
        private String tableId;
        private String functionUnitId;
    }
}
