package com.workflow.repository;

import com.workflow.entity.ApExecutionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Activepieces execution record data access layer.
 */
@Repository
public interface ApExecutionRecordRepository extends JpaRepository<ApExecutionRecord, Long>,
        JpaSpecificationExecutor<ApExecutionRecord> {

    /**
     * Find execution records by process instance ID with pagination.
     */
    Page<ApExecutionRecord> findByProcessInstanceId(String processInstanceId, Pageable pageable);

    /**
     * Find execution records by execution status with pagination.
     */
    Page<ApExecutionRecord> findByStatus(String status, Pageable pageable);
}
