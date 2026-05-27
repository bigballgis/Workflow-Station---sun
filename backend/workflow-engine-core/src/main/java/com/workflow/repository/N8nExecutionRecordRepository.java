package com.workflow.repository;

import com.workflow.entity.N8nExecutionRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * N8N execution record data access layer.
 */
@Repository
public interface N8nExecutionRecordRepository extends JpaRepository<N8nExecutionRecord, Long>,
        JpaSpecificationExecutor<N8nExecutionRecord> {

    /**
     * Find execution record by callback token.
     */
    Optional<N8nExecutionRecord> findByCallbackToken(String callbackToken);

    /**
     * Find execution records with the specified status whose start time is before
     * the cutoff (for timeout detection).
     */
    List<N8nExecutionRecord> findByStatusAndStartedAtBefore(String status, Instant cutoff);

    /**
     * Find execution records by process instance ID with pagination.
     */
    Page<N8nExecutionRecord> findByProcessInstanceId(String processInstanceId, Pageable pageable);

    /**
     * Find execution records by execution status with pagination.
     */
    Page<N8nExecutionRecord> findByStatus(String status, Pageable pageable);
}
