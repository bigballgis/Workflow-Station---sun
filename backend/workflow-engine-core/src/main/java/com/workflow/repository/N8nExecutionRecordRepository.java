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
 * N8N 执行记录数据访问层
 */
@Repository
public interface N8nExecutionRecordRepository extends JpaRepository<N8nExecutionRecord, Long>,
        JpaSpecificationExecutor<N8nExecutionRecord> {

    /**
     * 根据回调令牌查询执行记录
     */
    Optional<N8nExecutionRecord> findByCallbackToken(String callbackToken);

    /**
     * 查询指定状态且开始时间早于截止时间的执行记录（用于超时检测）
     */
    List<N8nExecutionRecord> findByStatusAndStartedAtBefore(String status, Instant cutoff);

    /**
     * 根据流程实例ID分页查询执行记录
     */
    Page<N8nExecutionRecord> findByProcessInstanceId(String processInstanceId, Pageable pageable);

    /**
     * 根据执行状态分页查询执行记录
     */
    Page<N8nExecutionRecord> findByStatus(String status, Pageable pageable);
}
