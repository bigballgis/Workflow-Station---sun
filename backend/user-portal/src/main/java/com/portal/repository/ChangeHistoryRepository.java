package com.portal.repository;

import com.portal.entity.ChangeHistory;
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
}
