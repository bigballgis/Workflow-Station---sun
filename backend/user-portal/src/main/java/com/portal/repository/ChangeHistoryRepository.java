package com.portal.repository;

import com.portal.entity.ChangeHistory;
import com.portal.enums.ChangeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 变更历史 Repository
 */
@Repository
public interface ChangeHistoryRepository extends JpaRepository<ChangeHistory, Long> {

    List<ChangeHistory> findByProcessInstanceIdOrderByTimestampAsc(String processInstanceId);

    List<ChangeHistory> findByTaskInstanceId(String taskInstanceId);

    @Modifying
    @Query("DELETE FROM ChangeHistory c WHERE c.processInstanceId = :processInstanceId")
    void deleteByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    /** Batch delete for purge flows — one statement instead of one per instance id. */
    @Modifying
    @Query("DELETE FROM ChangeHistory c WHERE c.processInstanceId IN :processInstanceIds")
    void deleteByProcessInstanceIdIn(@Param("processInstanceIds") List<String> processInstanceIds);

    /**
     * Find the most recent change record for a specific sub-table row change,
     * ordered by timestamp descending. Used for deduplication on re-save.
     */
        ChangeHistory findTopByProcessInstanceIdAndSubTableNameAndRowIdentifierAndFieldNameAndChangeTypeOrderByTimestampDesc(
            String processInstanceId, String subTableName, String rowIdentifier, String fieldName, ChangeType changeType);
}
