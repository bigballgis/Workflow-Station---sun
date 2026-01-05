package com.admin.repository;

import com.admin.entity.VirtualGroupTaskHistory;
import com.admin.enums.TaskActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 虚拟组任务历史仓库接口
 */
@Repository
public interface VirtualGroupTaskHistoryRepository extends JpaRepository<VirtualGroupTaskHistory, String> {
    
    /**
     * 根据任务ID查找历史记录
     */
    List<VirtualGroupTaskHistory> findByTaskIdOrderByCreatedAtDesc(String taskId);
    
    /**
     * 根据虚拟组ID查找历史记录
     */
    List<VirtualGroupTaskHistory> findByGroupIdOrderByCreatedAtDesc(String groupId);
    
    /**
     * 根据虚拟组ID分页查找历史记录
     */
    Page<VirtualGroupTaskHistory> findByGroupId(String groupId, Pageable pageable);
    
    /**
     * 根据用户ID查找历史记录（作为操作发起者）
     */
    List<VirtualGroupTaskHistory> findByFromUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 根据用户ID查找历史记录（作为操作接收者）
     */
    List<VirtualGroupTaskHistory> findByToUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * 根据操作类型查找历史记录
     */
    List<VirtualGroupTaskHistory> findByActionTypeOrderByCreatedAtDesc(TaskActionType actionType);
    
    /**
     * 查找任务的认领记录
     */
    @Query("SELECT h FROM VirtualGroupTaskHistory h WHERE h.taskId = :taskId AND h.actionType = 'CLAIMED'")
    List<VirtualGroupTaskHistory> findClaimHistoryByTaskId(@Param("taskId") String taskId);
    
    /**
     * 查找任务的委托记录
     */
    @Query("SELECT h FROM VirtualGroupTaskHistory h WHERE h.taskId = :taskId AND h.actionType = 'DELEGATED'")
    List<VirtualGroupTaskHistory> findDelegationHistoryByTaskId(@Param("taskId") String taskId);
    
    /**
     * 根据时间范围查找历史记录
     */
    @Query("SELECT h FROM VirtualGroupTaskHistory h WHERE h.createdAt BETWEEN :startTime AND :endTime ORDER BY h.createdAt DESC")
    List<VirtualGroupTaskHistory> findByTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);
    
    /**
     * 统计虚拟组的任务处理数量
     */
    long countByGroupIdAndActionType(String groupId, TaskActionType actionType);
}
