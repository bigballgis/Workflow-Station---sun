package com.developer.repository;

import com.developer.entity.OperationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志仓库
 */
@Repository
public interface OperationLogRepository extends JpaRepository<OperationLog, Long> {
    
    Page<OperationLog> findByOperator(String operator, Pageable pageable);
    
    Page<OperationLog> findByTargetTypeAndTargetId(String targetType, Long targetId, Pageable pageable);
    
    List<OperationLog> findByOperationTimeBetween(LocalDateTime start, LocalDateTime end);
    
    Page<OperationLog> findByOperationType(String operationType, Pageable pageable);
    
    @Query("SELECT o FROM OperationLog o WHERE " +
           "(:operationType IS NULL OR o.operationType = :operationType) AND " +
           "(:targetType IS NULL OR o.targetType = :targetType) AND " +
           "(:targetId IS NULL OR o.targetId = :targetId) AND " +
           "(:startTime IS NULL OR o.operationTime >= :startTime) AND " +
           "(:endTime IS NULL OR o.operationTime <= :endTime)")
    Page<OperationLog> findByQuery(@Param("operationType") String operationType,
                                    @Param("targetType") String targetType,
                                    @Param("targetId") Long targetId,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime,
                                    Pageable pageable);
}
