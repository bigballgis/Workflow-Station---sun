package com.portal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Record-level note: a rich-text comment or a file attachment
 * (Dataverse annotation-style single table, see up_record_note).
 *
 * Notes never cross process instances; the stream key is
 * (target_type, target_id, table_id):
 * target_type = TABLE  -> target_id = process instance id, table_id picks the
 *                         hosting table's shared stream within that process
 * target_type = RECORD -> target_id = sub-table row id
 */
@Entity
@Table(name = "up_record_note")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RecordNote {

    public static final String TARGET_TABLE = "TABLE";
    public static final String TARGET_RECORD = "RECORD";
    public static final String TYPE_COMMENT = "COMMENT";
    public static final String TYPE_ATTACHMENT = "ATTACHMENT";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64)
    private String targetId;

    @Column(name = "table_kind", nullable = false, length = 10)
    @Builder.Default
    private String tableKind = "DW";

    @Column(name = "table_id", nullable = false, length = 64)
    private String tableId;

    @Column(name = "function_unit_id", length = 64)
    private String functionUnitId;

    @Column(name = "note_type", nullable = false, length = 20)
    private String noteType;

    @Column(name = "parent_note_id", length = 64)
    private String parentNoteId;

    @Column(length = 255)
    private String subject;

    @Column(name = "body_html", columnDefinition = "TEXT")
    private String bodyHtml;

    @Column(name = "body_text", columnDefinition = "TEXT")
    private String bodyText;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_content", columnDefinition = "BYTEA")
    private byte[] fileContent;

    @Column(name = "is_inline_image", nullable = false)
    @Builder.Default
    private Boolean isInlineImage = false;

    @Column(name = "created_by", nullable = false, length = 64, updatable = false)
    private String createdBy;

    @Column(name = "created_by_name", length = 100, updatable = false)
    private String createdByName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Version
    @Column(name = "lock_version", nullable = false)
    @Builder.Default
    private Long lockVersion = 0L;
}
