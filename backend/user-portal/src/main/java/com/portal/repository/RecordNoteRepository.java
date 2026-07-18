package com.portal.repository;

import com.portal.entity.RecordNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface RecordNoteRepository extends JpaRepository<RecordNote, String> {

    /**
     * Summary projection: never touches body_html / file_content, safe for list queries.
     */
    interface RecordNoteSummary {
        String getId();
        String getTargetType();
        String getTargetId();
        String getNoteType();
        String getParentNoteId();
        String getSubject();
        String getBodyText();
        String getFileName();
        String getMimeType();
        Long getFileSize();
        Boolean getIsInlineImage();
        String getCreatedBy();
        String getCreatedByName();
        Instant getCreatedAt();
        String getUpdatedBy();
        Instant getUpdatedAt();
    }

    /**
     * Timeline stream key = (targetType, targetId, tableId). Notes never cross
     * process instances: TABLE scope uses targetId = process instance id with
     * tableId picking the hosting table's stream; RECORD scope uses a row id.
     */
    Page<RecordNoteSummary> findByTargetTypeAndTargetIdAndTableIdAndParentNoteIdIsNullAndIsDeletedFalseOrderByCreatedAtDesc(
            String targetType, String targetId, String tableId, Pageable pageable);

    List<RecordNoteSummary> findByParentNoteIdInAndIsDeletedFalseOrderByCreatedAtAsc(Collection<String> parentNoteIds);

    /** All live notes anchored to a process instance, comments and attachments alike (archive). */
    List<RecordNote> findByTargetIdAndIsDeletedFalseOrderByCreatedAtAsc(String targetId);

    @Modifying
    @Query("UPDATE RecordNote n SET n.isDeleted = true, n.updatedBy = :userId "
            + "WHERE n.id = :id OR n.parentNoteId = :id")
    int softDeleteWithChildren(@Param("id") String id, @Param("userId") String userId);

    /**
     * Re-anchors draft notes (written on the New Request page before the process
     * exists) to the freshly started instance. Restricted to the author's rows.
     */
    @Modifying
    @Query("UPDATE RecordNote n SET n.targetId = :instanceId "
            + "WHERE n.targetId = :draftId AND n.createdBy = :userId AND n.isDeleted = false")
    int adoptDraftTarget(@Param("draftId") String draftId, @Param("instanceId") String instanceId,
                         @Param("userId") String userId);
}
